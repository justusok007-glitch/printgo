package com.printgo.app.data.print

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.printgo.app.data.ipp.IppDecoder
import com.printgo.app.data.ipp.IppEncoder
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time setup when a USB printer is plugged in: identify it, work out how to talk to it and
 * what it can do, so it can be saved and used without asking again. Android printing needs no
 * downloaded driver; this "profile" is the equivalent and is stored with the saved printer.
 */
object UsbAutoSetup {

    sealed interface Result {
        data class Ready(val printer: Printer, val capabilities: PrinterCapabilities, val mode: String) : Result
        /** Printer identified, but it only speaks a vendor-specific language PrintGo cannot generate. */
        data class NeedsPrintService(val printer: Printer, val capabilities: PrinterCapabilities) : Result
        data class Failed(val message: String) : Result
    }

    fun parseDeviceId(id: String?): Map<String, String> {
        if (id == null) return emptyMap()
        return id.split(';').mapNotNull {
            val i = it.indexOf(':')
            if (i <= 0) null else it.substring(0, i).trim().uppercase() to it.substring(i + 1).trim()
        }.toMap()
    }

    suspend fun run(context: Context, device: UsbDevice): Result = withContext(Dispatchers.IO) {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usb.hasPermission(device)) return@withContext Result.Failed("USB access for the printer was not allowed.")
        val host = UsbIds.hostFor(device)
        val session = try { UsbSession.open(context, host) } catch (e: TransportException) {
            return@withContext Result.Failed(e.message ?: "Could not open the USB printer.")
        }
        session.use { s ->
            val id = s.deviceId() ?: s.namesAsDeviceId()
            val f = parseDeviceId(id)
            val maker = f["MFG"] ?: f["MANUFACTURER"] ?: UsbIds.safeName(device) { it.manufacturerName }
            val model = f["MDL"] ?: f["MODEL"] ?: UsbIds.safeName(device) { it.productName }
            val name = listOfNotNull(maker, model).joinToString(" ").ifBlank { "USB Printer" }
            val base = Printer(
                id = UsbIds.idFor(device), name = name, host = host, port = 0,
                connectionType = ConnectionType.USB, protocol = PrinterProtocol.NONE, resourcePath = "",
                make = maker, model = model, supportedFormats = listOf("application/pdf"),
                supportsColor = true, supportsDuplex = false, isOnline = true, isManual = false
            )

            if (s.isIppUsb) {
                try {
                    val req = IppEncoder.encode(
                        IppEncoder.createGetPrinterAttributesRequest("ipp://localhost/ipp/print", 1, listOf("all", "media-col-database"))
                    )
                    val resp = IppDecoder.decode(s.ippRequest(req))
                    val caps = PrinterCapabilities.fromIppResponse(resp, base)
                    val printer = base.copy(
                        supportedFormats = caps.documentFormatsSupported,
                        supportsColor = caps.colorSupported,
                        supportsDuplex = caps.sidesSupported.any { it.startsWith("two-sided") }
                    )
                    return@withContext Result.Ready(printer, caps, "USB • IPP")
                } catch (e: Exception) {
                    // fall through to the raw language route
                }
            }

            val lang = LanguagePicker.pick(id, name, "AUTO", strict = true)
            if (lang == null) {
                val caps = PrinterCapabilities.defaultFor(base).copy(makeAndModel = name)
                return@withContext Result.NeedsPrintService(base, caps)
            }
            val mode = lang
            val mono = mode == RawLanguage.PCL || mode == RawLanguage.ESCPOS
            val printer = base.copy(supportsColor = !mono)
            val caps = PrinterCapabilities.defaultFor(printer).copy(
                makeAndModel = name,
                colorSupported = !mono,
                colorModesSupported = if (mono) listOf("monochrome") else listOf("color", "monochrome"),
                documentFormatsSupported = listOf("application/pdf")
            )
            Result.Ready(printer, caps, "USB • ${mode.label}")
        }
    }
}
