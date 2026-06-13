package com.example.foz.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.foz.ui.LauncherUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenSidebarIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_sidebarTouch_opensDrawerAtLetter() {
        var openedAtLetter: Char? = null
        val state = LauncherUiState(
            drawerOpen = false,
            initialOnboardingCompleted = true
        )

        composeTestRule.setContent {
            HomeScreen(
                state = state,
                widgetViews = emptyList(),
                onLaunchApp = {},
                onLongPressApp = {},
                onSwipeUp = {},
                onSwipeDown = {},
                onCloseDrawer = {},
                onCloseSwipeUpPanel = {},
                onOpenDrawerAtLetter = { openedAtLetter = it },
                onRequestedSectionConsumed = {},
                onSearchChange = {},
                onOpenAppInfo = {},
                onUninstallApp = {},
                onLaunchShortcut = {},
                onTogglePinned = {},
                onMovePinned = { _, _ -> },
                onDismissAppActions = {},
                onAddWidget = {},
                onRemoveWidget = {},
                onOpenWallpaperPicker = {},
                onRequestLauncherRole = {},
                onOpenLauncherSettings = {},
                onDismissLauncherOnboarding = {},
                onToggleOnboardingFavorite = {},
                onCompleteInitialOnboarding = {},
                onOpenSettings = {}
            )
        }

        // Sidebar is on the right. Touch middle-right area.
        composeTestRule.onRoot().performTouchInput {
            click(Offset(width - 10f, height / 2f))
        }

        assertTrue("Should open drawer at a letter", openedAtLetter != null && openedAtLetter != '★')
    }

    @Test
    fun homeScreen_sidebarStarTouch_closesDrawer() {
        var closeDrawerCalled = false
        val state = LauncherUiState(
            drawerOpen = true,
            initialOnboardingCompleted = true
        )

        composeTestRule.setContent {
            HomeScreen(
                state = state,
                widgetViews = emptyList(),
                onLaunchApp = {},
                onLongPressApp = {},
                onSwipeUp = {},
                onSwipeDown = {},
                onCloseDrawer = { closeDrawerCalled = true },
                onCloseSwipeUpPanel = {},
                onOpenDrawerAtLetter = { },
                onRequestedSectionConsumed = {},
                onSearchChange = {},
                onOpenAppInfo = {},
                onUninstallApp = {},
                onLaunchShortcut = {},
                onTogglePinned = {},
                onMovePinned = { _, _ -> },
                onDismissAppActions = {},
                onAddWidget = {},
                onRemoveWidget = {},
                onOpenWallpaperPicker = {},
                onRequestLauncherRole = {},
                onOpenLauncherSettings = {},
                onDismissLauncherOnboarding = {},
                onToggleOnboardingFavorite = {},
                onCompleteInitialOnboarding = {},
                onOpenSettings = {}
            )
        }

        // Touch top-right area (where Star symbol is)
        composeTestRule.onRoot().performTouchInput {
            // Sidebar starts after some padding, but top-right should hit the Star
            click(Offset(width - 10f, height / 4f)) 
        }

        assertTrue("onCloseDrawer should be called when touching Star in open drawer", closeDrawerCalled)
    }
}
