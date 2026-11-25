package dev.miles.fliplink.ui.appdetail

import dev.miles.fliplink.data.RepositoryState
import dev.miles.fliplink.data.TakeoverResult
import dev.miles.fliplink.data.pm.MergedAppLink
import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.CompetitorInfo
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.PackagePriorState
import dev.miles.fliplink.domain.model.TakeoverUndoSnapshot
import dev.miles.fliplink.domain.model.VerificationState
import dev.miles.fliplink.domain.planner.AdbCommandBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDetailViewModelTest {
    @Test
    fun deterministicOrderingPlacesConflictsThenInactiveThenActive() {
        val repoFlow = MutableStateFlow(samplePrivilegedState())
        val vm = createViewModel("dev.miles.patched", repoFlow)

        val state = vm.uiState.value
        assertTrue(state.isFound)
        assertEquals(3, state.sections.size)
        assertEquals(DomainSectionGroup.CONFLICTS, state.sections[0].group)
        assertEquals(listOf("test.fliplink.example"), state.sections[0].rows.map { it.domain })
        assertEquals(
            "Claimed by Stock App (verified)",
            state.sections[0].rows.single().conflict?.summaryText,
        )

        assertEquals(DomainSectionGroup.INACTIVE, state.sections[1].group)
        assertEquals(listOf("b.example.com", "c.example.com"), state.sections[1].rows.map { it.domain })

        assertEquals(DomainSectionGroup.ACTIVE, state.sections[2].group)
        assertEquals(listOf("a.example.com"), state.sections[2].rows.map { it.domain })
        assertEquals("4 domains · 1 active · 1 conflict", state.summaryStatsText)

        vm.clear()
    }

    @Test
    fun singleGroupOmitsSectionHeaders() {
        val singleGroupApp = MergedAppLink(
            packageName = "com.example.single",
            displayLabel = "Single Group",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "com.example.single",
                linkHandlingAllowed = true,
                domains = listOf(
                    domainEntry("b.example.com", DomainStatus.Selected, userSelected = true, approval = ApprovalLevel.SELECTION),
                    domainEntry("a.example.com", DomainStatus.Verified, verification = VerificationState.Verified, approval = ApprovalLevel.VERIFIED),
                ),
                isReadOnly = false,
            ),
        )
        val repoFlow = MutableStateFlow(RepositoryState(apps = listOf(singleGroupApp), isReadOnly = false))
        val vm = createViewModel("com.example.single", repoFlow)

        val state = vm.uiState.value
        assertEquals(1, state.sections.size)
        assertNull(state.sections.single().group)
        assertEquals(listOf("a.example.com", "b.example.com"), state.sections.single().rows.map { it.domain })

        vm.clear()
    }

    @Test
    fun readOnlyModeGeneratesAdbCommandsForMasterDomainAndBulkActions() {
        val readOnlyApp = MergedAppLink(
            packageName = "dev.miles.fliplink.fixture.patched",
            displayLabel = "Fixture Patched",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "dev.miles.fliplink.fixture.patched",
                linkHandlingAllowed = true,
                domains = listOf(
                    domainEntry("test.fliplink.example", DomainStatus.Declared),
                    domainEntry("alt.fliplink.example", DomainStatus.Declared),
                ),
                isReadOnly = true,
            ),
        )
        val repoFlow = MutableStateFlow(RepositoryState(apps = listOf(readOnlyApp), isReadOnly = true))
        val vm = createViewModel("dev.miles.fliplink.fixture.patched", repoFlow)

        assertEquals("2 declared domains", vm.uiState.value.summaryStatsText)

        vm.onToggleMaster(true)
        val masterReq = vm.uiState.value.adbSheetRequest
        assertNotNull(masterReq)
        assertEquals(
            "adb shell pm set-app-links-allowed --user 0 --package dev.miles.fliplink.fixture.patched true",
            AdbCommandBuilder.formatCommands(masterReq!!.commands, includeAdbPrefix = true),
        )
        assertEquals(
            "pm set-app-links-allowed --user 0 --package dev.miles.fliplink.fixture.patched true",
            AdbCommandBuilder.formatCommands(masterReq.commands, includeAdbPrefix = false),
        )

        vm.onToggleDomain("test.fliplink.example", true)
        val domainReq = vm.uiState.value.adbSheetRequest
        assertNotNull(domainReq)
        assertEquals(
            "adb shell pm set-app-links-user-selection --user 0 --package dev.miles.fliplink.fixture.patched true test.fliplink.example",
            AdbCommandBuilder.formatCommands(domainReq!!.commands, includeAdbPrefix = true),
        )

        vm.onEnableAll()
        val allReq = vm.uiState.value.adbSheetRequest
        assertNotNull(allReq)
        assertEquals(2, allReq!!.commands.size)
        assertEquals(
            "adb shell pm set-app-links-allowed --user 0 --package dev.miles.fliplink.fixture.patched true\n" +
                "adb shell pm set-app-links-user-selection --user 0 --package dev.miles.fliplink.fixture.patched true all",
            AdbCommandBuilder.formatCommands(allReq.commands, includeAdbPrefix = true),
        )

        vm.dismissAdbSheet()
        assertNull(vm.uiState.value.adbSheetRequest)
        vm.clear()
    }

    @Test
    fun singleBulkToggleAndTakeoverUndoDispatchExpectedCallsAndEvents() {
        val repoFlow = MutableStateFlow(samplePrivilegedState())
        val recordedCalls = mutableListOf<String>()
        val undoSnapshot = TakeoverUndoSnapshot(
            targetPackage = "dev.miles.patched",
            targetPriorState = PackagePriorState("dev.miles.patched", true, emptyMap()),
            competitorPriorStates = emptyMap(),
            restoreCommands = emptyList(),
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = AppDetailViewModel(
            packageName = "dev.miles.patched",
            repositoryState = repoFlow,
            onSetLinkHandlingAllowed = { pkg, allowed ->
                recordedCalls.add("master:$pkg:$allowed")
                Result.success(Unit)
            },
            onSetDomainEnabled = { pkg, domain, enabled ->
                recordedCalls.add("domain:$pkg:$domain:$enabled")
                Result.failure(IllegalStateException("Simulated failure"))
            },
            onSetAllDomainsEnabled = { pkg, enabled ->
                recordedCalls.add("all:$pkg:$enabled")
                Result.success(Unit)
            },
            onTakeOverDomain = { pkg, domain ->
                recordedCalls.add("takeover:$pkg:$domain")
                TakeoverResult.Success(undoSnapshot)
            },
            onUndoTakeover = { snap ->
                recordedCalls.add("undo:${snap.targetPackage}")
                Result.success(Unit)
            },
            scope = scope,
            spinnerDelayMs = 0L,
        )

        val events = mutableListOf<AppDetailEvent>()
        val job = scope.launch {
            vm.events.collect { events.add(it) }
        }

        vm.onToggleMaster(false)
        vm.onEnableAll()
        vm.onDisableAll()
        vm.onToggleDomain("b.example.com", true)
        vm.onTakeOver("test.fliplink.example")

        assertEquals(
            listOf(
                "master:dev.miles.patched:false",
                "all:dev.miles.patched:true",
                "all:dev.miles.patched:false",
                "domain:dev.miles.patched:b.example.com:true",
                "takeover:dev.miles.patched:test.fliplink.example",
            ),
            recordedCalls,
        )

        val errorEvent = events[0] as AppDetailEvent.ShowError
        assertEquals("Simulated failure", errorEvent.message)
        assertFalse(errorEvent.fallbackCommands.isEmpty())

        val undoEvent = events[1] as AppDetailEvent.ShowUndoTakeover
        assertEquals("test.fliplink.example moved from Stock App", undoEvent.message)

        vm.onUndoTakeoverClicked(undoEvent.snapshot)
        assertEquals("undo:dev.miles.patched", recordedCalls.last())

        job.cancel()
        vm.clear()
    }

    private fun createViewModel(
        packageName: String,
        repoFlow: MutableStateFlow<RepositoryState>,
    ): AppDetailViewModel = AppDetailViewModel(
        packageName = packageName,
        repositoryState = repoFlow,
        onSetLinkHandlingAllowed = { _, _ -> Result.success(Unit) },
        onSetDomainEnabled = { _, _, _ -> Result.success(Unit) },
        onSetAllDomainsEnabled = { _, _ -> Result.success(Unit) },
        onTakeOverDomain = { _, _ -> TakeoverResult.Failure("not used") },
        onUndoTakeover = { Result.success(Unit) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        spinnerDelayMs = 0L,
    )

    private fun samplePrivilegedState(): RepositoryState {
        val patched = MergedAppLink(
            packageName = "dev.miles.patched",
            displayLabel = "Patched App",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "dev.miles.patched",
                linkHandlingAllowed = true,
                domains = listOf(
                    domainEntry("a.example.com", DomainStatus.Selected, userSelected = true, approval = ApprovalLevel.SELECTION),
                    domainEntry("c.example.com", DomainStatus.Denied, verification = VerificationState.Denied),
                    domainEntry("test.fliplink.example", DomainStatus.Disabled),
                    domainEntry("b.example.com", DomainStatus.Disabled),
                ),
                isReadOnly = false,
            ),
        )
        val stock = MergedAppLink(
            packageName = "dev.miles.stock",
            displayLabel = "Stock App",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "dev.miles.stock",
                linkHandlingAllowed = true,
                domains = listOf(
                    domainEntry(
                        "test.fliplink.example",
                        DomainStatus.Verified,
                        isAutoVerify = true,
                        verification = VerificationState.Verified,
                        approval = ApprovalLevel.VERIFIED,
                    ),
                ),
                isReadOnly = false,
            ),
        )
        val conflict = ConflictInfo(
            domain = "test.fliplink.example",
            targetPackage = "dev.miles.patched",
            targetApprovalLevel = ApprovalLevel.NONE,
            competitors = listOf(
                CompetitorInfo(
                    packageName = "dev.miles.stock",
                    approvalLevel = ApprovalLevel.VERIFIED,
                    isAutoVerify = true,
                    verificationState = VerificationState.Verified,
                    userSelected = false,
                ),
            ),
        )
        return RepositoryState(
            apps = listOf(patched, stock),
            conflictsByPackage = mapOf("dev.miles.patched" to mapOf("test.fliplink.example" to conflict)),
            isReadOnly = false,
        )
    }

    private fun domainEntry(
        domain: String,
        status: DomainStatus,
        isAutoVerify: Boolean = false,
        verification: VerificationState = VerificationState.None,
        userSelected: Boolean = false,
        approval: ApprovalLevel = ApprovalLevel.NONE,
    ): DomainEntry = DomainEntry(
        domain = domain,
        isAutoVerify = isAutoVerify,
        verificationState = verification,
        userSelected = userSelected,
        status = status,
        approvalLevel = approval,
    )
}
