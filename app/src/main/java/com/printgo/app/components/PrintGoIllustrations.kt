package com.printgo.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-fidelity vector illustrations matching the PrintGo design.
 */

@Composable
fun PrintGoLogoIcon(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Left motion lines (Cyan and Accent Green)
        drawLine(
            color = Color(0xFF1DE9B6),
            start = Offset(w * 0.05f, h * 0.42f),
            end = Offset(w * 0.28f, h * 0.42f),
            strokeWidth = h * 0.065f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(w * 0.01f, h * 0.54f),
            end = Offset(w * 0.22f, h * 0.54f),
            strokeWidth = h * 0.065f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF1DE9B6),
            start = Offset(w * 0.07f, h * 0.66f),
            end = Offset(w * 0.25f, h * 0.66f),
            strokeWidth = h * 0.065f,
            cap = StrokeCap.Round
        )

        // Top Paper Feed (White with rounded top)
        val paperPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = w * 0.38f,
                    top = h * 0.12f,
                    right = w * 0.82f,
                    bottom = h * 0.45f,
                    radiusX = w * 0.07f,
                    radiusY = w * 0.07f
                )
            )
        }
        drawPath(paperPath, Color.White)

        // Printer Body (White rounded block)
        val bodyPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = w * 0.28f,
                    top = h * 0.34f,
                    right = w * 0.94f,
                    bottom = h * 0.76f,
                    radiusX = w * 0.12f,
                    radiusY = w * 0.12f
                )
            )
        }
        drawPath(bodyPath, Color.White)

        // Front Output Slot
        drawRoundRect(
            color = Color(0xFF0D47A1),
            topLeft = Offset(w * 0.37f, h * 0.55f),
            size = Size(w * 0.48f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        // Ejected Paper (trapezoid / angled out)
        val ejectedPaper = Path().apply {
            moveTo(w * 0.40f, h * 0.62f)
            lineTo(w * 0.82f, h * 0.62f)
            lineTo(w * 0.88f, h * 0.96f)
            lineTo(w * 0.34f, h * 0.96f)
            close()
        }
        drawPath(ejectedPaper, Color.White)

        // 3 Color Stripes (Cyan, Magenta, Yellow)
        val stripeW = (w * 0.48f) / 3f
        // Cyan
        val cyanStripe = Path().apply {
            moveTo(w * 0.38f, h * 0.68f)
            lineTo(w * 0.38f + stripeW * 0.9f, h * 0.68f)
            lineTo(w * 0.34f + stripeW * 0.95f, h * 0.94f)
            lineTo(w * 0.35f, h * 0.94f)
            close()
        }
        drawPath(cyanStripe, Color(0xFF00E5FF))

        // Magenta
        val magentaStripe = Path().apply {
            moveTo(w * 0.38f + stripeW * 1.05f, h * 0.68f)
            lineTo(w * 0.38f + stripeW * 1.95f, h * 0.68f)
            lineTo(w * 0.35f + stripeW * 1.95f, h * 0.94f)
            lineTo(w * 0.35f + stripeW * 1.05f, h * 0.94f)
            close()
        }
        drawPath(magentaStripe, Color(0xFFFF2A85))

        // Yellow
        val yellowStripe = Path().apply {
            moveTo(w * 0.38f + stripeW * 2.1f, h * 0.68f)
            lineTo(w * 0.80f, h * 0.68f)
            lineTo(w * 0.86f, h * 0.94f)
            lineTo(w * 0.36f + stripeW * 2.1f, h * 0.94f)
            close()
        }
        drawPath(yellowStripe, Color(0xFFFFD600))
    }
}

/**
 * 3D-styled Printer Hero Illustration faithfully matching the user's reference image:
 * - Sleek modern white desktop printer at 3/4 perspective
 * - Rear upright paper sheet feed
 * - Dark plinth base & recessed scanner line
 * - Front horizontal groove and status LED indicators
 * - Output tray with ejected white sheet featuring 3 bold stripes (Cyan, Magenta, Yellow)
 * - White ceramic potted green houseplant on the right
 * - Floating lime-green accent sparks above the plant
 */
