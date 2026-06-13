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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    isDrawerOpen: Boolean = false,
    isVisible: Boolean = true,
    onInteractionStarted: (Char) -> Unit = {},
    onInteractionEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val SELECTED_PADDING = 12.dp
    val letters = remember { listOf('★') + ('A'..'Z').toList() }
    val haptics = LocalHapticFeedback.current
    var selectedIndex by remember { mutableIntStateOf(-1) }

    fun updateSelection(y: Float, height: Float, isInitial: Boolean = false) {
        if (height <= 0f) return
        val slotHeight = height / letters.size
        val idx = (y / slotHeight).toInt().coerceIn(0, letters.size - 1)
        if (isInitial || idx != selectedIndex) {
            selectedIndex = idx
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val letter = letters[idx]
            if (isInitial) {
                onInteractionStarted(letter)
            } else {
                if (idx == 0) onBackToFavorites() else onLetterSelected(letter)
            }
        }
    }

    Column(
        modifier = modifier
            .width(34.dp)
            .fillMaxHeight()
            //.background(if (isVisible) MaterialTheme.colorScheme.surface.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    down.consume()
                    updateSelection(down.position.y, size.height.toFloat(), isInitial = true)
                    
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (event.type == PointerEventType.Move) {
                            updateSelection(change.position.y, size.height.toFloat())
                            change.consume()
                        } else if (event.type == PointerEventType.Release || !change.pressed) {
                            selectedIndex = -1
                            onInteractionEnded()
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
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (index == 0) 10.sp else MaterialTheme.typography.labelSmall.fontSize,
                color = if (!isVisible) Color.Transparent else if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier.padding(end = if (isDrawerOpen && selectedIndex == index && isVisible) SELECTED_PADDING else 0.dp)
            )
        }
    }
}
