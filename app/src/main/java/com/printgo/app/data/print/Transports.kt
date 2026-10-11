package com.printgo.app.data.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

class TransportException(message: String) : IOException(message)

/** Low-level byte transports used for printers that do not speak IPP. */
object Transports {

    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun sendRaw9100(host: String, port: Int, file: File, onProgress: (Float) -> Unit) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, if (port > 0) port else 9100), 6000)
                s.soTimeout = 15000
                val total = file.length().coerceAtLeast(1)
                var sent = 0L
                s.getOutputStream().use { out ->
                    file.inputStream().use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sent += n
                            onProgress(sent.toFloat() / total)
                        }
                    }
                    out.flush()
                }
            }
        } catch (e: IOException) {
            throw TransportException("Could not reach the printer at $host. Check that it is on and on the same Wi-Fi.")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendBluetooth(context: Context, address: String, chunks: Sequence<ByteArray>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) throw TransportException("Allow Bluetooth access for PrintGo in Settings, then try again.")
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw TransportException("This phone has no Bluetooth.")
        if (!adapter.isEnabled) throw TransportException("Turn Bluetooth on and try again.")
        val device = try { adapter.getRemoteDevice(address) } catch (e: IllegalArgumentException) {
            throw TransportException("The Bluetooth printer address is not valid.")
        }
        try {
            adapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(SPP)
            socket.use { s ->
                s.connect()
                val out = s.outputStream
                for (c in chunks) {
                    var off = 0
                    while (off < c.size) {
                        val n = minOf(512, c.size - off)
                        out.write(c, off, n)
                        off += n
                        Thread.sleep(8) // thermal printers have tiny buffers
                    }
                }
                out.flush()
                Thread.sleep(400)
            }
        } catch (e: SecurityException) {
            throw TransportException("Allow Bluetooth access for PrintGo in Settings, then try again.")
        } catch (e: IOException) {
            throw TransportException("Could not connect to the Bluetooth printer. Make sure it is on, paired and close to the phone.")
        }
    }

    fun sendUsb(context: Context, hostUri: String, chunks: Sequence<ByteArray>) {
        val id = hostUri.removePrefix("usb://").toIntOrNull()
            ?: throw TransportException("The USB printer is not recognised. Unplug and reconnect it.")
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usb.deviceList.values.firstOrNull { it.deviceId == id }
            ?: throw TransportException("The USB printer is not connected.")
        if (!usb.hasPermission(device)) throw TransportException("Allow USB access for the printer, then try again.")
        var iface = (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_PRINTER
        } ?: throw TransportException("This USB device does not look like a printer.")
        val ep = (0 until iface.endpointCount).map { iface.getEndpoint(it) }.firstOrNull {
            it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT
        } ?: throw TransportException("The USB printer has no data endpoint.")
        val conn = usb.openDevice(device) ?: throw TransportException("Could not open the USB printer.")
        try {
            if (!conn.claimInterface(iface, true)) throw TransportException("The USB printer is in use by another app.")
            for (c in chunks) {
                var off = 0
                while (off < c.size) {
                    val n = minOf(16 * 1024, c.size - off)
                    val r = conn.bulkTransfer(ep, c.copyOfRange(off, off + n), n, 10000)
                    if (r < 0) throw TransportException("The USB printer stopped answering.")
                    off += n
                }
            }
        } finally {
            conn.releaseInterface(iface)
            conn.close()
        }
    }
}
