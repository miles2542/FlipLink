package dev.miles.fliplink.ui.appdetail

import dev.miles.fliplink.data.AppLinksRepository
import dev.miles.fliplink.data.RepositoryState
import dev.miles.fliplink.data.TakeoverResult
import dev.miles.fliplink.data.pm.MergedAppLink
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.model.TakeoverUndoSnapshot
import dev.miles.fliplink.domain.planner.AdbCommandBuilder
import dev.miles.fliplink.domain.planner.ConflictPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDetailViewModel(
    private val packageName: String,
    private val repositoryState: StateFlow<RepositoryState>,
    private val onSetLinkHandlingAllowed: suspend (String, Boolean) -> Result<Unit>,
    private val onSetDomainEnabled: suspend (String, String, Boolean) -> Result<Unit>,
    private val onSetAllDomainsEnabled: suspend (String, Boolean) -> Result<Unit>,
    private val onTakeOverDomain: suspend (String, String) -> TakeoverResult,
    private val onUndoTakeover: suspend (TakeoverUndoSnapshot) -> Result<Unit>,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val spinnerDelayMs: Long = 150L,
) {
    private val adbSheetRequest = MutableStateFlow<AdbSheetRequest?>(null)
    private val visibleInFlightKeys = MutableStateFlow<Set<String>>(emptySet())

    private val _events = MutableSharedFlow<AppDetailEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppDetailEvent> = _events.asSharedFlow()

    init {
        repositoryState
            .onEach { repo ->
                val activeKeys = repo.inFlightKeys
                if (activeKeys.isEmpty()) {
                    visibleInFlightKeys.value = emptySet()
                } else if (spinnerDelayMs <= 0L) {
                    visibleInFlightKeys.value = activeKeys
                } else {
                    visibleInFlightKeys.value = visibleInFlightKeys.value.intersect(activeKeys)
                    val snapshotKeys = activeKeys
                    scope.launch {
                        delay(spinnerDelayMs)
                        val stillActive = repositoryState.value.inFlightKeys.intersect(snapshotKeys)
                        if (stillActive.isNotEmpty()) {
                            visibleInFlightKeys.value = visibleInFlightKeys.value + stillActive
                        }
                    }
                }
            }
            .launchIn(scope)
    }

    val uiState: StateFlow<AppDetailUiState> = combine(
        repositoryState,
        visibleInFlightKeys,
        adbSheetRequest,
    ) { repo, inFlight, adbRequest ->
        mapUiState(
            packageName = packageName,
            repo = repo,
            visibleInFlightKeys = inFlight,
            adbSheetRequest = adbRequest,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = mapUiState(
            packageName = packageName,
            repo = repositoryState.value,
            visibleInFlightKeys = visibleInFlightKeys.value,
            adbSheetRequest = adbSheetRequest.value,
        ),
    )

    fun onToggleMaster(allowed: Boolean) {
        val current = uiState.value
        if (current.isReadOnly) {
            openMasterAdbSheet(allowed)
            return
        }
        scope.launch {
            val result = onSetLinkHandlingAllowed(packageName, allowed)
            result.onFailure { err ->
                _events.tryEmit(
                    AppDetailEvent.ShowError(
                        message = err.message ?: "Failed to update link handling",
                        fallbackCommands = listOf(
                            AdbCommandBuilder.buildLinkHandlingAllowedCommand(packageName, allowed),
                        ),
                    ),
                )
            }
        }
    }

    fun onToggleDomain(domain: String, enabled: Boolean) {
        val current = uiState.value
        if (current.isReadOnly) {
            openDomainAdbSheet(domain, enabled = true)
            return
        }
        scope.launch {
            val result = onSetDomainEnabled(packageName, domain, enabled)
            result.onFailure { err ->
                _events.tryEmit(
                    AppDetailEvent.ShowError(
                        message = err.message ?: "Failed to update $domain",
                        fallbackCommands = AdbCommandBuilder.buildUserSelectionCommand(
                            packageName = packageName,
                            enabled = enabled,
                            domains = listOf(domain),
                        ),
                    ),
                )
            }
        }
    }

    fun onEnableAll() {
        val current = uiState.value
        if (current.isReadOnly) {
            openAllAdbSheet()
            return
        }
        scope.launch {
            val result = onSetAllDomainsEnabled(packageName, true)
            result.onFailure { err ->
                _events.tryEmit(
                    AppDetailEvent.ShowError(
                        message = err.message ?: "Failed to enable all domains",
                        fallbackCommands = listOf(
                            AdbCommandBuilder.buildUserSelectionAllCommand(packageName, true),
                        ),
                    ),
                )
            }
        }
    }

    fun onDisableAll() {
        val current = uiState.value
        if (current.isReadOnly) {
            openAllAdbSheet()
            return
        }
        scope.launch {
            val result = onSetAllDomainsEnabled(packageName, false)
            result.onFailure { err ->
                _events.tryEmit(
                    AppDetailEvent.ShowError(
                        message = err.message ?: "Failed to disable all domains",
                        fallbackCommands = listOf(
                            AdbCommandBuilder.buildUserSelectionAllCommand(packageName, false),
                        ),
                    ),
                )
            }
        }
    }

    fun onTakeOver(domain: String) {
        val current = uiState.value
        if (current.isReadOnly) {
            openDomainAdbSheet(domain, enabled = true)
            return
        }
        val row = current.sections.asSequence().flatMap { it.rows }.firstOrNull { it.domain == domain }
        val ownerLabel = row?.conflict?.primaryOwnerLabel ?: "other app"

        scope.launch {
            when (val result = onTakeOverDomain(packageName, domain)) {
                is TakeoverResult.Success -> {
                    _events.tryEmit(
                        AppDetailEvent.ShowUndoTakeover(
                            message = "$domain moved from $ownerLabel",
                            snapshot = result.undoSnapshot,
                        ),
                    )
                }

                is TakeoverResult.Failure -> {
                    val fallback = runCatching {
                        ConflictPlanner.planTakeover(
                            targetPackage = packageName,
                            domains = listOf(domain),
                            allPackages = repositoryState.value.apps.map { it.linkInfo },
                        ).commands
                    }.getOrElse {
                        AdbCommandBuilder.buildUserSelectionCommand(packageName, true, listOf(domain))
                    }
                    _events.tryEmit(
                        AppDetailEvent.ShowError(
                            message = result.message,
                            fallbackCommands = fallback,
                        ),
                    )
                }
            }
        }
    }

    fun onUndoTakeoverClicked(snapshot: TakeoverUndoSnapshot) {
        scope.launch {
            val result = onUndoTakeover(snapshot)
            result.onFailure { err ->
                _events.tryEmit(
                    AppDetailEvent.ShowError(
                        message = err.message ?: "Failed to undo takeover",
                        fallbackCommands = snapshot.restoreCommands,
                    ),
                )
            }
        }
    }

    fun openMasterAdbSheet(allowed: Boolean = true) {
        adbSheetRequest.value = AdbSheetRequest(
            title = "Open supported links",
            subtitle = "Run via ADB or a local root/ADB shell for ${uiState.value.displayLabel}.",
            commands = listOf(
                AdbCommandBuilder.buildLinkHandlingAllowedCommand(packageName, allowed),
            ),
        )
    }

    fun openDomainAdbSheet(domain: String, enabled: Boolean = true) {
        adbSheetRequest.value = AdbSheetRequest(
            title = if (enabled) "Enable $domain" else "Disable $domain",
            subtitle = "Updates user selection for $packageName.",
            commands = AdbCommandBuilder.buildUserSelectionCommand(
                packageName = packageName,
                enabled = enabled,
                domains = listOf(domain),
            ),
        )
    }

    fun openAllAdbSheet() {
        val current = uiState.value
        val cmds = buildList {
            if (!current.linkHandlingAllowed || current.isReadOnly) {
                add(AdbCommandBuilder.buildLinkHandlingAllowedCommand(packageName, true))
            }
            add(AdbCommandBuilder.buildUserSelectionAllCommand(packageName, true))
        }
        adbSheetRequest.value = AdbSheetRequest(
            title = "Enable all domains",
            subtitle = "Enables link handling and selects all declared domains for ${current.displayLabel}.",
            commands = cmds,
        )
    }

    fun openCustomAdbSheet(title: String, subtitle: String?, commands: List<PmCommand>) {
        adbSheetRequest.value = AdbSheetRequest(
            title = title,
            subtitle = subtitle,
            commands = commands,
        )
    }

    fun dismissAdbSheet() {
        adbSheetRequest.value = null
    }

    fun onCommandCopied() {
        _events.tryEmit(AppDetailEvent.ShowInfo("Command copied to clipboard"))
    }

    fun clear() {
        scope.cancel()
    }

    companion object {
        fun mapUiState(
            packageName: String,
            repo: RepositoryState,
            visibleInFlightKeys: Set<String> = emptySet(),
            adbSheetRequest: AdbSheetRequest? = null,
        ): AppDetailUiState {
            val app = repo.apps.firstOrNull { it.packageName == packageName }
                ?: return AppDetailUiState(
                    packageName = packageName,
                    isFound = false,
                    isReadOnly = repo.isReadOnly,
                    adbSheetRequest = adbSheetRequest,
                )

            val labelByPackage = repo.apps.associate { it.packageName to it.displayLabel }
            val conflicts = repo.conflictsByPackage[packageName].orEmpty()
            val pkgFlightKey = AppLinksRepository.packageFlightKey(packageName)
            val isMasterInFlight = pkgFlightKey in visibleInFlightKeys

            val rows = app.linkInfo.domains.map { entry ->
                val conflictInfo = conflicts[entry.domain]
                val conflictUi = conflictInfo?.toUiModel(labelByPackage)
                val group = when {
                    conflictUi != null -> DomainSectionGroup.CONFLICTS
                    entry.status == DomainStatus.Disabled ||
                        entry.status == DomainStatus.Denied ||
                        entry.status == DomainStatus.Declared -> DomainSectionGroup.INACTIVE
                    else -> DomainSectionGroup.ACTIVE
                }
                val isEnabledForSwitch = entry.status == DomainStatus.Verified ||
                    entry.status == DomainStatus.Approved ||
                    entry.status == DomainStatus.Selected

                val domainKey = AppLinksRepository.domainFlightKey(packageName, entry.domain)
                DomainRowUiModel(
                    domain = entry.domain,
                    status = entry.status,
                    isAutoVerify = entry.isAutoVerify,
                    userSelected = entry.userSelected,
                    isEnabledForSwitch = isEnabledForSwitch,
                    conflict = conflictUi,
                    group = group,
                    isInFlight = isMasterInFlight || domainKey in visibleInFlightKeys,
                )
            }

            val orderedGroups = listOf(
                DomainSectionGroup.CONFLICTS,
                DomainSectionGroup.INACTIVE,
                DomainSectionGroup.ACTIVE,
            )
            val nonEmptySections = orderedGroups.mapNotNull { group ->
                val groupRows = rows.filter { it.group == group }
                    .sortedBy { it.domain.lowercase() }
                if (groupRows.isEmpty()) null else group to groupRows
            }
            val showGroupHeaders = nonEmptySections.size >= 2
            val sections = nonEmptySections.map { (group, groupRows) ->
                DomainSectionUiModel(
                    group = if (showGroupHeaders) group else null,
                    rows = groupRows,
                )
            }

            val activeDomains = app.linkInfo.domains.count { it.approvalLevel != ApprovalLevel.NONE }
            val hasAnyDisabledOrDenied = rows.any { !it.isEnabledForSwitch }
            val hasAnyActive = rows.any { it.isEnabledForSwitch }

            return AppDetailUiState(
                packageName = app.packageName,
                displayLabel = app.displayLabel,
                isFound = true,
                isReadOnly = app.linkInfo.isReadOnly,
                linkHandlingAllowed = app.linkInfo.linkHandlingAllowed,
                isMasterInFlight = isMasterInFlight,
                totalDomains = app.linkInfo.domains.size,
                activeDomains = activeDomains,
                conflictCount = conflicts.size,
                hasAnyDisabledOrDenied = hasAnyDisabledOrDenied,
                hasAnyActive = hasAnyActive,
                sections = sections,
                adbSheetRequest = adbSheetRequest,
            )
        }

        private fun ConflictInfo.toUiModel(labelByPackage: Map<String, String>): DomainConflictUiModel? {
            val primary = competitors.firstOrNull() ?: return null
            val primaryLabel = labelByPackage[primary.packageName] ?: primary.packageName
            val tierLabel = if (primary.approvalLevel == ApprovalLevel.VERIFIED) "verified" else "selected"
            val extraCount = (competitors.size - 1).coerceAtLeast(0)
            val summary = if (extraCount > 0) {
                "Claimed by $primaryLabel +$extraCount ($tierLabel)"
            } else {
                "Claimed by $primaryLabel ($tierLabel)"
            }
            return DomainConflictUiModel(
                primaryOwnerPackage = primary.packageName,
                primaryOwnerLabel = primaryLabel,
                primaryOwnerTierLabel = tierLabel,
                additionalCompetitorCount = extraCount,
                summaryText = summary,
            )
        }
    }
}
