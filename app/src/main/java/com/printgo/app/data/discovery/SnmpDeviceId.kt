package com.printgo.app.data.discovery

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Reads a network printer's IEEE-1284 device id ("MFG:HP;MDL:LaserJet P3010;CMD:PJL,PCL,POSTSCRIPT;")
 * over SNMP v1 (UDP 161, community "public"), the same way CUPS does. It tells us which language a
 * raw port-9100 printer reads, so nothing has to be guessed from the name.
 */
object SnmpDeviceId {

    /** Printer-Port-Monitor MIB ppmPrinterIEEE1284DeviceId.1, then HP's own device-id object. */
    private val OIDS = listOf(
        intArrayOf(1, 3, 6, 1, 4, 1, 2699, 1, 2, 1, 2, 1, 1, 3, 1),
        intArrayOf(1, 3, 6, 1, 4, 1, 11, 2, 3, 9, 1, 1, 7, 0)
    )

    fun query(host: String, timeoutMs: Int = 1200, community: String = "public"): String? {
        return try {
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                val addr = InetAddress.getByName(host)
                for ((i, oid) in OIDS.withIndex()) {
                    val req = buildGet(oid, community, 0x5047 + i)
                    var answered = false
                    var attempt = 0
                    while (!answered && attempt < 2) {
                        attempt++
                        try {
                            sock.send(DatagramPacket(req, req.size, addr, 161))
                            val buf = ByteArray(4096)
                            val pkt = DatagramPacket(buf, buf.size)
                            sock.receive(pkt)
                            answered = true
                            val id = parseResponse(buf.copyOf(pkt.length))?.let { cleanDeviceId(it) }
                            if (!id.isNullOrBlank()) return id
                        } catch (_: java.net.SocketTimeoutException) {
                        } catch (_: IllegalStateException) {
                            answered = true
                        } catch (_: IndexOutOfBoundsException) {
                            answered = true
                        }
                    }
                    // Nobody answers SNMP at all: do not wait for the second object as well.
                    if (!answered) return null
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Some printers put two length bytes or other junk in front of the text; keep from the first key on. */
    fun cleanDeviceId(raw: ByteArray): String? {
        val s = String(raw, Charsets.ISO_8859_1)
        val keys = listOf("MFG:", "MANUFACTURER:", "CMD:", "COMMAND SET:", "MDL:", "MODEL:")
        val start = keys.mapNotNull { k -> s.indexOf(k, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: return null
        return s.substring(start).trim { it <= ' ' }
    }

    // ---------- tiny BER encoder / decoder ----------

    internal fun buildGet(oid: IntArray, community: String, requestId: Int): ByteArray {
        val varbind = tlv(0x30, tlv(0x06, encodeOid(oid)) + byteArrayOf(0x05, 0x00))
        val pdu = tlv(0xA0, encInt(requestId) + encInt(0) + encInt(0) + tlv(0x30, varbind))
        return tlv(0x30, encInt(0) + tlv(0x04, community.toByteArray(Charsets.US_ASCII)) + pdu)
    }

    private fun encInt(v: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        var started = false
        for (shift in 24 downTo 0 step 8) {
            val b = (v shr shift) and 0xFF
            if (!started && shift > 0 && b == 0) continue
            if (!started && b and 0x80 != 0) bytes.write(0)
            started = true
            bytes.write(b)
        }
        return tlv(0x02, bytes.toByteArray())
    }

    internal fun encodeOid(oid: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(oid[0] * 40 + oid[1])
        for (i in 2 until oid.size) {
            val v = oid[i]
            val parts = ArrayList<Int>()
            var x = v
            parts.add(x and 0x7F)
            x = x ushr 7
            while (x > 0) { parts.add((x and 0x7F) or 0x80); x = x ushr 7 }
            for (p in parts.reversed()) out.write(p)
        }
        return out.toByteArray()
    }

    private fun tlv(tag: Int, value: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = value.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            else -> { out.write(0x82); out.write(n shr 8); out.write(n and 0xFF) }
        }
        out.write(value)
        return out.toByteArray()
    }

    /** Returns the OCTET STRING value of the first variable binding, or null on any error status. */
    internal fun parseResponse(data: ByteArray): ByteArray? {
        val r = Reader(data)
        r.enter(0x30)            // message
        r.skip()                 // version
        r.skip()                 // community
        val pduTag = r.peekTag()
        if (pduTag != 0xA2) return null
        r.enter(0xA2)
        r.skip()                 // request id
        val err = r.readInt()
        r.skip()                 // error index
        if (err != 0) return null
        r.enter(0x30)            // varbind list
        r.enter(0x30)            // varbind
        r.skip()                 // name
        if (r.peekTag() != 0x04) return null
        return r.readValue()
    }

    private class Reader(val d: ByteArray) {
        var p = 0
        fun peekTag() = d[p].toInt() and 0xFF
        private fun header(): Int {
            p++ // tag
            var len = d[p++].toInt() and 0xFF
            if (len and 0x80 != 0) {
                val n = len and 0x7F
                len = 0
                repeat(n) { len = (len shl 8) or (d[p++].toInt() and 0xFF) }
            }
            return len
        }
        fun enter(tag: Int) { if (peekTag() != tag) throw IllegalStateException("tag"); header() }
        fun skip() { val n = header(); p += n }
        fun readValue(): ByteArray { val n = header(); val v = d.copyOfRange(p, p + n); p += n; return v }
        fun readInt(): Int { val v = readValue(); var x = 0; for (b in v) x = (x shl 8) or (b.toInt() and 0xFF); return x }
    }
}
