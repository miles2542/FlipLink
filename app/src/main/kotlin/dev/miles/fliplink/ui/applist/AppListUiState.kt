package dev.miles.fliplink.ui.applist

import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.domain.model.AppDrift

enum class AppFilter(val label: String) {
    ALL("All"),
    NEEDS_ATTENTION("Needs attention"),
    CONFLICTS("Conflicts"),
    CONFIGURED("Configured"),
}

enum class AppBadgeTone {
    Warning,
    Error,
    Neutral,
}

data class AppListItemUiModel(
    val packageName: String,
    val displayLabel: String,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isReadOnly: Boolean,
    val linkHandlingAllowed: Boolean,
    val totalDomains: Int,
    val activeDomains: Int,
    val conflictCount: Int,
    val deniedDomains: Int,
    val needsAttention: Boolean,
    val isConfigured: Boolean,
    val summaryText: String,
    val badgeText: String?,
    val badgeTone: AppBadgeTone?,
)

data class FilterCounts(
    val all: Int = 0,
    val needsAttention: Int = 0,
    val conflicts: Int = 0,
    val configured: Int = 0,
) {
    fun countFor(filter: AppFilter): Int = when (filter) {
        AppFilter.ALL -> all
        AppFilter.NEEDS_ATTENTION -> needsAttention
        AppFilter.CONFLICTS -> conflicts
        AppFilter.CONFIGURED -> configured
    }
}

data class AppListUiState(
    val items: List<AppListItemUiModel> = emptyList(),
    val counts: FilterCounts = FilterCounts(),
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL,
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isReadOnly: Boolean = true,
    val shizukuState: ShizukuState = ShizukuState.NotRunning,
    val shizukuBannerDismissed: Boolean = false,
    val drifts: List<AppDrift> = emptyList(),
    val driftBannerSummary: String? = null,
    val appLabelsByPackage: Map<String, String> = emptyMap(),
    val inFlightKeys: Set<String> = emptySet(),
    val errorMessage: String? = null,
)
