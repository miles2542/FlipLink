package dev.miles.fliplink.data

import dev.miles.fliplink.data.pm.MergedAppLink
import dev.miles.fliplink.data.pm.PackageDiscovery
import dev.miles.fliplink.data.pm.PackageDiscoverySource
import dev.miles.fliplink.data.shizuku.ShellExecutor
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.data.storage.SnapshotStore
import dev.miles.fliplink.domain.model.AppDrift
import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.FlipLinkSnapshot
import dev.miles.fliplink.domain.model.PackageDesiredState
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.model.TakeoverUndoSnapshot
import dev.miles.fliplink.domain.model.VerificationState
import dev.miles.fliplink.domain.parser.PmAppLinksParser
import dev.miles.fliplink.domain.planner.AdbCommandBuilder
import dev.miles.fliplink.domain.planner.ConflictPlanner
import dev.miles.fliplink.domain.planner.DriftDetector
import dev.miles.fliplink.domain.planner.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RepositoryState(
    val apps: List<MergedAppLink> = emptyList(),
    val conflictsByPackage: Map<String, Map<String, ConflictInfo>> = emptyMap(),
    val inFlightKeys: Set<String> = emptySet(),
    val drifts: List<AppDrift> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isReadOnly: Boolean = true,
    val snapshot: FlipLinkSnapshot = FlipLinkSnapshot(),
)

sealed interface TakeoverResult {
    data class Success(val undoSnapshot: TakeoverUndoSnapshot) : TakeoverResult
    data class Failure(val message: String) : TakeoverResult
}

