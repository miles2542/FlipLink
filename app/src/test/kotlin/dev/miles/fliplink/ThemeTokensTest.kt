package dev.miles.fliplink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.ui.theme.AmoledFlipLinkColors
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun spacing_meets_accessibility_and_density_invariants() {
        assertTrue(FlipLinkSpacing.minTouchTarget >= 48.dp)
        assertEquals(56.dp, FlipLinkSpacing.topBarHeight)
        assertEquals(60.dp, FlipLinkSpacing.appRowHeight)
        assertEquals(56.dp, FlipLinkSpacing.domainRowHeight)
        assertEquals(0.5.dp, FlipLinkSpacing.hairline)
    }

    @Test
    fun amoled_palette_uses_pure_black_canvas() {
        assertEquals(Color.Black, AmoledFlipLinkColors.background)
        assertTrue(AmoledFlipLinkColors.isDark)
        assertEquals(4, ThemeMode.entries.size)
    }
}
