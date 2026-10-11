package com.printgo.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Core Brand Colors
val PrimaryBlue = Color(0xFF0D47A1)
val DeepBlue = Color(0xFF1E5A96)
val AccentGreen = Color(0xFF1DE9B6)
val AccentGreenDark = Color(0xFF00BFA5)

// Card Background Tints
val LightBlue = Color(0xFFE3F2FD)
val LightMint = Color(0xFFE8F8EF)
val LightPurple = Color(0xFFEDE7F6)

// Action Buttons
val ActionBlue = Color(0xFF1565C0)
val ActionGreen = Color(0xFF1B9E5A)
val ActionPurple = Color(0xFF6A3FB5)

// Typography & Base
val HeadingNavy = Color(0xFF1A237E)
val BodyGray = Color(0xFF5F6B7A)
val BackgroundWhite = Color(0xFFFAFBFF)
val CardBorderLight = Color(0xFFDCE3EF)
val SurfaceVariantLight = Color(0xFFF1F5FB)

@Immutable
data class PrintGoColors(
    val primaryBlue: Color,
    val deepBlue: Color,
    val accentGreen: Color,
    val cardPhotoBg: Color,
    val cardDocsBg: Color,
    val cardWebBg: Color,
    val actionBlue: Color,
    val actionGreen: Color,
    val actionPurple: Color,
    val headingColor: Color,
    val bodyColor: Color,
    val screenBackground: Color,
    val cardBorder: Color,
    val surfaceVariant: Color
)

val LocalPrintGoColors = staticCompositionLocalOf {
    PrintGoColors(
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
}
