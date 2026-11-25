package dev.miles.fliplink.data

import dev.miles.fliplink.data.pm.DiscoveredApp
import dev.miles.fliplink.data.pm.PackageDiscoverySource
import dev.miles.fliplink.data.shizuku.ShellExecutor
import dev.miles.fliplink.data.shizuku.ShellResult
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.data.storage.SnapshotStore
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.DriftItem
import dev.miles.fliplink.domain.model.PmCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class AppLinksRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakePackageDiscovery(
        private val discovered: List<DiscoveredApp>,
        private val fallback: Map<String, DiscoveredApp> = emptyMap(),
    ) : PackageDiscoverySource {
        override fun discoverWebLinkApps(): List<DiscoveredApp> = discovered
        override fun resolveAppMetadata(packageName: String): DiscoveredApp? = fallback[packageName]
    }

    private class FakePmShellExecutor(
        private val delayMs: Long = 0L,
    ) : ShellExecutor {
        val executedCommands = mutableListOf<PmCommand>()
        val activeCalls = AtomicInteger(0)
        val maxConcurrentCalls = AtomicInteger(0)

        data class FakePkgState(
            var id: String = "00000000-0000-0000-0000-000000000001",
            val autoVerifyStates: LinkedHashMap<String, String> = linkedMapOf(),
            var linkHandlingAllowed: Boolean = true,
            val enabledDomains: LinkedHashSet<String> = linkedSetOf(),
            val disabledDomains: LinkedHashSet<String> = linkedSetOf(),
        ) {
            fun render(packageName: String): String = buildString {
                if (autoVerifyStates.isNotEmpty()) {
                    appendLine("  $packageName:")
                    appendLine("    ID: $id")
                    appendLine("    Signatures: [AA:BB]")
                    appendLine("    Domain verification state:")
                    for ((domain, state) in autoVerifyStates) {
                        appendLine("      $domain: $state")
                    }
                } else {
                    appendLine("  $packageName $id:")
                }
                appendLine("    User 0:")
                appendLine("      Verification link handling allowed: $linkHandlingAllowed")
                appendLine("      Selection state:")
                if (enabledDomains.isNotEmpty()) {
                    appendLine("        Enabled:")
                    for (d in enabledDomains) {
                        appendLine("          $d")
                    }
                }
                if (disabledDomains.isNotEmpty()) {
                    appendLine("        Disabled:")
                    for (d in disabledDomains) {
                        appendLine("          $d")
                    }
                }
            }
        }

        val packages = LinkedHashMap<String, FakePkgState>()

        override suspend fun execPm(command: PmCommand): ShellResult {
            val current = activeCalls.incrementAndGet()
            maxConcurrentCalls.updateAndGet { maxOf(it, current) }
            try {
                if (delayMs > 0) delay(delayMs)
                executedCommands.add(command)
                return handleCommand(command)
            } finally {
                activeCalls.decrementAndGet()
            }
        }

        private fun handleCommand(cmd: PmCommand): ShellResult {
            return when (cmd.subcommand) {
                "get-app-links" -> {
                    if (cmd.args.size == 2) {
                        val out = packages.entries.joinToString("\n") { (pkg, state) -> state.render(pkg) }
                        ShellResult(0, out, "")
                    } else {
                        val pkg = cmd.args[2]
                        val state = packages[pkg]
                            ?: return ShellResult(1, "", "Package $pkg not found")
                        ShellResult(0, state.render(pkg), "")
                    }
                }

                "set-app-links-allowed" -> {
                    val pkg = cmd.args[3]
                    val allowed = cmd.args[4].toBoolean()
                    val state = packages[pkg] ?: return ShellResult(1, "", "Unknown package $pkg")
                    state.linkHandlingAllowed = allowed
                    ShellResult(0, "", "")
                }

                "set-app-links-user-selection" -> {
                    val pkg = cmd.args[3]
                    val enabled = cmd.args[4].toBoolean()
                    val state = packages[pkg] ?: return ShellResult(1, "", "Unknown package $pkg")
                    val targets = if (cmd.args[5] == "all") {
                        (state.enabledDomains + state.disabledDomains + state.autoVerifyStates.keys).toList()
                    } else {
                        cmd.args.subList(5, cmd.args.size)
                    }
                    for (domain in targets) {
                        if (enabled) {
                            state.disabledDomains.remove(domain)
                            state.enabledDomains.add(domain)
                        } else {
                            state.enabledDomains.remove(domain)
                            state.disabledDomains.add(domain)
                        }
                    }
                    ShellResult(0, "", "")
                }

                "set-app-links" -> {
                    val pkg = cmd.args[1]
                    val code = cmd.args[2]
                    val state = packages[pkg] ?: return ShellResult(1, "", "Unknown package $pkg")
                    val label = when (code) {
                        "0" -> "none"
                        "1" -> "verified"
                        "2" -> "approved"
                        "3" -> "denied"
                        else -> "none"
                    }
                    val targets = if (cmd.args[3] == "all") {
                        state.autoVerifyStates.keys.toList()
                    } else {
                        cmd.args.subList(3, cmd.args.size)
                    }
                    for (domain in targets) {
                        if (domain in state.autoVerifyStates) {
                            state.autoVerifyStates[domain] = label
                        }
                    }
                    ShellResult(0, "", "")
                }

                else -> ShellResult(1, "", "Unsupported command: ${cmd.subcommand}")
            }
        }
    }

    @Test
    fun readOnlyFallbackMarksDeclaredHostsWithoutShellCalls() = runBlocking {
        val discovery = FakePackageDiscovery(
            discovered = listOf(
                DiscoveredApp(
                    packageName = "dev.miles.fliplink.fixture.patched",
                    displayLabel = "Patched Fixture",
                    isSystemApp = false,
                    isEnabled = true,
                    declaredWebHosts = listOf("alt.fliplink.example", "test.fliplink.example"),
                ),
            ),
        )
        val executor = FakePmShellExecutor()
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
        val repo = AppLinksRepository(discovery, executor, store, shizukuState)

        repo.refresh()

        val state = repo.state.value
        assertTrue(state.isReadOnly)
        assertEquals(0, executor.executedCommands.size)
        assertEquals(1, state.apps.size)
        assertTrue(state.apps.first().linkInfo.domains.all { it.status == DomainStatus.Declared })
        assertTrue(state.conflictsByPackage.isEmpty())
        assertTrue(state.drifts.isEmpty())
    }

    @Test
    fun privilegedRefreshLeftJoinsMetadataAndSkipsUninstalled() = runBlocking {
        val discovery = FakePackageDiscovery(
            discovered = listOf(
                DiscoveredApp("com.example.alpha", "Alpha", isSystemApp = false, isEnabled = true, listOf("a.example.com")),
            ),
            fallback = mapOf(
                "com.example.disabled" to DiscoveredApp(
                    packageName = "com.example.disabled",
                    displayLabel = "Disabled Component App",
                    isSystemApp = true,
                    isEnabled = false,
                    declaredWebHosts = emptyList(),
                ),
            ),
        )
        val executor = FakePmShellExecutor().apply {
            packages["com.example.alpha"] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf("a.example.com"),
            )
            packages["com.example.disabled"] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf("b.example.com"),
            )
            packages["com.example.uninstalled"] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf("gone.example.com"),
            )
        }
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Ready(version = 13, uid = 2000))
        val repo = AppLinksRepository(discovery, executor, store, shizukuState)

        repo.refresh()

        val state = repo.state.value
        assertFalse(state.isReadOnly)
        assertEquals(listOf("com.example.alpha", "com.example.disabled"), state.apps.map { it.packageName })
    }

    @Test
    fun singleBulkAndMasterTogglesVerifyViaRereadAndPersistSnapshot() = runBlocking {
        val pkg = "dev.miles.fliplink.fixture.patched"
        val discovery = FakePackageDiscovery(
            listOf(DiscoveredApp(pkg, "Patched", isSystemApp = false, isEnabled = true, listOf("a.example", "b.example"))),
        )
        val executor = FakePmShellExecutor().apply {
            packages[pkg] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf("a.example", "b.example"),
            )
        }
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Ready(13, 2000))
        val repo = AppLinksRepository(discovery, executor, store, shizukuState) { 42L }

        repo.refresh()
        assertTrue(repo.setDomainEnabled(pkg, "a.example", true).isSuccess)
        assertEquals(
            DomainStatus.Selected,
            repo.state.value.apps.first().linkInfo.domains.first { it.domain == "a.example" }.status,
        )
        assertEquals(setOf("a.example"), store.load().packages[pkg]?.selectedDomains)

        assertTrue(repo.setAllDomainsEnabled(pkg, true).isSuccess)
        assertEquals(setOf("a.example", "b.example"), store.load().packages[pkg]?.selectedDomains)

        assertTrue(repo.setLinkHandlingAllowed(pkg, false).isSuccess)
        assertFalse(repo.state.value.apps.first().linkInfo.linkHandlingAllowed)
        assertFalse(store.load().packages.getValue(pkg).linkHandlingAllowed)

        // Verify post-mutation pm get-app-links --user 0 <pkg> re-reads occurred
        val rereads = executor.executedCommands.filter {
            it.subcommand == "get-app-links" && it.args == listOf("--user", "0", pkg)
        }
        assertEquals(3, rereads.size)
    }

    @Test
    fun takeoverDemotesCompetitorPromotesTargetAndUndoRestoresBoth() = runBlocking {
        val stock = "dev.miles.fliplink.fixture.stock"
        val patched = "dev.miles.fliplink.fixture.patched"
        val domain = "test.fliplink.example"

        val discovery = FakePackageDiscovery(
            listOf(
                DiscoveredApp(stock, "Stock", isSystemApp = false, isEnabled = true, listOf(domain)),
                DiscoveredApp(patched, "Patched", isSystemApp = false, isEnabled = true, listOf(domain)),
            ),
        )
        val executor = FakePmShellExecutor().apply {
            packages[stock] = FakePmShellExecutor.FakePkgState(
                autoVerifyStates = linkedMapOf(domain to "verified"),
                disabledDomains = linkedSetOf(),
            )
            packages[patched] = FakePmShellExecutor.FakePkgState(
                linkHandlingAllowed = false,
                disabledDomains = linkedSetOf(domain),
            )
        }
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Ready(13, 2000))
        val repo = AppLinksRepository(discovery, executor, store, shizukuState) { 100L }

        repo.refresh()
        assertTrue(repo.state.value.conflictsByPackage[patched]?.get(domain)?.isOutranked == true)

        val takeover = repo.takeOverDomain(patched, domain)
        assertTrue(takeover is TakeoverResult.Success)
        val undoSnapshot = (takeover as TakeoverResult.Success).undoSnapshot

        val afterTakeover = repo.state.value.apps.associateBy { it.packageName }
        assertEquals(
            DomainStatus.Denied,
            afterTakeover.getValue(stock).linkInfo.domains.first { it.domain == domain }.status,
        )
        assertEquals(
            DomainStatus.Selected,
            afterTakeover.getValue(patched).linkInfo.domains.first { it.domain == domain }.status,
        )
        assertTrue(afterTakeover.getValue(patched).linkInfo.linkHandlingAllowed)
        assertEquals(setOf(domain), store.load().packages[stock]?.deniedDomains)
        assertTrue(repo.state.value.conflictsByPackage[patched].isNullOrEmpty())

        assertTrue(repo.undoTakeover(undoSnapshot).isSuccess)
        val afterUndo = repo.state.value.apps.associateBy { it.packageName }
        assertEquals(
            DomainStatus.Verified,
            afterUndo.getValue(stock).linkInfo.domains.first { it.domain == domain }.status,
        )
        assertEquals(
            DomainStatus.Disabled,
            afterUndo.getValue(patched).linkInfo.domains.first { it.domain == domain }.status,
        )
        assertFalse(afterUndo.getValue(patched).linkInfo.linkHandlingAllowed)
        assertTrue(store.load().packages[stock]?.deniedDomains.orEmpty().isEmpty())
    }

    @Test
    fun driftDetectionSupportsReapplyAndAccept() = runBlocking {
        val stock = "dev.miles.fliplink.fixture.stock"
        val patched = "dev.miles.fliplink.fixture.patched"
        val domain = "test.fliplink.example"

        val discovery = FakePackageDiscovery(
            listOf(
                DiscoveredApp(stock, "Stock", isSystemApp = false, isEnabled = true, listOf(domain)),
                DiscoveredApp(patched, "Patched", isSystemApp = false, isEnabled = true, listOf(domain)),
            ),
        )
        val executor = FakePmShellExecutor().apply {
            packages[stock] = FakePmShellExecutor.FakePkgState(
                autoVerifyStates = linkedMapOf(domain to "verified"),
            )
            packages[patched] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf(domain),
            )
        }
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Ready(13, 2000))
        val repo = AppLinksRepository(discovery, executor, store, shizukuState) { 200L }

        repo.refresh()
        assertTrue(repo.takeOverDomain(patched, domain) is TakeoverResult.Success)
        assertTrue(repo.state.value.drifts.isEmpty())

        // Simulate external system re-verification on competitor & disabled selection on target
        executor.packages.getValue(stock).autoVerifyStates[domain] = "verified"
        executor.packages.getValue(patched).enabledDomains.remove(domain)
        executor.packages.getValue(patched).disabledDomains.add(domain)

        repo.refresh()
        val drifts = repo.state.value.drifts
        assertEquals(2, drifts.size)
        assertTrue(drifts.first { it.packageName == stock }.items.contains(DriftItem.CompetitorReclaimed(setOf(domain))))
        assertTrue(drifts.first { it.packageName == patched }.items.contains(DriftItem.SelectionLost(setOf(domain))))

        // Reapply patched only
        assertTrue(repo.reapplyDrift(patched).isSuccess)
        assertEquals(listOf(stock), repo.state.value.drifts.map { it.packageName })

        // Accept stock drift
        repo.acceptDrift(stock)
        assertTrue(repo.state.value.drifts.isEmpty())
        assertTrue(store.load().packages[stock]?.deniedDomains.orEmpty().isEmpty())
    }

    @Test
    fun concurrentMutationsAreSerializedByMutex() = runBlocking {
        val pkg = "dev.miles.fliplink.fixture.patched"
        val discovery = FakePackageDiscovery(
            listOf(DiscoveredApp(pkg, "Patched", isSystemApp = false, isEnabled = true, listOf("a.example", "b.example"))),
        )
        val executor = FakePmShellExecutor(delayMs = 25L).apply {
            packages[pkg] = FakePmShellExecutor.FakePkgState(
                disabledDomains = linkedSetOf("a.example", "b.example"),
            )
        }
        val store = SnapshotStore(tempFolder.root)
        val shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.Ready(13, 2000))
        val repo = AppLinksRepository(discovery, executor, store, shizukuState)

        repo.refresh()
        val results = listOf(
            async { repo.setDomainEnabled(pkg, "a.example", true) },
            async { repo.setDomainEnabled(pkg, "b.example", true) },
        ).awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, executor.maxConcurrentCalls.get())
    }
}