class AppLinksRepository(
    private val packageDiscovery: PackageDiscoverySource,
    private val shellExecutor: ShellExecutor,
    private val snapshotStore: SnapshotStore,
    private val shizukuState: StateFlow<ShizukuState>,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(RepositoryState())
    val state: StateFlow<RepositoryState> = _state.asStateFlow()

    suspend fun refresh() {
        mutex.withLock {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            val discovered = withContext(Dispatchers.IO) {
                packageDiscovery.discoverWebLinkApps()
            }
            val savedSnapshot = withContext(Dispatchers.IO) {
                snapshotStore.load()
            }

            if (!shizukuState.value.isReady) {
                val readOnlyApps = PackageDiscovery.toReadOnlyMerged(discovered)
                _state.update {
                    it.copy(
                        apps = readOnlyApps,
                        conflictsByPackage = emptyMap(),
                        drifts = emptyList(),
                        isLoading = false,
                        errorMessage = null,
                        isReadOnly = true,
                        snapshot = savedSnapshot,
                    )
                }
                return@withLock
            }

            val cmd = InputValidator.validatePmCommand("get-app-links", listOf("--user", "0"))
            val result = shellExecutor.execPm(cmd)
            if (!result.isSuccess) {
                val fallbackApps = if (_state.value.apps.isEmpty()) {
                    PackageDiscovery.toReadOnlyMerged(discovered)
                } else {
                    _state.value.apps
                }
                _state.update {
                    it.copy(
                        apps = fallbackApps,
                        isLoading = false,
                        errorMessage = result.stderr.ifBlank { "pm get-app-links failed (exit ${result.exitCode})" },
                        isReadOnly = fallbackApps.all { app -> app.linkInfo.isReadOnly },
                        snapshot = savedSnapshot,
                    )
                }
                return@withLock
            }

            val pmApps = PmAppLinksParser.parseAll(result.stdout)
            val merged = withContext(Dispatchers.IO) {
                PackageDiscovery.mergeWithPm(pmApps, discovered) { pkg ->
                    packageDiscovery.resolveAppMetadata(pkg)
                }
            }

            publishMergedState(
                merged = merged,
                snapshot = savedSnapshot,
                errorMessage = null,
            )
        }
    }

    suspend fun setLinkHandlingAllowed(packageName: String, allowed: Boolean): Result<Unit> {
        InputValidator.requireValidPackageName(packageName)
        if (!shizukuState.value.isReady) {
            return Result.failure(IllegalStateException("Shizuku is not ready"))
        }

        val key = packageFlightKey(packageName)
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val cmd = AdbCommandBuilder.buildLinkHandlingAllowedCommand(packageName, allowed)
                val execResult = shellExecutor.execPm(cmd)
                if (!execResult.isSuccess) {
                    val msg = execResult.stderr.ifBlank { "Failed to update link handling (exit ${execResult.exitCode})" }
                    _state.update { it.copy(errorMessage = msg) }
                    return@withLock Result.failure(IllegalStateException(msg))
                }

                val updatedInfo = rereadPackageLocked(packageName) ?: run {
                    val msg = "Failed to re-read $packageName after update"
                    _state.update { it.copy(errorMessage = msg) }
                    return@withLock Result.failure(IllegalStateException(msg))
                }

                val updatedSnapshot = updateSnapshotForPackage(
                    current = withContext(Dispatchers.IO) { snapshotStore.load() },
                    liveInfo = updatedInfo,
                )
                withContext(Dispatchers.IO) { snapshotStore.save(updatedSnapshot) }

                val merged = replacePackageInMerged(_state.value.apps, updatedInfo)
                publishMergedState(merged = merged, snapshot = updatedSnapshot, errorMessage = null)
                Result.success(Unit)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun setDomainEnabled(
        packageName: String,
        domain: String,
        enabled: Boolean,
    ): Result<Unit> {
        InputValidator.requireValidPackageName(packageName)
        InputValidator.requireValidDomain(domain, allowAllLiteral = false)
        if (!shizukuState.value.isReady) {
            return Result.failure(IllegalStateException("Shizuku is not ready"))
        }

        val key = domainFlightKey(packageName, domain)
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val currentInfo = _state.value.apps.firstOrNull { it.packageName == packageName }?.linkInfo
                val currentEntry = currentInfo?.domains?.firstOrNull { it.domain == domain }

                val commands = mutableListOf<PmCommand>()
                commands.addAll(
                    AdbCommandBuilder.buildUserSelectionCommand(packageName, enabled, listOf(domain)),
                )

                if (currentEntry != null && currentEntry.isAutoVerify) {
                    if (enabled && currentEntry.verificationState == VerificationState.Denied) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links",
                                listOf("--package", packageName, VerificationState.PM_STATE_APPROVED.toString(), domain),
                            ),
                        )
                    } else if (!enabled && currentEntry.verificationState.isApprovedOrVerified) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links",
                                listOf("--package", packageName, VerificationState.PM_STATE_DENIED.toString(), domain),
                            ),
                        )
                    }
                }

                val failure = executeCommandsSequentially(commands)
                if (failure != null) {
                    rereadAndPublishBestEffort(setOf(packageName), failure)
                    return@withLock Result.failure(IllegalStateException(failure))
                }

                val updatedInfo = rereadPackageLocked(packageName) ?: run {
                    val msg = "Failed to re-read $packageName after update"
                    _state.update { it.copy(errorMessage = msg) }
                    return@withLock Result.failure(IllegalStateException(msg))
                }

                val currentSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
                val existingDenied = currentSnapshot.packages[packageName]?.deniedDomains.orEmpty()
                val nextDenied = when {
                    enabled -> existingDenied - domain
                    currentEntry?.isAutoVerify == true -> existingDenied + domain
                    else -> existingDenied
                }
                val updatedSnapshot = updateSnapshotForPackage(
                    current = currentSnapshot,
                    liveInfo = updatedInfo,
                    deniedOverride = nextDenied,
                )
                withContext(Dispatchers.IO) { snapshotStore.save(updatedSnapshot) }

                val merged = replacePackageInMerged(_state.value.apps, updatedInfo)
                publishMergedState(merged = merged, snapshot = updatedSnapshot, errorMessage = null)
                Result.success(Unit)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun setAllDomainsEnabled(packageName: String, enabled: Boolean): Result<Unit> {
        InputValidator.requireValidPackageName(packageName)
        if (!shizukuState.value.isReady) {
            return Result.failure(IllegalStateException("Shizuku is not ready"))
        }

        val key = packageFlightKey(packageName)
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val currentInfo = _state.value.apps.firstOrNull { it.packageName == packageName }?.linkInfo
                val commands = mutableListOf<PmCommand>()
                commands.add(AdbCommandBuilder.buildUserSelectionAllCommand(packageName, enabled))

                if (currentInfo != null) {
                    val autoVerifyToApprove = if (enabled) {
                        currentInfo.domains
                            .filter { it.isAutoVerify && it.verificationState == VerificationState.Denied }
                            .map { it.domain }
                    } else {
                        emptyList()
                    }
                    val autoVerifyToDeny = if (!enabled) {
                        currentInfo.domains
                            .filter { it.isAutoVerify && it.verificationState.isApprovedOrVerified }
                            .map { it.domain }
                    } else {
                        emptyList()
                    }

                    for (chunk in InputValidator.chunkDomains(autoVerifyToApprove)) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links",
                                listOf("--package", packageName, VerificationState.PM_STATE_APPROVED.toString()) + chunk,
                            ),
                        )
                    }
                    for (chunk in InputValidator.chunkDomains(autoVerifyToDeny)) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links",
                                listOf("--package", packageName, VerificationState.PM_STATE_DENIED.toString()) + chunk,
                            ),
                        )
                    }
                }

                val failure = executeCommandsSequentially(commands)
                if (failure != null) {
                    rereadAndPublishBestEffort(setOf(packageName), failure)
                    return@withLock Result.failure(IllegalStateException(failure))
                }

                val updatedInfo = rereadPackageLocked(packageName) ?: run {
                    val msg = "Failed to re-read $packageName after update"
                    _state.update { it.copy(errorMessage = msg) }
                    return@withLock Result.failure(IllegalStateException(msg))
                }

                val currentSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
                val nextDenied = if (enabled) {
                    emptySet()
                } else {
                    updatedInfo.domains.filter { it.isAutoVerify }.map { it.domain }.toSet()
                }
                val updatedSnapshot = updateSnapshotForPackage(
                    current = currentSnapshot,
                    liveInfo = updatedInfo,
                    deniedOverride = nextDenied,
                )
                withContext(Dispatchers.IO) { snapshotStore.save(updatedSnapshot) }

                val merged = replacePackageInMerged(_state.value.apps, updatedInfo)
                publishMergedState(merged = merged, snapshot = updatedSnapshot, errorMessage = null)
                Result.success(Unit)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun takeOverDomain(targetPkg: String, domain: String): TakeoverResult {
        InputValidator.requireValidPackageName(targetPkg)
        InputValidator.requireValidDomain(domain, allowAllLiteral = false)
        if (!shizukuState.value.isReady) {
            return TakeoverResult.Failure("Shizuku is not ready")
        }

        val key = domainFlightKey(targetPkg, domain)
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val allInfos = _state.value.apps.map { it.linkInfo }
                val plan = try {
                    ConflictPlanner.planTakeover(
                        targetPackage = targetPkg,
                        domains = listOf(domain),
                        allPackages = allInfos,
                    )
                } catch (e: IllegalArgumentException) {
                    return@withLock TakeoverResult.Failure(e.message ?: "Invalid takeover plan")
                }

                val failure = executeCommandsSequentially(plan.commands)
                if (failure != null) {
                    rereadAndPublishBestEffort(plan.modifiedPackages, failure)
                    return@withLock TakeoverResult.Failure(failure)
                }

                val updatedByPkg = LinkedHashMap<String, AppLinkInfo>()
                for (pkg in plan.modifiedPackages) {
                    val info = rereadPackageLocked(pkg)
                    if (info != null) {
                        updatedByPkg[pkg] = info
                    }
                }

                var updatedSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
                updatedByPkg[targetPkg]?.let { targetInfo ->
                    val existingDenied = updatedSnapshot.packages[targetPkg]?.deniedDomains.orEmpty()
                    updatedSnapshot = updateSnapshotForPackage(
                        current = updatedSnapshot,
                        liveInfo = targetInfo,
                        deniedOverride = existingDenied - domain,
                    )
                }
                for (competitorPkg in plan.undoSnapshot.competitorPriorStates.keys) {
                    val compInfo = updatedByPkg[competitorPkg] ?: continue
                    val existingDenied = updatedSnapshot.packages[competitorPkg]?.deniedDomains.orEmpty()
                    updatedSnapshot = updateSnapshotForPackage(
                        current = updatedSnapshot,
                        liveInfo = compInfo,
                        deniedOverride = existingDenied + domain,
                    )
                }
                withContext(Dispatchers.IO) { snapshotStore.save(updatedSnapshot) }

                var merged = _state.value.apps
                for (info in updatedByPkg.values) {
                    merged = replacePackageInMerged(merged, info)
                }
                publishMergedState(merged = merged, snapshot = updatedSnapshot, errorMessage = null)
                TakeoverResult.Success(plan.undoSnapshot)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun undoTakeover(snapshot: TakeoverUndoSnapshot): Result<Unit> {
        if (!shizukuState.value.isReady) {
            return Result.failure(IllegalStateException("Shizuku is not ready"))
        }

        val key = packageFlightKey(snapshot.targetPackage)
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val modifiedPackages = buildSet {
                    add(snapshot.targetPackage)
                    addAll(snapshot.competitorPriorStates.keys)
                }

                val failure = executeCommandsSequentially(snapshot.restoreCommands)
                if (failure != null) {
                    rereadAndPublishBestEffort(modifiedPackages, failure)
                    return@withLock Result.failure(IllegalStateException(failure))
                }

                val updatedByPkg = LinkedHashMap<String, AppLinkInfo>()
                for (pkg in modifiedPackages) {
                    val info = rereadPackageLocked(pkg)
                    if (info != null) {
                        updatedByPkg[pkg] = info
                    }
                }

                var storedSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
                updatedByPkg[snapshot.targetPackage]?.let { targetInfo ->
                    storedSnapshot = updateSnapshotForPackage(
                        current = storedSnapshot,
                        liveInfo = targetInfo,
                    )
                }
                for ((competitorPkg, priorState) in snapshot.competitorPriorStates) {
                    val compInfo = updatedByPkg[competitorPkg] ?: continue
                    val existingDenied = storedSnapshot.packages[competitorPkg]?.deniedDomains.orEmpty()
                    storedSnapshot = updateSnapshotForPackage(
                        current = storedSnapshot,
                        liveInfo = compInfo,
                        deniedOverride = existingDenied - priorState.domainStates.keys,
                    )
                }
                withContext(Dispatchers.IO) { snapshotStore.save(storedSnapshot) }

                var merged = _state.value.apps
                for (info in updatedByPkg.values) {
                    merged = replacePackageInMerged(merged, info)
                }
                publishMergedState(merged = merged, snapshot = storedSnapshot, errorMessage = null)
                Result.success(Unit)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun reapplyDrift(packageName: String? = null): Result<Unit> {
        if (!shizukuState.value.isReady) {
            return Result.failure(IllegalStateException("Shizuku is not ready"))
        }

        val key = packageName?.let { packageFlightKey(it) } ?: DRIFT_ALL_FLIGHT_KEY
        addInFlightKey(key)
        return try {
            mutex.withLock {
                val targetDrifts = if (packageName == null) {
                    _state.value.drifts
                } else {
                    _state.value.drifts.filter { it.packageName == packageName }
                }
                if (targetDrifts.isEmpty()) {
                    return@withLock Result.success(Unit)
                }

                val commands = DriftDetector.buildReapplyPlan(targetDrifts)
                val affectedPackages = targetDrifts.map { it.packageName }.toSet()

                val failure = executeCommandsSequentially(commands)
                if (failure != null) {
                    rereadAndPublishBestEffort(affectedPackages, failure)
                    return@withLock Result.failure(IllegalStateException(failure))
                }

                var merged = _state.value.apps
                for (pkg in affectedPackages) {
                    val info = rereadPackageLocked(pkg)
                    if (info != null) {
                        merged = replacePackageInMerged(merged, info)
                    }
                }
                val savedSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
                publishMergedState(merged = merged, snapshot = savedSnapshot, errorMessage = null)
                Result.success(Unit)
            }
        } finally {
            removeInFlightKey(key)
        }
    }

    suspend fun acceptDrift(packageName: String? = null) {
        mutex.withLock {
            val currentAppsByPkg = _state.value.apps.associateBy { it.packageName }
            val currentDrifts = _state.value.drifts
            val packagesToAccept = if (packageName != null) {
                setOf(packageName)
            } else {
                currentDrifts.map { it.packageName }.toSet()
            }

            var updatedSnapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
            for (pkg in packagesToAccept) {
                val liveInfo = currentAppsByPkg[pkg]?.linkInfo ?: continue
                val existingDesired = updatedSnapshot.packages[pkg]
                val acceptedDenied = if (existingDesired == null) {
                    emptySet()
                } else {
                    val liveByDomain = liveInfo.domains.associateBy { it.domain }
                    existingDesired.deniedDomains.filter { domain ->
                        val entry = liveByDomain[domain] ?: return@filter false
                        if (entry.isAutoVerify) {
                            entry.verificationState == VerificationState.Denied && !entry.userSelected
                        } else {
                            !entry.userSelected
                        }
                    }.toSet()
                }
                updatedSnapshot = updateSnapshotForPackage(
                    current = updatedSnapshot,
                    liveInfo = liveInfo,
                    deniedOverride = acceptedDenied,
                )
            }

            withContext(Dispatchers.IO) { snapshotStore.save(updatedSnapshot) }
            publishMergedState(
                merged = _state.value.apps,
                snapshot = updatedSnapshot,
                errorMessage = null,
            )
        }
    }

    private suspend fun executeCommandsSequentially(commands: List<PmCommand>): String? {
        for (cmd in commands) {
            val result = shellExecutor.execPm(cmd)
            if (!result.isSuccess) {
                return result.stderr.ifBlank {
                    "pm ${cmd.subcommand} failed (exit ${result.exitCode})"
                }
            }
        }
        return null
    }

    private suspend fun rereadAndPublishBestEffort(
        packages: Set<String>,
        errorMessage: String,
    ) {
        var merged = _state.value.apps
        for (pkg in packages) {
            val info = rereadPackageLocked(pkg)
            if (info != null) {
                merged = replacePackageInMerged(merged, info)
            }
        }
        val snapshot = withContext(Dispatchers.IO) { snapshotStore.load() }
        publishMergedState(merged = merged, snapshot = snapshot, errorMessage = errorMessage)
    }

    private suspend fun rereadPackageLocked(packageName: String): AppLinkInfo? {
        val cmd = InputValidator.validatePmCommand("get-app-links", listOf("--user", "0", packageName))
        val result = shellExecutor.execPm(cmd)
        if (!result.isSuccess) return null
        return PmAppLinksParser.parseSingle(result.stdout, expectedPackageName = packageName)
    }

    private fun replacePackageInMerged(
        apps: List<MergedAppLink>,
        updatedInfo: AppLinkInfo,
    ): List<MergedAppLink> {
        var replaced = false
        val updated = apps.map { app ->
            if (app.packageName == updatedInfo.packageName) {
                replaced = true
                app.copy(linkInfo = updatedInfo)
            } else {
                app
            }
        }
        if (replaced) return updated

        val meta = packageDiscovery.resolveAppMetadata(updatedInfo.packageName) ?: return updated
        return (updated + MergedAppLink(
            packageName = updatedInfo.packageName,
            displayLabel = meta.displayLabel,
            isSystemApp = meta.isSystemApp,
            isEnabled = meta.isEnabled,
            linkInfo = updatedInfo,
        )).sortedWith(
            compareBy<MergedAppLink> { it.displayLabel.lowercase() }
                .thenBy { it.packageName },
        )
    }

    private fun publishMergedState(
        merged: List<MergedAppLink>,
        snapshot: FlipLinkSnapshot,
        errorMessage: String?,
    ) {
        val allInfos = merged.map { it.linkInfo }
        val conflictsByPackage = LinkedHashMap<String, Map<String, ConflictInfo>>(merged.size)
        for (app in merged) {
            val conflicts = ConflictPlanner.computeConflicts(app.packageName, allInfos)
            if (conflicts.isNotEmpty()) {
                conflictsByPackage[app.packageName] = conflicts
            }
        }
        val liveMap = merged.associate { it.packageName to it.linkInfo }
        val drifts = DriftDetector.computeDrift(snapshot, liveMap)

        _state.update {
            it.copy(
                apps = merged,
                conflictsByPackage = conflictsByPackage,
                drifts = drifts,
                isLoading = false,
                errorMessage = errorMessage,
                isReadOnly = false,
                snapshot = snapshot,
            )
        }
    }

    private fun updateSnapshotForPackage(
        current: FlipLinkSnapshot,
        liveInfo: AppLinkInfo,
        deniedOverride: Set<String>? = null,
    ): FlipLinkSnapshot {
        val existingDenied = deniedOverride ?: current.packages[liveInfo.packageName]?.deniedDomains.orEmpty()
        val selected = liveInfo.domains
            .filter { it.userSelected }
            .map { it.domain }
            .toSet()
        val approvedAutoVerify = liveInfo.domains
            .filter { it.isAutoVerify && it.verificationState.isApprovedOrVerified }
            .map { it.domain }
            .toSet()

        val desired = PackageDesiredState(
            linkHandlingAllowed = liveInfo.linkHandlingAllowed,
            selectedDomains = selected,
            deniedDomains = existingDenied,
            approvedAutoVerifyDomains = approvedAutoVerify,
            updatedAtEpochMs = clockMs(),
        )
        return current.copy(
            packages = current.packages + (liveInfo.packageName to desired),
        )
    }

    private fun addInFlightKey(key: String) {
        _state.update { it.copy(inFlightKeys = it.inFlightKeys + key) }
    }

    private fun removeInFlightKey(key: String) {
        _state.update { it.copy(inFlightKeys = it.inFlightKeys - key) }
    }

    companion object {
        private const val DRIFT_ALL_FLIGHT_KEY = "drift:all"

        fun packageFlightKey(packageName: String): String = "pkg:$packageName"
        fun domainFlightKey(packageName: String, domain: String): String = "domain:$packageName:$domain"
    }
}
