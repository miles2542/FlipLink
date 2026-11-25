package dev.miles.fliplink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme
import kotlinx.serialization.Serializable

@Serializable
sealed interface FlipLinkRoute {
    @Serializable
    data object AppList : FlipLinkRoute

    @Serializable
    data class AppDetail(val packageName: String) : FlipLinkRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlipLinkTheme {
                FlipLinkNavHost()
            }
        }
    }
}

@Composable
private fun FlipLinkNavHost() {
    val navController = rememberNavController()
    val colors = FlipLinkTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        NavHost(
            navController = navController,
            startDestination = FlipLinkRoute.AppList,
        ) {
            composable<FlipLinkRoute.AppList> {
                ScaffoldPlaceholderScreen(
                    title = "FlipLink",
                    subtitle = "App links manager",
                )
            }
            composable<FlipLinkRoute.AppDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<FlipLinkRoute.AppDetail>()
                ScaffoldPlaceholderScreen(
                    title = route.packageName,
                    subtitle = "Domain verification detail",
                )
            }
        }
    }
}

@Composable
private fun ScaffoldPlaceholderScreen(
    title: String,
    subtitle: String,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlipLinkSpacing.topBarHeight)
                .padding(horizontal = FlipLinkSpacing.rail),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = typography.topBar,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "dev.miles.fliplink",
                style = typography.monoCount,
                color = colors.textMuted,
                maxLines = 1,
            )
        }
        HorizontalDivider(
            thickness = FlipLinkSpacing.hairline,
            color = colors.border,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlipLinkSpacing.rail, vertical = FlipLinkSpacing.rail),
            verticalArrangement = Arrangement.spacedBy(FlipLinkSpacing.unit),
        ) {
            Text(
                text = subtitle.uppercase(),
                style = typography.eyebrow,
                color = colors.textMuted,
            )
            Text(
                text = "Ready for domain link inspection",
                style = typography.appLabel,
                color = colors.textPrimary,
            )
        }
    }
}
