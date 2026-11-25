package dev.miles.fliplink.ui.applist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.data.pm.AppIconCache
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.ui.components.BannerTone
import dev.miles.fliplink.ui.components.CompactBanner
import dev.miles.fliplink.ui.components.FlipLinkIcons
import dev.miles.fliplink.ui.components.SearchField
import dev.miles.fliplink.ui.components.StatusPill
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    uiState: AppListUiState,
    iconCache: AppIconCache,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (AppFilter) -> Unit,
    onToggleSystemApps: () -> Unit,
    onRefresh: () -> Unit,
    onDismissShizukuBanner: () -> Unit,
    onOpenShizukuSheet: () -> Unit,
    onOpenSettingsSheet: () -> Unit,
    onOpenDriftReviewSheet: () -> Unit,
    onReapplyAllDrift: () -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        AppListTopBar(
            shizukuState = uiState.shizukuState,
            isLoading = uiState.isLoading,
            onShizukuPillClick = onOpenShizukuSheet,
            onRefreshClick = onRefresh,
            onSettingsClick = onOpenSettingsSheet,
        )

        SearchField(
            query = uiState.searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )

        FilterChipBar(
            selectedFilter = uiState.selectedFilter,
            counts = uiState.counts,
            showSystemApps = uiState.showSystemApps,
            onFilterSelected = onFilterSelected,
            onToggleSystemApps = onToggleSystemApps,
        )

        ShizukuStatusBanner(
            shizukuState = uiState.shizukuState,
            dismissed = uiState.shizukuBannerDismissed,
            onOpenSheet = onOpenShizukuSheet,
            onDismiss = onDismissShizukuBanner,
        )

        if (uiState.driftBannerSummary != null) {
            CompactBanner(
                message = uiState.driftBannerSummary,
                tone = BannerTone.Warning,
                secondaryActionLabel = "Review",
                onSecondaryAction = onOpenDriftReviewSheet,
                actionLabel = "Re-apply",
                onAction = onReapplyAllDrift,
                modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
            )
        }

        if (uiState.errorMessage != null) {
            CompactBanner(
                message = uiState.errorMessage,
                tone = BannerTone.Error,
                actionLabel = "Retry",
                onAction = onRefresh,
                modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
            )
        }

        HorizontalDivider(
            thickness = FlipLinkSpacing.hairline,
            color = colors.border,
        )

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = colors.accent,
                        )
                    }
                }

                uiState.items.isEmpty() -> {
                    EmptyAppListState(
                        hasSearchQuery = uiState.searchQuery.isNotBlank(),
                        selectedFilter = uiState.selectedFilter,
                        onClearFilters = {
                            if (uiState.searchQuery.isNotBlank()) onSearchQueryChange("")
                            if (uiState.selectedFilter != AppFilter.ALL) onFilterSelected(AppFilter.ALL)
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = uiState.items,
                            key = { _, item -> item.packageName },
                        ) { index, item ->
                            AppListRow(
                                item = item,
                                iconCache = iconCache,
                                onClick = { onAppClick(item.packageName) },
                            )
                            if (index < uiState.items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = FlipLinkSpacing.appDividerInset),
                                    thickness = FlipLinkSpacing.hairline,
                                    color = colors.border,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListTopBar(
    shizukuState: ShizukuState,
    isLoading: Boolean,
    onShizukuPillClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlipLinkSpacing.topBarHeight)
            .padding(start = FlipLinkSpacing.rail, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "FlipLink",
            style = typography.topBar,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        ShizukuTopBarPill(
            state = shizukuState,
            onClick = onShizukuPillClick,
        )

        IconButton(
            onClick = onRefreshClick,
            enabled = !isLoading,
            modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
        ) {
            Icon(
                imageVector = FlipLinkIcons.Refresh,
                contentDescription = "Refresh app links",
                tint = if (isLoading) colors.textMuted else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
        ) {
            Icon(
                imageVector = FlipLinkIcons.Settings,
                contentDescription = "Settings",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ShizukuTopBarPill(
    state: ShizukuState,
    onClick: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    when (state) {
        ShizukuState.NotInstalled,
        ShizukuState.NotRunning,
        -> StatusPill(
            text = "Read-only",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
            onClick = onClick,
        )

        ShizukuState.PermissionRequired -> StatusPill(
            text = "Grant",
            containerColor = colors.statusWarningSurface,
            contentColor = colors.statusWarning,
            onClick = onClick,
        )

        ShizukuState.Binding -> StatusPill(
            text = "Connecting…",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
            isLoading = true,
            onClick = onClick,
        )

        is ShizukuState.Ready -> StatusPill(
            text = "Shizuku",
            containerColor = colors.statusActiveSurface,
            contentColor = colors.statusActive,
            dotColor = colors.statusActive,
            onClick = onClick,
        )

        is ShizukuState.Error -> StatusPill(
            text = "Error",
            containerColor = colors.statusErrorSurface,
            contentColor = colors.statusError,
            onClick = onClick,
        )
    }
}

@Composable
private fun FilterChipBar(
    selectedFilter: AppFilter,
    counts: FilterCounts,
    showSystemApps: Boolean,
    onFilterSelected: (AppFilter) -> Unit,
    onToggleSystemApps: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = FlipLinkSpacing.rail),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilter.entries.forEach { filter ->
            val count = counts.countFor(filter)
            CompactFilterChip(
                label = "${filter.label} ($count)",
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(colors.border),
        )

        CompactFilterChip(
            label = "System",
            selected = showSystemApps,
            onClick = onToggleSystemApps,
        )
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val shape = RoundedCornerShape(8.dp)

    val containerColor = if (selected) colors.accentSurface else colors.surface
    val contentColor = if (selected) colors.accent else colors.textSecondary
    val borderColor = if (selected) colors.accent.copy(alpha = 0.4f) else colors.border

    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .clip(shape)
                .background(containerColor)
                .border(width = FlipLinkSpacing.hairline, color = borderColor, shape = shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = typography.pill.copy(fontFeatureSettings = "tnum"),
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ShizukuStatusBanner(
    shizukuState: ShizukuState,
    dismissed: Boolean,
    onOpenSheet: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (dismissed) return
    when (shizukuState) {
        ShizukuState.NotInstalled -> CompactBanner(
            message = "Read-only mode — install Shizuku to edit app links.",
            tone = BannerTone.Neutral,
            actionLabel = "Setup",
            onAction = onOpenSheet,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
        )

        ShizukuState.NotRunning -> CompactBanner(
            message = "Shizuku is not running — editing disabled.",
            tone = BannerTone.Neutral,
            actionLabel = "Start",
            onAction = onOpenSheet,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
        )

        ShizukuState.PermissionRequired -> CompactBanner(
            message = "Grant Shizuku permission to modify domain links.",
            tone = BannerTone.Warning,
            actionLabel = "Grant",
            onAction = onOpenSheet,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
        )

        is ShizukuState.Error -> CompactBanner(
            message = "Shizuku error: ${shizukuState.message}",
            tone = BannerTone.Error,
            actionLabel = "Details",
            onAction = onOpenSheet,
            onDismiss = onDismiss,
            modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
        )

        ShizukuState.Binding,
        is ShizukuState.Ready,
        -> Unit
    }
}

@Composable
private fun AppListRow(
    item: AppListItemUiModel,
    iconCache: AppIconCache,
    onClick: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FlipLinkSpacing.appRowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = item.packageName,
            displayLabel = item.displayLabel,
            iconCache = iconCache,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.displayLabel,
                style = typography.appLabel,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.summaryText,
                style = typography.secondary.copy(fontFeatureSettings = "tnum"),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (item.badgeText != null && item.badgeTone != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val (containerColor, contentColor) = when (item.badgeTone) {
                AppBadgeTone.Warning -> colors.statusWarningSurface to colors.statusWarning
                AppBadgeTone.Error -> colors.statusErrorSurface to colors.statusError
                AppBadgeTone.Neutral -> colors.surfaceSubtle to colors.textSecondary
            }
            StatusPill(
                text = item.badgeText,
                containerColor = containerColor,
                contentColor = contentColor,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = FlipLinkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AppIcon(
    packageName: String,
    displayLabel: String,
    iconCache: AppIconCache,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val iconBitmap: ImageBitmap? by produceState(
        initialValue = iconCache.getCached(packageName),
        key1 = packageName,
    ) {
        if (value == null) {
            value = iconCache.loadIcon(packageName)
        }
    }

    val shape = RoundedCornerShape(8.dp)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(FlipLinkSpacing.appIconSize)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(FlipLinkSpacing.appIconSize)
                .clip(shape)
                .background(colors.surfaceSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayLabel.take(1).uppercase(),
                style = typography.appLabel,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun EmptyAppListState(
    hasSearchQuery: Boolean,
    selectedFilter: AppFilter,
    onClearFilters: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val message = when {
            hasSearchQuery -> "No apps or domains match your search."
            selectedFilter != AppFilter.ALL -> "No apps in \"${selectedFilter.label}\"."
            else -> "No apps declaring web links found."
        }
        Text(
            text = message,
            style = typography.appLabel,
            color = colors.textSecondary,
        )
        if (hasSearchQuery || selectedFilter != AppFilter.ALL) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClearFilters)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reset filters",
                    style = typography.inlineAction,
                    color = colors.accent,
                )
            }
        }
    }
}
