package com.printgo.app.data.ipp

import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Options mapped to IPP job attributes. */
data class IppJobOptions(
    val jobName: String,
    val copies: Int = 1,
    val duplex: DuplexMode = DuplexMode.ONE_SIDED,
    val colorMode: PrintColorMode = PrintColorMode.COLOR,
    val quality: PrintQuality = PrintQuality.NORMAL,
    val mediaKeyword: String? = null,
    val collate: Boolean = true,
    val supportedSides: List<String> = emptyList(),
    val supportsColorModes: Boolean = true,
    /** "none", "fit", "fill", "auto" or null to leave it to the printer. */
    val printScaling: String? = "none",
    /** What the printer said it supports. When known, only options it lists are sent. */
    val probe: IppProbe? = null,
    /** Retry mode for strict printers: send nothing but the document format (and copies). */
    val minimal: Boolean = false
)

sealed interface IppSendResult {
    data class Accepted(val jobId: Int, val jobUri: String?, val statusCode: Int) : IppSendResult
    data class Failed(val message: String, val statusCode: Int = -1, val retryable: Boolean = true) : IppSendResult
    data class TlsUntrusted(val fingerprint: String) : IppSendResult
}

data class IppJobStatus(val state: Int, val reasons: List<String>, val impressionsCompleted: Int?)

/** Sends documents with Print-Job and follows them with Get-Job-Attributes / Cancel-Job. */
class IppPrintClient(private val base: IppClient = IppClient()) {

    private val ippMedia = "application/ipp".toMediaType()

    companion object {
        const val OP_PRINT_JOB: Short = 0x0002
        const val OP_CANCEL_JOB: Short = 0x0008
        const val OP_GET_JOB_ATTRIBUTES: Short = 0x0009

        const val JOB_PENDING = 3
        const val JOB_HELD = 4
        const val JOB_PROCESSING = 5
        const val JOB_STOPPED = 6
        const val JOB_CANCELED = 7
        const val JOB_ABORTED = 8
        const val JOB_COMPLETED = 9

        /** [IppSendResult.Failed.statusCode] when no connection could be made at all. */
        const val NO_CONNECTION = -1
        /** [IppSendResult.Failed.statusCode] when the connection broke during the upload. */
        const val TRANSFER_BROKEN = -2

        /** IPP answers that mean "not like this" rather than "not at all": try fewer options or another format. */
        fun isFormatOrOptionRejection(code: Int) = code == 0x040A || code == 0x040B || code == 0x0400 || code == TRANSFER_BROKEN

        fun statusText(code: Int): String = when (code) {
            0x0400 -> "The printer rejected the request."
            0x0402 -> "The printer refused the job."
            0x0401 -> "The printer needs a password for this job."
            0x0403 -> "The printer refused access."
            0x0404, 0x0406 -> "The printer address looks wrong. Try re-adding the printer."
            0x040A -> "The printer does not accept this file type."
            0x040B -> "The printer does not accept this file type."
            0x040C -> "The file is too large for the printer."
            0x0501 -> "The printer is not able to print right now."
            0x0502 -> "The printer is busy. Try again in a moment."
            0x0503 -> "The printer does not understand this version of the printing protocol."
            0x0505 -> "The printer is not accepting jobs."
            0x0507 -> "The printer's job queue is full."
            else -> "Printer returned status 0x${Integer.toHexString(code)}."
        }

        /** Human friendly text for printer-state-reasons / job-state-reasons. */
        fun friendlyReason(reason: String): String? {
            val r = reason.lowercase().substringBefore("-error").substringBefore("-warning").substringBefore("-report")
            return when {
                r.startsWith("media-empty") || r.startsWith("media-needed") -> "The printer is out of paper."
                r.startsWith("media-jam") || r == "jam" -> "There is a paper jam."
                r.startsWith("toner-empty") || r.startsWith("marker-supply-empty") -> "The printer is out of ink or toner."
                r.startsWith("toner-low") || r.startsWith("marker-supply-low") -> null
                r.startsWith("cover-open") || r.startsWith("door-open") -> "A cover or door on the printer is open."
                r.startsWith("offline") || r.startsWith("shutdown") -> "The printer is offline."
                r.startsWith("input-tray-missing") -> "A paper tray is missing."
                r.startsWith("output-area-full") -> "The output tray is full."
                r.startsWith("job-canceled") -> "The job was cancelled."
                r.startsWith("document-format-error") -> "The printer could not read the file."
                r.startsWith("document-unprintable") -> "The printer could not print this file."
                else -> null
            }
        }
    }

