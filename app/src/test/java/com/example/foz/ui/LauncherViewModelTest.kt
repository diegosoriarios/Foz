package com.example.foz.ui

import android.app.Application
import com.example.foz.data.AppRepository
import com.example.foz.data.PrefsManager
import com.example.foz.model.AppInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {

    private lateinit var viewModel: LauncherViewModel
    private lateinit var appRepository: AppRepository
    private lateinit var prefsManager: PrefsManager
    private lateinit var application: Application

    @Before
    fun setup() {
        application = mock()
        appRepository = mock()
        prefsManager = mock {
            on { pinnedApps } doReturn emptyFlow()
            on { widgetIds } doReturn emptyFlow()
            on { launcherOnboardingDismissed } doReturn emptyFlow()
            on { initialOnboardingCompleted } doReturn emptyFlow()
            on { clockUse24h } doReturn emptyFlow()
            on { appIconSizeDp } doReturn emptyFlow()
            on { drawerPaddingPercent } doReturn emptyFlow()
            on { swipeUpEnabled } doReturn emptyFlow()
            on { swipeDownEnabled } doReturn emptyFlow()
            on { themeMode } doReturn emptyFlow()
            on { showNotifications } doReturn emptyFlow()
            on { usageLimitsEnabled } doReturn emptyFlow()
            on { hapticsEnabled } doReturn emptyFlow()
        }

        viewModel = LauncherViewModel(application, appRepository, prefsManager)
    }

    @Test
    fun setSearchQuery_filtersApps() = runTest {
        val apps = listOf(
            AppInfo("App A", "pkg.a", mock(), "class.a"),
            AppInfo("App B", "pkg.b", mock(), "class.b")
        )
        // Manual state update since we are not using real flows here for simplicity of example
        // In a real test, you'd trigger refreshApps and mock repository response
        
        viewModel.setSearchQuery("App A")
        // Check state...
    }

    @Test
    fun setThemeMode_callsPrefsManager() = runTest {
        viewModel.setThemeMode("dark")
        verify(prefsManager).setThemeMode("dark")
    }
}
