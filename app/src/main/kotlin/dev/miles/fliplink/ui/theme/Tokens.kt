package dev.miles.fliplink.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

object FlipLinkSpacing {
    val unit = 4.dp
    val rail = 16.dp
    val topBarHeight = 56.dp
    val appRowHeight = 60.dp
    val appIconSize = 36.dp
    val appDividerInset = 64.dp
    val domainRowHeight = 56.dp
    val domainDividerInset = 16.dp
    val minTouchTarget = 48.dp
    val hairline = 0.5.dp
}

@Immutable
data class FlipLinkColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSurface: Color,
    val statusActive: Color,
    val statusActiveSurface: Color,
    val statusWarning: Color,
    val statusWarningSurface: Color,
    val statusError: Color,
    val statusErrorSurface: Color,
)

val LocalFlipLinkColors = staticCompositionLocalOf { LightFlipLinkColors }
