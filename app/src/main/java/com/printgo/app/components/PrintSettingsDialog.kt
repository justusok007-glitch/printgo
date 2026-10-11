package com.printgo.app.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.printgo.app.data.local.PrintPresetEntity
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageNumberPosition
import com.printgo.app.model.PageOrder
import com.printgo.app.model.PageRangeParser
import com.printgo.app.model.PageRangeResult
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
import com.printgo.app.model.PrintSettingsValidator
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.model.WatermarkSettings
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.CardBorderLight
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import kotlinx.coroutines.launch

private enum class ActivePicker {
    COPIES,
    PAGES,
    MULTI_PAGE,
    DUPLEX,
    SIZE_LAYOUT,
    PAPER_PRINTING,
    EXTRAS,
    PRESETS
}

@Composable
fun PrintSettingsDialog(
    settings: PrintSettings,
    capabilities: PrinterCapabilities?,
    presets: List<PrintPresetEntity>,
    totalPages: Int,
    totalSheets: Int,
    clampedNotice: String? = null,
    onDismiss: () -> Unit,
    onUpdateSettings: ((PrintSettings) -> PrintSettings) -> Unit,
    onResetToDefaults: () -> Unit,
    onResetExcludedPages: () -> Unit,
    onApplyPreset: (PrintPresetEntity) -> Unit,
    onSavePreset: suspend (String) -> Result<Unit>,
    onRenamePreset: suspend (String, String) -> Result<Unit>,
    onDeletePreset: suspend (String) -> Result<Unit>
) {
    var activePicker by remember { mutableStateOf<ActivePicker?>(null) }
    var showConfirmResetDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp)
            ) {
                // Header Bar: Title and Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Print Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeadingNavy
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .semantics { contentDescription = "Close print settings" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = BodyGray
                        )
                    }
                }

                // Clamped notice banner if any unsupported setting was clamped
                if (!clampedNotice.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF7ED),
                        border = BorderStroke(1.dp, Color(0xFFFDBA74))
                    ) {
                        Text(
                            text = clampedNotice,
                            fontSize = 11.5.sp,
                            color = Color(0xFFC2410C),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CardBorderLight, thickness = 1.dp)

                // Scrollable Rows in strict order (1 to 8)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Copies
                    val copiesSummary = if (settings.copies > 1) {
                        "${settings.copies} ${if (settings.collate) "(Collated)" else "(Uncollated)"}"
                    } else {
                        "1"
                    }
                    SettingsRow(
                        title = "Copies",
                        summary = copiesSummary,
                        onClick = { activePicker = ActivePicker.COPIES },
                        testTag = "row_copies"
                    )

                    // 2. Pages
                    val pagesSummary = when (val sel = settings.pageSelection) {
                        is PageSelection.All -> if (settings.reverseOrder) "All (Reverse order)" else "All"
                        is PageSelection.Odd -> "Odd pages only"
                        is PageSelection.Even -> "Even pages only"
                        is PageSelection.Range -> sel.text
                        is PageSelection.Custom -> "${sel.pages.size} custom pages"
                    }
                    val excludedNote = if (settings.excludedPages.isNotEmpty()) {
                        " • ${settings.excludedPages.size} excluded"
                    } else ""
                    SettingsRow(
                        title = "Pages",
                        summary = pagesSummary + excludedNote,
                        onClick = { activePicker = ActivePicker.PAGES },
                        testTag = "row_pages"
                    )

                    // 3. Multi-Page Printing
                    val ppsSummary = if (settings.pagesPerSheet == PagesPerSheet.ONE) "Singly" else "${settings.pagesPerSheet.count} per sheet"
                    val borderSummary = if (settings.borderAroundPages) " • Border" else ""
                    SettingsRow(
                        title = "Multi-Page Printing",
                        summary = ppsSummary + borderSummary,
                        onClick = { activePicker = ActivePicker.MULTI_PAGE },
                        testTag = "row_multi_page"
                    )

                    // 4. Double-Sided Printing
                    val duplexSummary = when (settings.duplex) {
                        DuplexMode.ONE_SIDED -> "One side only"
                        DuplexMode.TWO_SIDED_LONG_EDGE -> "Long edge"
                        DuplexMode.TWO_SIDED_SHORT_EDGE -> "Short edge"
                    }
                    SettingsRow(
                        title = "Double-Sided Printing",
                        summary = duplexSummary,
                        onClick = { activePicker = ActivePicker.DUPLEX },
                        testTag = "row_duplex"
                    )

                    // 5. Size and Layout Options
                    val sizeLayoutSummary = "${settings.scale.label} | ${settings.margins.label} | ${settings.alignment.label}"
                    SettingsRow(
                        title = "Size and Layout Options",
                        summary = sizeLayoutSummary,
                        onClick = { activePicker = ActivePicker.SIZE_LAYOUT },
                        testTag = "row_size_layout"
                    )

                    // 6. Paper and Printing Options
                    val paperSummary = "${settings.paperSize.name} | ${settings.quality.label} • ${settings.colorMode.label}"
                    SettingsRow(
                        title = "Paper and Printing Options",
                        summary = paperSummary,
                        onClick = { activePicker = ActivePicker.PAPER_PRINTING },
                        testTag = "row_paper_printing"
                    )

                    // 7. Extras
                    val extrasList = mutableListOf<String>()
                    if (settings.pageNumbers != null) extrasList.add("Page numbers")
                    if (settings.dateStamp) extrasList.add("Date")
                    if (settings.fileNameHeader) extrasList.add("File name")
                    if (!settings.watermark?.text.isNullOrBlank()) extrasList.add("Watermark")
                    val extrasSummary = if (extrasList.isNotEmpty()) extrasList.joinToString(", ") else "None"
                    SettingsRow(
                        title = "Extras",
                        summary = extrasSummary,
                        onClick = { activePicker = ActivePicker.EXTRAS },
                        testTag = "row_extras"
                    )

                    // 8. Presets
                    val presetSummary = settings.activePresetName ?: "None"
                    SettingsRow(
                        title = "Presets",
                        summary = presetSummary,
                        onClick = { activePicker = ActivePicker.PRESETS },
                        testTag = "row_presets"
                    )
                }

                HorizontalDivider(color = CardBorderLight, thickness = 1.dp)

                // Footer Line: Estimated paper use
                Spacer(modifier = Modifier.height(10.dp))
                val totalCopies = settings.copies
                val paperText = if (totalCopies > 1) {
                    "$totalSheets sheets of ${settings.paperSize.name} (${totalSheets * totalCopies} sheets total for $totalCopies copies)"
                } else {
                    "$totalSheets sheets of ${settings.paperSize.name}"
                }
                Text(
                    text = paperText,
                    fontSize = 12.sp,
                    color = BodyGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons at bottom: "Reset to defaults" on left, "Done" on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showConfirmResetDialog = true },
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text(
                            text = "Reset to defaults",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue
                        )
                    }

                    PrimaryButton(
                        text = "Done",
                        onClick = onDismiss,
                        backgroundColor = ActionBlue,
                        modifier = Modifier.height(44.dp),
                        testTag = "btn_settings_done"
                    )
                }
            }
        }
    }

    // Confirmation for Reset to defaults
    if (showConfirmResetDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmResetDialog = false },
            title = { Text("Reset settings?", fontWeight = FontWeight.Bold, color = HeadingNavy) },
            text = { Text("All settings will be restored to their default values.", color = BodyGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
                        showConfirmResetDialog = false
                    }
                ) {
                    Text("Reset", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetDialog = false }) {
                    Text("Cancel", color = BodyGray)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    // Active Nested Pickers
    when (activePicker) {
        ActivePicker.COPIES -> CopiesPickerDialog(
            copies = settings.copies,
            collate = settings.collate,
            onDismiss = { activePicker = null },
            onConfirm = { newCopies, newCollate ->
                onUpdateSettings { it.copy(copies = newCopies, collate = newCollate) }
                activePicker = null
            }
        )
        ActivePicker.PAGES -> PagesPickerDialog(
            pageSelection = settings.pageSelection,
            reverseOrder = settings.reverseOrder,
            excludedPagesCount = settings.excludedPages.size,
            totalPages = totalPages,
            onResetExcluded = onResetExcludedPages,
            onDismiss = { activePicker = null },
            onConfirm = { newSelection, newReverse ->
                onUpdateSettings { it.copy(pageSelection = newSelection, reverseOrder = newReverse) }
                activePicker = null
            }
        )
        ActivePicker.MULTI_PAGE -> MultiPagePickerDialog(
            pagesPerSheet = settings.pagesPerSheet,
            pageOrder = settings.pageOrder,
            borderAroundPages = settings.borderAroundPages,
            onDismiss = { activePicker = null },
            onConfirm = { pps, order, border ->
                onUpdateSettings { it.copy(pagesPerSheet = pps, pageOrder = order, borderAroundPages = border) }
                activePicker = null
            }
        )
        ActivePicker.DUPLEX -> DuplexPickerDialog(
            currentDuplex = settings.duplex,
            capabilities = capabilities,
            onDismiss = { activePicker = null },
            onConfirm = { newDuplex ->
                onUpdateSettings { it.copy(duplex = newDuplex) }
                activePicker = null
            }
        )
        ActivePicker.SIZE_LAYOUT -> SizeLayoutPickerDialog(
            scale = settings.scale,
            margins = settings.margins,
            alignment = settings.alignment,
            orientation = settings.orientation,
            onDismiss = { activePicker = null },
            onConfirm = { s, m, a, o ->
                onUpdateSettings { it.copy(scale = s, margins = m, alignment = a, orientation = o) }
                activePicker = null
            }
        )
        ActivePicker.PAPER_PRINTING -> PaperPrintingPickerDialog(
            paperSize = settings.paperSize,
            quality = settings.quality,
            colorMode = settings.colorMode,
            capabilities = capabilities,
            onDismiss = { activePicker = null },
            onConfirm = { p, q, c ->
                onUpdateSettings { it.copy(paperSize = p, quality = q, colorMode = c) }
                activePicker = null
            }
        )
        ActivePicker.EXTRAS -> ExtrasPickerDialog(
            pageNumbers = settings.pageNumbers,
            dateStamp = settings.dateStamp,
            fileNameHeader = settings.fileNameHeader,
            watermark = settings.watermark,
            onDismiss = { activePicker = null },
            onConfirm = { pn, ds, fnh, wm ->
                onUpdateSettings { it.copy(pageNumbers = pn, dateStamp = ds, fileNameHeader = fnh, watermark = wm) }
                activePicker = null
            }
        )
        ActivePicker.PRESETS -> PresetsPickerDialog(
            presets = presets,
            activePresetName = settings.activePresetName,
            onDismiss = { activePicker = null },
            onApply = { preset ->
                onApplyPreset(preset)
                activePicker = null
            },
            onSaveCurrent = onSavePreset,
            onRename = onRenamePreset,
            onDelete = onDeletePreset
        )
        null -> {}
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp)
            .testTag(testTag)
            .semantics { contentDescription = "$title, currently $summary" }
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = HeadingNavy
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = summary,
            fontSize = 12.5.sp,
            color = BodyGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = CardBorderLight.copy(alpha = 0.5f), thickness = 0.8.dp)
}

