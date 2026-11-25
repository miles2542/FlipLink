package dev.miles.fliplink.ui.appdetail

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.miles.fliplink.data.pm.AppIconCache
import dev.miles.fliplink.ui.components.AdbCommandSheet
import dev.miles.fliplink.ui.components.DomainStatusPill
import dev.miles.fliplink.ui.components.FlipLinkIcons
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppDetailScreen(
    uiState: AppDetailUiState,
    events: SharedFlow<AppDetailEvent>,
    iconCache: AppIconCache,
    onBack: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
    onToggleDomain: (String, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onTakeOver: (String) -> Unit,
    onUndoTakeover: (dev.miles.fliplink.domain.model.TakeoverUndoSnapshot) -> Unit,
    onOpenMasterAdb: () -> Unit,
    onOpenAllAdb: () -> Unit,
    onOpenCustomAdb: (String, String?, List<dev.miles.fliplink.domain.model.PmCommand>) -> Unit,
    onDismissAdbSheet: () -> Unit,
    onCommandCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                is AppDetailEvent.ShowUndoTakeover -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onUndoTakeover(event.snapshot)
                    }
                }

                is AppDetailEvent.ShowError -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = "Copy ADB",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onOpenCustomAdb(
                            "Fallback ADB command",
                            event.message,
                            event.fallbackCommands,
                        )
                    }
                }

                is AppDetailEvent.ShowInfo -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background),
        ) {
            AppDetailTopBar(
                packageName = uiState.packageName,
                displayLabel = uiState.displayLabel,
                isReadOnly = uiState.isReadOnly,
                iconCache = iconCache,
                onBack = onBack,
                onOpenAppInfo = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", uiState.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
                onOpenCopyAdb = onOpenAllAdb,
            )

            HorizontalDivider(
                thickness = FlipLinkSpacing.hairline,
                color = colors.border,
            )

            if (!uiState.isFound) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Package ${uiState.packageName} is no longer installed or declares no web links.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                }
            } else {
                MasterControlStrip(
                    isReadOnly = uiState.isReadOnly,
                    linkHandlingAllowed = uiState.linkHandlingAllowed,
                    isInFlight = uiState.isMasterInFlight,
                    onToggle = {
                        if (uiState.isReadOnly) {
                            onOpenMasterAdb()
                        } else {
                            onToggleMaster(!uiState.linkHandlingAllowed)
                        }
                    },
                )

                HorizontalDivider(
                    thickness = FlipLinkSpacing.hairline,
                    color = colors.border,
                )

                BulkActionBar(
                    uiState = uiState,
                    onEnableAll = onEnableAll,
                    onDisableAll = onDisableAll,
                    onCopyAllAdb = onOpenAllAdb,
                )

                HorizontalDivider(
                    thickness = FlipLinkSpacing.hairline,
                    color = colors.border,
                )

                if (!uiState.isReadOnly && !uiState.linkHandlingAllowed) {
                    MasterOffHintBar(
                        isInFlight = uiState.isMasterInFlight,
                        onEnable = { onToggleMaster(true) },
                    )
                    HorizontalDivider(
                        thickness = FlipLinkSpacing.hairline,
                        color = colors.border,
                    )
                }

                if (uiState.totalDomains == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No web domains declared.",
                            style = typography.secondary,
                            color = colors.textSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        uiState.sections.forEachIndexed { sectionIndex, section ->
                            if (section.group != null) {
                                item(key = "header:${section.group.name}") {
                                    SectionHeaderRow(title = section.group.title)
                                }
                            }
                            itemsIndexed(
                                items = section.rows,
                                key = { _, row -> row.domain },
                            ) { rowIndex, row ->
                                DomainRow(
                                    row = row,
                                    isReadOnly = uiState.isReadOnly,
                                    onToggle = { enabled -> onToggleDomain(row.domain, enabled) },
                                    onTakeOver = { onTakeOver(row.domain) },
                                )
                                val isLastInSection = rowIndex == section.rows.lastIndex
                                val isLastSection = sectionIndex == uiState.sections.lastIndex
                                if (!(isLastInSection && isLastSection)) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            start = if (isLastInSection) 0.dp else FlipLinkSpacing.domainDividerInset,
                                        ),
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

    val adbRequest = uiState.adbSheetRequest
    if (adbRequest != null) {
        AdbCommandSheet(
            title = adbRequest.title,
            subtitle = adbRequest.subtitle,
            commands = adbRequest.commands,
            onDismiss = onDismissAdbSheet,
            onCopied = onCommandCopied,
        )
    }
}

@Composable
private fun AppDetailTopBar(
    packageName: String,
    displayLabel: String,
    isReadOnly: Boolean,
    iconCache: AppIconCache,
    onBack: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenCopyAdb: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlipLinkSpacing.topBarHeight)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
        ) {
            Icon(
                imageVector = FlipLinkIcons.ArrowBack,
                contentDescription = "Back",
                tint = colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }

        DetailAppIcon(
            packageName = packageName,
            displayLabel = displayLabel,
            iconCache = iconCache,
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = displayLabel,
                style = typography.appLabel.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                style = typography.adbBlock.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isReadOnly) {
            IconButton(
                onClick = onOpenCopyAdb,
                modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
            ) {
                Icon(
                    imageVector = FlipLinkIcons.Copy,
                    contentDescription = "Copy ADB",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        IconButton(
            onClick = onOpenAppInfo,
            modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
        ) {
            Icon(
                imageVector = FlipLinkIcons.Info,
                contentDescription = "App info",
                tint = colors.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun MasterControlStrip(
    isReadOnly: Boolean,
    linkHandlingAllowed: Boolean,
    isInFlight: Boolean,
    onToggle: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val subtitle = when {
        isReadOnly -> "Unknown (read-only)"
        linkHandlingAllowed -> "Allowed by system"
        else -> "Disabled"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(colors.surface)
            .clickable(enabled = !isInFlight, role = Role.Switch, onClick = onToggle)
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Open supported links",
                style = typography.appLabel,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = typography.secondary,
                color = if (!isReadOnly && !linkHandlingAllowed) colors.statusWarning else colors.textSecondary,
            )
        }

        if (isInFlight) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(14.dp),
                strokeWidth = 1.8.dp,
                color = colors.accent,
            )
        }

        Switch(
            checked = !isReadOnly && linkHandlingAllowed,
            enabled = !isInFlight,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.surface,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceSubtle,
                uncheckedBorderColor = colors.border,
            ),
        )
    }
}

@Composable
private fun BulkActionBar(
    uiState: AppDetailUiState,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    onCopyAllAdb: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = FlipLinkSpacing.rail, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = uiState.summaryStatsText,
            style = typography.monoCount,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (uiState.isReadOnly) {
            InlineActionChip(
                label = "Copy all ADB",
                color = colors.accent,
                enabled = uiState.totalDomains > 0,
                onClick = onCopyAllAdb,
            )
        } else {
            if (uiState.hasAnyDisabledOrDenied) {
                InlineActionChip(
                    label = "Enable all",
                    color = colors.accent,
                    enabled = !uiState.isMasterInFlight,
                    onClick = onEnableAll,
                )
            }
            if (uiState.hasAnyActive) {
                InlineActionChip(
                    label = "Disable all",
                    color = colors.textSecondary,
                    enabled = !uiState.isMasterInFlight,
                    onClick = onDisableAll,
                )
            }
        }
    }
}

@Composable
private fun MasterOffHintBar(
    isInFlight: Boolean,
    onEnable: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .background(colors.statusWarningSurface)
            .padding(start = FlipLinkSpacing.rail, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Master switch is off — links will open in browser until enabled.",
            style = typography.secondary,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        InlineActionChip(
            label = "Enable",
            color = colors.statusWarning,
            enabled = !isInFlight,
            onClick = onEnable,
        )
    }
}

@Composable
private fun SectionHeaderRow(title: String) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 8.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = typography.eyebrow,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun DomainRow(
    row: DomainRowUiModel,
    isReadOnly: Boolean,
    onToggle: (Boolean) -> Unit,
    onTakeOver: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlipLinkSpacing.domainRowHeight)
            .clickable(
                enabled = !row.isInFlight,
                role = if (isReadOnly) Role.Button else Role.Switch,
                onClick = { onToggle(!row.isEnabledForSwitch) },
            )
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.domain,
                    style = typography.domain,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DomainStatusPill(status = row.status)
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (row.isInFlight) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(14.dp),
                    strokeWidth = 1.8.dp,
                    color = colors.accent,
                )
            }

            if (isReadOnly) {
                IconButton(
                    onClick = { onToggle(true) },
                    modifier = Modifier.size(FlipLinkSpacing.minTouchTarget),
                ) {
                    Icon(
                        imageVector = FlipLinkIcons.Copy,
                        contentDescription = "Copy ADB command for ${row.domain}",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Switch(
                    checked = row.isEnabledForSwitch,
                    enabled = !row.isInFlight,
                    onCheckedChange = { checked -> onToggle(checked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.surface,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceSubtle,
                        uncheckedBorderColor = colors.border,
                    ),
                )
            }
        }

        val conflict = row.conflict
        if (conflict != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.statusWarningSurface)
                    .border(
                        width = FlipLinkSpacing.hairline,
                        color = colors.statusWarning.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = conflict.summaryText,
                    style = typography.secondary,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            enabled = !row.isInFlight,
                            role = Role.Button,
                            onClick = onTakeOver,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Take over",
                        style = typography.inlineAction,
                        color = colors.statusWarning,
                    )
                }
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
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FlipLinkTheme.typography.inlineAction,
            color = if (enabled) color else FlipLinkTheme.colors.textMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailAppIcon(
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
