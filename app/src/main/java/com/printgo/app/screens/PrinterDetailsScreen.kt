package com.printgo.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.R
import com.printgo.app.components.PrimaryButton
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Marker
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.ActionPurple
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.LightMint
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.ConnectionTestResult
import com.printgo.app.viewmodel.PrinterViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrinterDetailsScreen(
    printerId: String,
    printerViewModel: PrinterViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var printer by remember { mutableStateOf<Printer?>(null) }
    var capabilities by remember { mutableStateOf<PrinterCapabilities?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionTestResult?>(null) }

    var showRenameDialog by remember { mutableStateOf(false) }

    LaunchedEffect(printerId) {
        val found = printerViewModel.getPrinterById(printerId)
        printer = found
        if (found != null) {
            val caps = printerViewModel.loadCapabilitiesFor(found)
            capabilities = caps
        }
        isLoading = false
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("printer_details_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Printer Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("printer_details_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PrimaryBlue
                )
            )
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { innerPadding ->
        if (isLoading || printer == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else {
            val p = printer!!
            val caps = capabilities ?: PrinterCapabilities.defaultFor(p)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Header Card with Brand Avatar, Alias, Make/Model, Address & Status Chip
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = CircleShape,
                                color = p.brand.color.copy(alpha = 0.12f),
                                border = BorderStroke(2.dp, p.brand.color.copy(alpha = 0.4f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (p.connectionType == ConnectionType.PDF) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    } else {
                                        Text(
                                            text = p.brand.initials,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = p.brand.color
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = p.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HeadingNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    IconButton(
                                        onClick = { showRenameDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = caps.makeAndModel,
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (p.host.isNotBlank() && p.connectionType == ConnectionType.WIFI) {
                                    Text(
                                        text = "${p.host}:${p.port} (${p.protocol.name})",
                                        fontSize = 12.sp,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Status Chip (Ready, Busy, Offline, Stopped)
                            val statusColor = when (caps.state.lowercase()) {
                                "idle", "ready" -> ActionGreen
                                "processing", "busy" -> ActionBlue
                                "stopped" -> Color(0xFFE65100)
                                else -> Color.Gray
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(6.dp),
                                        shape = CircleShape,
                                        color = statusColor
                                    ) {}
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = caps.state,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        // State reasons warning banner
                        if (caps.stateReasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Status notice: ${caps.stateReasons.joinToString(", ")}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Ink / Toner Supplies Card
                if (caps.markerLevels.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Ink & Toner Supplies",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = HeadingNavy
                            )

                            caps.markerLevels.forEach { marker ->
                                MarkerSupplyRow(marker)
                            }
                        }
                    }
                }

                // 3. Capabilities Card (Paper sizes, color, duplex, formats, copies)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Capabilities & Hardware",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )

                        // Supported Media Chips
                        Column {
                            Text(
                                text = "Supported Paper Sizes",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                caps.mediaSupported.forEach { size ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = LightBlue,
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = size.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PrimaryBlue,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Feature Badges Grid
                        Row(modifier = Modifier.fillMaxWidth()) {
                            CapabilityBadge(
                                label = "Color Printing",
                                value = if (caps.colorSupported) "Supported" else "Monochrome",
                                isSupported = caps.colorSupported,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val hasDuplex = caps.sidesSupported.any { it.contains("two-sided") }
                            CapabilityBadge(
                                label = "Duplex (2-sided)",
                                value = if (hasDuplex) "Supported" else "1-Sided Only",
                                isSupported = hasDuplex,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            CapabilityBadge(
                                label = "Max Copies",
                                value = "${caps.copiesMax} copies",
                                isSupported = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            CapabilityBadge(
                                label = "Print Quality",
                                value = caps.qualitiesSupported.joinToString("/"),
                                isSupported = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Formats
                        Column {
                            Text(
                                text = "Document Formats",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = caps.documentFormatsSupported.joinToString(", ") { it.substringAfter("/") },
                                fontSize = 13.sp,
                                color = HeadingNavy,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 4. Test Connection Banner Result
                if (testResult != null) {
                    val res = testResult!!
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (res.isSuccess) LightMint else Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, if (res.isSuccess) ActionGreen else Color.Red)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (res.isSuccess) ActionGreen else Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (res.isSuccess) "Connection Test Successful" else "Connection Failed",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Text(
                                    text = if (res.isSuccess) "${res.message} • ${res.responseTimeMs}ms response" else res.message,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // 5. Actions Card: Set as Default, Refresh Status, Test Connection, Remove
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Set as Default
                        if (p.connectionType != ConnectionType.PDF) {
                            OutlinedButton(
                                onClick = {
                                    printerViewModel.setDefaultPrinter(p)
                                    printer = p.copy(isDefault = true)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Set as default printer") }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                            ) {
                                Icon(
                                    imageVector = if (p.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (p.isDefault) Color(0xFFFFB300) else PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (p.isDefault) "Default Printer" else "Set as Default Printer")
                            }
                        }

                        // Test Connection Button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTestingConnection = true
                                    testResult = printerViewModel.testConnection(p)
                                    isTestingConnection = false
                                }
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ActionBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing Connection...")
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Connection")
                            }
                        }

                        // Refresh Status Button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val updatedCaps = printerViewModel.loadCapabilitiesFor(p)
                                    capabilities = updatedCaps
                                    snackbarHostState.showSnackbar("Status & capabilities updated")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HeadingNavy)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Status")
                        }

                        // Remove Printer (for non-virtual printers)
                        if (p.connectionType != ConnectionType.PDF) {
                            OutlinedButton(
                                onClick = {
                                    printerViewModel.deletePrinter(p)
                                    onBackClick()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Remove Printer")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && printer != null) {
        var newAlias by remember { mutableStateOf(printer!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Printer", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newAlias,
                    onValueChange = { newAlias = it },
                    label = { Text("Printer Alias") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newAlias.isNotBlank()) {
                            printerViewModel.renamePrinter(printer!!, newAlias.trim())
                            printer = printer!!.copy(name = newAlias.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Save", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun MarkerSupplyRow(marker: Marker) {
    val barColor = try {
        Color(android.graphics.Color.parseColor(marker.colorHex))
    } catch (_: Exception) {
        PrimaryBlue
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = barColor
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = marker.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = HeadingNavy
                )
            }
            Text(
                text = if (marker.levelPercent >= 0) "${marker.levelPercent}%" else "OK",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = HeadingNavy
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (marker.levelPercent >= 0) marker.levelPercent / 100f else 0.8f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFFF1F5F9)
        )
    }
}

@Composable
private fun CapabilityBadge(
    label: String,
    value: String,
    isSupported: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSupported) HeadingNavy else Color.Gray
            )
        }
    }
}
