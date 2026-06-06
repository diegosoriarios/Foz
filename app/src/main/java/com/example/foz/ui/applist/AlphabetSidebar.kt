package com.example.foz.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AlphabetSidebar(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = remember { ('A'..'Z').toList() }
    val haptics = LocalHapticFeedback.current
    var selectedIndex by remember { mutableIntStateOf(-1) }

    fun indexFromY(y: Float, height: Float, itemCount: Int): Int {
        if (height <= 0f || itemCount == 0) return -1
        val slotHeight = height / itemCount
        return (y / slotHeight).toInt().coerceIn(0, itemCount - 1)
    }

    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val idx = indexFromY(offset.y, size.height.toFloat(), letters.size)
                        if (idx != -1 && idx != selectedIndex) {
                            selectedIndex = idx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letters[idx])
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        val idx = indexFromY(change.position.y, size.height.toFloat(), letters.size)
                        if (idx != -1 && idx != selectedIndex) {
                            selectedIndex = idx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letters[idx])
                        }
                    }
                )
            }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { index, letter ->
            Box(
                modifier = Modifier.clickable {
                    if (selectedIndex != index) {
                        selectedIndex = index
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onLetterSelected(letter)
                }
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = if (selectedIndex == index) 6.dp else 0.dp)
                )
            }
        }
    }
}
