package com.printgo.app.screens

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.R
import com.printgo.app.components.DocumentsCardIllustration
import com.printgo.app.components.PhotosCardIllustration
import com.printgo.app.components.PrintGoCard
import com.printgo.app.components.PrinterStatusCard
import com.printgo.app.components.WaveHeader
import com.printgo.app.components.WebPagesCardIllustration
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterConnectionStatus
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.ActionPurple
import com.printgo.app.theme.BackgroundWhite
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.DeepBlue
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.PrinterViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    printerViewModel: PrinterViewModel,
    onNavigateToPhotos: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToWebPages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val connectionStatus by printerViewModel.connectionStatus.collectAsStateWithLifecycle()

    // Re-check reachability when home screen resumes / appears, and every 30s while visible
    DisposableEffect(printerViewModel) {
        printerViewModel.startHomeScreenMonitoring()
        onDispose {
            printerViewModel.stopHomeScreenMonitoring()
        }
    }

    HomeScreenContent(
        connectionStatus = connectionStatus,
        onNavigateToPhotos = onNavigateToPhotos,
        onNavigateToDocuments = onNavigateToDocuments,
        onNavigateToWebPages = onNavigateToWebPages,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPrinters = onNavigateToPrinters,
        onRetryPrinter = {
            coroutineScope.launch {
                printerViewModel.checkCurrentPrinterReachability()
            }
        },
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    connectionStatus: PrinterConnectionStatus,
    onNavigateToPhotos: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToWebPages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    onRetryPrinter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundWhite)
            .testTag("home_screen")
    ) {
        // 1. Header (wrap content, no overlap with logo row)
        WaveHeader(
            onSettingsClick = onNavigateToSettings,
            statusCard = {
                PrinterStatusCard(
                    status = connectionStatus,
                    onOpenPrinters = onNavigateToPrinters,
                    onRetry = onRetryPrinter
                )
            }
        )

        // 2. Content area filling remaining height
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val availableHeight = maxHeight
            // Content needs vertical scrolling if remaining height is small (e.g. short screen or high font scale)
            val isShortScreen = availableHeight < 450.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isShortScreen) Modifier.verticalScroll(rememberScrollState())
                        else Modifier
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // The three feature cards sharing the height using weight(1f), min 112dp and max 168dp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .then(if (!isShortScreen) Modifier.weight(1f) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card 1: Photos and Images
                    PrintGoCard(
                        title = stringResource(R.string.photos_title),
                        subtitle = stringResource(R.string.photos_subtitle),
                        backgroundColor = PrintGoTheme.colors.cardPhotoBg,
                        buttonColor = ActionBlue,
                        icon = { PhotosCardIllustration(modifier = Modifier.size(72.dp)) },
                        onClick = onNavigateToPhotos,
                        modifier = if (!isShortScreen) Modifier.weight(1f).heightIn(min = 112.dp, max = 168.dp) else Modifier.heightIn(min = 112.dp, max = 168.dp),
                        testTag = "card_photos"
                    )

                    // Card 2: Documents
                    PrintGoCard(
                        title = stringResource(R.string.documents_title),
                        subtitle = stringResource(R.string.documents_subtitle),
                        backgroundColor = PrintGoTheme.colors.cardDocsBg,
                        buttonColor = ActionGreen,
                        icon = { DocumentsCardIllustration(modifier = Modifier.size(72.dp)) },
                        onClick = onNavigateToDocuments,
                        modifier = if (!isShortScreen) Modifier.weight(1f).heightIn(min = 112.dp, max = 168.dp) else Modifier.heightIn(min = 112.dp, max = 168.dp),
                        testTag = "card_documents"
                    )

                    // Card 3: Web pages
                    PrintGoCard(
                        title = stringResource(R.string.webpages_title),
                        subtitle = stringResource(R.string.webpages_subtitle),
                        backgroundColor = PrintGoTheme.colors.cardWebBg,
                        buttonColor = ActionPurple,
                        icon = { WebPagesCardIllustration(modifier = Modifier.size(72.dp)) },
                        onClick = onNavigateToWebPages,
                        modifier = if (!isShortScreen) Modifier.weight(1f).heightIn(min = 112.dp, max = 168.dp) else Modifier.heightIn(min = 112.dp, max = 168.dp),
                        testTag = "card_webpages"
                    )
                }

                // Centered hint above bottom wave if space allows
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Tip: share any file from your phone to PrintGo to print it",
                    fontSize = 12.sp,
                    color = BodyGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Anchored bottom decoration at the bottom edge behind navigation bar inset
                BottomWaveDecoration(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

/**
 * Slim two-layer curved bottom wave decoration about 48dp high
 * (thin AccentGreen line over deep blue). Anchored to bottom.
 */
@Composable
fun BottomWaveDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Deep Blue fluid bottom wave
        val blueWave = Path().apply {
            moveTo(0f, h * 0.40f)
            cubicTo(
                w * 0.32f, h * 0.15f,
                w * 0.68f, h * 0.65f,
                w, h * 0.30f
            )
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            blueWave,
            Brush.verticalGradient(
                colors = listOf(DeepBlue, PrimaryBlue),
                startY = h * 0.20f,
                endY = h
            )
        )

        // 2. Thin Accent Green crest line over deep blue wave
        val greenCrest = Path().apply {
            moveTo(0f, h * 0.40f)
            cubicTo(
                w * 0.32f, h * 0.15f,
                w * 0.68f, h * 0.65f,
                w, h * 0.30f
            )
        }
        drawPath(
            path = greenCrest,
            color = AccentGreen,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

// ==========================================
// COMPOSE PREVIEWS FOR VARIOUS SCREEN SIZES
// ==========================================

@Preview(name = "Small Screen 360x640dp", widthDp = 360, heightDp = 640)
@Composable
fun HomeScreenPreviewSmall() {
    PrintGoTheme {
        HomeScreenContent(
            connectionStatus = PrinterConnectionStatus.Connected(
                printerName = "HP Color LaserJet Pro",
                address = "WiFi • 192.168.1.150",
                connectionType = ConnectionType.WIFI,
                isReady = true
            ),
            onNavigateToPhotos = {},
            onNavigateToDocuments = {},
            onNavigateToWebPages = {},
            onNavigateToSettings = {},
            onNavigateToPrinters = {},
            onRetryPrinter = {}
        )
    }
}

@Preview(name = "Standard Phone 411x891dp", widthDp = 411, heightDp = 891)
@Composable
fun HomeScreenPreviewStandard() {
    PrintGoTheme {
        HomeScreenContent(
            connectionStatus = PrinterConnectionStatus.Connected(
                printerName = "Canon TS8300 Series",
                address = "WiFi • 192.168.1.42",
                connectionType = ConnectionType.WIFI,
                isReady = true,
                warnings = listOf("Low ink")
            ),
            onNavigateToPhotos = {},
            onNavigateToDocuments = {},
            onNavigateToWebPages = {},
            onNavigateToSettings = {},
            onNavigateToPrinters = {},
            onRetryPrinter = {}
        )
    }
}

@Preview(name = "Tall Phone 411x1000dp FontScale 1.3", widthDp = 411, heightDp = 1000, fontScale = 1.3f)
@Composable
fun HomeScreenPreviewTallFontScale() {
    PrintGoTheme {
        HomeScreenContent(
            connectionStatus = PrinterConnectionStatus.Connected(
                printerName = "Epson EcoTank ET-2850",
                address = "WiFi • 192.168.1.88",
                connectionType = ConnectionType.WIFI,
                isReady = true
            ),
            onNavigateToPhotos = {},
            onNavigateToDocuments = {},
            onNavigateToWebPages = {},
            onNavigateToSettings = {},
            onNavigateToPrinters = {},
            onRetryPrinter = {}
        )
    }
}

@Preview(name = "System Dark Mode Stays Light", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HomeScreenPreviewDarkStaysLight() {
    PrintGoTheme(darkTheme = false) {
        HomeScreenContent(
            connectionStatus = PrinterConnectionStatus.Connected(
                printerName = "Brother MFC-L2750DW",
                address = "WiFi • 192.168.1.110",
                connectionType = ConnectionType.WIFI,
                isReady = true
            ),
            onNavigateToPhotos = {},
            onNavigateToDocuments = {},
            onNavigateToWebPages = {},
            onNavigateToSettings = {},
            onNavigateToPrinters = {},
            onRetryPrinter = {}
        )
    }
}
