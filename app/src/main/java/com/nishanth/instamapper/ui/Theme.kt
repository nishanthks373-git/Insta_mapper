package com.nishanth.instamapper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFD1246F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E6),
    onPrimaryContainer = Color(0xFF3F0020),
    secondary = Color(0xFFE8631A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0CC),
    onSecondaryContainer = Color(0xFF331200),
    tertiary = Color(0xFF6A3BD8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF22005D),
    background = Color(0xFFFFF7F9),
    surface = Color(0xFFFFF7F9),
    surfaceVariant = Color(0xFFF4DDE5),
    onSurfaceVariant = Color(0xFF5A4049),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF0F4),
    surfaceContainer = Color(0xFFFCEAF0),
    surfaceContainerHigh = Color(0xFFF7E3EA),
    surfaceContainerHighest = Color(0xFFF1DCE4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8DBA),
    onPrimary = Color(0xFF5E0B34),
    primaryContainer = Color(0xFF8B1A51),
    onPrimaryContainer = Color(0xFFFFD9E6),
    secondary = Color(0xFFFFB085),
    onSecondary = Color(0xFF552100),
    secondaryContainer = Color(0xFF7A3300),
    onSecondaryContainer = Color(0xFFFFE0CC),
    tertiary = Color(0xFFCDB6FF),
    onTertiary = Color(0xFF390C8C),
    tertiaryContainer = Color(0xFF5127B5),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF140D16),
    surface = Color(0xFF140D16),
    surfaceVariant = Color(0xFF4C3A45),
    onSurfaceVariant = Color(0xFFD9BFCB),
    surfaceContainerLowest = Color(0xFF0F0911),
    surfaceContainerLow = Color(0xFF1C1420),
    surfaceContainer = Color(0xFF221927),
    surfaceContainerHigh = Color(0xFF2C2231),
    surfaceContainerHighest = Color(0xFF372C3C),
)

val InstaColors = listOf(Color(0xFF833AB4), Color(0xFFC13584), Color(0xFFE1306C), Color(0xFFFD1D1D), Color(0xFFF77737))

val BrandGradient = Brush.linearGradient(InstaColors)

/** Vivid two-colour gradients used for tiles, pills and charts. */
object Palette {
    val sunset = listOf(Color(0xFFFF5F6D), Color(0xFFFFC371))
    val ocean = listOf(Color(0xFF00B4DB), Color(0xFF0083B0))
    val grape = listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
    val mint = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    val candy = listOf(Color(0xFFF857A6), Color(0xFFFF5858))
    val gold = listOf(Color(0xFFF7971E), Color(0xFFFFD200))
    val berry = listOf(Color(0xFFC33764), Color(0xFF1D2671))
    val sky = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
    val lime = listOf(Color(0xFF56AB2F), Color(0xFFA8E063))
    val insta = listOf(Color(0xFF833AB4), Color(0xFFE1306C), Color(0xFFF77737))

    val all = listOf(sunset, ocean, grape, mint, candy, gold, berry, sky, lime, insta)

    /** Colours for chips that cycle through the rainbow. */
    val chip = listOf(
        Color(0xFFE1306C), Color(0xFF0083B0), Color(0xFF8E2DE2), Color(0xFF11998E),
        Color(0xFFF7971E), Color(0xFF0072FF), Color(0xFFC33764), Color(0xFF56AB2F),
    )

    val spotify = Color(0xFF1DB954)
    val youtube = Color(0xFFFF0033)
}

@Composable
fun InstaMapperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