    /** Live Get-Printer-Attributes: which formats the printer accepts right now and where to send them. */
    suspend fun probe(printer: Printer, pinned: String?): IppProbe? {
        val r = base.getPrinterAttributes(printer, pinned)
        if (r !is IppResult.Success) return null
        return IppProbe.from(r.response, r.effectivePath)
    }

    private fun opAttrs(printerUri: String, extra: List<IppAttribute> = emptyList()): IppAttributeGroup {
        val list = mutableListOf(
            IppAttribute("attributes-charset", IppTag.CHARSET, listOf(IppValue.StringValue(IppTag.CHARSET, "utf-8"))),
            IppAttribute("attributes-natural-language", IppTag.NATURAL_LANGUAGE, listOf(IppValue.StringValue(IppTag.NATURAL_LANGUAGE, "en"))),
            IppAttribute("printer-uri", IppTag.URI, listOf(IppValue.StringValue(IppTag.URI, printerUri)))
        )
        list.addAll(extra)
        return IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES_TAG, list)
    }

    private fun kw(name: String, value: String) =
        IppAttribute(name, IppTag.KEYWORD, listOf(IppValue.StringValue(IppTag.KEYWORD, value)))

    private fun int(name: String, value: Int) =
        IppAttribute(name, IppTag.INTEGER, listOf(IppValue.IntValue(value)))

    private fun enum(name: String, value: Int) =
        IppAttribute(name, IppTag.ENUM, listOf(IppValue.IntValue(value)))

    private fun endpoints(printer: Printer): Pair<String, String> {
        val https = printer.protocol == PrinterProtocol.IPPS
        val port = if (printer.port > 0) printer.port else 631
        val path = printer.resourcePath.trim().removePrefix("/").ifBlank { "ipp/print" }
        val host = IppClient.urlHost(printer.host)
        return "${if (https) "https" else "http"}://$host:$port/$path" to "${if (https) "ipps" else "ipp"}://$host:$port/$path"
    }

    internal fun buildPrintJob(printerUri: String, mime: String, o: IppJobOptions): ByteArray {
        val op = opAttrs(
            printerUri,
            listOf(
                IppAttribute("requesting-user-name", IppTag.NAME_WITHOUT_LANGUAGE, listOf(IppValue.StringValue(IppTag.NAME_WITHOUT_LANGUAGE, "PrintGo"))),
                IppAttribute("job-name", IppTag.NAME_WITHOUT_LANGUAGE, listOf(IppValue.StringValue(IppTag.NAME_WITHOUT_LANGUAGE, o.jobName.take(120)))),
                IppAttribute("document-format", IppTag.MIME_MEDIA_TYPE, listOf(IppValue.StringValue(IppTag.MIME_MEDIA_TYPE, mime)))
            )
        )
        val job = mutableListOf<IppAttribute>()
        val p = o.probe
        if (o.copies > 1) job.add(int("copies", o.copies))
        if (!o.minimal) {
            if (o.copies > 1) {
                val mdh = if (o.collate) "separate-documents-collated-copies" else "separate-documents-uncollated-copies"
                if (p == null || p.multipleDocumentHandling.contains(mdh)) job.add(kw("multiple-document-handling", mdh))
            }
            val sides = when (o.duplex) {
                DuplexMode.ONE_SIDED -> "one-sided"
                DuplexMode.TWO_SIDED_LONG_EDGE -> "two-sided-long-edge"
                DuplexMode.TWO_SIDED_SHORT_EDGE -> "two-sided-short-edge"
            }
            if (p != null) {
                // Only what the printer listed: strict printers refuse the whole job over one unknown value.
                if (sides in p.sides) job.add(kw("sides", sides))
                val color = if (o.colorMode == PrintColorMode.GRAYSCALE) "monochrome" else "color"
                if (color in p.colorModes) job.add(kw("print-color-mode", color))
            } else {
                if (o.duplex == DuplexMode.ONE_SIDED || o.supportedSides.isEmpty() || o.supportedSides.contains(sides)) {
                    job.add(kw("sides", sides))
                }
                if (o.supportsColorModes) {
                    job.add(kw("print-color-mode", if (o.colorMode == PrintColorMode.GRAYSCALE) "monochrome" else "color"))
                }
            }
            val q = when (o.quality) { PrintQuality.DRAFT -> 3; PrintQuality.NORMAL -> 4; PrintQuality.HIGH -> 5 }
            if (p == null || q in p.qualities) job.add(enum("print-quality", q))
            // PDF pages are already laid out at paper size. Pictures carry no resolution, so they must be
            // fitted to the page (with "none" a 300 dpi JPEG would print as a huge image: a blank corner).
            o.printScaling?.let { if (p == null || it in p.scalings) job.add(kw("print-scaling", it)) }
            o.mediaKeyword?.let { m -> if (p == null || p.media.any { it.equals(m, true) }) job.add(kw("media", m)) }
        }

        val groups = mutableListOf(op)
        if (job.isNotEmpty()) groups.add(IppAttributeGroup(IppTag.JOB_ATTRIBUTES_TAG, job))
        val msg = IppMessage(code = OP_PRINT_JOB, requestId = 1, attributeGroups = groups)
        return IppEncoder.encode(msg)
    }

    suspend fun printJob(
        printer: Printer,
        pinnedFingerprint: String?,
        data: File,
        mime: String,
        options: IppJobOptions,
        onProgress: (Float) -> Unit = {}
    ): IppSendResult = withContext(Dispatchers.IO) {
        val (url, ippUri) = endpoints(printer)
        var seen: String? = null
        val client = base.createOkHttpClient(printer, pinnedFingerprint) { seen = it }
            .newBuilder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val header = buildPrintJob(ippUri, mime, options)
        val total = header.size + data.length()
        val body = object : RequestBody() {
            override fun contentType() = ippMedia
            override fun contentLength() = total
            override fun writeTo(sink: BufferedSink) {
                sink.write(header)
                var sent = header.size.toLong()
                data.inputStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        onProgress(sent.toFloat() / total)
                    }
                }
            }
        }
        val request = Request.Builder().url(url).post(body).header("Content-Type", "application/ipp").build()
        try {
            client.newCall(request).await().use { resp ->
                if (!resp.isSuccessful) {
                    val retry = resp.code >= 500 || resp.code == 408
                    val text = when (resp.code) {
                        400 -> "The printer rejected the request."
                        401, 403 -> "The printer requires authentication, which PrintGo does not support yet."
                        404 -> "The printer address looks wrong. Remove the printer and add it again."
                        413 -> "The file is too large for this printer."
                        426 -> "The printer requires a secure (IPPS) connection."
                        else -> "The printer answered with HTTP ${resp.code}."
                    }
                    // An HTTP 400 is how some printers say "I cannot take this request": let the caller retry
                    // with fewer options or another format, exactly as for IPP status 0x0400.
                    val code = when (resp.code) { 400 -> 0x0400; 413 -> 0x040C; else -> resp.code }
                    return@withContext IppSendResult.Failed(text, code, retry)
                }
                val bytes = resp.body?.bytes() ?: return@withContext IppSendResult.Failed("The printer sent an empty answer.")
                val msg = try { IppDecoder.decode(bytes) } catch (e: Exception) {
                    return@withContext IppSendResult.Failed("The printer sent an unreadable answer.")
                }
                if (!msg.isSuccess) {
                    val code = msg.code.toInt()
                    val retry = code == 0x0502 || code == 0x0501 || code == 0x0507
                    return@withContext IppSendResult.Failed(statusText(code), code, retry)
                }
                IppSendResult.Accepted(
                    jobId = msg.getInt("job-id") ?: 0,
                    jobUri = msg.getString("job-uri"),
                    statusCode = msg.code.toInt()
                )
            }
        } catch (e: SocketTimeoutException) {
            IppSendResult.Failed("The printer did not answer in time. Check that it is on and on the same Wi-Fi.")
        } catch (e: java.net.ConnectException) {
            IppSendResult.Failed("Could not reach the printer. Check that it is on and on the same Wi-Fi.", NO_CONNECTION)
        } catch (e: java.net.NoRouteToHostException) {
            IppSendResult.Failed("Could not reach the printer. Check that it is on and on the same Wi-Fi.", NO_CONNECTION)
        } catch (e: java.net.UnknownHostException) {
            IppSendResult.Failed("Could not reach the printer. Check that it is on and on the same Wi-Fi.", NO_CONNECTION)
        } catch (e: java.net.UnknownServiceException) {
            IppSendResult.Failed("Android blocked the connection to the printer. Update PrintGo.", NO_CONNECTION, retryable = false)
        } catch (e: SSLException) {
            val fp = seen
            if (fp != null && pinnedFingerprint == null) IppSendResult.TlsUntrusted(fp)
            else IppSendResult.Failed("Secure connection to the printer failed. Re-add the printer to trust its certificate again.", retryable = false)
        } catch (e: IOException) {
            // Connected, but the transfer broke off. Many printers simply hang up when they cannot read the
            // format they are being sent, so the caller may try another format.
            IppSendResult.Failed("The connection to the printer was cut off while sending.", TRANSFER_BROKEN)
        } catch (e: Exception) {
            IppSendResult.Failed(e.message ?: "Unexpected printing error.")
        }
    }

    private suspend fun simpleOp(
        printer: Printer,
        pinned: String?,
        code: Short,
        jobId: Int,
        extra: List<IppAttribute> = emptyList()
    ): IppMessage? = withContext(Dispatchers.IO) {
        val (url, ippUri) = endpoints(printer)
        val op = opAttrs(ippUri, listOf(int("job-id", jobId)) + extra)
        val bytes = IppEncoder.encode(IppMessage(code = code, requestId = 2, attributeGroups = listOf(op)))
        val client = base.createOkHttpClient(printer, pinned) { }
        val req = Request.Builder().url(url).post(bytes.toRequestBody(ippMedia)).header("Content-Type", "application/ipp").build()
        try {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                IppDecoder.decode(r.body?.bytes() ?: return@withContext null)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getJobStatus(printer: Printer, pinned: String?, jobId: Int): IppJobStatus? {
        val msg = simpleOp(
            printer, pinned, OP_GET_JOB_ATTRIBUTES, jobId,
            listOf(
                IppAttribute(
                    "requested-attributes", IppTag.KEYWORD,
                    listOf("job-state", "job-state-reasons", "job-impressions-completed").map { IppValue.StringValue(IppTag.KEYWORD, it) }
                )
            )
        ) ?: return null
        if (!msg.isSuccess) return null
        val state = msg.getInt("job-state") ?: return null
        return IppJobStatus(state, msg.getStrings("job-state-reasons"), msg.getInt("job-impressions-completed"))
    }

    suspend fun cancelJob(printer: Printer, pinned: String?, jobId: Int): Boolean {
        val msg = simpleOp(printer, pinned, OP_CANCEL_JOB, jobId) ?: return false
        return msg.isSuccess
    }
}

/** Runs an OkHttp call so that cancelling the coroutine really aborts the network transfer. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { try { cancel() } catch (_: Throwable) { } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resumeWith(Result.success(response)) else response.close()
        }
    })
}
