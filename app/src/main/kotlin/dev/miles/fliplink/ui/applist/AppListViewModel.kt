package dev.miles.fliplink.ui.applist

import dev.miles.fliplink.data.RepositoryState
import dev.miles.fliplink.data.pm.MergedAppLink
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.DomainStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListViewModel(
    private val repositoryState: StateFlow<RepositoryState>,
    private val shizukuState: StateFlow<ShizukuState>,
    private val showSystemApps: StateFlow<Boolean>,
    private val onSetShowSystemApps: (Boolean) -> Unit,
    private val onRefreshRepository: suspend () -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(AppFilter.ALL)
    private val isRefreshing = MutableStateFlow(false)
    private val shizukuBannerDismissed = MutableStateFlow(false)

    private data class UiControls(
        val query: String,
        val filter: AppFilter,
        val refreshing: Boolean,
        val bannerDismissed: Boolean,
    )

    private val controls = combine(
        searchQuery,
        selectedFilter,
        isRefreshing,
        shizukuBannerDismissed,
    ) { query, filter, refreshing, bannerDismissed ->
        UiControls(
            query = query,
            filter = filter,
            refreshing = refreshing,
            bannerDismissed = bannerDismissed,
        )
    }

    val uiState: StateFlow<AppListUiState> = combine(
        repositoryState,
        shizukuState,
        showSystemApps,
        controls,
    ) { repo, shizuku, showSystem, ctrl ->
        mapUiState(
            repo = repo,
            shizuku = shizuku,
            showSystemApps = showSystem,
            searchQuery = ctrl.query,
            selectedFilter = ctrl.filter,
            isRefreshing = ctrl.refreshing,
            shizukuBannerDismissed = ctrl.bannerDismissed,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = mapUiState(
            repo = repositoryState.value,
            shizuku = shizukuState.value,
            showSystemApps = showSystemApps.value,
            searchQuery = searchQuery.value,
            selectedFilter = selectedFilter.value,
            isRefreshing = isRefreshing.value,
            shizukuBannerDismissed = shizukuBannerDismissed.value,
        ),
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterSelected(filter: AppFilter) {
        selectedFilter.value = filter
    }

    fun onToggleSystemApps() {
        onSetShowSystemApps(!showSystemApps.value)
    }

    fun onDismissShizukuBanner() {
        shizukuBannerDismissed.value = true
    }

    fun onRefresh() {
        if (isRefreshing.value) return
        scope.launch {
            isRefreshing.value = true
            try {
                onRefreshRepository()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun clear() {
        scope.cancel()
    }

    companion object {
        fun mapUiState(
            repo: RepositoryState,
            shizuku: ShizukuState,
            showSystemApps: Boolean,
            searchQuery: String,
            selectedFilter: AppFilter,
            isRefreshing: Boolean,
            shizukuBannerDismissed: Boolean,
        ): AppListUiState {
            val trimmedQuery = searchQuery.trim()
            val systemFiltered = if (showSystemApps) {
                repo.apps
            } else {
                repo.apps.filter { !it.isSystemApp }
            }

            val queryMatched = if (trimmedQuery.isEmpty()) {
                systemFiltered
            } else {
                systemFiltered.filter { app -> matchesQuery(app, trimmedQuery) }
            }

            val mappedModels = queryMatched.map { app ->
                val conflicts = repo.conflictsByPackage[app.packageName].orEmpty()
                toItemUiModel(app, conflicts)
            }

            val counts = FilterCounts(
                all = mappedModels.size,
                needsAttention = mappedModels.count { it.needsAttention },
                conflicts = mappedModels.count { it.conflictCount > 0 },
                configured = mappedModels.count { it.isConfigured },
            )

            val filteredItems = when (selectedFilter) {
                AppFilter.ALL -> mappedModels
                AppFilter.NEEDS_ATTENTION -> mappedModels.filter { it.needsAttention }
                AppFilter.CONFLICTS -> mappedModels.filter { it.conflictCount > 0 }
                AppFilter.CONFIGURED -> mappedModels.filter { it.isConfigured }
            }.sortedWith(itemComparator(repo.isReadOnly))

            return AppListUiState(
                items = filteredItems,
                counts = counts,
                searchQuery = searchQuery,
                selectedFilter = selectedFilter,
                showSystemApps = showSystemApps,
                isLoading = repo.isLoading,
                isRefreshing = isRefreshing,
                isReadOnly = repo.isReadOnly,
                shizukuState = shizuku,
                shizukuBannerDismissed = shizukuBannerDismissed,
                errorMessage = repo.errorMessage,
            )
        }

        private fun matchesQuery(app: MergedAppLink, query: String): Boolean {
            if (app.displayLabel.contains(query, ignoreCase = true)) return true
            if (app.packageName.contains(query, ignoreCase = true)) return true
            return app.linkInfo.domains.any { it.domain.contains(query, ignoreCase = true) }
        }

        private fun toItemUiModel(
            app: MergedAppLink,
            conflicts: Map<String, ConflictInfo>,
        ): AppListItemUiModel {
            val linkInfo = app.linkInfo
            val domains = linkInfo.domains
            val totalDomains = domains.size
            val activeDomains = domains.count { it.approvalLevel != ApprovalLevel.NONE }
            val deniedDomains = domains.count { it.status == DomainStatus.Denied }
            val conflictCount = conflicts.size
            val isReadOnly = linkInfo.isReadOnly

            val needsAttention = !isReadOnly && (
                !linkInfo.linkHandlingAllowed ||
                    conflictCount > 0 ||
                    activeDomains < totalDomains
                )
            val isConfigured = !isReadOnly &&
                linkInfo.linkHandlingAllowed &&
                activeDomains > 0 &&
                conflictCount == 0

            val summaryText = buildSummaryText(
                isReadOnly = isReadOnly,
                linkHandlingAllowed = linkInfo.linkHandlingAllowed,
                activeDomains = activeDomains,
                totalDomains = totalDomains,
                conflictCount = conflictCount,
            )

            val (badgeText, badgeTone) = when {
                isReadOnly -> null to null
                conflictCount > 0 -> {
                    val label = if (conflictCount == 1) "Conflict" else "$conflictCount conflicts"
                    label to AppBadgeTone.Warning
                }
                !linkInfo.linkHandlingAllowed -> "Off" to AppBadgeTone.Neutral
                deniedDomains > 0 -> "$deniedDomains denied" to AppBadgeTone.Error
                else -> null to null
            }

            return AppListItemUiModel(
                packageName = app.packageName,
                displayLabel = app.displayLabel,
                isSystemApp = app.isSystemApp,
                isEnabled = app.isEnabled,
                isReadOnly = isReadOnly,
                linkHandlingAllowed = linkInfo.linkHandlingAllowed,
                totalDomains = totalDomains,
                activeDomains = activeDomains,
                conflictCount = conflictCount,
                deniedDomains = deniedDomains,
                needsAttention = needsAttention,
                isConfigured = isConfigured,
                summaryText = summaryText,
                badgeText = badgeText,
                badgeTone = badgeTone,
            )
        }

        private fun buildSummaryText(
            isReadOnly: Boolean,
            linkHandlingAllowed: Boolean,
            activeDomains: Int,
            totalDomains: Int,
            conflictCount: Int,
        ): String {
            val domainNoun = if (totalDomains == 1) "domain" else "domains"
            if (isReadOnly) {
                return "$totalDomains declared $domainNoun"
            }
            val base = if (!linkHandlingAllowed) {
                "Disabled · $totalDomains $domainNoun"
            } else {
                "$activeDomains/$totalDomains active"
            }
            return if (conflictCount > 0) {
                val conflictNoun = if (conflictCount == 1) "conflict" else "conflicts"
                "$base · $conflictCount $conflictNoun"
            } else {
                base
            }
        }

        private fun itemComparator(isReadOnly: Boolean): Comparator<AppListItemUiModel> {
            if (isReadOnly) {
                return compareBy<AppListItemUiModel> { it.displayLabel.lowercase() }
                    .thenBy { it.packageName }
            }
            return compareByDescending<AppListItemUiModel> { it.conflictCount > 0 }
                .thenByDescending { it.needsAttention }
                .thenBy { it.displayLabel.lowercase() }
                .thenBy { it.packageName }
        }
    }
}
