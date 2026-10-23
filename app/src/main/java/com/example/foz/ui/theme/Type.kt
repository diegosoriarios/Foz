package com.example.foz.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.foz.R

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

// Sizes/weights/letter-spacing mirror the Material 3 defaults, with the
// family switched to Figtree app-wide. bodyLarge keeps the original override.
private val FigtreeStyle = TextStyle(fontFamily = Figtree)

val Typography = Typography(
    displayLarge = FigtreeStyle.copy(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = FigtreeStyle.copy(
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = FigtreeStyle.copy(
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = FigtreeStyle.copy(
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = FigtreeStyle.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = FigtreeStyle.copy(
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = FigtreeStyle.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = FigtreeStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = FigtreeStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = FigtreeStyle.copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = FigtreeStyle.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = FigtreeStyle.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = FigtreeStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = FigtreeStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = FigtreeStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
