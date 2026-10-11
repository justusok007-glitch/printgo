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

    /** True when [port] on [host] accepts a TCP connection within [timeoutMs]. */
    fun isPortOpen(host: String, port: Int, timeoutMs: Int = 1500): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }; true
    } catch (e: Exception) { false }

    fun sendRaw9100(host: String, port: Int, file: File, onProgress: (Float) -> Unit) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, if (port > 0) port else 9100), 6000)
                s.soTimeout = 15000
                val total = file.length().coerceAtLeast(1)
                var sent = 0L
                val out = s.getOutputStream()
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
                // Close gracefully: half-close, then let the printer finish reading before the socket goes away,
                // so the end of the job is never cut off by a reset.
                s.shutdownOutput()
                drain(s, 5000)
            }
        } catch (e: IOException) {
            throw TransportException("Could not reach the printer at $host. Check that it is on and on the same Wi-Fi.")
        }
    }

    private fun drain(s: Socket, timeoutMs: Int) {
        try {
            s.soTimeout = timeoutMs
            val input = s.getInputStream()
            val buf = ByteArray(1024)
            val until = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < until) { if (input.read(buf) < 0) break }
        } catch (_: IOException) { }
    }

    /**
     * Sends [file] as a binary job over LPD (RFC 1179, port 515). Used only when a printer offers LPD but
     * not raw port 9100. [queue] "raw" passes the data through untouched on HP JetDirect and most others.
     */
    fun sendLpd(host: String, port: Int, queue: String, jobName: String, file: File, onProgress: (Float) -> Unit) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, if (port > 0) port else 515), 6000)
                s.soTimeout = 30000
                val out = java.io.BufferedOutputStream(s.getOutputStream(), 32 * 1024)
                val input = s.getInputStream()
                fun ack(step: String) {
                    out.flush()
                    val b = input.read()
                    if (b != 0) throw TransportException("The printer refused the job ($step). Try again or use \"Print with system dialog\".")
                }
                val num = (System.currentTimeMillis() % 1000).toString().padStart(3, '0')
                val host8 = "printgo"
                val df = "dfA$num$host8"
                val cf = "cfA$num$host8"
                val title = jobName.filter { it.code in 32..126 }.take(60).ifBlank { "PrintGo" }

                out.write(byteArrayOf(2)); out.write("$queue\n".toByteArray(Charsets.US_ASCII)); ack("queue")

                val total = file.length().coerceAtLeast(1)
                out.write(byteArrayOf(3)); out.write("${file.length()} $df\n".toByteArray(Charsets.US_ASCII)); ack("data")
                var sent = 0L
                file.inputStream().use { inp ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        onProgress(sent.toFloat() / total)
                    }
                }
                out.write(0); ack("data end")

                val control = "H$host8\nPprintgo\nJ$title\nl$df\nU$df\nN$title\n".toByteArray(Charsets.US_ASCII)
                out.write(byteArrayOf(2)); out.write("${control.size} $cf\n".toByteArray(Charsets.US_ASCII)); ack("control")
                out.write(control); out.write(0); ack("control end")
            }
        } catch (e: TransportException) {
            throw e
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
