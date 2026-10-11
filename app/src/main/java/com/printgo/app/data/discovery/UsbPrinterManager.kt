package com.printgo.app.data.discovery

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.printgo.app.data.print.UsbIds
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

    /** Called when a USB printer is plugged in while the app runs. */
    var onPrinterAttached: ((UsbDevice) -> Unit)? = null
    /** Called after the user answers the permission dialog. */
    var onPermissionResult: ((UsbDevice, Boolean) -> Unit)? = null
    /** Called when a printer is unplugged. */
    var onPrinterDetached: (() -> Unit)? = null

    private var keepAlive = false

    private fun deviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshUsbDevices()
                    deviceExtra(intent)?.takeIf { UsbIds.isPrinter(it) }?.let { onPrinterAttached?.invoke(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbDevices()
                    onPrinterDetached?.invoke()
                }
                actionUsbPermission -> {
                    refreshUsbDevices()
                    val d = deviceExtra(intent)
                    if (d != null) onPermissionResult?.invoke(d, intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
        }
    }

    private var receiverRegistered = false

    /** Always-on detection for the lifetime of the app (never stopped by screens). */
    fun startAutoDetect() {
        keepAlive = true
        startListening()
    }

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
        if (keepAlive) return
        shutdown()
    }

    fun shutdown() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        keepAlive = false
    }

    fun hasPermission(device: UsbDevice) = usbManager?.hasPermission(device) == true

    fun refreshUsbDevices() {
        val manager = usbManager ?: return
        val printers = manager.deviceList.values.filter { UsbIds.isPrinter(it) }.map { device ->
            val hasPerm = manager.hasPermission(device)
            val product = UsbIds.safeName(device) { it.productName }
            val maker = UsbIds.safeName(device) { it.manufacturerName }
            Printer(
                id = UsbIds.idFor(device),
                name = listOfNotNull(maker, product).joinToString(" ").ifBlank { "USB Printer" },
                host = UsbIds.hostFor(device),
                port = 0,
                connectionType = ConnectionType.USB,
                protocol = PrinterProtocol.NONE,
                resourcePath = "",
                make = maker,
                model = product ?: "USB Printer",
                supportedFormats = listOf("application/pdf"),
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
        val device = UsbIds.find(context, printer.host) ?: return
        requestPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(actionUsbPermission).setPackage(context.packageName)
        manager.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }
}
