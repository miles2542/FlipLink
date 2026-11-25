package dev.miles.fliplink.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.ui.components.FlipLinkIcons
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme
import dev.miles.fliplink.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    showSystemApps: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onShowSystemAppsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography
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
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Settings",
                style = typography.topBar,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "APPEARANCE",
                style = typography.eyebrow,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlipLinkSpacing.rail, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle)
                    .border(FlipLinkSpacing.hairline, colors.border, RoundedCornerShape(10.dp)),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    ThemeOptionRow(
                        label = mode.displayLabel(),
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                    )
                    if (index < ThemeMode.entries.lastIndex) {
                        HorizontalDivider(
                            thickness = FlipLinkSpacing.hairline,
                            color = colors.border,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Dynamic Color",
                subtitle = "Use wallpaper accent colors (Material You)",
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = FlipLinkSpacing.rail),
                thickness = FlipLinkSpacing.hairline,
                color = colors.border,
            )

            SettingsToggleRow(
                title = "Show system apps",
                subtitle = "Include pre-installed system packages in the list",
                checked = showSystemApps,
                onCheckedChange = onShowSystemAppsChange,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = FlipLinkSpacing.minTouchTarget)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = typography.appLabel,
            color = if (selected) colors.accent else colors.textPrimary,
        )
        if (selected) {
            Icon(
                imageVector = FlipLinkIcons.Check,
                contentDescription = "Selected",
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Box(modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .padding(horizontal = FlipLinkSpacing.rail, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = typography.appLabel,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = typography.secondary,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun ThemeMode.displayLabel(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.AMOLED -> "AMOLED Black"
}