@Composable
fun PrinterHeroIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Ambient Drop Shadows under printer, ejected page, and potted plant
        // Main printer shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55001438), Color(0x00001438)),
                center = Offset(w * 0.44f, h * 0.80f),
                radius = w * 0.42f
            ),
            topLeft = Offset(w * 0.08f, h * 0.68f),
            size = Size(w * 0.72f, h * 0.26f)
        )

        // Shadow under plant pot
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66001438), Color(0x00001438)),
                center = Offset(w * 0.88f, h * 0.81f),
                radius = w * 0.16f
            ),
            topLeft = Offset(w * 0.76f, h * 0.74f),
            size = Size(w * 0.24f, h * 0.14f)
        )

        // 2. Rear Paper Input (upright standing paper at the back)
        // Dark navy bracket behind paper
        val rearBracket = Path().apply {
            moveTo(w * 0.38f, h * 0.22f)
            lineTo(w * 0.66f, h * 0.24f)
            lineTo(w * 0.68f, h * 0.34f)
            lineTo(w * 0.36f, h * 0.32f)
            close()
        }
        drawPath(rearBracket, Color(0xFF122340))

        // White paper sheet standing upright in feeder
        val rearPaper = Path().apply {
            moveTo(w * 0.42f, h * 0.12f)
            lineTo(w * 0.65f, h * 0.13f)
            lineTo(w * 0.63f, h * 0.32f)
            lineTo(w * 0.40f, h * 0.31f)
            close()
        }
        drawPath(
            rearPaper,
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0)),
                startY = h * 0.12f,
                endY = h * 0.32f
            )
        )
        drawPath(rearPaper, Color(0x22000000), style = Stroke(width = 1f))

        // 3. Dark Navy Plinth / Rubber Base Feet under printer
        val darkBase = Path().apply {
            moveTo(w * 0.08f, h * 0.69f)
            lineTo(w * 0.74f, h * 0.72f)
            lineTo(w * 0.72f, h * 0.80f)
            lineTo(w * 0.07f, h * 0.76f)
            close()
        }
        drawPath(
            darkBase,
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                startY = h * 0.69f,
                endY = h * 0.80f
            )
        )

        // 4. Printer Main Body
        // Top surface (tilted 3D plane)
        val topSurface = Path().apply {
            moveTo(w * 0.20f, h * 0.30f)
            lineTo(w * 0.68f, h * 0.32f)
            lineTo(w * 0.77f, h * 0.36f)
            lineTo(w * 0.75f, h * 0.40f)
            lineTo(w * 0.11f, h * 0.37f)
            close()
        }
        drawPath(
            topSurface,
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFEDF2F7)),
                startY = h * 0.30f,
                endY = h * 0.40f
            )
        )

        // Top lid dark recess line (scanner slit)
        val scannerLid = Path().apply {
            moveTo(w * 0.17f, h * 0.32f)
            lineTo(w * 0.66f, h * 0.34f)
            lineTo(w * 0.65f, h * 0.38f)
            lineTo(w * 0.16f, h * 0.36f)
            close()
        }
        drawPath(scannerLid, Color(0xFF1E293B))

        // Left 3D side edge
        val leftEdge = Path().apply {
            moveTo(w * 0.11f, h * 0.37f)
            lineTo(w * 0.17f, h * 0.39f)
            lineTo(w * 0.15f, h * 0.68f)
            lineTo(w * 0.09f, h * 0.66f)
            close()
        }
        drawPath(
            leftEdge,
            Brush.horizontalGradient(
                colors = listOf(Color(0xFFD9E2EC), Color(0xFFF0F4F8)),
                startX = w * 0.09f,
                endX = w * 0.17f
            )
        )

        // Main Front Body Face
        val frontFace = Path().apply {
            moveTo(w * 0.17f, h * 0.39f)
            lineTo(w * 0.75f, h * 0.41f)
            lineTo(w * 0.74f, h * 0.73f)
            lineTo(w * 0.15f, h * 0.70f)
            close()
        }
        drawPath(
            frontFace,
            Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9), Color(0xFFE2E8F0)),
                startY = h * 0.39f,
                endY = h * 0.73f
            )
        )

        // Subtle horizontal seam separating scanner lid from front chassis
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(w * 0.16f, h * 0.48f),
            end = Offset(w * 0.74f, h * 0.50f),
            strokeWidth = 1.5f
        )

        // Front horizontal handle groove (recessed bar in middle)
        val handleBar = Path().apply {
            addRoundRect(
                RoundRect(
                    left = w * 0.31f,
                    top = h * 0.42f,
                    right = w * 0.40f,
                    bottom = h * 0.44f,
                    radiusX = 4f,
                    radiusY = 4f
                )
            )
        }
        drawPath(handleBar, Color(0xFFCBD5E1))

        // Status indicator LEDs
        // Left faint power LED
        drawCircle(
            color = Color(0xFF93C5FD).copy(alpha = 0.8f),
            radius = w * 0.012f,
            center = Offset(w * 0.20f, h * 0.43f)
        )

        // Right dual status dots (soft blue & cyan dots)
        drawCircle(
            color = Color(0xFF93C5FD),
            radius = w * 0.010f,
            center = Offset(w * 0.56f, h * 0.45f)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = w * 0.012f,
            center = Offset(w * 0.59f, h * 0.45f)
        )

        // Front Output Tray Opening (dark cavity)
        val outputMouth = Path().apply {
            moveTo(w * 0.20f, h * 0.55f)
            lineTo(w * 0.56f, h * 0.57f)
            lineTo(w * 0.55f, h * 0.75f)
            lineTo(w * 0.19f, h * 0.73f)
            close()
        }
        drawPath(outputMouth, Color(0xFF0F172A))

        // Inner tray highlight rim
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(w * 0.20f, h * 0.55f),
            end = Offset(w * 0.56f, h * 0.57f),
            strokeWidth = 2f
        )

        // 5. Ejected Printed Paper with 3 Vibrant Stripes
        // White sheet sliding forward-left out of the mouth
        val printedSheet = Path().apply {
            moveTo(w * 0.22f, h * 0.56f)
            lineTo(w * 0.52f, h * 0.60f)
            lineTo(w * 0.41f, h * 0.88f)
            lineTo(w * 0.07f, h * 0.80f)
            close()
        }
        // Shadow immediately under ejected paper
        val sheetShadow = Path().apply {
            moveTo(w * 0.07f, h * 0.80f)
            lineTo(w * 0.41f, h * 0.88f)
            lineTo(w * 0.40f, h * 0.91f)
            lineTo(w * 0.06f, h * 0.83f)
            close()
        }
        drawPath(sheetShadow, Color(0x33001438))

        // Pure white printed sheet
        drawPath(printedSheet, Color.White)
        drawPath(printedSheet, Color(0x1A000000), style = Stroke(width = 1f))

        // Calculate stripe coordinates for 3 wide bars: Cyan, Magenta, Yellow
        val paperTopW = (w * 0.52f - w * 0.22f)
        val paperBotW = (w * 0.41f - w * 0.07f)
        val stripeTopWidth = paperTopW * 0.24f
        val stripeBotWidth = paperBotW * 0.24f
        val startTopX = w * 0.26f
        val startBotX = w * 0.12f

        // Stripe 1: Cyan / Bright Electric Blue
        val cyanBar = Path().apply {
            moveTo(startTopX, h * 0.64f)
            lineTo(startTopX + stripeTopWidth, h * 0.65f)
            lineTo(startBotX + stripeBotWidth, h * 0.84f)
            lineTo(startBotX, h * 0.82f)
            close()
        }
        drawPath(cyanBar, Color(0xFF00B0FF))

        // Stripe 2: Magenta / Hot Pink
        val magentaTopX = startTopX + stripeTopWidth + paperTopW * 0.06f
        val magentaBotX = startBotX + stripeBotWidth + paperBotW * 0.06f
        val magentaBar = Path().apply {
            moveTo(magentaTopX, h * 0.65f)
            lineTo(magentaTopX + stripeTopWidth, h * 0.66f)
            lineTo(magentaBotX + stripeBotWidth, h * 0.85f)
            lineTo(magentaBotX, h * 0.83f)
            close()
        }
        drawPath(magentaBar, Color(0xFFFF2E93))

        // Stripe 3: Sunny Golden Yellow
        val yellowTopX = magentaTopX + stripeTopWidth + paperTopW * 0.06f
        val yellowBotX = magentaBotX + stripeBotWidth + paperBotW * 0.06f
        val yellowBar = Path().apply {
            moveTo(yellowTopX, h * 0.66f)
            lineTo(yellowTopX + stripeTopWidth, h * 0.67f)
            lineTo(yellowBotX + stripeBotWidth, h * 0.86f)
            lineTo(yellowBotX, h * 0.84f)
            close()
        }
        drawPath(yellowBar, Color(0xFFFFB703))

        // 6. Potted Houseplant on Right
        // White ceramic pot
        val potPath = Path().apply {
            moveTo(w * 0.80f, h * 0.54f)
            lineTo(w * 0.96f, h * 0.55f)
            lineTo(w * 0.93f, h * 0.77f)
            lineTo(w * 0.82f, h * 0.76f)
            close()
        }
        drawPath(
            potPath,
            Brush.horizontalGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1)),
                startX = w * 0.80f,
                endX = w * 0.96f
            )
        )
        drawPath(potPath, Color(0x1A000000), style = Stroke(width = 1f))

        // Dark soil oval inside pot
        drawOval(
            color = Color(0xFF261C14),
            topLeft = Offset(w * 0.805f, h * 0.53f),
            size = Size(w * 0.15f, h * 0.035f)
        )

        // Lush green leaves growing from the pot
        // Back darker green leaf
        drawSmoothLeaf(
            drawScope = this,
            start = Offset(w * 0.88f, h * 0.54f),
            control1 = Offset(w * 0.98f, h * 0.38f),
            tip = Offset(w * 0.96f, h * 0.24f),
            control2 = Offset(w * 0.90f, h * 0.36f),
            color = Color(0xFF15803D)
        )

        // Left arching leaf
        drawSmoothLeaf(
            drawScope = this,
            start = Offset(w * 0.86f, h * 0.54f),
            control1 = Offset(w * 0.77f, h * 0.44f),
            tip = Offset(w * 0.76f, h * 0.30f),
            control2 = Offset(w * 0.84f, h * 0.38f),
            color = Color(0xFF16A34A)
        )

        // Center tall main leaf
        drawSmoothLeaf(
            drawScope = this,
            start = Offset(w * 0.88f, h * 0.54f),
            control1 = Offset(w * 0.84f, h * 0.32f),
            tip = Offset(w * 0.89f, h * 0.16f),
            control2 = Offset(w * 0.94f, h * 0.32f),
            color = Color(0xFF22C55E)
        )

        // Right spreading leaf
        drawSmoothLeaf(
            drawScope = this,
            start = Offset(w * 0.89f, h * 0.54f),
            control1 = Offset(w * 0.97f, h * 0.48f),
            tip = Offset(w * 0.99f, h * 0.36f),
            control2 = Offset(w * 0.93f, h * 0.44f),
            color = Color(0xFF15803D)
        )

        // Front lighter accent leaf
        drawSmoothLeaf(
            drawScope = this,
            start = Offset(w * 0.87f, h * 0.55f),
            control1 = Offset(w * 0.82f, h * 0.48f),
            tip = Offset(w * 0.84f, h * 0.38f),
            control2 = Offset(w * 0.89f, h * 0.44f),
            color = Color(0xFF4ADE80)
        )

        // 7. Floating Lime-Green Sparks / Accents above the plant
        // Upper spark (tilted pill)
        rotate(degrees = 25f, pivot = Offset(w * 0.74f, h * 0.16f)) {
            drawRoundRect(
                color = Color(0xFF84CC16),
                topLeft = Offset(w * 0.72f, h * 0.14f),
                size = Size(w * 0.038f, h * 0.085f),
                cornerRadius = CornerRadius(12f, 12f)
            )
        }

        // Lower spark (tilted smaller pill)
        rotate(degrees = -30f, pivot = Offset(w * 0.79f, h * 0.22f)) {
            drawRoundRect(
                color = Color(0xFFA3E635),
                topLeft = Offset(w * 0.78f, h * 0.20f),
                size = Size(w * 0.035f, h * 0.065f),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
    }
}

