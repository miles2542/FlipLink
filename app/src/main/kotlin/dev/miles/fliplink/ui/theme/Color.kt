package dev.miles.fliplink.ui.theme

import androidx.compose.ui.graphics.Color

internal val LightFlipLinkColors = FlipLinkColors(
    isDark = false,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    textPrimary = Color(0xFF09090B),
    textSecondary = Color(0xFF52525B),
    textMuted = Color(0xFF71717A),
    accent = Color(0xFF2563EB),
    accentSurface = Color(0xFFEFF6FF),
    statusActive = Color(0xFF16A34A),
    statusActiveSurface = Color(0xFFDCFCE7),
    statusWarning = Color(0xFFD97706),
    statusWarningSurface = Color(0xFFFEF3C7),
    statusError = Color(0xFFDC2626),
    statusErrorSurface = Color(0xFFFEE2E2),
)

internal val DarkFlipLinkColors = FlipLinkColors(
    isDark = true,
    background = Color(0xFF111214),
    surface = Color(0xFF181A1D),
    surfaceSubtle = Color(0xFF22252A),
    border = Color(0xFF2E3238),
    textPrimary = Color(0xFFF4F4F5),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    accent = Color(0xFF60A5FA),
    accentSurface = Color(0xFF172554),
    statusActive = Color(0xFF22C55E),
    statusActiveSurface = Color(0xFF14532D),
    statusWarning = Color(0xFFF59E0B),
    statusWarningSurface = Color(0xFF451A03),
    statusError = Color(0xFFEF4444),
    statusErrorSurface = Color(0xFF450A0A),
)

internal val AmoledFlipLinkColors = DarkFlipLinkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF090A0B),
    surfaceSubtle = Color(0xFF141619),
    border = Color(0xFF22252A),
)
