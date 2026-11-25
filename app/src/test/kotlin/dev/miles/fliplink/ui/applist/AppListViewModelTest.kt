package dev.miles.fliplink.ui.applist

import dev.miles.fliplink.data.RepositoryState
import dev.miles.fliplink.data.pm.MergedAppLink
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.domain.model.AppDrift
import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.CompetitorInfo
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.DriftItem
import dev.miles.fliplink.domain.model.VerificationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListViewModelTest {
    @Test
    fun filtersAndCountsReflectSearchSystemAndCategories() {
        val repoFlow = MutableStateFlow(samplePrivilegedRepoState())
        val shizukuFlow = MutableStateFlow<ShizukuState>(ShizukuState.Ready(version = 13, uid = 2000))
        val showSystemFlow = MutableStateFlow(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val vm = AppListViewModel(
            repositoryState = repoFlow,
            shizukuState = shizukuFlow,
            showSystemApps = showSystemFlow,
            onSetShowSystemApps = { showSystemFlow.value = it },
            onRefreshRepository = {},
            scope = scope,
        )

        // System app excluded initially: 3 user apps
        // Patched (conflict + needs attention), Broken (linkHandlingAllowed=false -> needs attention), Healthy (configured)
        val initial = vm.uiState.value
        assertEquals(3, initial.counts.all)
        assertEquals(2, initial.counts.needsAttention)
        assertEquals(1, initial.counts.conflicts)
        assertEquals(1, initial.counts.configured)
        // Deterministic sort: conflict first, then needs attention, then alphabetical
        assertEquals(
            listOf("dev.miles.patched", "com.example.broken", "com.example.healthy"),
            initial.items.map { it.packageName },
        )

        // Filter to Conflicts
        vm.onFilterSelected(AppFilter.CONFLICTS)
        assertEquals(listOf("dev.miles.patched"), vm.uiState.value.items.map { it.packageName })

        // Filter to Configured
        vm.onFilterSelected(AppFilter.CONFIGURED)
        assertEquals(listOf("com.example.healthy"), vm.uiState.value.items.map { it.packageName })

        // Filter back to All and search by domain host
        vm.onFilterSelected(AppFilter.ALL)
        vm.onSearchQueryChange("fliplink.example")
        assertEquals(listOf("dev.miles.patched"), vm.uiState.value.items.map { it.packageName })
        assertEquals(1, vm.uiState.value.counts.all)

        // Clear search and enable System apps
        vm.onSearchQueryChange("")
        vm.onToggleSystemApps()
        assertTrue(vm.uiState.value.showSystemApps)
        assertEquals(4, vm.uiState.value.counts.all)
        assertEquals(2, vm.uiState.value.counts.configured)

        vm.clear()
    }

    @Test
    fun readOnlyModeShowsDeclaredSummaryAndZeroActionableCounts() {
        val readOnlyApps = listOf(
            MergedAppLink(
                packageName = "dev.miles.fliplink.fixture.stock",
                displayLabel = "Stock Fixture",
                isSystemApp = false,
                isEnabled = true,
                linkInfo = AppLinkInfo(
                    packageName = "dev.miles.fliplink.fixture.stock",
                    linkHandlingAllowed = true,
                    domains = listOf(
                        DomainEntry(
                            domain = "test.fliplink.example",
                            isAutoVerify = false,
                            verificationState = VerificationState.None,
                            userSelected = false,
                            status = DomainStatus.Declared,
                            approvalLevel = ApprovalLevel.NONE,
                        ),
                        DomainEntry(
                            domain = "alt.fliplink.example",
                            isAutoVerify = false,
                            verificationState = VerificationState.None,
                            userSelected = false,
                            status = DomainStatus.Declared,
                            approvalLevel = ApprovalLevel.NONE,
                        ),
                    ),
                    isReadOnly = true,
                ),
            ),
        )

        val state = AppListViewModel.mapUiState(
            repo = RepositoryState(apps = readOnlyApps, isReadOnly = true),
            shizuku = ShizukuState.NotRunning,
            showSystemApps = false,
            searchQuery = "alt.fliplink",
            selectedFilter = AppFilter.ALL,
            isRefreshing = false,
            shizukuBannerDismissed = false,
        )

        assertEquals(1, state.items.size)
        val item = state.items.single()
        assertEquals("2 declared domains", item.summaryText)
        assertFalse(item.needsAttention)
        assertFalse(item.isConfigured)
        assertEquals(0, state.counts.needsAttention)
        assertEquals(0, state.counts.conflicts)
        assertEquals(0, state.counts.configured)
    }

    @Test
    fun driftBannerSummaryShowsOnlyWhenShizukuReadyAndDriftsPresent() {
        val drifts = listOf(
            AppDrift(
                packageName = "dev.miles.patched",
                items = listOf(DriftItem.SelectionLost(setOf("alt.fliplink.example"))),
            ),
        )
        val readyState = AppListViewModel.mapUiState(
            repo = samplePrivilegedRepoState().copy(drifts = drifts),
            shizuku = ShizukuState.Ready(version = 13, uid = 2000),
            showSystemApps = false,
            searchQuery = "",
            selectedFilter = AppFilter.ALL,
            isRefreshing = false,
            shizukuBannerDismissed = false,
        )
        assertEquals("1 domain changed in 1 app", readyState.driftBannerSummary)
        assertEquals(1, readyState.drifts.size)

        val notReadyState = AppListViewModel.mapUiState(
            repo = samplePrivilegedRepoState().copy(drifts = drifts),
            shizuku = ShizukuState.NotRunning,
            showSystemApps = false,
            searchQuery = "",
            selectedFilter = AppFilter.ALL,
            isRefreshing = false,
            shizukuBannerDismissed = false,
        )
        assertEquals(null, notReadyState.driftBannerSummary)
        assertTrue(notReadyState.drifts.isEmpty())
    }

    private fun samplePrivilegedRepoState(): RepositoryState {
        val patched = MergedAppLink(
            packageName = "dev.miles.patched",
            displayLabel = "Patched App",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "dev.miles.patched",
                linkHandlingAllowed = true,
                domains = listOf(
                    DomainEntry(
                        domain = "test.fliplink.example",
                        isAutoVerify = false,
                        verificationState = VerificationState.None,
                        userSelected = false,
                        status = DomainStatus.Disabled,
                        approvalLevel = ApprovalLevel.NONE,
                    ),
                ),
                isReadOnly = false,
            ),
        )

        val broken = MergedAppLink(
            packageName = "com.example.broken",
            displayLabel = "Alpha Broken",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "com.example.broken",
                linkHandlingAllowed = false,
                domains = listOf(
                    DomainEntry(
                        domain = "broken.example",
                        isAutoVerify = true,
                        verificationState = VerificationState.None,
                        userSelected = false,
                        status = DomainStatus.Disabled,
                        approvalLevel = ApprovalLevel.NONE,
                    ),
                ),
                isReadOnly = false,
            ),
        )

        val healthy = MergedAppLink(
            packageName = "com.example.healthy",
            displayLabel = "Healthy App",
            isSystemApp = false,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "com.example.healthy",
                linkHandlingAllowed = true,
                domains = listOf(
                    DomainEntry(
                        domain = "healthy.example",
                        isAutoVerify = true,
                        verificationState = VerificationState.Verified,
                        userSelected = false,
                        status = DomainStatus.Verified,
                        approvalLevel = ApprovalLevel.VERIFIED,
                    ),
                ),
                isReadOnly = false,
            ),
        )

        val systemApp = MergedAppLink(
            packageName = "com.android.browser",
            displayLabel = "System Browser",
            isSystemApp = true,
            isEnabled = true,
            linkInfo = AppLinkInfo(
                packageName = "com.android.browser",
                linkHandlingAllowed = true,
                domains = listOf(
                    DomainEntry(
                        domain = "sys.example",
                        isAutoVerify = true,
                        verificationState = VerificationState.Verified,
                        userSelected = false,
                        status = DomainStatus.Verified,
                        approvalLevel = ApprovalLevel.VERIFIED,
                    ),
                ),
                isReadOnly = false,
            ),
        )

        val conflicts = mapOf(
            "dev.miles.patched" to mapOf(
                "test.fliplink.example" to ConflictInfo(
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
                ),
            ),
        )

        return RepositoryState(
            apps = listOf(healthy, broken, patched, systemApp),
            conflictsByPackage = conflicts,
            isReadOnly = false,
        )
    }
}
