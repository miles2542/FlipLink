package dev.miles.fliplink

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.miles.fliplink.data.shizuku.ShizukuState
import dev.miles.fliplink.ui.appdetail.AppDetailScreen
import dev.miles.fliplink.ui.appdetail.AppDetailViewModel
import dev.miles.fliplink.ui.applist.AppListScreen
import dev.miles.fliplink.ui.applist.AppListViewModel
import dev.miles.fliplink.ui.sheets.DriftReviewSheet
import dev.miles.fliplink.ui.sheets.SettingsSheet
import dev.miles.fliplink.ui.sheets.ShizukuStatusSheet
import dev.miles.fliplink.ui.theme.FlipLinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
sealed interface FlipLinkRoute {
    @Serializable
    data object AppList : FlipLinkRoute

    @Serializable
    data class AppDetail(val packageName: String) : FlipLinkRoute
}

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as FlipLinkApplication).container
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

    override fun onStart() {
        super.onStart()
        container.shizukuBridge.onForeground()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            container.shizukuBridge.onBackground()
        }
        super.onStop()
    }
}

@Composable
private fun FlipLinkNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val colors = FlipLinkTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val themeMode by container.preferencesStore.themeMode.collectAsState()
    val dynamicColor by container.preferencesStore.dynamicColor.collectAsState()
    val showSystemApps by container.preferencesStore.showSystemApps.collectAsState()
    val shizukuState by container.shizukuBridge.state.collectAsState()

    var showShizukuSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDriftReviewSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = container.snapshotStore.exportJson(container.repository.state.value.snapshot)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open output file")
                }
            }
            if (result.isSuccess) {
                snackbarHostState.showSnackbar("Config exported")
            } else {
                snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Export failed")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Unable to read selected file")
                }
            }
            val rawJson = readResult.getOrElse { error ->
                snackbarHostState.showSnackbar(error.message ?: "Import failed")
                return@launch
            }
            val parsed = container.snapshotStore.importJson(rawJson).getOrElse { error ->
                snackbarHostState.showSnackbar(error.message ?: "Invalid config file")
                return@launch
            }
            container.repository.importSnapshot(parsed)
            showSettingsSheet = false
            val currentDrifts = container.repository.state.value.drifts
            if (shizukuState.isReady && currentDrifts.isNotEmpty()) {
                showDriftReviewSheet = true
            } else {
                val appNoun = if (parsed.packages.size == 1) "app" else "apps"
                snackbarHostState.showSnackbar("Config imported (${parsed.packages.size} $appNoun)")
            }
        }
    }

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
                    onOpenDriftReviewSheet = { showDriftReviewSheet = true },
                    onReapplyAllDrift = {
                        coroutineScope.launch {
                            val result = container.repository.reapplyDrift(null)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Saved configuration re-applied")
                            } else {
                                snackbarHostState.showSnackbar(
                                    result.exceptionOrNull()?.message ?: "Failed to re-apply changes",
                                )
                            }
                        }
                    },
                    onAppClick = { packageName ->
                        navController.navigate(FlipLinkRoute.AppDetail(packageName))
                    },
                )
            }
            composable<FlipLinkRoute.AppDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<FlipLinkRoute.AppDetail>()
                val detailViewModel = remember(container, route.packageName) {
                    AppDetailViewModel(
                        packageName = route.packageName,
                        repositoryState = container.repository.state,
                        onSetLinkHandlingAllowed = container.repository::setLinkHandlingAllowed,
                        onSetDomainEnabled = container.repository::setDomainEnabled,
                        onSetAllDomainsEnabled = container.repository::setAllDomainsEnabled,
                        onTakeOverDomain = container.repository::takeOverDomain,
                        onUndoTakeover = container.repository::undoTakeover,
                    )
                }
                DisposableEffect(detailViewModel) {
                    onDispose { detailViewModel.clear() }
                }
                val detailUiState by detailViewModel.uiState.collectAsState()
                AppDetailScreen(
                    uiState = detailUiState,
                    events = detailViewModel.events,
                    iconCache = container.appIconCache,
                    onBack = { navController.popBackStack() },
                    onToggleMaster = detailViewModel::onToggleMaster,
                    onToggleDomain = detailViewModel::onToggleDomain,
                    onEnableAll = detailViewModel::onEnableAll,
                    onDisableAll = detailViewModel::onDisableAll,
                    onTakeOver = detailViewModel::onTakeOver,
                    onUndoTakeover = detailViewModel::onUndoTakeoverClicked,
                    onOpenMasterAdb = { detailViewModel.openMasterAdbSheet() },
                    onOpenAllAdb = detailViewModel::openAllAdbSheet,
                    onOpenCustomAdb = detailViewModel::openCustomAdbSheet,
                    onDismissAdbSheet = detailViewModel::dismissAdbSheet,
                    onCommandCopied = detailViewModel::onCommandCopied,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
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
            onExportConfig = { exportLauncher.launch("fliplink-config.json") },
            onImportConfig = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onDismiss = { showSettingsSheet = false },
        )
    }

    if (showDriftReviewSheet) {
        DriftReviewSheet(
            drifts = appListUiState.drifts,
            appLabels = appListUiState.appLabelsByPackage,
            iconCache = container.appIconCache,
            inFlightKeys = appListUiState.inFlightKeys,
            onReapplyPackage = { pkg ->
                coroutineScope.launch {
                    val result = container.repository.reapplyDrift(pkg)
                    if (result.isFailure) {
                        snackbarHostState.showSnackbar(
                            result.exceptionOrNull()?.message ?: "Failed to re-apply $pkg",
                        )
                    }
                }
            },
            onAcceptPackage = { pkg ->
                coroutineScope.launch {
                    container.repository.acceptDrift(pkg)
                }
            },
            onReapplyAll = {
                coroutineScope.launch {
                    val result = container.repository.reapplyDrift(null)
                    if (result.isSuccess) {
                        showDriftReviewSheet = false
                        snackbarHostState.showSnackbar("All changes re-applied")
                    } else {
                        snackbarHostState.showSnackbar(
                            result.exceptionOrNull()?.message ?: "Failed to re-apply changes",
                        )
                    }
                }
            },
            onAcceptAll = {
                coroutineScope.launch {
                    container.repository.acceptDrift(null)
                    showDriftReviewSheet = false
                }
            },
            onDismiss = { showDriftReviewSheet = false },
        )
    }
}
