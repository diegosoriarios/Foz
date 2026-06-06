package com.example.foz

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentFilter
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
import com.example.foz.model.AppInfo
import com.example.foz.model.AppShortcut
import com.example.foz.receiver.PackageChangeReceiver
import com.example.foz.ui.LauncherViewModel
import com.example.foz.ui.home.HomeScreen
import com.example.foz.ui.theme.FozTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var packageChangeReceiver: PackageChangeReceiver
    private val setWallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        packageChangeReceiver = PackageChangeReceiver {
            viewModel.refreshApps()
        }
        registerReceiver(
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
        )

        setContent {
            FozTheme {
                LauncherRoot(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startWidgetListening()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopWidgetListening()
    }

    override fun onDestroy() {
        unregisterReceiver(packageChangeReceiver)
        super.onDestroy()
    }

    @Composable
    private fun LauncherRoot(viewModel: LauncherViewModel) {
        val state by viewModel.uiState.collectAsState()
        val widgetViews by androidx.compose.runtime.remember(state.widgetIds) {
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
            onSwipeUp = { viewModel.openDrawer() },
            onSwipeDown = { viewModel.showNotificationShade() },
            onCloseDrawer = { viewModel.closeDrawer() },
            onSearchChange = { viewModel.setSearchQuery(it) },
            onOpenAppInfo = { app -> startActivity(viewModel.appInfoIntent(app.packageName)) },
            onUninstallApp = { app -> startActivity(viewModel.uninstallIntent(app.packageName)) },
            onLaunchShortcut = { shortcut -> launchShortcut(shortcut) },
            onTogglePinned = { app -> viewModel.togglePinned(app) },
            onDismissAppActions = { viewModel.dismissAppActions() },
            onAddWidget = { addWidget() },
            onRemoveWidget = { widgetId -> viewModel.removeWidgetId(widgetId) },
            onToggleSystemWallpaper = { viewModel.toggleUseSystemWallpaper() },
            onOpenWallpaperPicker = { openWallpaperPicker() }
        )
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

    private fun addWidget() {
        val providers = viewModel.availableWidgets()
        val provider = providers.firstOrNull()
        if (provider == null) {
            Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show()
            return
        }
        val added = viewModel.addWidget(provider)
        if (!added) {
            requestWidgetBind(provider)
        }
    }

    private fun requestWidgetBind(provider: android.content.ComponentName) {
        val widgetId = viewModel.allocateWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Widget binding not supported", Toast.LENGTH_SHORT).show()
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
}
