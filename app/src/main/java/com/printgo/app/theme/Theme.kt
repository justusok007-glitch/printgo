package com.printgo.app.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = HeadingNavy,
    secondary = DeepBlue,
    onSecondary = Color.White,
    secondaryContainer = LightMint,
    onSecondaryContainer = HeadingNavy,
    tertiary = AccentGreen,
    onTertiary = HeadingNavy,
    background = BackgroundWhite,
    onBackground = HeadingNavy,
    surface = Color.White,
    onSurface = HeadingNavy,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = BodyGray,
    outline = CardBorderLight
)

@Composable
fun PrintGoTheme(
    darkTheme: Boolean = false, // Always light theme
    content: @Composable () -> Unit
) {
    val customColors = PrintGoColors(
        primaryBlue = PrimaryBlue,
        deepBlue = DeepBlue,
        accentGreen = AccentGreen,
        cardPhotoBg = LightBlue,
        cardDocsBg = LightMint,
        cardWebBg = LightPurple,
        actionBlue = ActionBlue,
        actionGreen = ActionGreen,
        actionPurple = ActionPurple,
        headingColor = HeadingNavy,
        bodyColor = BodyGray,
        screenBackground = BackgroundWhite,
        cardBorder = CardBorderLight,
        surfaceVariant = SurfaceVariantLight
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // White status bar icons over blue header
                insetsController.isAppearanceLightStatusBars = false
                // Dark navigation bar icons on light bottom background
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(
        LocalPrintGoColors provides customColors
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}

object PrintGoTheme {
    val colors: PrintGoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPrintGoColors.current
}