// ==========================================
// 1. COPIES PICKER
// ==========================================

@Composable
private fun CopiesPickerDialog(
    copies: Int,
    collate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Boolean) -> Unit
) {
    var count by remember { mutableIntStateOf(copies) }
    var textVal by remember { mutableStateOf(copies.toString()) }
    var collateVal by remember { mutableStateOf(collate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copies", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stepper + Text input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (count > 1) {
                                count--
                                textVal = count.toString()
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease copies", tint = PrimaryBlue)
                    }

                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            textVal = digits
                            digits.toIntOrNull()?.let {
                                count = it.coerceIn(1, 99)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )
                    )

                    IconButton(
                        onClick = {
                            if (count < 99) {
                                count++
                                textVal = count.toString()
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase copies", tint = PrimaryBlue)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Collate Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Collate", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                        Text(
                            text = if (count > 1) "Sort multiple copies (1,2,3... 1,2,3)" else "Enabled for 2+ copies",
                            fontSize = 11.5.sp,
                            color = BodyGray
                        )
                    }
                    Switch(
                        checked = collateVal,
                        onCheckedChange = { collateVal = it },
                        enabled = count > 1,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(count, collateVal) },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BodyGray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

// ==========================================
// 2. PAGES PICKER
// ==========================================

@Composable
private fun PagesPickerDialog(
    pageSelection: PageSelection,
    reverseOrder: Boolean,
    excludedPagesCount: Int,
    totalPages: Int,
    onResetExcluded: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (PageSelection, Boolean) -> Unit
) {
    var mode by remember {
        mutableStateOf(
            when (pageSelection) {
                is PageSelection.All -> "all"
                is PageSelection.Odd -> "odd"
                is PageSelection.Even -> "even"
                is PageSelection.Range -> "range"
                is PageSelection.Custom -> "all"
            }
        )
    }

    var rangeText by remember {
        mutableStateOf(
            if (pageSelection is PageSelection.Range) pageSelection.text else "1-$totalPages"
        )
    }
    var reverse by remember { mutableStateOf(reverseOrder) }

    val parseResult = remember(rangeText, totalPages) {
        if (mode == "range") PageRangeParser.parse(rangeText, totalPages) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pages to Print", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Radio list: All, Odd, Even, Custom range
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "all" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == "all",
                        onClick = { mode = "all" },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Text("All pages", color = HeadingNavy)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "odd" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == "odd",
                        onClick = { mode = "odd" },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Text("Odd pages only (1, 3, 5...)", color = HeadingNavy)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "even" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == "even",
                        onClick = { mode = "even" },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Text("Even pages only (2, 4, 6...)", color = HeadingNavy)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "range" },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == "range",
                        onClick = { mode = "range" },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Text("Custom range", color = HeadingNavy)
                }

                if (mode == "range") {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it },
                        placeholder = { Text("e.g. 1-3, 5, 8-10") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        singleLine = true,
                        isError = parseResult is PageRangeResult.Error
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    when (parseResult) {
                        is PageRangeResult.Error -> {
                            Text(
                                text = parseResult.message,
                                color = Color(0xFFDC2626),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                        is PageRangeResult.Success -> {
                            Text(
                                text = "${parseResult.pages.size} pages selected",
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                        null -> {}
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorderLight, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Reverse Order Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Reverse order", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                    Switch(
                        checked = reverse,
                        onCheckedChange = { reverse = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                    )
                }

                // Excluded pages note
                if (excludedPagesCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$excludedPagesCount pages excluded in preview",
                            fontSize = 11.5.sp,
                            color = Color(0xFFDC2626)
                        )
                        TextButton(onClick = onResetExcluded) {
                            Text("Reset", color = ActionBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selection = when (mode) {
                        "odd" -> PageSelection.Odd
                        "even" -> PageSelection.Even
                        "range" -> {
                            if (parseResult is PageRangeResult.Success) {
                                PageSelection.Range(rangeText.trim())
                            } else {
                                PageSelection.All
                            }
                        }
                        else -> PageSelection.All
                    }
                    onConfirm(selection, reverse)
                },
                enabled = mode != "range" || parseResult is PageRangeResult.Success,
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BodyGray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

// ==========================================
// 3. MULTI-PAGE PRINTING PICKER
// ==========================================

@Composable
private fun MultiPagePickerDialog(
    pagesPerSheet: PagesPerSheet,
    pageOrder: PageOrder,
    borderAroundPages: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PagesPerSheet, PageOrder, Boolean) -> Unit
) {
    var pps by remember { mutableStateOf(pagesPerSheet) }
    var order by remember { mutableStateOf(pageOrder) }
    var border by remember { mutableStateOf(borderAroundPages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Multi-Page Printing", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Pages per sheet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BodyGray)
                Spacer(modifier = Modifier.height(8.dp))

                val options = listOf(
                    PagesPerSheet.ONE to "Singly",
                    PagesPerSheet.TWO to "2 per sheet",
                    PagesPerSheet.FOUR to "4 per sheet",
                    PagesPerSheet.SIX to "6 per sheet",
                    PagesPerSheet.NINE to "9 per sheet"
                )

                options.forEach { (optionPps, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pps = optionPps }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pps == optionPps,
                            onClick = { pps = optionPps },
                            colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        MiniGridIcon(cols = optionPps.cols, rows = optionPps.rows)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(label, color = HeadingNavy, fontWeight = if (pps == optionPps) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                if (pps != PagesPerSheet.ONE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Page order", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BodyGray)
                    Spacer(modifier = Modifier.height(6.dp))

                    PageOrder.entries.forEach { po ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { order = po }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = order == po,
                                onClick = { order = po },
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(po.label, color = HeadingNavy)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Border around pages", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                        Switch(
                            checked = border,
                            onCheckedChange = { border = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pps, order, border) },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BodyGray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
private fun MiniGridIcon(cols: Int, rows: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(1.dp, PrimaryBlue, RoundedCornerShape(2.dp))
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(cols) {
                        Box(
                            modifier = Modifier
                                .size(if (cols > 2) 4.dp else 6.dp)
                                .background(PrimaryBlue.copy(alpha = 0.5f), RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. DUPLEX PICKER
// ==========================================

@Composable
private fun DuplexPickerDialog(
    currentDuplex: DuplexMode,
    capabilities: PrinterCapabilities?,
    onDismiss: () -> Unit,
    onConfirm: (DuplexMode) -> Unit
) {
    var selected by remember { mutableStateOf(currentDuplex) }

    val supportsLong = capabilities == null || capabilities.sidesSupported.any { it.contains("long-edge") }
    val supportsShort = capabilities == null || capabilities.sidesSupported.any { it.contains("short-edge") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Double-Sided Printing", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // One-Sided
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = DuplexMode.ONE_SIDED }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == DuplexMode.ONE_SIDED,
                        onClick = { selected = DuplexMode.ONE_SIDED },
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Text("One side only", color = HeadingNavy)
                }

                // Long Edge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = supportsLong) { selected = DuplexMode.TWO_SIDED_LONG_EDGE }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == DuplexMode.TWO_SIDED_LONG_EDGE,
                        onClick = { selected = DuplexMode.TWO_SIDED_LONG_EDGE },
                        enabled = supportsLong,
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Column {
                        Text(
                            "Long edge (Standard book binding)",
                            color = if (supportsLong) HeadingNavy else BodyGray.copy(alpha = 0.5f)
                        )
                        if (!supportsLong) {
                            Text("Not supported by this printer", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }
                }

                // Short Edge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = supportsShort) { selected = DuplexMode.TWO_SIDED_SHORT_EDGE }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected == DuplexMode.TWO_SIDED_SHORT_EDGE,
                        onClick = { selected = DuplexMode.TWO_SIDED_SHORT_EDGE },
                        enabled = supportsShort,
                        colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                    )
                    Column {
                        Text(
                            "Short edge (Notepad style flip)",
                            color = if (supportsShort) HeadingNavy else BodyGray.copy(alpha = 0.5f)
                        )
                        if (!supportsShort) {
                            Text("Not supported by this printer", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
            ) {
                Text("OK", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = BodyGray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

// ==========================================
// 5. SIZE AND LAYOUT OPTIONS PICKER
// ==========================================

@Composable
private fun SizeLayoutPickerDialog(
    scale: PrintScale,
    margins: PrintMargins,
    alignment: PrintAlignment,
    orientation: PrintOrientation,
    onDismiss: () -> Unit,
    onConfirm: (PrintScale, PrintMargins, PrintAlignment, PrintOrientation) -> Unit
) {
    var scaleMode by remember {
        mutableStateOf(
            when (scale) {
                is PrintScale.FitToPage -> "fit"
                is PrintScale.FillPage -> "fill"
                is PrintScale.ActualSize -> "actual"
                is PrintScale.CustomPercent -> "custom"
            }
        )
    }
    var customPercent by remember {
        mutableFloatStateOf(
            if (scale is PrintScale.CustomPercent) scale.percent.toFloat() else 100f
        )
    }

    var marginMode by remember {
        mutableStateOf(
            when (margins) {
                is PrintMargins.None -> "none"
                is PrintMargins.Narrow -> "narrow"
                is PrintMargins.Normal -> "normal"
                is PrintMargins.Wide -> "wide"
                is PrintMargins.Custom -> "custom"
            }
        )
    }
    var topMarginText by remember { mutableStateOf(margins.topMm.toInt().toString()) }
    var bottomMarginText by remember { mutableStateOf(margins.bottomMm.toInt().toString()) }
    var leftMarginText by remember { mutableStateOf(margins.leftMm.toInt().toString()) }
    var rightMarginText by remember { mutableStateOf(margins.rightMm.toInt().toString()) }

    var selectedAlignment by remember { mutableStateOf(alignment) }
    var selectedOrientation by remember { mutableStateOf(orientation) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Size & Layout Options", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // SECTION 1: SCALE
                    Text("Scale", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "fit" to "Fit to page",
                        "fill" to "Fill page (crop)",
                        "actual" to "Actual size (100%)",
                        "custom" to "Custom scale"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scaleMode = mode },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scaleMode == mode,
                                onClick = { scaleMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text(label, color = HeadingNavy)
                        }
                    }
                    if (scaleMode == "custom") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Slider(
                                value = customPercent,
                                onValueChange = { customPercent = it },
                                valueRange = 25f..400f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("${customPercent.toInt()}%", fontWeight = FontWeight.Bold, color = HeadingNavy)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(10.dp))

                    // SECTION 2: MARGINS
                    Text("Margins", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "none" to "None (0 mm)",
                        "narrow" to "Narrow (6 mm)",
                        "normal" to "Normal (12 mm)",
                        "wide" to "Wide (25 mm)",
                        "custom" to "Custom margins"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { marginMode = mode },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = marginMode == mode,
                                onClick = { marginMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text(label, color = HeadingNavy)
                        }
                    }

                    if (marginMode == "custom") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = topMarginText,
                                onValueChange = { topMarginText = it.filter { c -> c.isDigit() } },
                                label = { Text("Top") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = bottomMarginText,
                                onValueChange = { bottomMarginText = it.filter { c -> c.isDigit() } },
                                label = { Text("Btm") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = leftMarginText,
                                onValueChange = { leftMarginText = it.filter { c -> c.isDigit() } },
                                label = { Text("Left") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = rightMarginText,
                                onValueChange = { rightMarginText = it.filter { c -> c.isDigit() } },
                                label = { Text("Rgt") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(10.dp))

                    // SECTION 3: ALIGNMENT (3x3 Grid)
                    Text("Alignment", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val grid = listOf(
                        listOf(PrintAlignment.TOP_LEFT, PrintAlignment.TOP, PrintAlignment.TOP_RIGHT),
                        listOf(PrintAlignment.LEFT, PrintAlignment.CENTER, PrintAlignment.RIGHT),
                        listOf(PrintAlignment.BOTTOM_LEFT, PrintAlignment.BOTTOM, PrintAlignment.BOTTOM_RIGHT)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        grid.forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { alignItem ->
                                    val isSelected = selectedAlignment == alignItem
                                    Surface(
                                        modifier = Modifier
                                            .size(54.dp, 36.dp)
                                            .clickable { selectedAlignment = alignItem },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) ActionBlue else LightBlue,
                                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderLight)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = when (alignItem) {
                                                    PrintAlignment.TOP_LEFT -> "↖"
                                                    PrintAlignment.TOP -> "↑"
                                                    PrintAlignment.TOP_RIGHT -> "↗"
                                                    PrintAlignment.LEFT -> "←"
                                                    PrintAlignment.CENTER -> "•"
                                                    PrintAlignment.RIGHT -> "→"
                                                    PrintAlignment.BOTTOM_LEFT -> "↙"
                                                    PrintAlignment.BOTTOM -> "↓"
                                                    PrintAlignment.BOTTOM_RIGHT -> "↘"
                                                },
                                                color = if (isSelected) Color.White else PrimaryBlue,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(10.dp))

                    // SECTION 4: ORIENTATION
                    Text("Orientation", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PrintOrientation.entries.forEach { orient ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOrientation = orient },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOrientation == orient,
                                onClick = { selectedOrientation = orient },
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text(orient.label, color = HeadingNavy)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = BodyGray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalScale = when (scaleMode) {
                                "fill" -> PrintScale.FillPage
                                "actual" -> PrintScale.ActualSize
                                "custom" -> PrintScale.CustomPercent(customPercent.toInt().coerceIn(25, 400))
                                else -> PrintScale.FitToPage
                            }
                            val finalMargins = when (marginMode) {
                                "none" -> PrintMargins.None
                                "narrow" -> PrintMargins.Narrow
                                "wide" -> PrintMargins.Wide
                                "custom" -> {
                                    val t = topMarginText.toFloatOrNull()?.coerceIn(0f, 50f) ?: 12f
                                    val b = bottomMarginText.toFloatOrNull()?.coerceIn(0f, 50f) ?: 12f
                                    val l = leftMarginText.toFloatOrNull()?.coerceIn(0f, 50f) ?: 12f
                                    val r = rightMarginText.toFloatOrNull()?.coerceIn(0f, 50f) ?: 12f
                                    PrintMargins.Custom(t, b, l, r)
                                }
                                else -> PrintMargins.Normal
                            }
                            onConfirm(finalScale, finalMargins, selectedAlignment, selectedOrientation)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. PAPER AND PRINTING OPTIONS PICKER
// ==========================================

@Composable
private fun PaperPrintingPickerDialog(
    paperSize: PaperSize,
    quality: PrintQuality,
    colorMode: PrintColorMode,
    capabilities: PrinterCapabilities?,
    onDismiss: () -> Unit,
    onConfirm: (PaperSize, PrintQuality, PrintColorMode) -> Unit
) {
    var selectedPaper by remember { mutableStateOf(paperSize) }
    var selectedQuality by remember { mutableStateOf(quality) }
    var selectedColorMode by remember { mutableStateOf(colorMode) }

    var isCustomPaper by remember { mutableStateOf(false) }
    var customWMm by remember { mutableStateOf("210") }
    var customHMm by remember { mutableStateOf("297") }

    val defaultMediaName = capabilities?.mediaDefault ?: "A4"
    val standardPapers = remember(capabilities) {
        val supported = capabilities?.mediaSupported ?: emptyList()
        if (supported.isNotEmpty()) {
            supported
        } else {
            listOf(
                PaperSize("A4", 210, 297),
                PaperSize("Letter", 216, 279),
                PaperSize("Legal", 216, 356),
                PaperSize("A3", 297, 420),
                PaperSize("A5", 148, 210),
                PaperSize("4x6 Photo", 102, 152),
                PaperSize("5x7 Photo", 127, 178)
            )
        }
    }

    val isColorSupported = capabilities?.colorSupported ?: true
    val supportedQualities = capabilities?.qualitiesSupported?.map { it.lowercase() } ?: listOf("draft", "normal", "high")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Paper & Printing Options", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Paper Size
                    Text("Paper Size", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    standardPapers.forEach { p ->
                        val isDefault = p.name.equals(defaultMediaName, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPaper = p
                                    isCustomPaper = false
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isCustomPaper && selectedPaper.name.equals(p.name, ignoreCase = true),
                                onClick = {
                                    selectedPaper = p
                                    isCustomPaper = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text("${p.name} (${p.widthMm}×${p.heightMm} mm)", color = HeadingNavy)
                            if (isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = LightBlue
                                ) {
                                    Text(
                                        text = "Default",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Custom Paper option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCustomPaper = true }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isCustomPaper,
                            onClick = { isCustomPaper = true },
                            colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                        )
                        Text("Custom size", color = HeadingNavy)
                    }

                    if (isCustomPaper) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customWMm,
                                onValueChange = { customWMm = it.filter { c -> c.isDigit() } },
                                label = { Text("Width mm (50-500)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = customHMm,
                                onValueChange = { customHMm = it.filter { c -> c.isDigit() } },
                                label = { Text("Height mm (50-500)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Quality
                    Text("Print Quality", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PrintQuality.entries.forEach { q ->
                        val isSupported = supportedQualities.contains(q.name.lowercase())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isSupported) { selectedQuality = q }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == q,
                                onClick = { selectedQuality = q },
                                enabled = isSupported,
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text(
                                text = q.label,
                                color = if (isSupported) HeadingNavy else BodyGray.copy(alpha = 0.5f)
                            )
                            if (!isSupported) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(Not supported)", fontSize = 11.sp, color = BodyGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Color
                    Text("Color Mode", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    PrintColorMode.entries.forEach { cm ->
                        val isSupported = if (cm == PrintColorMode.COLOR) isColorSupported else true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isSupported) { selectedColorMode = cm }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedColorMode == cm,
                                onClick = { selectedColorMode = cm },
                                enabled = isSupported,
                                colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                            )
                            Text(
                                text = cm.label,
                                color = if (isSupported) HeadingNavy else BodyGray.copy(alpha = 0.5f)
                            )
                            if (!isSupported) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("(Monochrome printer)", fontSize = 11.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = BodyGray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val paper = if (isCustomPaper) {
                                val w = customWMm.toIntOrNull()?.coerceIn(50, 500) ?: 210
                                val h = customHMm.toIntOrNull()?.coerceIn(50, 500) ?: 297
                                PaperSize("Custom ($w×$h mm)", w, h)
                            } else {
                                selectedPaper
                            }
                            onConfirm(paper, selectedQuality, selectedColorMode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. EXTRAS PICKER
// ==========================================

@Composable
private fun ExtrasPickerDialog(
    pageNumbers: PageNumberPosition?,
    dateStamp: Boolean,
    fileNameHeader: Boolean,
    watermark: WatermarkSettings?,
    onDismiss: () -> Unit,
    onConfirm: (PageNumberPosition?, Boolean, Boolean, WatermarkSettings?) -> Unit
) {
    var hasPageNumbers by remember { mutableStateOf(pageNumbers != null) }
    var pageNumberPos by remember { mutableStateOf(pageNumbers ?: PageNumberPosition.BOTTOM_CENTER) }

    var hasDate by remember { mutableStateOf(dateStamp) }
    var hasFileName by remember { mutableStateOf(fileNameHeader) }

    var hasWatermark by remember { mutableStateOf(!watermark?.text.isNullOrBlank()) }
    var watermarkText by remember { mutableStateOf(watermark?.text ?: "CONFIDENTIAL") }
    var watermarkOpacity by remember { mutableFloatStateOf(watermark?.opacity ?: 0.20f) }
    var isDiagonal by remember { mutableStateOf(watermark?.isDiagonal ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Extras & Stamps", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. Page Numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Page numbers", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                            Text("Prints sheet index at margin", fontSize = 11.5.sp, color = BodyGray)
                        }
                        Switch(
                            checked = hasPageNumbers,
                            onCheckedChange = { hasPageNumbers = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                        )
                    }

                    if (hasPageNumbers) {
                        Spacer(modifier = Modifier.height(6.dp))
                        PageNumberPosition.entries.forEach { pos ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { pageNumberPos = pos }
                                    .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = pageNumberPos == pos,
                                    onClick = { pageNumberPos = pos },
                                    colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                                )
                                Text(pos.label, color = HeadingNavy, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Date and Time Stamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Date & time stamp", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                            Text("Prints timestamp at bottom corner", fontSize = 11.5.sp, color = BodyGray)
                        }
                        Switch(
                            checked = hasDate,
                            onCheckedChange = { hasDate = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. File Name Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("File name header", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                            Text("Prints document title at top corner", fontSize = 11.5.sp, color = BodyGray)
                        }
                        Switch(
                            checked = hasFileName,
                            onCheckedChange = { hasFileName = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorderLight)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 4. Watermark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Watermark text", fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                            Text("Draws semi-transparent watermark", fontSize = 11.5.sp, color = BodyGray)
                        }
                        Switch(
                            checked = hasWatermark,
                            onCheckedChange = { hasWatermark = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ActionBlue)
                        )
                    }

                    if (hasWatermark) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Watermark text") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", fontSize = 12.sp, color = BodyGray)
                        Slider(
                            value = watermarkOpacity,
                            onValueChange = { watermarkOpacity = it },
                            valueRange = 0.05f..0.50f,
                            colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .clickable { isDiagonal = true }
                                    .padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isDiagonal,
                                    onClick = { isDiagonal = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                                )
                                Text("Diagonal", color = HeadingNavy, fontSize = 13.sp)
                            }
                            Row(
                                modifier = Modifier.clickable { isDiagonal = false },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !isDiagonal,
                                    onClick = { isDiagonal = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = ActionBlue)
                                )
                                Text("Horizontal", color = HeadingNavy, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = BodyGray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalPn = if (hasPageNumbers) pageNumberPos else null
                            val finalWm = if (hasWatermark && watermarkText.isNotBlank()) {
                                WatermarkSettings(watermarkText.trim(), watermarkOpacity, isDiagonal)
                            } else null
                            onConfirm(finalPn, hasDate, hasFileName, finalWm)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. PRESETS PICKER
// ==========================================

@Composable
private fun PresetsPickerDialog(
    presets: List<PrintPresetEntity>,
    activePresetName: String?,
    onDismiss: () -> Unit,
    onApply: (PrintPresetEntity) -> Unit,
    onSaveCurrent: suspend (String) -> Result<Unit>,
    onRename: suspend (String, String) -> Result<Unit>,
    onDelete: suspend (String) -> Result<Unit>
) {
    val coroutineScope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<PrintPresetEntity?>(null) }
    var presetToDelete by remember { mutableStateOf<PrintPresetEntity?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.dp, CardBorderLight)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Print Presets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                    TextButton(onClick = { showSaveDialog = true }) {
                        Text("+ Save Current", fontWeight = FontWeight.Bold, color = ActionBlue)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isActive = preset.name.equals(activePresetName, ignoreCase = true)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApply(preset) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) LightBlue else Color(0xFFF8FAFC)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isActive) PrimaryBlue else CardBorderLight
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = preset.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = HeadingNavy
                                        )
                                        if (preset.isBuiltIn) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFE2E8F0)
                                            ) {
                                                Text(
                                                    text = "Built-in",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BodyGray,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (isActive) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active preset",
                                                tint = ActionBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!preset.isBuiltIn) {
                                    IconButton(
                                        onClick = { presetToRename = preset },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename preset", tint = BodyGray, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { presetToDelete = preset },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete preset", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close", color = BodyGray) }
                }
            }
        }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        var newPresetName by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Preset", fontWeight = FontWeight.Bold, color = HeadingNavy) },
            text = {
                Column {
                    Text("Enter a unique name for this preset (up to 30 characters):", fontSize = 13.sp, color = BodyGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = {
                            newPresetName = it
                            errorMessage = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage != null
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(errorMessage!!, color = Color(0xFFDC2626), fontSize = 11.5.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = onSaveCurrent(newPresetName)
                            if (res.isSuccess) {
                                showSaveDialog = false
                            } else {
                                errorMessage = res.exceptionOrNull()?.message ?: "Invalid name"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel", color = BodyGray) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    // Rename Preset Dialog
    presetToRename?.let { preset ->
        var renameText by remember { mutableStateOf(preset.name) }
        var renameError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { presetToRename = null },
            title = { Text("Rename Preset", fontWeight = FontWeight.Bold, color = HeadingNavy) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = {
                            renameText = it
                            renameError = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = renameError != null
                    )
                    if (renameError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(renameError!!, color = Color(0xFFDC2626), fontSize = 11.5.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val res = onRename(preset.id, renameText)
                            if (res.isSuccess) {
                                presetToRename = null
                            } else {
                                renameError = res.exceptionOrNull()?.message ?: "Invalid name"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ActionBlue)
                ) {
                    Text("Rename", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToRename = null }) { Text("Cancel", color = BodyGray) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }

    // Delete Preset Dialog
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Preset?", fontWeight = FontWeight.Bold, color = HeadingNavy) },
            text = { Text("Are you sure you want to delete preset '${preset.name}'?", color = BodyGray) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            onDelete(preset.id)
                            presetToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) { Text("Cancel", color = BodyGray) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }
}
