package dev.miles.fliplink

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.ui.applist.AppListScreen
import dev.miles.fliplink.ui.applist.AppListViewModel
import dev.miles.fliplink.ui.components.FlipLinkIcons
import dev.miles.fliplink.ui.sheets.SettingsSheet
import dev.miles.fliplink.ui.sheets.ShizukuStatusSheet
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
        val container = (application as FlipLinkApplication).container
        setContent {
            val themeMode by container.preferencesStore.themeMode.collectAsState()
            val dynamicColor by container.preferencesStore.dynamicColor.collectAsState()
            val shizukuState by container.shizukuBridge.state.collectAsState()

            LaunchedEffect(shizukuState) {
                if (shizukuState !is ShizukuState.Binding) {
                    container.repository.refresh()
                }
            }

            FlipLinkTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
            ) {
                val isDark = FlipLinkTheme.colors.isDark
                LaunchedEffect(isDark) {
                    val style = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                    enableEdgeToEdge(
                        statusBarStyle = style,
                        navigationBarStyle = style,
                    )
                }
                FlipLinkNavHost(container = container)
            }
        }
    }
}

@Composable
private fun FlipLinkNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val colors = FlipLinkTheme.colors

    val themeMode by container.preferencesStore.themeMode.collectAsState()
    val dynamicColor by container.preferencesStore.dynamicColor.collectAsState()
    val showSystemApps by container.preferencesStore.showSystemApps.collectAsState()
    val shizukuState by container.shizukuBridge.state.collectAsState()

    var showShizukuSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val appListViewModel = remember(container) {
        AppListViewModel(
            repositoryState = container.repository.state,
            shizukuState = container.shizukuBridge.state,
            showSystemApps = container.preferencesStore.showSystemApps,
            onSetShowSystemApps = container.preferencesStore::setShowSystemApps,
            onRefreshRepository = container.repository::refresh,
        )
    }
    DisposableEffect(appListViewModel) {
        onDispose { appListViewModel.clear() }
    }

    val appListUiState by appListViewModel.uiState.collectAsState()

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
                AppListScreen(
                    uiState = appListUiState,
                    iconCache = container.appIconCache,
                    onSearchQueryChange = appListViewModel::onSearchQueryChange,
                    onFilterSelected = appListViewModel::onFilterSelected,
                    onToggleSystemApps = appListViewModel::onToggleSystemApps,
                    onRefresh = appListViewModel::onRefresh,
                    onDismissShizukuBanner = appListViewModel::onDismissShizukuBanner,
                    onOpenShizukuSheet = { showShizukuSheet = true },
                    onOpenSettingsSheet = { showSettingsSheet = true },
                    onAppClick = { packageName ->
                        navController.navigate(FlipLinkRoute.AppDetail(packageName))
                    },
                )
            }
            composable<FlipLinkRoute.AppDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<FlipLinkRoute.AppDetail>()
                ScaffoldDetailPlaceholderScreen(
                    packageName = route.packageName,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    if (showShizukuSheet) {
        ShizukuStatusSheet(
            state = shizukuState,
            onRequestPermission = container.shizukuBridge::requestPermission,
            onRecheck = container.shizukuBridge::recheck,
            onDismiss = { showShizukuSheet = false },
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            showSystemApps = showSystemApps,
            onThemeModeChange = container.preferencesStore::setThemeMode,
            onDynamicColorChange = container.preferencesStore::setDynamicColor,
            onShowSystemAppsChange = container.preferencesStore::setShowSystemApps,
            onDismiss = { showSettingsSheet = false },
        )
    }
}

@Composable
private fun ScaffoldDetailPlaceholderScreen(
    packageName: String,
    onBack: () -> Unit,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlipLinkSpacing.topBarHeight)
                .padding(start = 4.dp, end = FlipLinkSpacing.rail),
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
            Text(
                text = packageName,
                style = typography.topBar,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                text = "DOMAIN VERIFICATION DETAIL",
                style = typography.eyebrow,
                color = colors.textMuted,
            )
            Text(
                text = packageName,
                style = typography.domain,
                color = colors.textPrimary,
            )
        }
    }
}
