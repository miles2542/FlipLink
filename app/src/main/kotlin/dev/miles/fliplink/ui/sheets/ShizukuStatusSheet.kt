package dev.miles.fliplink.ui.sheets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.data.shizuku.ShizukuBridge
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.ui.components.StatusPill
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuStatusSheet(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlipLinkSpacing.rail)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Shizuku Status",
                    style = typography.topBar,
                    color = colors.textPrimary,
                )
                ShizukuSheetBadge(state)
            }

            when (state) {
                ShizukuState.NotInstalled -> {
                    Text(
                        text = "Shizuku is not installed. FlipLink works in read-only mode and generates copyable adb shell commands for any domain change.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openShizukuDownload(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Get Shizuku", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Use Read-only", maxLines = 1)
                        }
                    }
                }

                ShizukuState.NotRunning -> {
                    Text(
                        text = "Shizuku is installed, but its background service is not running. Start Shizuku via Wireless Debugging or ADB, then re-check.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openShizukuApp(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Open Shizuku", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { openDeveloperOrWirelessDebugging(context) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Wireless Debugging", maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = onRecheck,
                        modifier = Modifier
                            .align(Alignment.End)
                            .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                    ) {
                        Text("Re-check", color = colors.accent)
                    }
                }

                ShizukuState.PermissionRequired -> {
                    Text(
                        text = "Shizuku is running. Grant FlipLink permission to query and update app link verification states.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Grant Permission", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onRecheck,
                            modifier = Modifier
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Re-check", maxLines = 1)
                        }
                    }
                }

                ShizukuState.Binding -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Connecting to Shizuku shell service…",
                            style = typography.appLabel,
                            color = colors.textSecondary,
                        )
                    }
                }

                is ShizukuState.Ready -> {
                    Text(
                        text = "Connected. FlipLink can inspect and modify per-domain verification and user-selection state.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceSubtle)
                            .border(FlipLinkSpacing.hairline, colors.border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "Shizuku v${state.version} · UID ${state.uid}",
                            style = typography.adbBlock,
                            color = colors.textPrimary,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = onRecheck,
                            modifier = Modifier.defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Re-check")
                        }
                    }
                }

                is ShizukuState.Error -> {
                    Text(
                        text = "Failed to connect to the Shizuku service.",
                        style = typography.secondary,
                        color = colors.textSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceSubtle)
                            .border(FlipLinkSpacing.hairline, colors.statusError.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = state.message,
                            style = typography.adbBlock,
                            color = colors.statusError,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onRecheck,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Retry", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { openShizukuApp(context) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                        ) {
                            Text("Open Shizuku", maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.End)
                            .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                    ) {
                        Text("Use Read-only", color = colors.textSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ShizukuSheetBadge(state: ShizukuState) {
    val colors = FlipLinkTheme.colors
    when (state) {
        ShizukuState.NotInstalled -> StatusPill(
            text = "Not installed",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
        )

        ShizukuState.NotRunning -> StatusPill(
            text = "Not running",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
        )

        ShizukuState.PermissionRequired -> StatusPill(
            text = "Permission required",
            containerColor = colors.statusWarningSurface,
            contentColor = colors.statusWarning,
        )

        ShizukuState.Binding -> StatusPill(
            text = "Connecting…",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
            isLoading = true,
        )

        is ShizukuState.Ready -> StatusPill(
            text = "Connected",
            containerColor = colors.statusActiveSurface,
            contentColor = colors.statusActive,
            dotColor = colors.statusActive,
        )

        is ShizukuState.Error -> StatusPill(
            text = "Error",
            containerColor = colors.statusErrorSurface,
            contentColor = colors.statusError,
        )
    }
}

private fun openShizukuDownload(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Throwable) {
        // No browser available
    }
}

private fun openShizukuApp(context: Context) {
    val pm = context.packageManager
    val launch = pm.getLaunchIntentForPackage(ShizukuBridge.SHIZUKU_PACKAGE_NAME)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (launch != null) {
        try {
            context.startActivity(launch)
            return
        } catch (_: Throwable) {
            // Fall through to app info
        }
    }
    val details = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", ShizukuBridge.SHIZUKU_PACKAGE_NAME, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (_: Throwable) {
        // Ignore if settings unavailable
    }
}

private fun openDeveloperOrWirelessDebugging(context: Context) {
    val candidates = listOf(
        Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
            // Try next fallback
        }
    }
}
