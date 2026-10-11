package com.printgo.app.data.discovery

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsbPrinterManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _attachedPrinters = MutableStateFlow<List<Printer>>(emptyList())
    val attachedPrinters: StateFlow<List<Printer>> = _attachedPrinters.asStateFlow()

    private val actionUsbPermission = "com.printgo.app.USB_PERMISSION"

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                actionUsbPermission -> {
                    refreshUsbDevices()
                }
            }
        }
    }

    private var receiverRegistered = false

    fun startListening() {
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(actionUsbPermission)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            receiverRegistered = true
        }
        refreshUsbDevices()
    }

    fun stopListening() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    fun refreshUsbDevices() {
        val manager = usbManager ?: return
        val deviceList = manager.deviceList.values

        val printerDevices = deviceList.filter { isUsbPrinter(it) }

        val printers = printerDevices.map { device ->
            val hasPerm = manager.hasPermission(device)
            val name = (device.productName ?: device.deviceName).ifBlank { "USB Printer" }
            Printer(
                id = "usb_${device.deviceId}_${device.vendorId}_${device.productId}",
                name = name,
                host = "usb://${device.deviceId}",
                port = 0,
                connectionType = ConnectionType.USB,
                protocol = PrinterProtocol.RAW9100,
                resourcePath = "",
                make = device.manufacturerName,
                model = device.productName ?: "USB Printer (Class 7)",
                supportedFormats = listOf("application/pdf", "image/jpeg"),
                supportsColor = true,
                supportsDuplex = false,
                isOnline = hasPerm,
                isDefault = false,
                lastUsedAt = 0L,
                isManual = false
            )
        }

        _attachedPrinters.value = printers
    }

    fun requestUsbPermission(printer: Printer) {
        val manager = usbManager ?: return
        val device = manager.deviceList.values.firstOrNull {
            "usb_${it.deviceId}_${it.vendorId}_${it.productId}" == printer.id
        } ?: return

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(actionUsbPermission),
            flags
        )
        manager.requestPermission(device, permissionIntent)
    }

    private fun isUsbPrinter(device: UsbDevice): Boolean {
        // Direct device class 7 (Printer)
        if (device.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true

        // Or any interface is class 7
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) {
                return true
            }
        }
        return false
    }
}
