package com.printgo.app.data.print

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream
import java.io.File

/** One open USB printer: raw bulk writes, IEEE-1284 device id, and IPP-over-USB (HTTP tunnelled over bulk endpoints). */
class UsbSession private constructor(
    private val conn: UsbDeviceConnection,
    private val device: UsbDevice,
    private val iface: UsbInterface,
    private val out: UsbEndpoint,
    private val inp: UsbEndpoint?,
    val isIppUsb: Boolean
) : AutoCloseable {

    companion object {
        fun open(context: Context, hostUri: String, preferIppUsb: Boolean = true): UsbSession {
            val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = UsbIds.find(context, hostUri)
                ?: throw TransportException("The USB printer is not plugged in. Connect the cable and try again.")
            if (!usb.hasPermission(device)) throw TransportException("Allow USB access for the printer, then try again.")

            val ifaces = (0 until device.interfaceCount).map { device.getInterface(it) }
                .filter { it.interfaceClass == UsbConstants.USB_CLASS_PRINTER }
            if (ifaces.isEmpty()) throw TransportException("This USB device does not look like a printer.")

            fun eps(i: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint?> {
                var o: UsbEndpoint? = null; var n: UsbEndpoint? = null
                for (k in 0 until i.endpointCount) {
                    val e = i.getEndpoint(k)
                    if (e.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (e.direction == UsbConstants.USB_DIR_OUT) { if (o == null) o = e } else { if (n == null) n = e }
                }
                return o to n
            }

            val ippUsb = if (preferIppUsb) ifaces.firstOrNull { it.interfaceSubclass == 1 && it.interfaceProtocol == 4 && eps(it).let { p -> p.first != null && p.second != null } } else null
            val chosen = ippUsb ?: ifaces.firstOrNull { it.interfaceProtocol != 4 && eps(it).first != null }
                ?: throw TransportException("The USB printer has no data endpoint.")
            val (o, n) = eps(chosen)
            val conn = usb.openDevice(device) ?: throw TransportException("Could not open the USB printer.")
            if (!conn.claimInterface(chosen, true)) {
                conn.close()
                throw TransportException("The USB printer is in use by another app.")
            }
            try { conn.setInterface(chosen) } catch (_: Exception) { }
            return UsbSession(conn, device, chosen, o!!, n, ippUsb != null)
        }
    }

    /** IEEE 1284 device id string (contains CMD:PCL,POSTSCRIPT,PDF… on most printers), or null. */
    fun deviceId(): String? {
        return try {
            val buf = ByteArray(1024)
            val r = conn.controlTransfer(
                0xA1, 0, 0, (iface.id shl 8) or iface.alternateSetting, buf, buf.size, 3000
            )
            if (r < 3) null else {
                val len = (((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)).coerceIn(2, r)
                String(buf, 2, (len - 2).coerceAtLeast(0), Charsets.ISO_8859_1)
            }
        } catch (e: Exception) { null }
    }

    fun write(data: ByteArray, off: Int = 0, len: Int = data.size - off) {
        var p = off
        val end = off + len
        while (p < end) {
            val n = minOf(16 * 1024, end - p)
            val chunk = if (p == 0 && n == data.size) data else data.copyOfRange(p, p + n)
            val r = conn.bulkTransfer(out, chunk, n, 15000)
            if (r < 0) throw TransportException("The USB printer stopped answering.")
            p += n
        }
    }

    fun writeAll(chunks: Sequence<ByteArray>) { for (c in chunks) write(c) }

    /** Sends one IPP request (header bytes + optional file) through the IPP-USB tunnel; returns the IPP response body. */
    fun ippRequest(ippHeader: ByteArray, file: File? = null, onProgress: (Float) -> Unit = {}): ByteArray {
        val inEp = inp ?: throw TransportException("The USB printer cannot send answers back.")
        val total = ippHeader.size + (file?.length() ?: 0L)
        val http = ("POST /ipp/print HTTP/1.1\r\nHost: localhost:631\r\nUser-Agent: PrintGo\r\n" +
            "Content-Type: application/ipp\r\nContent-Length: $total\r\nConnection: keep-alive\r\n\r\n").toByteArray()
        write(http)
        write(ippHeader)
        file?.inputStream()?.use { input ->
            val buf = ByteArray(16 * 1024)
            var sent = ippHeader.size.toLong()
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                write(buf, 0, n)
                sent += n
                onProgress(sent.toFloat() / total)
            }
        }
        return readHttpBody(inEp)
    }

    private fun readHttpBody(inEp: UsbEndpoint): ByteArray {
        val acc = ByteArrayOutputStream()
        val buf = ByteArray(inEp.maxPacketSize.coerceAtLeast(512) * 8)
        fun more(timeout: Int): Boolean {
            val r = conn.bulkTransfer(inEp, buf, buf.size, timeout)
            if (r > 0) acc.write(buf, 0, r)
            return r > 0
        }
        val deadline = System.currentTimeMillis() + 120_000
        var headerEnd = -1
        while (headerEnd < 0) {
            if (System.currentTimeMillis() > deadline) throw TransportException("The printer did not answer in time.")
            more(5000)
            headerEnd = indexOf(acc.toByteArray(), "\r\n\r\n".toByteArray())
        }
        val all0 = acc.toByteArray()
        val head = String(all0, 0, headerEnd, Charsets.ISO_8859_1)
        val status = head.lineSequence().first().split(' ').getOrNull(1)?.toIntOrNull() ?: 0
        if (status !in 200..299) throw TransportException("The printer answered with HTTP $status over USB.")
        val bodyStart = headerEnd + 4
        val chunked = head.contains("transfer-encoding: chunked", ignoreCase = true)
        val clen = Regex("content-length:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)?.toIntOrNull()
        if (!chunked && clen != null) {
            while (acc.size() - bodyStart < clen) {
                if (System.currentTimeMillis() > deadline) throw TransportException("The printer did not answer in time.")
                more(5000)
            }
            return acc.toByteArray().copyOfRange(bodyStart, bodyStart + clen)
        }
        // chunked
        while (true) {
            if (System.currentTimeMillis() > deadline) throw TransportException("The printer did not answer in time.")
            val dec = decodeChunked(acc.toByteArray(), bodyStart)
            if (dec != null) return dec
            more(5000)
        }
    }

    private fun decodeChunked(all: ByteArray, start: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        var p = start
        while (true) {
            val eol = indexOf(all, "\r\n".toByteArray(), p)
            if (eol < 0) return null
            val size = String(all, p, eol - p, Charsets.ISO_8859_1).substringBefore(';').trim().toIntOrNull(16) ?: return null
            p = eol + 2
            if (size == 0) return out.toByteArray()
            if (all.size < p + size + 2) return null
            out.write(all, p, size)
            p += size + 2
        }
    }

    private fun indexOf(h: ByteArray, n: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..h.size - n.size) {
            for (j in n.indices) if (h[i + j] != n[j]) continue@outer
            return i
        }
        return -1
    }

    override fun close() {
        try { conn.releaseInterface(iface) } catch (_: Exception) { }
        try { conn.close() } catch (_: Exception) { }
    }
}
