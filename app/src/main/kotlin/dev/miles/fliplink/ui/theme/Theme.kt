package dev.miles.fliplink.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun FlipLinkTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val colors = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDark) DarkFlipLinkColors else LightFlipLinkColors
        ThemeMode.LIGHT -> LightFlipLinkColors
        ThemeMode.DARK -> DarkFlipLinkColors
        ThemeMode.AMOLED -> AmoledFlipLinkColors
    }

    val colorScheme = resolveColorScheme(
        colors = colors,
        themeMode = themeMode,
        dynamicColor = dynamicColor,
    )
    val typography = FlipLinkTypography()

    CompositionLocalProvider(
        LocalFlipLinkColors provides colors,
        LocalFlipLinkTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography.toMaterialTypography(),
            content = content,
        )
    }
}

@Composable
private fun resolveColorScheme(
    colors: FlipLinkColors,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
): ColorScheme {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        val dynamic = if (colors.isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        return if (themeMode == ThemeMode.AMOLED) {
            dynamic.copy(
                background = Color.Black,
                surface = AmoledFlipLinkColors.surface,
            )
        } else {
            dynamic
        }
    }

    return if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.Black,
            primaryContainer = colors.accentSurface,
            onPrimaryContainer = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSubtle,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.statusError,
            errorContainer = colors.statusErrorSurface,
            onErrorContainer = colors.statusError,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            primaryContainer = colors.accentSurface,
            onPrimaryContainer = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceSubtle,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.statusError,
            errorContainer = colors.statusErrorSurface,
            onErrorContainer = colors.statusError,
        )
    }
}

object FlipLinkTheme {
    val colors: FlipLinkColors
        @Composable get() = LocalFlipLinkColors.current

    val typography: FlipLinkTypography
        @Composable get() = LocalFlipLinkTypography.current
}
