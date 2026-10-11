package com.printgo.app.data.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.ConcurrentHashMap

class BluetoothPrinterManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _pairedPrinters = MutableStateFlow<List<Printer>>(emptyList())
    val pairedPrinters: StateFlow<List<Printer>> = _pairedPrinters.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<Printer>>(emptyList())
    val discoveredPrinters: StateFlow<List<Printer>> = _discoveredPrinters.asStateFlow()

    val allPrinters: Flow<List<Printer>> = combine(_pairedPrinters, _discoveredPrinters) { paired, disc ->
        (paired + disc).distinctBy { it.id }
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredMap = ConcurrentHashMap<String, Printer>()

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        val name = device.name ?: device.address ?: "Bluetooth Device"
                        val isPrinterClass = isPrinterDeviceClass(device)
                        val printer = Printer(
                            id = "bt_${device.address}",
                            name = name,
                            host = device.address,
                            port = 0,
                            connectionType = ConnectionType.BLUETOOTH,
                            protocol = PrinterProtocol.NONE,
                            resourcePath = "",
                            make = null,
                            model = if (isPrinterClass) "Bluetooth Printer" else "Bluetooth Device",
                            supportedFormats = listOf("application/pdf", "image/jpeg"),
                            supportsColor = true,
                            supportsDuplex = false,
                            isOnline = true,
                            isDefault = false,
                            lastUsedAt = 0L,
                            isManual = false
                        )
                        discoveredMap[device.address] = printer
                        _discoveredPrinters.value = discoveredMap.values.toList()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    private var receiverRegistered = false

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!isBluetoothEnabled()) {
            _pairedPrinters.value = emptyList()
            return
        }

        try {
            val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
            val list = bonded.map { device ->
                val name = device.name ?: device.address ?: "Bluetooth Device"
                val isPrinterClass = isPrinterDeviceClass(device)
                Printer(
                    id = "bt_${device.address}",
                    name = name,
                    host = device.address,
                    port = 0,
                    connectionType = ConnectionType.BLUETOOTH,
                    protocol = PrinterProtocol.NONE,
                    resourcePath = "",
                    make = null,
                    model = if (isPrinterClass) "Paired Printer" else "Paired Device",
                    supportedFormats = listOf("application/pdf", "image/jpeg"),
                    supportsColor = true,
                    supportsDuplex = false,
                    isOnline = true,
                    isDefault = false,
                    lastUsedAt = 0L,
                    isManual = false
                )
            }
            _pairedPrinters.value = list
        } catch (_: Exception) {
            _pairedPrinters.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!isBluetoothEnabled()) return

        stopDiscovery()
        discoveredMap.clear()
        _discoveredPrinters.value = emptyList()

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(receiver, filter)
            receiverRegistered = true

            bluetoothAdapter?.startDiscovery()
            _isScanning.value = true
        } catch (_: Exception) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
            if (receiverRegistered) {
                context.unregisterReceiver(receiver)
                receiverRegistered = false
            }
        } catch (_: Exception) {}
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    private fun isPrinterDeviceClass(device: BluetoothDevice): Boolean {
        val btClass = device.bluetoothClass ?: return false
        val major = btClass.majorDeviceClass
        // 0x0600 is IMAGING (printers, scanners, cameras)
        return major == BluetoothClass.Device.Major.IMAGING ||
                major == BluetoothClass.Device.Major.PERIPHERAL ||
                (device.name?.contains("print", ignoreCase = true) == true)
    }
}
