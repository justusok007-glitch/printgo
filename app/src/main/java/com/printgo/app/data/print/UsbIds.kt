package com.printgo.app.data.print

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Android hands out a new numeric device id every time a cable is plugged in, so printers are
 * remembered by vendor/product id instead ("usb://04f9:0042").
 */
object UsbIds {
    fun hostFor(d: UsbDevice) = "usb://%04x:%04x".format(d.vendorId, d.productId)
    fun idFor(d: UsbDevice) = "usb_%04x_%04x".format(d.vendorId, d.productId)

    fun find(context: Context, host: String): UsbDevice? {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        val key = host.removePrefix("usb://")
        val m = Regex("^([0-9a-fA-F]{4}):([0-9a-fA-F]{4})$").find(key)
        return if (m != null) {
            val vid = m.groupValues[1].toInt(16)
            val pid = m.groupValues[2].toInt(16)
            usb.deviceList.values.firstOrNull { it.vendorId == vid && it.productId == pid && isPrinter(it) }
        } else {
            val id = key.toIntOrNull() ?: return null // legacy "usb://<deviceId>"
            usb.deviceList.values.firstOrNull { it.deviceId == id }
        }
    }

    fun isPrinter(d: UsbDevice): Boolean {
        if (d.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true
        for (i in 0 until d.interfaceCount) if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
        return false
    }

    fun isAttached(context: Context, host: String): Boolean {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        val d = find(context, host) ?: return false
        return usb.hasPermission(d)
    }

    fun safeName(d: UsbDevice, what: (UsbDevice) -> String?): String? =
        try { what(d)?.takeIf { it.isNotBlank() } } catch (e: SecurityException) { null }
}
