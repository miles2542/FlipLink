package dev.miles.fliplink.ui.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.data.AppLinksRepository
import dev.miles.fliplink.data.pm.AppIconCache
import dev.miles.fliplink.domain.model.AppDrift
import dev.miles.fliplink.domain.model.DriftItem
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

data class DriftLineUiModel(
    val text: String,
    val isWarning: Boolean,
)

fun formatDriftDomains(domains: Set<String>, maxVisible: Int = 3): String {
    val sorted = domains.sorted()
    if (sorted.size <= maxVisible) return sorted.joinToString(", ")
    val head = sorted.take(maxVisible).joinToString(", ")
    val remaining = sorted.size - maxVisible
    return "$head +$remaining more"
}

fun formatDriftItemLine(item: DriftItem): DriftLineUiModel = when (item) {
    DriftItem.LinkHandlingDisabled -> DriftLineUiModel(
        text = "Master switch turned off",
        isWarning = false,
    )

    is DriftItem.SelectionLost -> DriftLineUiModel(
        text = "Unselected: ${formatDriftDomains(item.domains)}",
        isWarning = false,
    )

    is DriftItem.VerificationLost -> DriftLineUiModel(
        text = "Approval lost: ${formatDriftDomains(item.domains)}",
        isWarning = false,
    )

    is DriftItem.CompetitorReclaimed -> DriftLineUiModel(
        text = "Reclaimed: ${formatDriftDomains(item.domains)} (re-deny)",
        isWarning = true,
    )
}

fun formatDriftBannerSummary(drifts: List<AppDrift>): String? {
    if (drifts.isEmpty()) return null
    val appCount = drifts.size
    val domainCount = drifts.sumOf { drift ->
        drift.items.flatMapTo(LinkedHashSet()) { item ->
            when (item) {
                DriftItem.LinkHandlingDisabled -> emptySet()
                is DriftItem.SelectionLost -> item.domains
                is DriftItem.VerificationLost -> item.domains
                is DriftItem.CompetitorReclaimed -> item.domains
            }
        }.size
    }
    val appNoun = if (appCount == 1) "app" else "apps"
    if (domainCount == 0) {
        return "Link settings changed in $appCount $appNoun"
    }
    val domainNoun = if (domainCount == 1) "domain" else "domains"
    return "$domainCount $domainNoun changed in $appCount $appNoun"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriftReviewSheet(
    drifts: List<AppDrift>,
    appLabels: Map<String, String>,
    iconCache: AppIconCache,
    inFlightKeys: Set<String>,
    onReapplyPackage: (String) -> Unit,
    onAcceptPackage: (String) -> Unit,
    onReapplyAll: () -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAllBusy = inFlightKeys.contains(AppLinksRepository.DRIFT_ALL_FLIGHT_KEY)

    LaunchedEffect(drifts) {
        if (drifts.isEmpty()) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlipLinkSpacing.rail),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Review changes",
                    style = typography.topBar,
                    color = colors.textPrimary,
                )
                Text(
                    text = formatDriftBannerSummary(drifts)
                        ?: "OS link state matches your saved configuration.",
                    style = typography.secondary.copy(fontFeatureSettings = "tnum"),
                    color = colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(
                thickness = FlipLinkSpacing.hairline,
                color = colors.border,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                itemsIndexed(
                    items = drifts,
                    key = { _, drift -> drift.packageName },
                ) { index, drift ->
                    val isPkgBusy = isAllBusy ||
                        inFlightKeys.contains(AppLinksRepository.packageFlightKey(drift.packageName))
                    DriftPackageRow(
                        drift = drift,
                        displayLabel = appLabels[drift.packageName] ?: drift.packageName,
                        iconCache = iconCache,
                        isBusy = isPkgBusy,
                        onReapply = { onReapplyPackage(drift.packageName) },
                        onAccept = { onAcceptPackage(drift.packageName) },
                    )
                    if (index < drifts.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = FlipLinkSpacing.rail),
                            thickness = FlipLinkSpacing.hairline,
                            color = colors.border,
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = FlipLinkSpacing.hairline,
                color = colors.border,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlipLinkSpacing.rail),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onAcceptAll,
                    enabled = !isAllBusy && drifts.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                ) {
                    Text("Accept all", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onReapplyAll,
                    enabled = !isAllBusy && drifts.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                ) {
                    if (isAllBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.surface,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isAllBusy) "Re-applying…" else "Re-apply all",
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DriftPackageRow(
    drift: AppDrift,
    displayLabel: String,
    iconCache: AppIconCache,
    isBusy: Boolean,
    onReapply: () -> Unit,
    onAccept: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DriftAppIcon(
                packageName = drift.packageName,
                displayLabel = displayLabel,
                iconCache = iconCache,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = displayLabel,
                    style = typography.appLabel,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = drift.packageName,
                    style = typography.secondary.copy(fontFeatureSettings = "tnum"),
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 38.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            drift.items.forEach { item ->
                val line = formatDriftItemLine(item)
                Text(
                    text = line.text,
                    style = typography.adbBlock,
                    color = if (line.isWarning) colors.statusWarning else colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineActionChip(
                    label = if (isBusy) "Re-applying…" else "Re-apply",
                    color = if (isBusy) colors.textMuted else colors.accent,
                    enabled = !isBusy,
                    onClick = onReapply,
                )
                InlineActionChip(
                    label = "Accept",
                    color = if (isBusy) colors.textMuted else colors.textSecondary,
                    enabled = !isBusy,
                    onClick = onAccept,
                )
            }
        }
    }
}

@Composable
private fun InlineActionChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val typography = FlipLinkTheme.typography
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.inlineAction,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DriftAppIcon(
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

    val shape = RoundedCornerShape(6.dp)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(shape)
                .background(colors.surfaceSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayLabel.take(1).uppercase(),
                style = typography.secondary,
                color = colors.textMuted,
            )
        }
    }
}
