package com.rirdev.rirdrop.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = BgDark,
    primaryContainer = NeonLimeContainer,
    onPrimaryContainer = NeonLime,
    secondary = BrandGold,
    onSecondary = BgDark,
    secondaryContainer = BrandGoldContainer,
    onSecondaryContainer = BrandGold,
    tertiary = StreamCyan,
    onTertiary = BgDark,
    tertiaryContainer = StreamCyanContainer,
    onTertiaryContainer = StreamCyan,
    background = BgDark,
    onBackground = TextPrimary,
    surface = Color(0xFF141519),
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Color(0xFF0F1014),
    surfaceContainerLow = Color(0xFF17181F),
    surfaceContainer = Color(0xFF1E2028),
    surfaceContainerHigh = Color(0xFF262833),
    surfaceContainerHighest = Color(0xFF2E313E),
    outline = BorderSubtle,
    outlineVariant = Color(0xFF282B37),
    error = StatusError
)

val RirDropShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RirDropTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgDark.toArgb()
            window.navigationBarColor = BgDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = RirDropShapes,
        content = content
    )
}