private fun drawSmoothLeaf(
    drawScope: DrawScope,
    start: Offset,
    control1: Offset,
    tip: Offset,
    control2: Offset,
    color: Color
) {
    val leafPath = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(control1.x, control1.y, tip.x, tip.y)
        quadraticTo(control2.x, control2.y, start.x, start.y)
        close()
    }
    drawScope.drawPath(leafPath, color)

    // Subtle leaf center vein
    drawScope.drawLine(
        color = Color.White.copy(alpha = 0.25f),
        start = start,
        end = tip,
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )
}

/**
 * Card 1 Artwork: Photos and Images (Tilted polaroid pictures with sunny mountain landscape).
 */
@Composable
fun PhotosCardIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Back photo (tilted left)
        rotate(-14f, pivot = Offset(w * 0.45f, h * 0.55f)) {
            // Shadow
            drawRoundRect(
                color = Color(0x1A0D47A1),
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.68f, h * 0.72f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            // White photo frame
            drawRoundRect(
                color = Color(0xFFF1F6FD),
                topLeft = Offset(w * 0.10f, h * 0.10f),
                size = Size(w * 0.68f, h * 0.72f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            // Back photo inner gradient (Orange / Sunset)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFFFF9E80), Color(0xFFFF5252))),
                topLeft = Offset(w * 0.16f, h * 0.16f),
                size = Size(w * 0.56f, h * 0.48f),
                cornerRadius = CornerRadius(8f, 8f)
            )
        }

        // Front photo (tilted slightly right)
        rotate(8f, pivot = Offset(w * 0.55f, h * 0.52f)) {
            // Shadow
            drawRoundRect(
                color = Color(0x2E1E3A5F),
                topLeft = Offset(w * 0.28f, h * 0.16f),
                size = Size(w * 0.68f, h * 0.74f),
                cornerRadius = CornerRadius(16f, 16f)
            )
            // White polaroid frame
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.25f, h * 0.14f),
                size = Size(w * 0.68f, h * 0.74f),
                cornerRadius = CornerRadius(16f, 16f)
            )

            // Inner picture frame (Blue sky)
            val picLeft = w * 0.31f
            val picTop = h * 0.20f
            val picWidth = w * 0.56f
            val picHeight = h * 0.50f

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF42A5F5), Color(0xFF90CAF9))),
                topLeft = Offset(picLeft, picTop),
                size = Size(picWidth, picHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // Sun
            drawCircle(
                color = Color(0xFFFFD54F),
                radius = picWidth * 0.14f,
                center = Offset(picLeft + picWidth * 0.25f, picTop + picHeight * 0.30f)
            )

            // Mountains inside photo
            val mountainPath = Path().apply {
                moveTo(picLeft, picTop + picHeight)
                lineTo(picLeft + picWidth * 0.45f, picTop + picHeight * 0.45f)
                lineTo(picLeft + picWidth * 0.75f, picTop + picHeight * 0.75f)
                lineTo(picLeft + picWidth, picTop + picHeight * 0.50f)
                lineTo(picLeft + picWidth, picTop + picHeight)
                close()
            }
            drawPath(
                mountainPath,
                Brush.verticalGradient(
                    listOf(Color(0xFF00C853), Color(0xFF00796B)),
                    startY = picTop + picHeight * 0.45f,
                    endY = picTop + picHeight
                )
            )

            // Front smaller mountain peak
            val frontPeak = Path().apply {
                moveTo(picLeft + picWidth * 0.35f, picTop + picHeight)
                lineTo(picLeft + picWidth * 0.68f, picTop + picHeight * 0.58f)
                lineTo(picLeft + picWidth, picTop + picHeight * 0.85f)
                lineTo(picLeft + picWidth, picTop + picHeight)
                close()
            }
            drawPath(frontPeak, Color(0xFF1B5E20))
        }
    }
}

