package dev.miles.fliplink.data.pm

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.Build
import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.VerificationState
import dev.miles.fliplink.domain.planner.InputValidator
import org.xmlpull.v1.XmlPullParser

data class DiscoveredApp(
    val packageName: String,
    val displayLabel: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val declaredWebHosts: List<String>,
)

data class MergedAppLink(
    val packageName: String,
    val displayLabel: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val linkInfo: AppLinkInfo,
)

interface PackageDiscoverySource {
    fun discoverWebLinkApps(): List<DiscoveredApp>
    fun resolveAppMetadata(packageName: String): DiscoveredApp?
}

class PackageDiscovery(
    context: Context,
) : PackageDiscoverySource {
    private val pm: PackageManager = context.applicationContext.packageManager

    override fun discoverWebLinkApps(): List<DiscoveredApp> {
        val installedApps = getInstalledApplicationsAll()
        val result = mutableListOf<DiscoveredApp>()

        for (appInfo in installedApps) {
            val hosts = extractManifestWebHosts(appInfo)
            if (hosts.isEmpty()) continue
            result.add(toDiscoveredApp(appInfo.packageName, appInfo, hosts))
        }

        return result.sortedWith(
            compareBy<DiscoveredApp> { it.displayLabel.lowercase() }
                .thenBy { it.packageName },
        )
    }

    override fun resolveAppMetadata(packageName: String): DiscoveredApp? {
        val appInfo = getApplicationInfoOrNull(packageName) ?: return null
        return toDiscoveredApp(packageName, appInfo, declaredWebHosts = emptyList())
    }

    private fun getInstalledApplicationsAll(): List<ApplicationInfo> {
        val flags = PackageManager.MATCH_ALL
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(flags)
        }
    }

    private fun extractManifestWebHosts(appInfo: ApplicationInfo): List<String> {
        val resources = try {
            pm.getResourcesForApplication(appInfo)
        } catch (_: Throwable) {
            return emptyList()
        }

        return try {
            resources.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                parseManifestWebHosts(parser, resources)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun getApplicationInfoOrNull(packageName: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun toDiscoveredApp(
        packageName: String,
        appInfo: ApplicationInfo,
        declaredWebHosts: List<String>,
    ): DiscoveredApp {
        val label = appInfo.loadLabel(pm).toString().ifBlank { packageName }
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return DiscoveredApp(
            packageName = packageName,
            displayLabel = label,
            isSystemApp = isSystem,
            isEnabled = appInfo.enabled,
            declaredWebHosts = declaredWebHosts,
        )
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        internal fun parseManifestWebHosts(
            parser: XmlResourceParser,
            resources: Resources?,
        ): List<String> {
            val discoveredHosts = LinkedHashSet<String>()
            var componentDepth = 0
            var inIntentFilter = false
            var hasViewAction = false
            var hasBrowsableCategory = false
            var hasWebScheme = false
            val filterHosts = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "activity", "activity-alias" -> componentDepth++
                            "intent-filter" -> if (componentDepth > 0) {
                                inIntentFilter = true
                                hasViewAction = false
                                hasBrowsableCategory = false
                                hasWebScheme = false
                                filterHosts.clear()
                            }

                            "action" -> if (inIntentFilter) {
                                val name = readAndroidAttr(parser, resources, "name")
                                if (name == Intent.ACTION_VIEW) {
                                    hasViewAction = true
                                }
                            }

                            "category" -> if (inIntentFilter) {
                                val name = readAndroidAttr(parser, resources, "name")
                                if (name == Intent.CATEGORY_BROWSABLE) {
                                    hasBrowsableCategory = true
                                }
                            }

                            "data" -> if (inIntentFilter) {
                                val scheme = readAndroidAttr(parser, resources, "scheme")?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    hasWebScheme = true
                                }
                                val host = readAndroidAttr(parser, resources, "host")?.trim()
                                if (!host.isNullOrEmpty() && host != "*") {
                                    filterHosts.add(host)
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "intent-filter" -> {
                                if (inIntentFilter && hasViewAction && hasBrowsableCategory && hasWebScheme) {
                                    for (host in filterHosts) {
                                        if (InputValidator.isValidDomain(host, allowAllLiteral = false)) {
                                            discoveredHosts.add(host)
                                        }
                                    }
                                }
                                inIntentFilter = false
                            }

                            "activity", "activity-alias" -> {
                                if (componentDepth > 0) componentDepth--
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return discoveredHosts.sorted()
        }

        private fun readAndroidAttr(
            parser: XmlResourceParser,
            resources: Resources?,
            attrName: String,
        ): String? {
            val raw = parser.getAttributeValue(ANDROID_NAMESPACE, attrName)
            if (raw != null && !raw.startsWith("@")) return raw
            val resId = parser.getAttributeResourceValue(ANDROID_NAMESPACE, attrName, 0)
            if (resId != 0 && resources != null) {
                return try {
                    resources.getString(resId)
                } catch (_: Throwable) {
                    null
                }
            }
            return raw
        }

        fun mergeWithPm(
            pmApps: List<AppLinkInfo>,
            discovered: List<DiscoveredApp>,
            resolveFallback: (String) -> DiscoveredApp?,
        ): List<MergedAppLink> {
            val discoveredByPkg = discovered.associateBy { it.packageName }
            val result = mutableListOf<MergedAppLink>()

            for (pmApp in pmApps) {
                if (pmApp.domains.isEmpty()) continue
                val meta = discoveredByPkg[pmApp.packageName]
                    ?: resolveFallback(pmApp.packageName)
                    ?: continue
                result.add(
                    MergedAppLink(
                        packageName = pmApp.packageName,
                        displayLabel = meta.displayLabel,
                        isSystemApp = meta.isSystemApp,
                        isEnabled = meta.isEnabled,
                        linkInfo = pmApp,
                    ),
                )
            }

            return result.sortedWith(
                compareBy<MergedAppLink> { it.displayLabel.lowercase() }
                    .thenBy { it.packageName },
            )
        }

        fun toReadOnlyMerged(discovered: List<DiscoveredApp>): List<MergedAppLink> {
            return discovered
                .filter { it.declaredWebHosts.isNotEmpty() }
                .map { app ->
                    val entries = app.declaredWebHosts.map { host ->
                        DomainEntry(
                            domain = host,
                            isAutoVerify = false,
                            verificationState = VerificationState.None,
                            userSelected = false,
                            status = DomainStatus.Declared,
                            approvalLevel = ApprovalLevel.NONE,
                        )
                    }
                    MergedAppLink(
                        packageName = app.packageName,
                        displayLabel = app.displayLabel,
                        isSystemApp = app.isSystemApp,
                        isEnabled = app.isEnabled,
                        linkInfo = AppLinkInfo(
                            packageName = app.packageName,
                            linkHandlingAllowed = true,
                            domains = entries,
                            hasAutoVerifyBlock = false,
                            isReadOnly = true,
                        ),
                    )
                }
                .sortedWith(
                    compareBy<MergedAppLink> { it.displayLabel.lowercase() }
                        .thenBy { it.packageName },
                )
        }
    }
}
