package com.example.foz

import android.app.WallpaperManager
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.receiver.PackageChangeReceiver
import com.example.foz.ui.LauncherUiState
import com.example.foz.ui.LauncherViewModel
import com.example.foz.ui.home.components.WidgetPickerDialog
import com.example.foz.ui.home.HomeScreen
import com.example.foz.ui.settings.SettingsActivity
import com.example.foz.ui.theme.FozTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var packageChangeReceiver: PackageChangeReceiver
    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                viewModel.onWallpaperChanged()
            }
        }
    }
    private val setWallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val launcherRoleRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshLauncherRoleStatus()
    }
    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        
        if (result.resultCode == RESULT_OK) {
            if (widgetId != -1) {
                val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
                if (info?.configure != null) {
                    startConfigureWidget(widgetId)
                }
                viewModel.addWidgetId(widgetId)
            }
        } else {
            if (widgetId != -1) {
                viewModel.deleteWidgetId(widgetId)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            viewModel.refreshWeather()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        packageChangeReceiver = PackageChangeReceiver {
            viewModel.refreshApps()
        }

        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            val state by viewModel.uiState.collectAsState()
            val darkTheme = state.monochromeMode || state.isDarkTheme(androidx.compose.foundation.isSystemInDarkTheme())
            FozTheme(
                darkTheme = darkTheme,
                dynamicColor = state.useDynamicColor && !state.monochromeMode,
                wallpaperChangeToken = state.wallpaperChangeToken,
                monochromeMode = state.monochromeMode
            ) {
                LauncherRoot(viewModel = viewModel, state = state)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshLauncherRoleStatus()
        registerReceiver(
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wallpaperChangedReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(
                wallpaperChangedReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
            )
        }
        viewModel.startWidgetListening()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLauncherRoleStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Any new intent to the launcher while it's running (like pressing Home) should reset state
        viewModel.resetNavigationIfBlocked()
    }

    override fun onStop() {
        unregisterReceiver(packageChangeReceiver)
        unregisterReceiver(wallpaperChangedReceiver)
        viewModel.stopWidgetListening()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Composable
    private fun LauncherRoot(viewModel: LauncherViewModel, state: LauncherUiState) {

        val widgetViews by androidx.compose.runtime.remember(state.widgetIds, state.wallpaperChangeToken) {
            androidx.compose.runtime.mutableStateOf(viewModel.widgetHostViews())
        }

        LaunchedEffect(Unit) {
            viewModel.refreshApps()
        }

        HomeScreen(
            state = state,
            widgetViews = widgetViews,
            onLaunchApp = { app -> launchApp(app) },
            onLongPressApp = { app -> viewModel.onAppLongPress(app) },
            onSwipeUp = { viewModel.openSwipeUpPanel() },
            onSwipeDown = {
                if (state.showNotifications) {
                    viewModel.showNotificationShade()
                }
            },
            onCloseDrawer = { viewModel.closeDrawer() },
            onCloseSwipeUpPanel = { viewModel.closeSwipeUpPanel() },
            onOpenDrawerAtLetter = { letter -> viewModel.openDrawerAtLetter(letter) },
            onRequestedSectionConsumed = { viewModel.clearRequestedSectionLetter() },
            onSearchChange = { viewModel.setSearchQuery(it) },
            onOpenAppInfo = { app -> startActivity(viewModel.appInfoIntent(app.packageName)) },
            onUninstallApp = { app -> startActivity(viewModel.uninstallIntent(app.packageName)) },
            onLaunchShortcut = { shortcut -> launchShortcut(shortcut) },
            onTogglePinned = { app -> viewModel.togglePinned(app) },
            onMovePinned = { app, direction -> viewModel.movePinned(app, direction) },
            onDismissAppActions = { viewModel.dismissAppActions() },
            onRenameApp = { app, newName -> viewModel.renameApp(app.packageName, newName) },
            onHideApp = { app, hide -> viewModel.hideApp(app.packageName, hide) },
            onSetCustomIcon = { app, iconName -> viewModel.setCustomIcon(app.packageName, iconName) },
            onAddWidget = { viewModel.openWidgetPicker() },
            onRemoveWidget = { widgetId -> viewModel.removeWidgetId(widgetId) },
            onMoveWidget = { widgetId, direction -> viewModel.moveWidget(widgetId, direction) },
            onResizeWidget = { widgetId, height -> viewModel.resizeWidget(widgetId, height) },
            onConfigureWidget = { widgetId -> startConfigureWidget(widgetId) },
            onOpenWallpaperPicker = { openWallpaperPicker() },
            onRequestLauncherRole = { requestLauncherRole() },
            onOpenLauncherSettings = { openDefaultLauncherSettings() },
            onDismissLauncherOnboarding = { viewModel.dismissLauncherOnboarding() },
            onToggleOnboardingFavorite = { app -> viewModel.toggleOnboardingFavorite(app.packageName) },
            onCompleteInitialOnboarding = { viewModel.completeInitialOnboarding() },
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        )

        if (state.showWidgetPicker) {
            WidgetPickerDialog(
                widgets = state.availableWidgets,
                onWidgetSelected = { widgetInfo ->
                    viewModel.dismissWidgetPicker()
                    requestWidgetBind(widgetInfo.providerInfo)
                },
                onDismiss = { viewModel.dismissWidgetPicker() }
            )
        }
    }

    private fun launchApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            viewModel.closeDrawer()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Unable to launch ${app.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchShortcut(shortcut: AppShortcut) {
        viewModel.startShortcut(shortcut)
        viewModel.closeDrawer()
    }

    private fun startConfigureWidget(widgetId: Int) {
        try {
            // Use the system configuration intent
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to configure widget", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestWidgetBind(info: android.appwidget.AppWidgetProviderInfo) {
        val widgetId = viewModel.allocateWidgetId()
        val bound = AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(widgetId, info.provider)
        if (bound) {
            if (info.configure != null) {
                startConfigureWidget(widgetId)
            }
            viewModel.addWidgetId(widgetId)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            widgetBindLauncher.launch(intent)
        }
    }

    private fun openWallpaperPicker() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Intent(Intent.ACTION_SET_WALLPAPER)
        } else {
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        }
        try {
            setWallpaperLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Unable to open wallpaper settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                launcherRoleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        openDefaultLauncherSettings()
    }

    private fun openDefaultLauncherSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        )
        val launched = intents.any { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!launched) {
            Toast.makeText(this, "Unable to open launcher settings", Toast.LENGTH_SHORT).show()
        }
    }
}
