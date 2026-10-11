package com.printgo.app.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.print.PrintManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.BuildConfig
import com.printgo.app.R
import com.printgo.app.components.PrimaryButton
import com.printgo.app.data.discovery.ScanStatus
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.ActionPurple
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.LightMint
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.ConnectionState
import com.printgo.app.viewmodel.PrinterTab
import com.printgo.app.viewmodel.PrinterViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintersScreen(
    printerViewModel: PrinterViewModel,
    onBackClick: () -> Unit,
    onNavigateToDetails: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedTab by printerViewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedPrinter by printerViewModel.selectedPrinter.collectAsStateWithLifecycle()
    val savedPrinters by printerViewModel.savedPrinters.collectAsStateWithLifecycle()
    val discoveredPrinters by printerViewModel.discoveredPrinters.collectAsStateWithLifecycle()
    val scanStatus by printerViewModel.scanStatus.collectAsStateWithLifecycle()
    val showDemoPrinters by printerViewModel.showDemoPrinters.collectAsStateWithLifecycle()
    val connectionState by printerViewModel.connectionState.collectAsStateWithLifecycle()

    var showManualAddDialog by remember { mutableStateOf(false) }
    var renamePrinterTarget by remember { mutableStateOf<Printer?>(null) }
    var showTroubleshootingTips by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bluetooth permission request launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            printerViewModel.bluetoothPrinterManager.refreshPairedDevices()
            printerViewModel.bluetoothPrinterManager.startDiscovery()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Bluetooth permission is required to search for nearby Bluetooth printers.")
            }
        }
    }

    DisposableEffect(Unit) {
        printerViewModel.selectTab(PrinterTab.WIFI)
        onDispose {
            printerViewModel.wifiDiscoveryManager.stopDiscovery()
            printerViewModel.bluetoothPrinterManager.stopDiscovery()
            printerViewModel.usbPrinterManager.stopListening()
        }
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("printers_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.printers_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("printers_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showManualAddDialog = true },
                        modifier = Modifier.testTag("printers_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Printer",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { printerViewModel.refreshDiscovery() },
                        modifier = Modifier.testTag("printers_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Connection Tabs (WiFi, Bluetooth, USB) with AccentGreen Indicator
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = AccentGreen,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == PrinterTab.WIFI,
                    onClick = { printerViewModel.selectTab(PrinterTab.WIFI) },
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.65f)
                )
                Tab(
                    selected = selectedTab == PrinterTab.BLUETOOTH,
                    onClick = {
                        printerViewModel.selectTab(PrinterTab.BLUETOOTH)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            bluetoothPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                            )
                        } else {
                            printerViewModel.bluetoothPrinterManager.refreshPairedDevices()
                        }
                    },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("Bluetooth", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.65f)
                )
                Tab(
                    selected = selectedTab == PrinterTab.USB,
                    onClick = { printerViewModel.selectTab(PrinterTab.USB) },
                    icon = { Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("USB", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(alpha = 0.65f)
                )
            }

            // 2. Slim AccentGreen progress bar when scanning
            if (scanStatus == ScanStatus.Scanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AccentGreen,
                    trackColor = Color.Transparent
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LightMint
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ActionGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Searching for printers on your network…",
                            fontSize = 12.sp,
                            color = HeadingNavy,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3. Printer List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                // WiFi Not Connected Warning
                if (selectedTab == PrinterTab.WIFI && !printerViewModel.wifiDiscoveryManager.isWifiConnected()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                            border = BorderStroke(1.dp, Color(0xFFFFEDD5))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Wi-Fi Not Connected",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connect this phone to the same Wi-Fi network as your printer to discover it automatically.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                                ) {
                                    Text("Open Wi-Fi Settings")
                                }
                            }
                        }
                    }
                }

                // Bluetooth Disabled Warning
                if (selectedTab == PrinterTab.BLUETOOTH && !printerViewModel.bluetoothPrinterManager.isBluetoothEnabled()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            border = BorderStroke(1.dp, Color(0xFFDBEAFE))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BluetoothDisabled,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Bluetooth is Turned Off",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enable Bluetooth to pair and connect with mobile Bluetooth receipt and label printers.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                                ) {
                                    Text("Turn On Bluetooth")
                                }
                            }
                        }
                    }
                }

                // USB OTG Notice
                if (selectedTab == PrinterTab.USB && discoveredPrinters.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Connect with USB OTG",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Plug your printer's USB cable into this device using a USB-OTG adapter. Power on the printer and grant permission when prompted.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { printerViewModel.usbPrinterManager.refreshUsbDevices() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                                ) {
                                    Text("Check Connected USB Devices")
                                }
                            }
                        }
                    }
                }

                // Section: Saved Printers (Always includes "Save as PDF" at the top)
                item {
                    Text(
                        text = "Saved Printers",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeadingNavy,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                items(savedPrinters, key = { it.id }) { printer ->
                    PrinterRowItem(
                        printer = printer,
                        isSelected = selectedPrinter?.id == printer.id,
                        onSelect = {
                            if (printer.connectionType == ConnectionType.PDF) {
                                printerViewModel.selectPrinter(printer)
                                onBackClick()
                            } else {
                                printerViewModel.startConnecting(printer)
                            }
                        },
                        onInfoClick = { onNavigateToDetails(printer.id) },
                        onSetDefault = { printerViewModel.setDefaultPrinter(printer) },
                        onRename = { renamePrinterTarget = printer },
                        onDelete = { printerViewModel.deletePrinter(printer) },
                        isSaved = true
                    )
                }

                // Section: Discovered Nearby Printers
                val nearbyPrinters = discoveredPrinters.filter { nearby ->
                    savedPrinters.none { saved -> saved.id == nearby.id }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Discovered Nearby (${nearbyPrinters.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeadingNavy,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (nearbyPrinters.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (scanStatus == ScanStatus.Scanning) "Searching for printers..." else "No printers discovered yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Make sure your printer is turned on and connected to the same network.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { printerViewModel.refreshDiscovery() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                                ) {
                                    Text("Retry Scan")
                                }
                            }
                        }
                    }
                } else {
                    items(nearbyPrinters, key = { it.id }) { printer ->
                        PrinterRowItem(
                            printer = printer,
                            isSelected = selectedPrinter?.id == printer.id,
                            onSelect = {
                                printerViewModel.startConnecting(printer)
                            },
                            onInfoClick = { onNavigateToDetails(printer.id) },
                            onSetDefault = {
                                printerViewModel.savePrinter(printer)
                                printerViewModel.setDefaultPrinter(printer)
                            },
                            onRename = null,
                            onDelete = null,
                            isSaved = false
                        )
                    }
                }

                // Section: "Can't find your printer?" card at bottom
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Can't find your printer?",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = HeadingNavy
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "If your printer doesn't advertise via mDNS, you can connect directly by its IP address or send the job to Android's print spooler.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showManualAddDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                                ) {
                                    Text("Add by IP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Opening Android system print services...")
                                            }
                                        } catch (e: Exception) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("System print manager unavailable")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1.1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ActionBlue)
                                ) {
                                    Text("System Print", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Debug Demo Printers Switch (in Debug builds only)
                if (BuildConfig.DEBUG) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Show demo printers",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HeadingNavy
                                    )
                                    Text(
                                        text = "Injects mock WiFi & BT printers for emulator testing",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = showDemoPrinters,
                                    onCheckedChange = { printerViewModel.toggleDemoPrinters() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Connection Flow Bottom Sheet (Part C)
    if (connectionState !is ConnectionState.Idle) {
        ModalBottomSheet(
            onDismissRequest = { printerViewModel.cancelConnection() },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = connectionState) {
                    is ConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            color = PrimaryBlue,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to Printer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Querying status and capabilities of ${state.printer.name}...",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = { printerViewModel.cancelConnection() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }

                    is ConnectionState.Connected -> {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape,
                            color = LightMint
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ActionGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Printer Connected!",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.printer.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue
                        )
                        Text(
                            text = "${state.capabilities.makeAndModel} • State: ${state.capabilities.state}",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        PrimaryButton(
                            text = "Continue",
                            onClick = {
                                printerViewModel.selectPrinter(state.printer)
                                printerViewModel.resetConnectionState()
                                onBackClick()
                            },
                            backgroundColor = ActionBlue
                        )
                    }

                    is ConnectionState.TlsUntrusted -> {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFFBEB)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Trust Security Certificate?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${state.printer.name} is using a self-signed security certificate on your local network.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("SHA-256 Certificate Fingerprint:", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = state.fingerprint,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = HeadingNavy
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        PrimaryButton(
                            text = "Trust Certificate",
                            onClick = {
                                printerViewModel.trustTlsFingerprint(state.printer, state.fingerprint)
                            },
                            backgroundColor = ActionBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { printerViewModel.cancelConnection() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }

                    is ConnectionState.Failed -> {
                        Surface(
                            modifier = Modifier.size(58.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFEBEE)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Connection Failed",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = state.tip,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Part E: Expandable "Can't connect?" card with 4 tips
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showTroubleshootingTips = !showTroubleshootingTips },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.HelpOutline,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Can't connect? Troubleshooting Tips",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showTroubleshootingTips) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                AnimatedVisibility(visible = showTroubleshootingTips) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        TroubleshootItem(1, "Verify phone and printer are connected to the exact same Wi-Fi network.")
                                        TroubleshootItem(2, "Make sure the printer is turned on and not in power-saving sleep mode.")
                                        TroubleshootItem(3, "Disable any active VPN or router guest-network isolation.")
                                        TroubleshootItem(4, "Try 'Add by IP address' with port 631 if mDNS broadcast is blocked.")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { printerViewModel.resetConnectionState() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            PrimaryButton(
                                text = "Retry",
                                onClick = { printerViewModel.startConnecting(state.printer) },
                                backgroundColor = ActionBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is ConnectionState.Idle -> {
                        // Sheet not shown when idle
                    }
                }
            }
        }
    }

    // Manual Add Printer Dialog
    if (showManualAddDialog) {
        ManualAddPrinterDialog(
            onDismiss = { showManualAddDialog = false },
            onAdd = { name, host, port, protocol ->
                coroutineScope.launch {
                    val result = printerViewModel.addManualPrinter(name, host, port, protocol)
                    if (result.isSuccess) {
                        showManualAddDialog = false
                        snackbarHostState.showSnackbar("Printer added successfully!")
                    } else {
                        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Failed to add printer")
                    }
                }
            }
        )
    }

    // Rename Printer Dialog
    if (renamePrinterTarget != null) {
        RenamePrinterDialog(
            printer = renamePrinterTarget!!,
            onDismiss = { renamePrinterTarget = null },
            onConfirm = { newName ->
                printerViewModel.renamePrinter(renamePrinterTarget!!, newName)
                renamePrinterTarget = null
            }
        )
    }
}

@Composable
private fun TroubleshootItem(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$number.",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color(0xFF475569),
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun PrinterRowItem(
    printer: Printer,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfoClick: () -> Unit,
    onSetDefault: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    isSaved: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) LightBlue else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Colored Avatar
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = printer.brand.color.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, printer.brand.color.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (printer.connectionType == ConnectionType.PDF) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            text = printer.brand.initials,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = printer.brand.color
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Printer Name & Model Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = printer.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeadingNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Small status dot
                    if (printer.connectionType != ConnectionType.PDF) {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = if (printer.isOnline) ActionGreen else Color.LightGray
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = printer.displaySubtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Connection Badge (WiFi, BT, USB, PDF)
            Surface(
                modifier = Modifier.padding(horizontal = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = when (printer.connectionType) {
                    ConnectionType.WIFI -> LightBlue
                    ConnectionType.BLUETOOTH -> Color(0xFFEFF6FF)
                    ConnectionType.USB -> Color(0xFFF3E8FF)
                    ConnectionType.PDF -> Color(0xFFFFEBEE)
                }
            ) {
                Text(
                    text = printer.connectionType.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (printer.connectionType) {
                        ConnectionType.WIFI -> PrimaryBlue
                        ConnectionType.BLUETOOTH -> ActionBlue
                        ConnectionType.USB -> ActionPurple
                        ConnectionType.PDF -> Color(0xFFE53935)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Details Info Action
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Printer Details",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Default Star Action
            if (printer.connectionType != ConnectionType.PDF) {
                IconButton(
                    onClick = onSetDefault,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (printer.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Default",
                        tint = if (printer.isDefault) Color(0xFFFFB300) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Saved printer options menu (Rename, Set default, Remove)
            if (isSaved && printer.connectionType != ConnectionType.PDF) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set as default") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300)) },
                            onClick = {
                                showMenu = false
                                onSetDefault()
                            }
                        )
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Remove", color = Color(0xFFDC2626)) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFDC2626)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAddPrinterDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, host: String, port: String, protocol: PrinterProtocol) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("631") }
    var protocol by remember { mutableStateOf(PrinterProtocol.IPP) }
    var protocolDropdownExpanded by remember { mutableStateOf(false) }

    var hostError by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Printer by IP", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Printer Name (optional)") },
                    placeholder = { Text("e.g. Office LaserJet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        hostError = null
                    },
                    label = { Text("IP Address or Hostname") },
                    placeholder = { Text("e.g. 192.168.1.100 or printer.local") },
                    isError = hostError != null,
                    supportingText = {
                        if (hostError != null) Text(hostError!!, color = Color.Red)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port (default 631 for IPP, 9100 for Raw)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Protocol selector
                ExposedDropdownMenuBox(
                    expanded = protocolDropdownExpanded,
                    onExpandedChange = { protocolDropdownExpanded = !protocolDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = protocol.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = protocolDropdownExpanded,
                        onDismissRequest = { protocolDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("IPP (Internet Printing Protocol - Default)") },
                            onClick = {
                                protocol = PrinterProtocol.IPP
                                port = "631"
                                protocolDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("IPPS (Secure IPP / SSL)") },
                            onClick = {
                                protocol = PrinterProtocol.IPPS
                                port = "631"
                                protocolDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Raw 9100 (JetDirect / Port 9100)") },
                            onClick = {
                                protocol = PrinterProtocol.RAW9100
                                port = "9100"
                                protocolDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (isChecking) "Checking..." else "Add Printer",
                onClick = {
                    if (host.trim().isBlank()) {
                        hostError = "Please enter a valid IP address or hostname"
                        return@PrimaryButton
                    }
                    isChecking = true
                    onAdd(name, host, port, protocol)
                },
                enabled = !isChecking,
                modifier = Modifier.width(130.dp)
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun RenamePrinterDialog(
    printer: Printer,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(printer.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Printer", fontWeight = FontWeight.Bold, color = HeadingNavy) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Printer Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) onConfirm(newName)
                }
            ) {
                Text("Save", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
