package com.printgo.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.R
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.DeepBlue
import com.printgo.app.theme.PrimaryBlue

/**
 * Custom wave shape for the bottom of the blue gradient header.
 * Gently curves across the bottom padding area.
 */
val HeaderWaveShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height

    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w, h * 0.94f)
    cubicTo(
        w * 0.70f, h * 1.0f,
        w * 0.35f, h * 1.01f,
        0f, h * 0.94f
    )
    lineTo(0f, 0f)
    close()
}

@Composable
fun WaveHeader(
    onSettingsClick: () -> Unit,
    statusCard: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HeaderWaveShape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PrimaryBlue,
                        Color(0xFF0F52BA),
                        DeepBlue
                    )
                )
            )
            .statusBarsPadding()
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo row with 16dp vertical padding and 20dp horizontal padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo & Tagline Group
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrintGoLogoIcon(size = 46.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Print",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Go",
                                color = AccentGreen,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        Text(
                            text = stringResource(R.string.tagline),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Settings gear button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status Card in normal flow below the logo row
            statusCard()
        }
    }
}

/**
 * Circular 3D-styled PrintGo Logo Icon.
 */
@Composable
fun PrintGoLogoIcon(size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E88E5),
                        PrimaryBlue,
                        Color(0xFF0A2B64)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.78f)) {
            val w = this.size.width
            val h = this.size.height

            // Outer subtle circle ring
            drawCircle(
                color = AccentGreen.copy(alpha = 0.4f),
                radius = w * 0.46f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Paper Sheet emerging from printer top
            val paperPath = Path().apply {
                moveTo(w * 0.28f, h * 0.44f)
                lineTo(w * 0.28f, h * 0.22f)
                lineTo(w * 0.72f, h * 0.22f)
                lineTo(w * 0.72f, h * 0.44f)
                close()
            }
            drawPath(paperPath, Color.White)

            // Decorative lines on paper sheet
            drawLine(
                color = PrimaryBlue.copy(alpha = 0.4f),
                start = Offset(w * 0.36f, h * 0.28f),
                end = Offset(w * 0.64f, h * 0.28f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = AccentGreen,
                start = Offset(w * 0.36f, h * 0.35f),
                end = Offset(w * 0.54f, h * 0.35f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Printer Body Main Box
            val bodyPath = Path().apply {
                moveTo(w * 0.16f, h * 0.42f)
                lineTo(w * 0.84f, h * 0.42f)
                lineTo(w * 0.84f, h * 0.72f)
                lineTo(w * 0.16f, h * 0.72f)
                close()
            }
            drawPath(
                bodyPath,
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF0F4F8), Color(0xFFD9E2EC))
                )
            )

            // Printer output slot
            drawRoundRect(
                color = Color(0xFF102A43),
                topLeft = Offset(w * 0.25f, h * 0.54f),
                size = androidx.compose.ui.geometry.Size(w * 0.50f, h * 0.11f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )

            // Printed document emerging from bottom slot
            val outputDoc = Path().apply {
                moveTo(w * 0.30f, h * 0.60f)
                lineTo(w * 0.70f, h * 0.60f)
                lineTo(w * 0.70f, h * 0.82f)
                lineTo(w * 0.30f, h * 0.82f)
                close()
            }
            drawPath(outputDoc, Color.White)

            // Accent status power LED dot
            drawCircle(
                color = AccentGreen,
                radius = 2.dp.toPx(),
                center = Offset(w * 0.74f, h * 0.48f)
            )
        }
    }
}
