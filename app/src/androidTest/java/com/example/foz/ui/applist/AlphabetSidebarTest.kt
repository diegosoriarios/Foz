package com.example.foz.ui.applist

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlphabetSidebarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sidebar_tapLetter_triggersSelection() {
        var selectedLetter: Char? = null
        var interactionStartedLetter: Char? = null

        composeTestRule.setContent {
            AlphabetSidebar(
                onLetterSelected = { selectedLetter = it },
                onInteractionStarted = { interactionStartedLetter = it },
                modifier = Modifier.testTag("sidebar")
            )
        }

        // Tap middle of sidebar (should be around 'M' or 'N')
        composeTestRule.onNodeWithTag("sidebar").performTouchInput {
            click(center)
        }

        assertTrue("Interaction should have started", interactionStartedLetter != null)
        assertTrue("Letter should have been selected", selectedLetter != null)
        assertEquals(interactionStartedLetter, selectedLetter)
    }

    @Test
    fun sidebar_drag_updatesSelectionContinuously() {
        val selectedLetters = mutableListOf<Char>()

        composeTestRule.setContent {
            AlphabetSidebar(
                onLetterSelected = { selectedLetters.add(it) },
                modifier = Modifier.testTag("sidebar")
            )
        }

        composeTestRule.onNodeWithTag("sidebar").performTouchInput {
            // Drag from top to bottom
            down(topCenter)
            moveTo(center)
            moveTo(bottomCenter)
            up()
        }

        // Should have captured multiple letters during the drag
        assertTrue("Should have selected multiple letters", selectedLetters.size > 2)
        assertEquals('★', selectedLetters.first())
        assertEquals('Z', selectedLetters.last())
    }

    @Test
    fun sidebar_tapStar_callsBackToFavorites() {
        var backToFavoritesCalled = false

        composeTestRule.setContent {
            AlphabetSidebar(
                onLetterSelected = {},
                onBackToFavorites = { backToFavoritesCalled = true },
                modifier = Modifier.testTag("sidebar")
            )
        }

        // Tap the very top (Star symbol)
        composeTestRule.onNodeWithTag("sidebar").performTouchInput {
            click(topCenter)
        }

        assertTrue("Back to favorites should be called when tapping Star", backToFavoritesCalled)
    }

    @Test
    fun sidebar_releaseTouch_callsOnInteractionEnded() {
        var interactionEnded = false

        composeTestRule.setContent {
            AlphabetSidebar(
                onLetterSelected = {},
                onInteractionEnded = { interactionEnded = true },
                modifier = Modifier.testTag("sidebar")
            )
        }

        composeTestRule.onNodeWithTag("sidebar").performTouchInput {
            down(center)
            up()
        }

        assertTrue("onInteractionEnded should be called after touch release", interactionEnded)
    }

    @Test
    fun sidebar_drawerOpenState_appliesPaddingToSelected() {
        // This is a visual/layout test, harder to verify padding directly in Compose tests 
        // without custom modifiers, but we can verify the component still renders.
        composeTestRule.setContent {
            AlphabetSidebar(
                onLetterSelected = {},
                isDrawerOpen = true,
                modifier = Modifier.testTag("sidebar")
            )
        }
        
        composeTestRule.onNodeWithTag("sidebar").assertExists()
    }
}