/**
 * Card 2 Artwork: Documents (Overlapping PDF, Word, and Excel files).
 */
@Composable
fun DocumentsCardIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Excel sheet badge (Green, top right)
        rotate(12f, pivot = Offset(w * 0.60f, h * 0.35f)) {
            drawRoundRect(
                color = Color(0xFF2E7D32),
                topLeft = Offset(w * 0.38f, h * 0.10f),
                size = Size(w * 0.45f, h * 0.52f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // Grid lines
            for (i in 1..3) {
                drawLine(
                    color = Color.White.copy(alpha = 0.6f),
                    start = Offset(w * 0.44f, h * (0.16f + i * 0.08f)),
                    end = Offset(w * 0.76f, h * (0.16f + i * 0.08f)),
                    strokeWidth = 2.5f
                )
            }
        }

        // Word document badge (Blue, bottom right)
        rotate(16f, pivot = Offset(w * 0.70f, h * 0.65f)) {
            drawRoundRect(
                color = Color(0xFF1976D2),
                topLeft = Offset(w * 0.45f, h * 0.40f),
                size = Size(w * 0.46f, h * 0.50f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // "W" symbol
            val wPath = Path().apply {
                moveTo(w * 0.54f, h * 0.52f)
                lineTo(w * 0.58f, h * 0.74f)
                lineTo(w * 0.63f, h * 0.58f)
                lineTo(w * 0.68f, h * 0.74f)
                lineTo(w * 0.72f, h * 0.52f)
            }
            drawPath(
                wPath,
                Color.White,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Front PDF Document (White file with red PDF badge)
        rotate(-6f, pivot = Offset(w * 0.38f, h * 0.55f)) {
            // Shadow
            drawRoundRect(
                color = Color(0x26000000),
                topLeft = Offset(w * 0.10f, h * 0.22f),
                size = Size(w * 0.52f, h * 0.68f),
                cornerRadius = CornerRadius(14f, 14f)
            )
            // White document sheet
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.08f, h * 0.20f),
                size = Size(w * 0.52f, h * 0.68f),
                cornerRadius = CornerRadius(14f, 14f)
            )

            // Text placeholder lines on PDF
            drawLine(
                color = Color(0xFFB0BEC5),
                start = Offset(w * 0.16f, h * 0.30f),
                end = Offset(w * 0.48f, h * 0.30f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFCFD8DC),
                start = Offset(w * 0.16f, h * 0.38f),
                end = Offset(w * 0.44f, h * 0.38f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFCFD8DC),
                start = Offset(w * 0.16f, h * 0.46f),
                end = Offset(w * 0.50f, h * 0.46f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Red PDF badge
            drawRoundRect(
                color = Color(0xFFE53935),
                topLeft = Offset(w * 0.12f, h * 0.58f),
                size = Size(w * 0.44f, h * 0.22f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // Stylized "PDF" mark on the badge
            drawLine(
                color = Color.White,
                start = Offset(w * 0.18f, h * 0.64f),
                end = Offset(w * 0.18f, h * 0.74f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.24f, h * 0.64f),
                end = Offset(w * 0.24f, h * 0.74f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.30f, h * 0.64f),
                end = Offset(w * 0.30f, h * 0.74f),
                strokeWidth = 3f
            )
        }
    }
}

/**
 * Card 3 Artwork: Web pages (Vibrant globe wireframe with cursor pointer).
 */
@Composable
fun WebPagesCardIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val cx = w * 0.46f
        val cy = h * 0.48f
        val r = w * 0.38f

        // Globe outer drop shadow
        drawCircle(
            color = Color(0x221A237E),
            radius = r,
            center = Offset(cx + 4f, cy + 6f)
        )

        // Globe base gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF448AFF), Color(0xFF2979FF), Color(0xFF1565C0)),
                center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                radius = r * 1.3f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // Globe Border
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 3.5f)
        )

        // Equator horizontal line
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(cx - r, cy),
            end = Offset(cx + r, cy),
            strokeWidth = 3f
        )

        // Upper latitude
        val upperLatH = r * 0.52f
        val upperLatHalfW = kotlin.math.sqrt(r * r - upperLatH * upperLatH)
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(cx - upperLatHalfW, cy - upperLatH),
            end = Offset(cx + upperLatHalfW, cy - upperLatH),
            strokeWidth = 2.5f
        )

        // Lower latitude
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(cx - upperLatHalfW, cy + upperLatH),
            end = Offset(cx + upperLatHalfW, cy + upperLatH),
            strokeWidth = 2.5f
        )

        // Prime meridian (vertical line)
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(cx, cy - r),
            end = Offset(cx, cy + r),
            strokeWidth = 3f
        )

        // Curved longitudinal meridians
        drawOval(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset(cx - r * 0.55f, cy - r),
            size = Size(r * 1.1f, r * 2f),
            style = Stroke(width = 2.5f)
        )

        // Stylized Mouse Cursor Arrow clicking onto bottom right of globe
        val arrowTip = Offset(cx + r * 0.35f, cy + r * 0.30f)
        val cursorPath = Path().apply {
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowTip.x + 36f, arrowTip.y + 24f)
            lineTo(arrowTip.x + 18f, arrowTip.y + 24f)
            lineTo(arrowTip.x + 28f, arrowTip.y + 46f)
            lineTo(arrowTip.x + 18f, arrowTip.y + 50f)
            lineTo(arrowTip.x + 8f, arrowTip.y + 28f)
            lineTo(arrowTip.x - 2f, arrowTip.y + 36f)
            close()
        }

        // Shadow under cursor
        drawPath(
            cursorPath,
            Color(0x40000000)
        )

        // Cursor main body (Navy & White border)
        drawPath(
            cursorPath,
            Color(0xFF0D47A1)
        )
        drawPath(
            cursorPath,
            Color.White,
            style = Stroke(width = 3f, join = StrokeJoin.Round)
        )
    }
}
