package com.example.foz.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TextWithOutline(
    text: String,
    style: TextStyle,
    mainColor: Color,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color.Black.copy(alpha = 0.6f),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Box(modifier = modifier) {
        // Multi-layered shadow to simulate a thicker outline
        Text(
            text = text,
            style = style.copy(
                shadow = Shadow(
                    color = outlineColor,
                    offset = Offset(0f, 0f),
                    blurRadius = 2f
                )
            ),
            color = mainColor,
            maxLines = maxLines,
            overflow = overflow
        )
        Text(
            text = text,
            style = style.copy(
                shadow = Shadow(
                    color = outlineColor.copy(alpha = 0.3f),
                    offset = Offset(1f, 1f),
                    blurRadius = 2f
                )
            ),
            color = mainColor,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
