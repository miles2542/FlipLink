package dev.miles.fliplink.ui.components

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.planner.AdbCommandBuilder
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbCommandSheet(
    title: String,
    subtitle: String?,
    commands: List<PmCommand>,
    onDismiss: () -> Unit,
    onCopied: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var includeAdbPrefix by remember { mutableStateOf(true) }

    val formattedCommands = remember(commands, includeAdbPrefix) {
        AdbCommandBuilder.formatCommands(
            commands = commands,
            includeAdbPrefix = includeAdbPrefix,
        )
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
                .padding(horizontal = FlipLinkSpacing.rail)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = typography.topBar,
                color = colors.textPrimary,
            )

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = typography.secondary,
                    color = colors.textSecondary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "COMMAND",
                    style = typography.eyebrow,
                    color = colors.textMuted,
                )

                val pillBg = if (includeAdbPrefix) colors.accentSurface else colors.surfaceSubtle
                val pillBorder = if (includeAdbPrefix) colors.accent.copy(alpha = 0.4f) else colors.border
                val pillFg = if (includeAdbPrefix) colors.accent else colors.textSecondary

                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .border(FlipLinkSpacing.hairline, pillBorder, RoundedCornerShape(8.dp))
                        .clickable(role = Role.Switch) { includeAdbPrefix = !includeAdbPrefix }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (includeAdbPrefix) {
                            Icon(
                                imageVector = FlipLinkIcons.Check,
                                contentDescription = null,
                                tint = pillFg,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "Include \"adb shell\"",
                            style = typography.pill,
                            color = pillFg,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceSubtle)
                    .border(FlipLinkSpacing.hairline, colors.border, RoundedCornerShape(8.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = formattedCommands,
                        style = typography.adbBlock,
                        color = colors.textPrimary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                ) {
                    Text("Close", maxLines = 1)
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("FlipLink ADB Command", formattedCommands),
                        )
                        onCopied()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier
                        .weight(1.4f)
                        .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget),
                ) {
                    Icon(
                        imageVector = FlipLinkIcons.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy command", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
