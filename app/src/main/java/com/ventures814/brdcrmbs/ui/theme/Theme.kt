package com.ventures814.brdcrmbs.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BrdcrmbsColors = darkColorScheme(
    primary = BrightGreen,
    onPrimary = NearBlack,
    primaryContainer = DeepGreen,
    onPrimaryContainer = BrightGreen,
    secondary = BrightGreen,
    onSecondary = NearBlack,
    secondaryContainer = DeepGreen,
    onSecondaryContainer = BrightGreen,
    background = NearBlack,
    onBackground = OffWhite,
    surface = NearBlack,
    onSurface = OffWhite,
    surfaceVariant = Charcoal,
    onSurfaceVariant = MutedText,
    surfaceContainer = Charcoal,
    outline = CardOutline
)

@Composable
fun BrdcrmbsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrdcrmbsColors,
        typography = Typography,
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
