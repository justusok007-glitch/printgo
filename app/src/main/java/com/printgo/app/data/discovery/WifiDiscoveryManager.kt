package com.printgo.app.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

sealed interface ScanStatus {
    object Idle : ScanStatus
    object Scanning : ScanStatus
    object Done : ScanStatus
    data class Error(val message: String) : ScanStatus
}

class WifiDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredPrinters = MutableStateFlow<List<Printer>>(emptyList())
    val discoveredPrinters: StateFlow<List<Printer>> = _discoveredPrinters.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val printersMap = ConcurrentHashMap<String, Printer>()

    // Sequential queue for resolving NSD services without collisions
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolveJob: Job? = null
    private var autoStopJob: Job? = null

    private val serviceTypes = listOf(
        "_ipp._tcp",
        "_ipps._tcp",
        "_printer._tcp",
        "_pdl-datastream._tcp"
    )

    fun isWifiConnected(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun startDiscovery() {
        stopDiscovery()

        if (!isWifiConnected()) {
            _scanStatus.value = ScanStatus.Error("Wi-Fi is not connected. Connect to Wi-Fi to search for printers.")
            return
        }

        printersMap.clear()
        _discoveredPrinters.value = emptyList()
        _scanStatus.value = ScanStatus.Scanning

        // Acquire Multicast lock
        try {
            multicastLock = wifiManager?.createMulticastLock("PrintGoMulticastLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        startResolveProcessor()

        // Start discovery for all printer service types
        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    // Ignore non-fatal start failures for individual types
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}

                override fun onDiscoveryStarted(serviceType: String?) {}

                override fun onDiscoveryStopped(serviceType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveQueue.trySend(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val key = "${serviceInfo.serviceName}_${serviceInfo.serviceType}"
                    printersMap.remove(key)
                    publishPrinters()
                }
            }

            try {
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                activeListeners.add(listener)
            } catch (_: Exception) {}
        }

        // Auto-stop scan after 15 seconds
        autoStopJob = scope.launch {
            delay(15_000)
            if (_scanStatus.value == ScanStatus.Scanning) {
                stopDiscovery()
                _scanStatus.value = ScanStatus.Done
            }
        }
    }

    private fun startResolveProcessor() {
        resolveJob = scope.launch(Dispatchers.IO) {
            for (serviceInfo in resolveQueue) {
                if (!isActive) break
                resolveSingleService(serviceInfo)
                delay(200) // Small cooldown between resolutions to prevent mDNS stack thrashing
            }
        }
    }

    private suspend fun resolveSingleService(serviceInfo: NsdServiceInfo) {
        val result = kotlinx.coroutines.suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null, null)
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    if (continuation.isActive) continuation.resume(resolvedInfo, null)
                }
            }

            try {
                nsdManager?.resolveService(serviceInfo, listener)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null, null)
            }
        }

        if (result != null) {
            val hostAddress = result.host?.hostAddress ?: return
            val isLpd = (result.serviceType ?: "").contains("_printer")
            // An LPD (port 515) entry is not a raw-data port: use the printer's raw port 9100 when it has one.
            val port = if (isLpd && checkReachability(hostAddress, 9100, 1500)) 9100 else result.port
            val serviceName = result.serviceName ?: "Network Printer"
            val serviceType = result.serviceType ?: ""

            val protocol = when {
                serviceType.contains("ipps") -> PrinterProtocol.IPPS
                serviceType.contains("ipp") -> PrinterProtocol.IPP
                else -> PrinterProtocol.RAW9100
            }

            val attributes = TxtRecordParser.parse(result.attributes)
            val isOnline = checkReachability(hostAddress, port)

            val printer = Printer(
                id = "wifi_${hostAddress}_${port}_${serviceName.replace(" ", "_")}",
                name = serviceName,
                host = hostAddress,
                port = port,
                connectionType = ConnectionType.WIFI,
                protocol = protocol,
                resourcePath = attributes.resourcePath,
                make = attributes.make,
                model = attributes.model,
                supportedFormats = attributes.supportedFormats,
                supportsColor = attributes.supportsColor,
                supportsDuplex = attributes.supportsDuplex,
                isOnline = isOnline,
                isDefault = false,
                lastUsedAt = 0L,
                isManual = false
            )

            // De-duplicate matching host and name, keeping the best way in: IPPS > IPP > raw 9100 > LPD.
            val dedupeKey = "${hostAddress}_${serviceName.lowercase()}"
            val existing = printersMap[dedupeKey]
            fun rank(p: Printer) = when (p.protocol) {
                PrinterProtocol.IPPS -> 4
                PrinterProtocol.IPP -> 3
                else -> if (p.port == 515) 1 else 2
            }
            if (existing == null || rank(printer) > rank(existing)) {
                printersMap[dedupeKey] = printer
                publishPrinters()
            }
        }
    }

    fun stopDiscovery() {
        autoStopJob?.cancel()
        autoStopJob = null
        resolveJob?.cancel()
        resolveJob = null

        activeListeners.forEach { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
        activeListeners.clear()

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (_: Exception) {}

        if (_scanStatus.value == ScanStatus.Scanning) {
            _scanStatus.value = ScanStatus.Done
        }
    }

    private fun publishPrinters() {
        val list = printersMap.values.toList()
        _discoveredPrinters.value = list
    }

    suspend fun checkReachability(host: String, port: Int, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
