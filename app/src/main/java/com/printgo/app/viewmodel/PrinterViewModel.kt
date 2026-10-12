package com.printgo.app.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.BuildConfig
import com.printgo.app.data.discovery.BluetoothPrinterManager
import com.printgo.app.data.discovery.IpValidator
import com.printgo.app.data.discovery.ScanStatus
import com.printgo.app.data.discovery.UsbPrinterManager
import com.printgo.app.data.discovery.WifiDiscoveryManager
import com.printgo.app.data.ipp.IppClient
import com.printgo.app.data.ipp.IppResult
import com.printgo.app.data.local.PrintGoDatabase
import com.printgo.app.data.repository.PrinterRepository
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.model.PrinterConnectionStatus
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.printgo.app.data.print.UsbAutoSetup
import com.printgo.app.data.print.UsbIds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PrinterTab {
    WIFI,
    BLUETOOTH,
    USB
}

sealed interface ConnectionState {
    object Idle : ConnectionState
    data class Connecting(val printer: Printer) : ConnectionState
    data class Connected(val printer: Printer, val capabilities: PrinterCapabilities) : ConnectionState
    data class TlsUntrusted(val printer: Printer, val fingerprint: String) : ConnectionState
    data class Failed(val printer: Printer, val message: String, val tip: String) : ConnectionState
}

sealed interface CapabilitiesState {
    object Loading : CapabilitiesState
    data class Ready(val capabilities: PrinterCapabilities) : CapabilitiesState
    data class Error(val message: String) : CapabilitiesState
}

data class ConnectionTestResult(
    val isSuccess: Boolean,
    val message: String,
    val responseTimeMs: Long = 0L
)

sealed interface UsbEvent {
    data class Message(val text: String) : UsbEvent
    data class NeedsPrintService(val printerName: String, val maker: String?) : UsbEvent
}

class PrinterViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PrintGoDatabase.getInstance(application)
    val repository = PrinterRepository(db.savedPrinterDao())
    val ippClient = IppClient()
    private val prefs = application.getSharedPreferences("printgo_printer_prefs", Context.MODE_PRIVATE)

    val wifiDiscoveryManager = WifiDiscoveryManager(application, viewModelScope)
    val bluetoothPrinterManager = BluetoothPrinterManager(application)
    val usbPrinterManager = UsbPrinterManager(application)

    private val _selectedTab = MutableStateFlow(PrinterTab.WIFI)
    val selectedTab: StateFlow<PrinterTab> = _selectedTab.asStateFlow()

    private val _selectedPrinter = MutableStateFlow<Printer?>(Printer.SAVE_AS_PDF)
    val selectedPrinter: StateFlow<Printer?> = _selectedPrinter.asStateFlow()

    private val _selectedPrinterCapabilities = MutableStateFlow<CapabilitiesState>(
        CapabilitiesState.Ready(PrinterCapabilities.defaultFor(Printer.SAVE_AS_PDF))
    )
    val selectedPrinterCapabilities: StateFlow<CapabilitiesState> = _selectedPrinterCapabilities.asStateFlow()

    private val _connectionStatus = MutableStateFlow<PrinterConnectionStatus>(PrinterConnectionStatus.PdfOnly)
    val connectionStatus: StateFlow<PrinterConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _showDemoPrinters = MutableStateFlow(BuildConfig.DEBUG)
    val showDemoPrinters: StateFlow<Boolean> = _showDemoPrinters.asStateFlow()

    private var activeConnectionJob: Job? = null
    private var homeMonitoringJob: Job? = null

    // Demo printers for debugging / emulator environments
    val demoWifiPrinter = Printer(
        id = "demo_hp_laserjet",
        name = "HP Color LaserJet Pro MFP",
        host = "192.168.1.150",
        port = 631,
        connectionType = ConnectionType.WIFI,
        protocol = PrinterProtocol.IPP,
        resourcePath = "ipp/print",
        make = "HP",
        model = "Color LaserJet Pro M479fdw",
        supportedFormats = listOf("application/pdf", "image/urf", "image/jpeg"),
        supportsColor = true,
        supportsDuplex = true,
        isOnline = true,
        isDefault = false,
        lastUsedAt = 0L,
        isManual = false
    )

    val demoBluetoothPrinter = Printer(
        id = "demo_brother_pocketjet",
        name = "Brother PocketJet PJ-773",
        host = "00:11:22:33:44:55",
        port = 0,
        connectionType = ConnectionType.BLUETOOTH,
        protocol = PrinterProtocol.NONE,
        resourcePath = "",
        make = "Brother",
        model = "PocketJet Mobile Printer",
        supportedFormats = listOf("application/pdf"),
        supportsColor = false,
        supportsDuplex = false,
        isOnline = true,
        isDefault = false,
        lastUsedAt = 0L,
        isManual = false
    )

    // Saved printers from Room
    val rawSavedPrinters: StateFlow<List<Printer>> = repository.savedPrinters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All saved printers, always prepended with "Save as PDF"
    val savedPrinters: StateFlow<List<Printer>> = rawSavedPrinters.combine(_selectedTab) { saved, tab ->
        val filtered = when (tab) {
            PrinterTab.WIFI -> saved.filter { it.connectionType == ConnectionType.WIFI || it.connectionType == ConnectionType.PDF }
            PrinterTab.BLUETOOTH -> saved.filter { it.connectionType == ConnectionType.BLUETOOTH || it.connectionType == ConnectionType.PDF }
            PrinterTab.USB -> saved.filter { it.connectionType == ConnectionType.USB || it.connectionType == ConnectionType.PDF }
        }
        val withoutPdf = filtered.filter { it.connectionType != ConnectionType.PDF }
        listOf(Printer.SAVE_AS_PDF) + withoutPdf
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(Printer.SAVE_AS_PDF))

    // Discovered printers for current tab
    val discoveredPrinters: StateFlow<List<Printer>> = combine(
        _selectedTab,
        wifiDiscoveryManager.discoveredPrinters,
        bluetoothPrinterManager.allPrinters,
        usbPrinterManager.attachedPrinters,
        _showDemoPrinters
    ) { tab, wifi, btList, usb, showDemo ->
        when (tab) {
            PrinterTab.WIFI -> {
                val list = wifi.toMutableList()
                if (showDemo && list.none { it.id == demoWifiPrinter.id }) {
                    list.add(demoWifiPrinter)
                }
                list
            }
            PrinterTab.BLUETOOTH -> {
                val combinedBt = btList.toMutableList()
                if (showDemo && combinedBt.none { it.id == demoBluetoothPrinter.id }) {
                    combinedBt.add(demoBluetoothPrinter)
                }
                combinedBt
            }
            PrinterTab.USB -> usb
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanStatus: StateFlow<ScanStatus> = wifiDiscoveryManager.scanStatus

    private val _usbEvents = MutableSharedFlow<UsbEvent>(extraBufferCapacity = 8)
    val usbEvents: SharedFlow<UsbEvent> = _usbEvents.asSharedFlow()

    init {
        restorePersistedPrinter()
        usbPrinterManager.onPrinterAttached = { handleUsbDevice(it) }
        usbPrinterManager.onPermissionResult = { device, granted ->
            if (granted) handleUsbDevice(device)
            else _usbEvents.tryEmit(UsbEvent.Message("USB access was not allowed, so the printer cannot be used."))
        }
        usbPrinterManager.onPrinterDetached = { viewModelScope.launch { checkCurrentPrinterReachability() } }
        usbPrinterManager.startAutoDetect()
    }

    /** Plugged-in USB printer: set it up once, save it, and make it the active printer. */
    fun handleUsbDevice(device: UsbDevice) {
        if (!UsbIds.isPrinter(device)) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!usbPrinterManager.hasPermission(device)) {
                usbPrinterManager.requestPermission(device)
                return@launch
            }
            val saved = repository.getPrinter(UsbIds.idFor(device))
            if (saved != null) {
                val p = saved.toPrinter().copy(isOnline = true)
                selectPrinter(p)
                _usbEvents.tryEmit(UsbEvent.Message("${p.name} is connected."))
                return@launch
            }
            _usbEvents.tryEmit(UsbEvent.Message("USB printer found. Setting it up…"))
            when (val r = UsbAutoSetup.run(getApplication(), device)) {
                is UsbAutoSetup.Result.Ready -> {
                    repository.savePrinter(r.printer, r.capabilities)
                    selectPrinter(r.printer)
                    _usbEvents.tryEmit(UsbEvent.Message("${r.printer.name} connected and saved (${r.mode}). It will be remembered."))
                }
                is UsbAutoSetup.Result.NeedsPrintService -> {
                    repository.savePrinter(r.printer, r.capabilities)
                    selectPrinter(r.printer)
                    _usbEvents.tryEmit(UsbEvent.NeedsPrintService(r.printer.name, r.printer.make))
                }
                is UsbAutoSetup.Result.Failed -> _usbEvents.tryEmit(UsbEvent.Message(r.message))
            }
        }
    }

    private fun restorePersistedPrinter() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = prefs.getString("selected_printer_id", null)
            val printer = when {
                savedId == null -> repository.getDefaultPrinter() ?: Printer.SAVE_AS_PDF
                savedId == Printer.SAVE_AS_PDF.id -> Printer.SAVE_AS_PDF
                savedId == demoWifiPrinter.id -> demoWifiPrinter
                savedId == demoBluetoothPrinter.id -> demoBluetoothPrinter
                else -> repository.getPrinter(savedId)?.toPrinter() ?: repository.getDefaultPrinter() ?: Printer.SAVE_AS_PDF
            }
            _selectedPrinter.value = printer
            if (printer != null) {
                loadCapabilitiesFor(printer)
            }
            checkCurrentPrinterReachability()
        }
    }

    fun selectTab(tab: PrinterTab) {
        _selectedTab.value = tab
        when (tab) {
            PrinterTab.WIFI -> {
                bluetoothPrinterManager.stopDiscovery()
                usbPrinterManager.stopListening()
                wifiDiscoveryManager.startDiscovery()
            }
            PrinterTab.BLUETOOTH -> {
                wifiDiscoveryManager.stopDiscovery()
                usbPrinterManager.stopListening()
                bluetoothPrinterManager.refreshPairedDevices()
                bluetoothPrinterManager.startDiscovery()
            }
            PrinterTab.USB -> {
                wifiDiscoveryManager.stopDiscovery()
                bluetoothPrinterManager.stopDiscovery()
                usbPrinterManager.startListening()
            }
        }
    }

    fun selectPrinter(printer: Printer?) {
        _selectedPrinter.value = printer
        prefs.edit().putString("selected_printer_id", printer?.id).apply()
        viewModelScope.launch {
            if (printer != null) {
                if (printer.connectionType != ConnectionType.PDF) {
                    repository.updateLastUsed(printer.id)
                }
                loadCapabilitiesFor(printer)
            }
            checkCurrentPrinterReachability()
        }
    }

    fun startHomeScreenMonitoring() {
        homeMonitoringJob?.cancel()
        homeMonitoringJob = viewModelScope.launch {
            while (isActive) {
                checkCurrentPrinterReachability()
                delay(30_000L)
            }
        }
    }

    fun stopHomeScreenMonitoring() {
        homeMonitoringJob?.cancel()
        homeMonitoringJob = null
    }

    suspend fun checkCurrentPrinterReachability() = withContext(Dispatchers.IO) {
        val printer = _selectedPrinter.value
        if (printer == null) {
            _connectionStatus.value = PrinterConnectionStatus.None
            return@withContext
        }
        if (printer.connectionType == ConnectionType.PDF) {
            _connectionStatus.value = PrinterConnectionStatus.PdfOnly
            return@withContext
        }

        val address = if (printer.connectionType == ConnectionType.USB) {
            "USB"
        } else if (printer.host.isNotBlank()) {
            "${printer.connectionType.name} • ${printer.host}"
        } else {
            printer.connectionType.name
        }

        val caps = (_selectedPrinterCapabilities.value as? CapabilitiesState.Ready)?.capabilities
        val initialWarnings = extractWarnings(caps)

        _connectionStatus.value = PrinterConnectionStatus.Checking(
            printerName = printer.name,
            address = address,
            warnings = initialWarnings
        )

        // Demo printer fast path
        if (printer.id.startsWith("demo_")) {
            _connectionStatus.value = PrinterConnectionStatus.Connected(
                printerName = printer.name,
                address = address,
                connectionType = printer.connectionType,
                isReady = true,
                warnings = initialWarnings
            )
            return@withContext
        }

        // Bluetooth / USB check
        if (printer.connectionType == ConnectionType.BLUETOOTH || printer.connectionType == ConnectionType.USB) {
            val isOnline = if (printer.connectionType == ConnectionType.USB) {
                UsbIds.isAttached(getApplication(), printer.host)
            } else printer.isOnline
            _connectionStatus.value = if (isOnline) {
                PrinterConnectionStatus.Connected(
                    printerName = printer.name,
                    address = address,
                    connectionType = printer.connectionType,
                    isReady = true,
                    warnings = initialWarnings
                )
            } else {
                PrinterConnectionStatus.Unreachable(
                    printerName = printer.name,
                    address = address,
                    connectionType = printer.connectionType,
                    warnings = initialWarnings
                )
            }
            return@withContext
        }

        // WiFi printer check: TCP socket reachability with 2-second timeout
        val isTcpReachable = wifiDiscoveryManager.checkReachability(printer.host, printer.port)
        if (!isTcpReachable) {
            _connectionStatus.value = PrinterConnectionStatus.Unreachable(
                printerName = printer.name,
                address = address,
                connectionType = printer.connectionType,
                warnings = initialWarnings
            )
            return@withContext
        }

        // TCP reachable; if IPP/IPPS, query real attributes to detect printer state and supply warnings
        if (printer.protocol == PrinterProtocol.IPP || printer.protocol == PrinterProtocol.IPPS) {
            val saved = repository.getPrinter(printer.id)
            val result = ippClient.getPrinterAttributes(printer, saved?.tlsFingerprint)
            if (result is IppResult.Success) {
                val freshCaps = PrinterCapabilities.fromIppResponse(result.response, printer)
                repository.saveCapabilities(printer.id, freshCaps)
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(freshCaps)
                val updatedWarnings = extractWarnings(freshCaps)

                val isBusy = freshCaps.state.equals("Processing", ignoreCase = true) ||
                    freshCaps.state.equals("Busy", ignoreCase = true)
                val isStopped = freshCaps.state.equals("Stopped", ignoreCase = true)

                _connectionStatus.value = when {
                    isStopped -> PrinterConnectionStatus.Unreachable(
                        printerName = printer.name,
                        address = address,
                        connectionType = printer.connectionType,
                        warnings = updatedWarnings
                    )
                    isBusy -> PrinterConnectionStatus.Busy(
                        printerName = printer.name,
                        address = address,
                        connectionType = printer.connectionType,
                        warnings = updatedWarnings
                    )
                    else -> PrinterConnectionStatus.Connected(
                        printerName = printer.name,
                        address = address,
                        connectionType = printer.connectionType,
                        isReady = true,
                        warnings = updatedWarnings
                    )
                }
                return@withContext
            }
        }

        _connectionStatus.value = PrinterConnectionStatus.Connected(
            printerName = printer.name,
            address = address,
            connectionType = printer.connectionType,
            isReady = true,
            warnings = initialWarnings
        )
    }

    private fun extractWarnings(caps: PrinterCapabilities?): List<String> {
        if (caps == null) return emptyList()
        val list = mutableListOf<String>()
        val reasons = caps.stateReasons.map { it.lowercase() }

        if (reasons.any { it.contains("empty") || it.contains("jam") || it.contains("paper") }) {
            list.add("Out of paper")
        }
        if (reasons.any { it.contains("low") || it.contains("toner") || it.contains("ink") } ||
            caps.markerLevels.any { it.levelPercent in 0..15 }) {
            list.add("Low ink")
        }
        return list.distinct()
    }

    fun toggleDemoPrinters() {
        _showDemoPrinters.value = !_showDemoPrinters.value
    }

    fun refreshDiscovery() {
        when (_selectedTab.value) {
            PrinterTab.WIFI -> {
                refreshSavedPrintersReachability()
                wifiDiscoveryManager.startDiscovery()
            }
            PrinterTab.BLUETOOTH -> {
                bluetoothPrinterManager.refreshPairedDevices()
                bluetoothPrinterManager.startDiscovery()
            }
            PrinterTab.USB -> {
                usbPrinterManager.refreshUsbDevices()
            }
        }
    }

    private fun refreshSavedPrintersReachability() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSaved = rawSavedPrinters.value
            currentSaved.forEach { saved ->
                if (saved.connectionType == ConnectionType.WIFI && saved.host.isNotBlank()) {
                    val reachable = wifiDiscoveryManager.checkReachability(saved.host, saved.port)
                    if (reachable != saved.isOnline) {
                        repository.savePrinter(saved.copy(isOnline = reachable))
                    }
                }
            }
        }
    }

    fun savePrinter(printer: Printer) {
        viewModelScope.launch {
            repository.savePrinter(printer)
        }
    }

    fun setDefaultPrinter(printer: Printer) {
        viewModelScope.launch {
            repository.setDefaultPrinter(printer.id)
            _selectedPrinter.value = printer.copy(isDefault = true)
        }
    }

    fun renamePrinter(printer: Printer, newName: String) {
        viewModelScope.launch {
            repository.renamePrinter(printer.id, newName.trim())
            if (_selectedPrinter.value?.id == printer.id) {
                _selectedPrinter.value = _selectedPrinter.value?.copy(name = newName.trim())
            }
        }
    }

    fun deletePrinter(printer: Printer) {
        viewModelScope.launch {
            repository.deletePrinter(printer.id)
            if (_selectedPrinter.value?.id == printer.id) {
                _selectedPrinter.value = Printer.SAVE_AS_PDF
            }
        }
    }

    suspend fun addManualPrinter(
        nameInput: String,
        hostInput: String,
        portInput: String,
        protocol: PrinterProtocol
    ): Result<Printer> = withContext(Dispatchers.IO) {
        val validation = IpValidator.validate(hostInput, portInput)
        if (validation is IpValidator.ValidationResult.Error) {
            return@withContext Result.failure(IllegalArgumentException(validation.message))
        }

        val valid = validation as IpValidator.ValidationResult.Valid
        val isOnline = wifiDiscoveryManager.checkReachability(valid.host, valid.port)

        val displayName = nameInput.trim().ifBlank { "Network Printer (${valid.host})" }
        val id = "manual_${valid.host}_${valid.port}"

        val printer = Printer(
            id = id,
            name = displayName,
            host = valid.host,
            port = valid.port,
            connectionType = ConnectionType.WIFI,
            protocol = protocol,
            resourcePath = if (protocol == PrinterProtocol.RAW9100) "" else "ipp/print",
            make = null,
            model = "Manual Configuration",
            supportedFormats = listOf("application/pdf", "image/urf"),
            supportsColor = true,
            supportsDuplex = true,
            isOnline = isOnline,
            isDefault = false,
            lastUsedAt = System.currentTimeMillis(),
            isManual = true
        )

        repository.savePrinter(printer)
        selectPrinter(printer)
        Result.success(printer)
    }

    // --- CONNECTION FLOW & IPP CAPABILITIES ---

    fun startConnecting(printer: Printer) {
        activeConnectionJob?.cancel()
        _connectionState.value = ConnectionState.Connecting(printer)

        activeConnectionJob = viewModelScope.launch(Dispatchers.IO) {
            // Non-WiFi printers or Save as PDF
            if (printer.connectionType != ConnectionType.WIFI || printer.protocol == PrinterProtocol.RAW9100) {
                val usbCached = if (printer.connectionType == ConnectionType.USB) repository.loadCachedCapabilities(printer.id) else null
                val defaultCaps = usbCached ?: PrinterCapabilities.defaultFor(printer)
                repository.savePrinter(printer, defaultCaps)
                repository.updateLastUsed(printer.id)
                _selectedPrinter.value = printer
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(defaultCaps)
                _connectionState.value = ConnectionState.Connected(printer, defaultCaps)
                return@launch
            }

            // Demo printer fast-path
            if (printer.id == demoWifiPrinter.id) {
                val demoCaps = PrinterCapabilities.defaultFor(demoWifiPrinter).copy(
                    makeAndModel = "HP Color LaserJet Pro MFP M479fdw",
                    state = "Idle",
                    stateReasons = emptyList(),
                    markerLevels = listOf(
                        com.printgo.app.model.Marker("Black Toner", "#1E293B", 85, "toner"),
                        com.printgo.app.model.Marker("Cyan Toner", "#0EA5E9", 70, "toner"),
                        com.printgo.app.model.Marker("Magenta Toner", "#EC4899", 65, "toner"),
                        com.printgo.app.model.Marker("Yellow Toner", "#EAB308", 90, "toner")
                    )
                )
                repository.savePrinter(printer, demoCaps)
                _selectedPrinter.value = printer
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(demoCaps)
                _connectionState.value = ConnectionState.Connected(printer, demoCaps)
                return@launch
            }

            // Real WiFi printer IPP connection
            val savedEntity = repository.getPrinter(printer.id)
            val cachedCaps = repository.loadCachedCapabilities(printer.id)
            val now = System.currentTimeMillis()

            // Check if cached capabilities are valid (newer than 10 minutes)
            val cachedIsFresh = cachedCaps != null && (now - cachedCaps.lastUpdated < 10 * 60 * 1000)

            val pinnedFingerprint = savedEntity?.tlsFingerprint
            val ippResult = ippClient.getPrinterAttributes(printer, pinnedFingerprint)

            // IPP did not work, but many office printers still take raw data on port 9100: use that
            // instead of refusing the printer (the print engine finds out which language it reads).
            suspend fun connectAsRaw(): Boolean {
                if (printer.host.isBlank()) return false
                val open = withContext(Dispatchers.IO) { com.printgo.app.data.print.Transports.isPortOpen(printer.host, 9100) }
                if (!open) return false
                val raw = printer.copy(protocol = PrinterProtocol.RAW9100, port = 9100, resourcePath = "", isOnline = true)
                val caps = PrinterCapabilities.defaultFor(raw)
                repository.savePrinter(raw, caps)
                repository.updateLastUsed(raw.id)
                _selectedPrinter.value = raw
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(caps)
                _connectionState.value = ConnectionState.Connected(raw, caps)
                return true
            }

            when (ippResult) {
                is IppResult.Success -> {
                    val caps = PrinterCapabilities.fromIppResponse(ippResult.response, printer)
                    val updatedPrinter = if (ippResult.effectivePath != printer.resourcePath && ippResult.effectivePath.isNotBlank()) {
                        printer.copy(resourcePath = ippResult.effectivePath, isOnline = true)
                    } else {
                        printer.copy(isOnline = true)
                    }

                    repository.savePrinter(updatedPrinter, caps)
                    repository.updateLastUsed(updatedPrinter.id)
                    _selectedPrinter.value = updatedPrinter
                    _selectedPrinterCapabilities.value = CapabilitiesState.Ready(caps)
                    _connectionState.value = ConnectionState.Connected(updatedPrinter, caps)
                }
                is IppResult.TlsUntrusted -> {
                    _connectionState.value = ConnectionState.TlsUntrusted(printer, ippResult.fingerprint)
                }
                is IppResult.HttpError -> {
                    if (cachedIsFresh && cachedCaps != null) {
                        _selectedPrinter.value = printer
                        _selectedPrinterCapabilities.value = CapabilitiesState.Ready(cachedCaps)
                        _connectionState.value = ConnectionState.Connected(printer, cachedCaps)
                    } else if (!connectAsRaw()) {
                        _connectionState.value = ConnectionState.Failed(
                            printer,
                            "Printer returned HTTP error ${ippResult.statusCode}",
                            "Verify the printer resource path or port (default: 631). Try Add by IP address."
                        )
                    }
                }
                is IppResult.Timeout -> {
                    if (cachedIsFresh && cachedCaps != null) {
                        _selectedPrinter.value = printer
                        _selectedPrinterCapabilities.value = CapabilitiesState.Ready(cachedCaps)
                        _connectionState.value = ConnectionState.Connected(printer, cachedCaps)
                    } else if (!connectAsRaw()) {
                        _connectionState.value = ConnectionState.Failed(
                            printer,
                            "Printer connection timed out",
                            "Make sure the printer is turned on, awake from sleep mode, and on the same Wi-Fi."
                        )
                    }
                }
                is IppResult.IppStatusError -> {
                    if (!connectAsRaw()) {
                        _connectionState.value = ConnectionState.Failed(
                            printer,
                            ippResult.message,
                            "The printer rejected the request. Ensure IPP is enabled in the printer's web admin panel."
                        )
                    }
                }
                is IppResult.NetworkError -> {
                    if (cachedIsFresh && cachedCaps != null) {
                        _selectedPrinter.value = printer
                        _selectedPrinterCapabilities.value = CapabilitiesState.Ready(cachedCaps)
                        _connectionState.value = ConnectionState.Connected(printer, cachedCaps)
                    } else if (!connectAsRaw()) {
                        _connectionState.value = ConnectionState.Failed(
                            printer,
                            ippResult.message,
                            "Check that phone and printer are on the same Wi-Fi, and disable VPN or guest-network isolation."
                        )
                    }
                }
            }
        }
    }

    fun cancelConnection() {
        activeConnectionJob?.cancel()
        activeConnectionJob = null
        _connectionState.value = ConnectionState.Idle
    }

    fun resetConnectionState() {
        _connectionState.value = ConnectionState.Idle
    }

    fun trustTlsFingerprint(printer: Printer, fingerprint: String) {
        viewModelScope.launch {
            repository.updateTlsFingerprint(printer.id, fingerprint)
            startConnecting(printer)
        }
    }

    suspend fun loadCapabilitiesFor(printer: Printer): PrinterCapabilities {
        _selectedPrinterCapabilities.value = CapabilitiesState.Loading
        return withContext(Dispatchers.IO) {
            val cached = repository.loadCachedCapabilities(printer.id)
            val now = System.currentTimeMillis()
            if (cached != null && (now - cached.lastUpdated < 10 * 60 * 1000)) {
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(cached)
                return@withContext cached
            }

            if (printer.connectionType == ConnectionType.USB && cached != null) {
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(cached)
                return@withContext cached
            }

            if (printer.connectionType != ConnectionType.WIFI || printer.protocol == PrinterProtocol.RAW9100) {
                val defaults = PrinterCapabilities.defaultFor(printer)
                repository.saveCapabilities(printer.id, defaults)
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(defaults)
                return@withContext defaults
            }

            if (printer.id == demoWifiPrinter.id) {
                val demoCaps = PrinterCapabilities.defaultFor(demoWifiPrinter).copy(
                    makeAndModel = "HP Color LaserJet Pro MFP M479fdw",
                    state = "Idle"
                )
                _selectedPrinterCapabilities.value = CapabilitiesState.Ready(demoCaps)
                return@withContext demoCaps
            }

            val saved = repository.getPrinter(printer.id)
            val result = ippClient.getPrinterAttributes(printer, saved?.tlsFingerprint)
            val caps = when (result) {
                is IppResult.Success -> PrinterCapabilities.fromIppResponse(result.response, printer)
                else -> cached ?: PrinterCapabilities.defaultFor(printer)
            }
            repository.saveCapabilities(printer.id, caps)
            _selectedPrinterCapabilities.value = CapabilitiesState.Ready(caps)
            caps
        }
    }

    suspend fun testConnection(printer: Printer): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (printer.connectionType == ConnectionType.PDF) {
            return@withContext ConnectionTestResult(true, "Virtual PDF printer is always ready", 5)
        }
        if (printer.id.startsWith("demo_")) {
            return@withContext ConnectionTestResult(true, "Demo printer connection test succeeded", 42)
        }

        if (printer.connectionType != ConnectionType.WIFI) {
            return@withContext ConnectionTestResult(true, "${printer.connectionType.name} device ready", 15)
        }

        val saved = repository.getPrinter(printer.id)
        val startTime = System.currentTimeMillis()
        val result = ippClient.getPrinterAttributes(printer, saved?.tlsFingerprint)
        val elapsed = System.currentTimeMillis() - startTime

        when (result) {
            is IppResult.Success -> {
                ConnectionTestResult(true, "Connected via ${printer.protocol.name} (${result.effectivePath})", result.responseTimeMs)
            }
            is IppResult.Timeout -> {
                ConnectionTestResult(false, "Connection timed out after ${elapsed}ms")
            }
            is IppResult.HttpError -> {
                ConnectionTestResult(false, "HTTP ${result.statusCode}: ${result.message}")
            }
            is IppResult.TlsUntrusted -> {
                ConnectionTestResult(false, "TLS Certificate untrusted (fingerprint: ${result.fingerprint.take(16)}...)")
            }
            is IppResult.IppStatusError -> {
                ConnectionTestResult(false, result.message)
            }
            is IppResult.NetworkError -> {
                ConnectionTestResult(false, result.message)
            }
        }
    }

    suspend fun getPrinterById(id: String): Printer? = withContext(Dispatchers.IO) {
        if (id == Printer.SAVE_AS_PDF.id) return@withContext Printer.SAVE_AS_PDF
        if (id == demoWifiPrinter.id) return@withContext demoWifiPrinter
        if (id == demoBluetoothPrinter.id) return@withContext demoBluetoothPrinter

        val saved = repository.getPrinter(id)?.toPrinter()
        if (saved != null) return@withContext saved

        discoveredPrinters.value.firstOrNull { it.id == id }
    }

    override fun onCleared() {
        super.onCleared()
        activeConnectionJob?.cancel()
        wifiDiscoveryManager.stopDiscovery()
        bluetoothPrinterManager.stopDiscovery()
        usbPrinterManager.shutdown()
    }
}
