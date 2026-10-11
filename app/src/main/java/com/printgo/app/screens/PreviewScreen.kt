package com.printgo.app.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.components.PrimaryButton
import com.printgo.app.data.converter.ConversionResult
import com.printgo.app.data.converter.DocumentConverter
import com.printgo.app.data.converter.PrintableDocument
import com.printgo.app.data.layout.PagePlacement
import com.printgo.app.data.layout.Sheet
import com.printgo.app.data.layout.SheetLayoutEngine
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageOrder
import com.printgo.app.model.PageSelection
import com.printgo.app.model.PagesPerSheet
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrintAlignment
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintMargins
import com.printgo.app.model.PrintOrientation
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.PrintScale
import com.printgo.app.model.PrintSettings
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.LightMint
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.PrintSettingsViewModel
import com.printgo.app.viewmodel.PrinterViewModel
import com.printgo.app.viewmodel.SelectionViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    selectionViewModel: SelectionViewModel,
    printerViewModel: PrinterViewModel,
    printSettingsViewModel: PrintSettingsViewModel,
    onBackClick: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val selectedItems by selectionViewModel.selectedItems.collectAsStateWithLifecycle()
    val selectedPrinter by printerViewModel.selectedPrinter.collectAsStateWithLifecycle()
    val printerCapabilities by printerViewModel.selectedPrinterCapabilities.collectAsStateWithLifecycle()
    val settings by printSettingsViewModel.settings.collectAsStateWithLifecycle()

    val converter = remember { DocumentConverter(context) }
    val conversionResults = remember { mutableStateMapOf<String, ConversionResult>() }

    var isConvertingAny by remember { mutableStateOf(false) }
    var currentConvertingTitle by remember { mutableStateOf("") }
    var conversionErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog & Fullscreen Viewer states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var fullScreenViewerSheetIndex by remember { mutableStateOf<Int?>(null) }

    // Back handler
    BackHandler(onBack = onBackClick)

    // Trigger document conversion for any item not yet converted
    LaunchedEffect(selectedItems) {
        if (selectedItems.isEmpty()) {
            conversionResults.clear()
            return@LaunchedEffect
        }

        conversionErrorMessage = null
        selectedItems.forEach { item ->
            val key = item.uri.toString()
            if (!conversionResults.containsKey(key)) {
                isConvertingAny = true
                currentConvertingTitle = item.displayName
                conversionResults[key] = ConversionResult.Loading(item)

                val result = converter.convert(item)
                conversionResults[key] = result

                if (result is ConversionResult.Error) {
                    conversionErrorMessage = result.message
                }
            }
        }
        isConvertingAny = false
    }

    // Keep settings aligned with selected printer capabilities
    LaunchedEffect(selectedPrinter, printerCapabilities) {
        val caps = (printerCapabilities as? com.printgo.app.viewmodel.CapabilitiesState.Ready)?.capabilities
        printSettingsViewModel.onPrinterSelected(selectedPrinter, caps)
    }

    // Extract ready printable documents in original selection order
    val readyDocuments = remember(selectedItems, conversionResults.values.toList()) {
        selectedItems.mapNotNull { item ->
            val res = conversionResults[item.uri.toString()]
            if (res is ConversionResult.Success) res.document else null
        }
    }

    val totalSourcePages = remember(readyDocuments) {
        readyDocuments.sumOf { it.pageCount }
    }

    // Calculate layout sheets live based on active PrintSettings
    val sheets = remember(readyDocuments, settings) {
        if (readyDocuments.isEmpty()) {
            emptyList()
        } else {
            SheetLayoutEngine.computeSheets(
                documents = readyDocuments,
                settings = settings,
                includeExcludedForPreview = true
            )
        }
    }

    // Number of active (printable) sheets
    val printableSheetCount = remember(sheets, settings) {
        val nonExcludedSheets = SheetLayoutEngine.computeSheets(
            documents = readyDocuments,
            settings = settings,
            includeExcludedForPreview = false
        )
        nonExcludedSheets.size * settings.copies
    }

    val canPrint = selectedPrinter != null && printableSheetCount > 0 && !isConvertingAny

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("preview_screen"),
        topBar = {
            TopAppBar(
                title = {
                    // Left Printer Chip
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onNavigateToPrinters() }
                            .testTag("preview_printer_chip"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (selectedPrinter?.connectionType == ConnectionType.PDF) {
                                    Icons.Default.PictureAsPdf
                                } else {
                                    Icons.Default.Print
                                },
                                contentDescription = null,
                                tint = if (selectedPrinter != null) AccentGreen else Color(0xFFFFCDD2),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedPrinter?.name ?: "Not selected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // PRINT action button with count badge
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = canPrint) {
                                // Print action (printing pipeline hooked in upcoming turn)
                            }
                            .testTag("preview_print_button"),
                        shape = RoundedCornerShape(18.dp),
                        color = if (canPrint) AccentGreen else Color.White.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (printableSheetCount > 0) "PRINT ($printableSheetCount)" else "PRINT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canPrint) HeadingNavy else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Settings Gear action icon
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("preview_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Print Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Level State: Empty Selection
            if (selectedItems.isEmpty()) {
                EmptySelectionCard(onGoHome = onBackClick)
                return@Scaffold
            }

            // Top Level State: Conversion In-Progress Banner
            if (isConvertingAny) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LightBlue
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Preparing '$currentConvertingTitle' for printing...",
                                fontSize = 12.sp,
                                color = HeadingNavy,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = PrimaryBlue
                        )
                    }
                }
            }

            // Top Level State: Conversion Error Alert Card
            if (conversionErrorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color(0xFFFFCDD2))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = conversionErrorMessage ?: "Error converting item",
                            fontSize = 12.sp,
                            color = Color(0xFFC62828),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                conversionResults.clear()
                                isConvertingAny = true
                            }
                        ) {
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ActionBlue)
                        }
                    }
                }
            }

            // Settings Summary 3-line Header Card
            SettingsSummaryHeaderCard(
                settings = settings,
                onClick = { showSettingsDialog = true }
            )

            // Multi-Document Headers + Pages List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (sheets.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PrimaryBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("preview_sheets_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // If multiple documents are selected, show document header rows
                        if (readyDocuments.size > 1) {
                            item {
                                DocumentManagementHeader(
                                    documents = readyDocuments,
                                    onRemoveDocument = { doc ->
                                        val itemToRemove = selectedItems.find { it.displayName == doc.title }
                                        if (itemToRemove != null) {
                                            selectionViewModel.deselectItem(itemToRemove)
                                        }
                                    }
                                )
                            }
                        }

                        // Sheets
                        itemsIndexed(sheets, key = { index, sheet -> "${sheet.sheetIndex}_${sheet.placements.size}" }) { index, sheet ->
                            SheetPreviewCard(
                                sheet = sheet,
                                totalSheets = sheets.size,
                                documents = readyDocuments,
                                settings = settings,
                                onSheetClick = {
                                    fullScreenViewerSheetIndex = index
                                },
                                onRotatePage = { pageIndex, delta ->
                                    printSettingsViewModel.rotatePage(pageIndex, delta)
                                },
                                onRotateAll = { delta ->
                                    printSettingsViewModel.rotateAllPages(delta, totalSourcePages)
                                },
                                onToggleExclude = { pageIndex ->
                                    printSettingsViewModel.togglePageExclusion(pageIndex)
                                }
                            )
                        }
                    }
                }
            }

            // Small Info Line Under Pages
            BottomPrintInfoLine(
                totalSheets = sheets.size,
                printableSheets = printableSheetCount,
                totalPages = totalSourcePages,
                copies = settings.copies,
                paperSize = settings.paperSize
            )
        }
    }

    // Settings Quick Adjustment Bottom Sheet Dialog
    if (showSettingsDialog) {
        PrintSettingsBottomSheet(
            settings = settings,
            capabilities = (printerCapabilities as? com.printgo.app.viewmodel.CapabilitiesState.Ready)?.capabilities,
            onDismiss = { showSettingsDialog = false },
            onUpdateSettings = { printSettingsViewModel.updateSettings(it) },
            onResetDefaults = { printSettingsViewModel.resetToDefaults() }
        )
    }

    // Full-Screen Interactive Sheet Viewer
    if (fullScreenViewerSheetIndex != null && sheets.isNotEmpty()) {
        val currentIndex = fullScreenViewerSheetIndex!!.coerceIn(0, sheets.size - 1)
        FullScreenSheetViewer(
            initialSheetIndex = currentIndex,
            sheets = sheets,
            documents = readyDocuments,
            settings = settings,
            onClose = { fullScreenViewerSheetIndex = null },
            onRotatePage = { pageIndex, delta ->
                printSettingsViewModel.rotatePage(pageIndex, delta)
            }
        )
    }
}

