package com.nishanth.instamapper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB0306F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E7),
    onPrimaryContainer = Color(0xFF3E0021),
    secondary = Color(0xFFC75A1E),
    secondaryContainer = Color(0xFFFFDBC9),
    onSecondaryContainer = Color(0xFF341000),
    tertiary = Color(0xFF6B3FA0),
    tertiaryContainer = Color(0xFFEEDCFF),
    background = Color(0xFFFFF8F8),
    surface = Color(0xFFFFF8F8),
    surfaceVariant = Color(0xFFF3DDE4),
    surfaceContainer = Color(0xFFF9ECEF),
    surfaceContainerHigh = Color(0xFFF4E6EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB0CF),
    onPrimary = Color(0xFF5F1138),
    primaryContainer = Color(0xFF8E1F57),
    onPrimaryContainer = Color(0xFFFFD8E7),
    secondary = Color(0xFFFFB68E),
    secondaryContainer = Color(0xFF7A3000),
    onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFD9B9FF),
    tertiaryContainer = Color(0xFF52267F),
    background = Color(0xFF191113),
    surface = Color(0xFF191113),
    surfaceVariant = Color(0xFF514348),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31272A),
)

val BrandGradient = Brush.linearGradient(
    listOf(Color(0xFF833AB4), Color(0xFFC13584), Color(0xFFE1306C), Color(0xFFF77737))
)

@Composable
fun InstaMapperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
