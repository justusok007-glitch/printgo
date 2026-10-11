package com.printgo.app.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.model.PrinterConnectionStatus
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.CardBorderLight
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrinterStatusCard(
    status: PrinterConnectionStatus,
    onOpenPrinters: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityLabel = when (status) {
        is PrinterConnectionStatus.None -> "No printer selected. Tap to select printer."
        is PrinterConnectionStatus.PdfOnly -> "Save as PDF selected. Tap to change printer."
        is PrinterConnectionStatus.Checking -> "Checking printer ${status.printerName} status."
        is PrinterConnectionStatus.Connected -> "Printer ${status.printerName} is connected and ready."
        is PrinterConnectionStatus.Busy -> "Printer ${status.printerName} is busy."
        is PrinterConnectionStatus.Unreachable -> "Printer ${status.printerName} is offline."
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        val isNarrow = maxWidth < 340.dp

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color(0x140D47A1),
                    spotColor = Color(0x1A0D47A1)
                )
                .clip(RoundedCornerShape(24.dp))
                .clickable { onOpenPrinters() }
                .semantics { contentDescription = accessibilityLabel }
                .testTag("home_printer_status_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                },
                label = "printer_status_transition"
            ) { currentStatus ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Left: 48dp Icon Circle + 14dp Status Dot
                    PrinterStatusIconContainer(status = currentStatus)

                    Spacer(modifier = Modifier.width(14.dp))

                    // 2. Middle: Text Column (Line 1: Name, Line 2: Status, Line 3: Address, Warnings)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Line 1: Printer Name (Bold Navy, 1 line, ellipsized)
                        Text(
                            text = currentStatus.printerName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Line 2: Status Text in Body Gray (1 line)
                        val statusLine2 = when (currentStatus) {
                            is PrinterConnectionStatus.None -> "Tap to find printers on your WiFi"
                            is PrinterConnectionStatus.PdfOnly -> "Save documents without a printer"
                            is PrinterConnectionStatus.Checking -> "Checking…"
                            is PrinterConnectionStatus.Connected -> if (currentStatus.isReady) "Connected • Ready" else "Connected"
                            is PrinterConnectionStatus.Busy -> "Connected • Busy"
                            is PrinterConnectionStatus.Unreachable -> "Offline – check that the printer is on and on the same WiFi"
                        }

                        Text(
                            text = statusLine2,
                            fontSize = 12.sp,
                            color = BodyGray,
                            lineHeight = 16.sp,
                            maxLines = if (currentStatus is PrinterConnectionStatus.Unreachable) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Line 3: Small caption address (e.g. "WiFi • 192.168.1.25"), hidden when no address
                        val addressCaption = currentStatus.address
                        if (!addressCaption.isNullOrBlank() && currentStatus !is PrinterConnectionStatus.Unreachable) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = addressCaption,
                                fontSize = 11.sp,
                                color = BodyGray.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Warning chips below when applicable (e.g. "Low ink", "Out of paper")
                        if (currentStatus.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                currentStatus.warnings.forEach { warning ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFFF7ED),
                                        border = BorderStroke(1.dp, Color(0xFFFDBA74))
                                    ) {
                                        Text(
                                            text = warning,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFC2410C),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 3. Right: Action Buttons
                    when (currentStatus) {
                        is PrinterConnectionStatus.None -> {
                            PrimaryButton(
                                text = "Select printer",
                                onClick = onOpenPrinters,
                                backgroundColor = ActionBlue,
                                modifier = Modifier.height(38.dp),
                                testTag = "home_status_select_button"
                            )
                        }
                        is PrinterConnectionStatus.Unreachable -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = onRetry,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ActionBlue)
                                }
                                if (isNarrow) {
                                    IconButton(
                                        onClick = onOpenPrinters,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Change printer", tint = PrimaryBlue)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onOpenPrinters,
                                        modifier = Modifier.height(36.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("Change", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                    }
                                }
                            }
                        }
                        else -> {
                            if (isNarrow) {
                                IconButton(
                                    onClick = onOpenPrinters,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change printer", tint = PrimaryBlue)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onOpenPrinters,
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Text("Change", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 48dp circular icon container with a 14dp status dot on the bottom-right corner.
 */
@Composable
private fun PrinterStatusIconContainer(status: PrinterConnectionStatus) {
    val isPdf = status is PrinterConnectionStatus.PdfOnly

    Box(
        modifier = Modifier.size(52.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main 48dp circular container
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (isPdf) Color(0xFFFFEBEE) else LightBlue
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Print,
                    contentDescription = null,
                    tint = if (isPdf) Color(0xFFDC2626) else PrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Status dot (14dp with white border ring) - omitted for Save as PDF
        if (!isPdf) {
            val dotColor = when (status) {
                is PrinterConnectionStatus.Connected, is PrinterConnectionStatus.Busy -> AccentGreen
                is PrinterConnectionStatus.Unreachable -> Color(0xFFF59E0B) // Orange
                is PrinterConnectionStatus.Checking -> ActionBlue
                is PrinterConnectionStatus.None -> Color(0xFF9E9E9E) // Gray
                is PrinterConnectionStatus.PdfOnly -> Color.Transparent
            }

            val shouldPulse = status is PrinterConnectionStatus.Connected ||
                status is PrinterConnectionStatus.Busy ||
                status is PrinterConnectionStatus.Checking

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing gentle glow ring behind the dot
                if (shouldPulse) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.45f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 0.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .scale(pulseScale)
                            .background(dotColor.copy(alpha = pulseAlpha), CircleShape)
                    )
                }

                // 14dp Solid dot with crisp white border ring
                Surface(
                    modifier = Modifier.size(14.dp),
                    shape = CircleShape,
                    color = dotColor,
                    border = BorderStroke(2.dp, Color.White)
                ) {}
            }
        }
    }
}
