package com.example.foz.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlphabetSidebar(
    onLetterSelected: (Char) -> Unit,
    onBackToFavorites: () -> Unit = {},
    availableLetters: List<Char> = ('A'..'Z').toList(),
    isDrawerOpen: Boolean = false,
    isVisible: Boolean = true,
    selectedIndex: Int = -1,
    onSelectedIndexChange: (Int) -> Unit = {},
    onInteractionStarted: (Char) -> Unit = {},
    onInteractionEnded: () -> Unit = {},
    showVariablePadding: Boolean = false,
    modifier: Modifier = Modifier
) {
    val letters = remember(availableLetters) { listOf('★') + availableLetters }
    val haptics = LocalHapticFeedback.current
    
    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)
    val currentOnBackToFavorites by rememberUpdatedState(onBackToFavorites)
    val currentOnInteractionStarted by rememberUpdatedState(onInteractionStarted)
    val currentOnInteractionEnded by rememberUpdatedState(onInteractionEnded)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val currentSelectedIndexState = rememberUpdatedState(selectedIndex)

    fun updateSelection(y: Float, height: Float, isInitial: Boolean = false) {
        if (height <= 0f) return
        val slotHeight = height / letters.size
        val idx = (y / slotHeight).toInt().coerceIn(0, letters.size - 1)
        if (isInitial || idx != currentSelectedIndexState.value) {
            currentOnSelectedIndexChange(idx)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val letter = letters[idx]
            
            if (idx == 0) {
                currentOnBackToFavorites()
            } else {
                currentOnLetterSelected(letter)
            }
            
            if (isInitial) {
                currentOnInteractionStarted(letter)
            }
        }
    }

    Column(
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateSelection(down.position.y, size.height.toFloat(), isInitial = true)
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (event.type == PointerEventType.Move) {
                            updateSelection(change.position.y, size.height.toFloat())
                            change.consume()
                        } else if (event.type == PointerEventType.Release || !change.pressed) {
                            currentOnSelectedIndexChange(-1)
                            currentOnInteractionEnded()
                            break
                        }
                    }
                }
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = if (isVisible) Alignment.Start else Alignment.End
    ) {
        letters.forEachIndexed { index, letter ->
            val padding = if (showVariablePadding && selectedIndex != -1) {
                when (Math.abs(index - selectedIndex)) {
                    0 -> 24.dp
                    1 -> 16.dp
                    2 -> 8.dp
                    else -> 0.dp
                }
            } else if (isDrawerOpen && selectedIndex == index) {
                12.dp
            } else {
                0.dp
            }

            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (index == 0) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                color = if (!isVisible) Color.Transparent else if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.padding(end = padding)
            )
        }
    }
}