/**
 * Empty selection placeholder
 */
@Composable
private fun EmptySelectionCard(onGoHome: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = LightBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Files Selected",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeadingNavy
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose documents, photos or web pages to preview layout and print.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = "Select Files",
                    onClick = onGoHome,
                    backgroundColor = ActionBlue
                )
            }
        }
    }
}

/**
 * Compact 3-line settings summary card under top bar.
 */
@Composable
private fun SettingsSummaryHeaderCard(
    settings: PrintSettings,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("preview_settings_summary_card"),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.12f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Paper Size | Quality | Duplex
                Text(
                    text = "${settings.paperSize.name} • ${settings.quality.label} • ${settings.duplex.label}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeadingNavy
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Line 2: Scale | Margins | Alignment
                Text(
                    text = "${settings.scale.label} • ${settings.margins.label} • ${settings.alignment.label}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Line 3: Copies | Page Selection • Color Mode • Pages Per Sheet
                val pageSelectionText = when (val sel = settings.pageSelection) {
                    is PageSelection.All -> "All pages"
                    is PageSelection.Odd -> "Odd pages"
                    is PageSelection.Even -> "Even pages"
                    is PageSelection.Range -> "Pages: ${sel.text}"
                    is PageSelection.Custom -> "${sel.pages.size} selected page(s)"
                }
                Text(
                    text = "Copies: ${settings.copies} • $pageSelectionText • ${settings.colorMode.label} • ${settings.pagesPerSheet.count}-up",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryBlue
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = LightBlue
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Multi-document chips with remove (x) button.
 */
@Composable
private fun DocumentManagementHeader(
    documents: List<PrintableDocument>,
    onRemoveDocument: (PrintableDocument) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Text(
            text = "Active Documents (${documents.size}):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = HeadingNavy
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            documents.forEach { doc ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = LightBlue,
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = doc.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = HeadingNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove document",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onRemoveDocument(doc) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Sheet preview card with soft shadow, skeleton shimmer and page counter pill.
 */
@Composable
private fun SheetPreviewCard(
    sheet: Sheet,
    totalSheets: Int,
    documents: List<PrintableDocument>,
    settings: PrintSettings,
    onSheetClick: () -> Unit,
    onRotatePage: (globalPageIndex: Int, degreesDelta: Int) -> Unit,
    onRotateAll: (degreesDelta: Int) -> Unit,
    onToggleExclude: (globalPageIndex: Int) -> Unit
) {
    var sheetBitmap by remember(sheet, settings) { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember(sheet, settings) { mutableStateOf(true) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(sheet, settings) {
        isRendering = true
        sheetBitmap = SheetLayoutEngine.renderSheetBitmap(
            sheet = sheet,
            documents = documents,
            settings = settings,
            targetWidthPx = 720
        )
        isRendering = false
    }

    val primaryPlacement = sheet.placements.firstOrNull()
    val isPrimaryExcluded = primaryPlacement?.isExcluded == true

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Paper Container
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(4.dp),
                    ambientColor = Color(0x33000000),
                    spotColor = Color(0x22000000)
                )
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .aspectRatio(1f / sheet.aspectRatio)
                .pointerInput(sheet) {
                    detectTapGestures(
                        onTap = { onSheetClick() },
                        onLongPress = { showOptionsMenu = true }
                    )
                }
                .testTag("preview_sheet_${sheet.sheetIndex}")
        ) {
            if (sheetBitmap != null) {
                Image(
                    bitmap = sheetBitmap!!.asImageBitmap(),
                    contentDescription = "Sheet ${sheet.sheetIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            } else {
                // Shimmer skeleton loading
                ShimmerSkeletonBox(aspectRatio = 1f / sheet.aspectRatio)
            }

            // Excluded sheet banner if all placements on this sheet are excluded
            if (isPrimaryExcluded && settings.pagesPerSheet == PagesPerSheet.ONE) {
                Surface(
                    color = Color(0xAA000000),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xDDE53935)
                        ) {
                            Text(
                                text = "EXCLUDED FROM PRINT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Top-right quick action ⋮ menu
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { showOptionsMenu = true },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Page actions",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false }
                ) {
                    if (primaryPlacement != null) {
                        DropdownMenuItem(
                            text = { Text("Rotate Right 90°", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showOptionsMenu = false
                                onRotatePage(primaryPlacement.globalPageIndex, 90)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rotate Left 90°", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.RotateLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showOptionsMenu = false
                                onRotatePage(primaryPlacement.globalPageIndex, -90)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (primaryPlacement.isExcluded) "Include this page" else "Exclude this page",
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (primaryPlacement.isExcluded) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showOptionsMenu = false
                                onToggleExclude(primaryPlacement.globalPageIndex)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rotate All Pages (+90°)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showOptionsMenu = false
                            onRotateAll(90)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Page counter pill: "1 / 3"
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 1.dp
        ) {
            Text(
                text = "${sheet.sheetIndex + 1} / $totalSheets",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HeadingNavy,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Shimmer skeleton placeholder while rendering page bitmap.
 */
@Composable
private fun ShimmerSkeletonBox(aspectRatio: Float) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9).copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = PrimaryBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Rendering sheet...", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

/**
 * Bottom Info Line under pages:
 * total sheets, total pages, copies, and paper size.
 */
@Composable
private fun BottomPrintInfoLine(
    totalSheets: Int,
    printableSheets: Int,
    totalPages: Int,
    copies: Int,
    paperSize: PaperSize
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$printableSheets sheet${if (printableSheets > 1) "s" else ""} to print • $totalPages page${if (totalPages > 1) "s" else ""}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = HeadingNavy
            )
            Text(
                text = "$copies cop${if (copies > 1) "ies" else "y"} • ${paperSize.name} (${paperSize.widthMm}×${paperSize.heightMm} mm)",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * Print Settings Bottom Sheet with live controls for all options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintSettingsBottomSheet(
    settings: PrintSettings,
    capabilities: com.printgo.app.model.PrinterCapabilities?,
    onDismiss: () -> Unit,
    onUpdateSettings: ((PrintSettings) -> PrintSettings) -> Unit,
    onResetDefaults: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Print Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeadingNavy
                )
                TextButton(onClick = {
                    onResetDefaults()
                    onDismiss()
                }) {
                    Text("Reset Defaults", fontSize = 12.sp, color = ActionBlue)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Copies Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Copies", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                    Text("1 to ${capabilities?.copiesMax ?: 99}", fontSize = 11.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (settings.copies > 1) {
                                onUpdateSettings { it.copy(copies = it.copies - 1) }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease copies")
                    }
                    Text(
                        text = "${settings.copies}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeadingNavy,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            val max = capabilities?.copiesMax ?: 99
                            if (settings.copies < max) {
                                onUpdateSettings { it.copy(copies = it.copies + 1) }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase copies")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Paper Size
            Text("Paper Size", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizes = listOf(
                    PaperSize("A4", 210, 297),
                    PaperSize("Letter", 216, 279),
                    PaperSize("Legal", 216, 356),
                    PaperSize("4x6 Photo", 102, 152)
                )
                sizes.forEach { size ->
                    val selected = settings.paperSize.name.equals(size.name, ignoreCase = true)
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(paperSize = size) } },
                        label = { Text(size.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Scale Mode
            Text("Scale", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PrintScale.FitToPage to "Fit",
                    PrintScale.FillPage to "Fill",
                    PrintScale.ActualSize to "Actual"
                ).forEach { (scale, label) ->
                    val selected = settings.scale::class == scale::class
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(scale = scale) } },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Pages Per Sheet (N-Up)
            Text("Pages Per Sheet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PagesPerSheet.entries.forEach { pps ->
                    val selected = settings.pagesPerSheet == pps
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(pagesPerSheet = pps) } },
                        label = { Text("${pps.count}-up", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Duplex Mode
            Text("2-Sided Printing", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    DuplexMode.ONE_SIDED to "1-Sided",
                    DuplexMode.TWO_SIDED_LONG_EDGE to "Long Edge",
                    DuplexMode.TWO_SIDED_SHORT_EDGE to "Short Edge"
                ).forEach { (mode, label) ->
                    val selected = settings.duplex == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(duplex = mode) } },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Color Mode
            Text("Color Mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrintColorMode.entries.forEach { mode ->
                    val selected = settings.colorMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(colorMode = mode) } },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. Margins
            Text("Margins", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PrintMargins.None to "None",
                    PrintMargins.Narrow to "Narrow",
                    PrintMargins.Normal to "Normal",
                    PrintMargins.Wide to "Wide"
                ).forEach { (m, label) ->
                    val selected = settings.margins::class == m::class
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateSettings { it.copy(margins = m) } },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = "Apply & Close",
                onClick = onDismiss,
                backgroundColor = ActionBlue
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Full-screen zoomable, pannable, and swipeable sheet viewer dialog.
 */
@Composable
private fun FullScreenSheetViewer(
    initialSheetIndex: Int,
    sheets: List<Sheet>,
    documents: List<PrintableDocument>,
    settings: PrintSettings,
    onClose: () -> Unit,
    onRotatePage: (globalPageIndex: Int, degreesDelta: Int) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialSheetIndex.coerceIn(0, sheets.size - 1)) }
    val currentSheet = sheets[currentIndex]

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var highResBitmap by remember(currentSheet, settings) { mutableStateOf<Bitmap?>(null) }
    var isLoadingHighRes by remember(currentSheet, settings) { mutableStateOf(true) }

    // Lazy load high-res bitmap
    LaunchedEffect(currentSheet, settings) {
        isLoadingHighRes = true
        highResBitmap = SheetLayoutEngine.renderSheetBitmap(
            sheet = currentSheet,
            documents = documents,
            settings = settings,
            targetWidthPx = 1200
        )
        isLoadingHighRes = false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xF0000000)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    // Indicator pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Sheet ${currentIndex + 1} of ${sheets.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val primary = currentSheet.placements.firstOrNull()
                                if (primary != null) {
                                    onRotatePage(primary.globalPageIndex, 90)
                                }
                            }
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color.White)
                        }
                    }
                }

                // Interactive zoomable & pannable container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(currentSheet) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(currentSheet) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (highResBitmap != null) {
                        Image(
                            bitmap = highResBitmap!!.asImageBitmap(),
                            contentDescription = "Sheet ${currentIndex + 1}",
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .aspectRatio(1f / currentSheet.aspectRatio)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .shadow(8.dp, RoundedCornerShape(2.dp))
                                .background(Color.White),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                    }
                }

                // Bottom Navigation controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Sheet",
                            tint = if (currentIndex > 0) Color.White else Color.Gray
                        )
                    }

                    Text(
                        text = "Double-tap or pinch to zoom",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )

                    IconButton(
                        onClick = {
                            if (currentIndex < sheets.size - 1) {
                                currentIndex++
                                scale = 1f
                                offset = Offset.Zero
                            }
                        },
                        enabled = currentIndex < sheets.size - 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Sheet",
                            tint = if (currentIndex < sheets.size - 1) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
    }
}
