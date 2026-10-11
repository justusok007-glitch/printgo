package com.printgo.app.data.print

import android.content.Context
import android.graphics.Bitmap
import com.printgo.app.data.converter.PdfPageRenderer
import com.printgo.app.data.converter.PrintableDocument
import com.printgo.app.data.ipp.IppClient
import com.printgo.app.data.ipp.IppDecoder
import com.printgo.app.data.ipp.IppEncoder
import com.printgo.app.data.ipp.IppJobOptions
import com.printgo.app.data.ipp.IppPrintClient
import com.printgo.app.data.ipp.IppSendResult
import com.printgo.app.data.layout.SheetLayoutEngine
import com.printgo.app.data.local.JobStatus
import com.printgo.app.data.local.PrintGoDatabase
import com.printgo.app.data.local.PrintJobDao
import com.printgo.app.data.local.PrintJobEntity
import com.printgo.app.data.local.SavedPrinterDao
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.PrintSettings
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.model.PrinterProtocol
import com.printgo.app.service.PrintJobService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PrintRequest(
    val documents: List<PrintableDocument>,
    val settings: PrintSettings,
    val printer: Printer,
    val capabilities: PrinterCapabilities?,
    val title: String
)

class PrintFailure(message: String, val retryable: Boolean = true) : Exception(message)

/**
 * Sequential print queue. Jobs are persisted in Room, the rendered PDF of every job is kept on disk
 * so a failed job can be retried or re-printed without the original document.
 */
object PrintJobManager {

    private lateinit var appContext: Context
    private lateinit var dao: PrintJobDao
    private lateinit var printerDao: SavedPrinterDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pending = ConcurrentHashMap<String, PrintRequest>()
    private val running = ConcurrentHashMap<String, Job>()
    private val ipp = IppPrintClient()
    private var started = false

    lateinit var jobs: StateFlow<List<PrintJobEntity>>
        private set

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        appContext = context.applicationContext
        val db = PrintGoDatabase.getInstance(appContext)
        dao = db.printJobDao()
        printerDao = db.savedPrinterDao()
        jobs = dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
        scope.launch {
            // Anything that was in flight when the process died cannot continue.
            dao.findActive().forEach {
                dao.upsert(
                    it.copy(
                        status = JobStatus.FAILED,
                        statusMessage = "Interrupted when the app closed. Tap Retry to send it again.",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            for (id in queue) {
                val j = launch { process(id) }
                running[id] = j
                j.join()
                running.remove(id)
                dao.get(id)?.let {
                    if (JobStatus.isActive(it.status)) {
                        dao.upsert(it.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelled.", updatedAt = now()))
                    }
                    notifyFinished(dao.get(id))
                }
            }
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun jobsDir() = File(appContext.filesDir, "jobs").apply { mkdirs() }
    fun pdfFile(jobId: String) = File(jobsDir(), "$jobId.pdf")

    // ---------- public API ----------

    suspend fun enqueue(req: PrintRequest): String {
        val id = UUID.randomUUID().toString()
        val sheets = SheetLayoutEngine.computeSheets(req.documents, req.settings, false).size
        val s = req.settings
        val caps = req.capabilities
        val entity = PrintJobEntity(
            id = id,
            title = req.title,
            createdAt = now(),
            updatedAt = now(),
            status = JobStatus.QUEUED,
            statusMessage = "Waiting…",
            sheets = sheets,
            sourcePages = req.documents.sumOf { it.pageCount },
            copies = s.copies,
            collate = s.collate,
            duplex = s.duplex.name,
            colorMode = s.colorMode.name,
            quality = s.quality.name,
            paperName = s.paperSize.name,
            paperWidthMm = s.paperSize.widthMm,
            paperHeightMm = s.paperSize.heightMm,
            settingsSummary = summary(s),
            printerId = req.printer.id,
            printerName = req.printer.name,
            connectionType = req.printer.connectionType.name,
            protocol = req.printer.protocol.name,
            host = req.printer.host,
            port = req.printer.port,
            resourcePath = req.printer.resourcePath,
            formatsCsv = (caps?.documentFormatsSupported?.ifEmpty { null } ?: req.printer.supportedFormats).joinToString(","),
            sidesCsv = (caps?.sidesSupported ?: listOf("one-sided")).joinToString(","),
            colorModesSupported = caps?.colorModesSupported?.isNotEmpty() ?: true
        )
        withContext(Dispatchers.IO) { dao.upsert(entity) }
        pending[id] = req
        queue.send(id)
        PrintJobService.start(appContext)
        return id
    }

    fun summary(s: PrintSettings): String {
        val parts = mutableListOf<String>()
        parts.add(s.paperSize.name)
        parts.add(if (s.colorMode == PrintColorMode.COLOR) "Color" else "Grayscale")
        if (s.copies > 1) parts.add("${s.copies} copies")
        if (s.duplex != DuplexMode.ONE_SIDED) parts.add("Two-sided")
        if (s.pagesPerSheet.count > 1) parts.add("${s.pagesPerSheet.count} per sheet")
        return parts.joinToString(" • ")
    }

    fun cancel(id: String) {
        scope.launch {
            val job = dao.get(id) ?: return@launch
            if (!JobStatus.isActive(job.status)) return@launch
            // Show "Cancelled" straight away; the work is stopped right behind it.
            dao.upsert(job.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelled.", progress = 0f, updatedAt = now()))
            pending.remove(id)
            running[id]?.cancel()
            notifyFinished(dao.get(id))
            val latest = dao.get(id) ?: job
            val remote = maxOf(job.remoteJobId, latest.remoteJobId)
            if (remote > 0 && job.connectionType == ConnectionType.WIFI.name && job.protocol != PrinterProtocol.RAW9100.name) {
                withContext(NonCancellable) { remoteCancel(job.copy(remoteJobId = remote)) }
            }
        }
    }

    fun retry(id: String) {
        scope.launch {
            val job = dao.get(id) ?: return@launch
            if (JobStatus.isActive(job.status)) return@launch
            if (job.pdfPath == null || !File(job.pdfPath).exists()) {
                dao.upsert(job.copy(statusMessage = "The saved pages are gone. Print it again from the preview.", updatedAt = now()))
                return@launch
            }
            dao.upsert(job.copy(status = JobStatus.QUEUED, statusMessage = "Waiting…", progress = 0f, remoteJobId = 0, updatedAt = now()))
            queue.send(id)
            PrintJobService.start(appContext)
        }
    }

    /** Creates a fresh job from the saved pages of an older one (history → reprint). */
    suspend fun reprint(id: String): String? = withContext(Dispatchers.IO) {
        val old = dao.get(id) ?: return@withContext null
        val src = old.pdfPath?.let { File(it) }?.takeIf { it.exists() } ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val dst = pdfFile(newId)
        src.copyTo(dst, overwrite = true)
        dao.upsert(
            old.copy(
                id = newId, createdAt = now(), updatedAt = now(), status = JobStatus.QUEUED,
                statusMessage = "Waiting…", progress = 0f, remoteJobId = 0, outputUri = null,
                pdfPath = dst.absolutePath, attempts = 0
            )
        )
        queue.send(newId)
        PrintJobService.start(appContext)
        newId
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        running[id]?.cancel()
        dao.get(id)?.pdfPath?.let { File(it).delete() }
        dao.delete(id)
    }

    suspend fun clearFinished() = withContext(Dispatchers.IO) {
        dao.findFinished().forEach { it.pdfPath?.let { p -> File(p).delete() } }
        dao.deleteFinished()
    }

    suspend fun cleanupOlderThan(days: Int) = withContext(Dispatchers.IO) {
        if (days <= 0) return@withContext
        val cutoff = now() - days * 24L * 60 * 60 * 1000
        dao.findOlderThan(cutoff).forEach { it.pdfPath?.let { p -> File(p).delete() } }
        dao.deleteOlderThan(cutoff)
    }

    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val f = File(appContext.cacheDir, "printgo_history.csv")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        f.bufferedWriter().use { w ->
            w.write("Date,Document,Printer,Status,Sheets,Copies,Settings,Message\n")
            dao.getAll().forEach {
                w.write(
                    listOf(
                        fmt.format(java.util.Date(it.createdAt)), q(it.title), q(it.printerName), it.status,
                        it.sheets.toString(), it.copies.toString(), q(it.settingsSummary), q(it.statusMessage)
                    ).joinToString(",") + "\n"
                )
            }
        }
        f
    }

    // ---------- processing ----------

    private suspend fun update(id: String, block: (PrintJobEntity) -> PrintJobEntity): PrintJobEntity? {
        val cur = dao.get(id) ?: return null
        if (cur.status == JobStatus.CANCELLED) return cur
        val next = block(cur).copy(updatedAt = now())
        dao.upsert(next)
        return next
    }

    private fun notifyFinished(job: PrintJobEntity?) {
        if (job != null && !JobStatus.isActive(job.status)) PrintNotifications.result(appContext, job)
    }

    private fun printerOf(job: PrintJobEntity) = Printer(
        id = job.printerId,
        name = job.printerName,
        host = job.host,
        port = job.port,
        connectionType = runCatching { ConnectionType.valueOf(job.connectionType) }.getOrDefault(ConnectionType.WIFI),
        protocol = runCatching { PrinterProtocol.valueOf(job.protocol) }.getOrDefault(PrinterProtocol.IPP),
        resourcePath = job.resourcePath
    )

    private suspend fun remoteCancel(job: PrintJobEntity) {
        try {
            val fp = printerDao.getPrinter(job.printerId)?.tlsFingerprint
            ipp.cancelJob(printerOf(job), fp, job.remoteJobId)
        } catch (_: Exception) { }
    }

    private suspend fun process(id: String) {
        val start = dao.get(id) ?: return
        if (start.status == JobStatus.CANCELLED) return
        try {
            update(id) { it.copy(status = JobStatus.PREPARING, statusMessage = "Preparing pages…", progress = 0.02f, attempts = it.attempts + 1) }
            val pdf = ensurePdf(id)
            val job = update(id) { it.copy(pdfPath = pdf.absolutePath, statusMessage = "Connecting…", progress = 0.1f) } ?: return
            if (job.status == JobStatus.CANCELLED) return
            transmit(job, pdf)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val cur = dao.get(id)
                if (cur != null && cur.status != JobStatus.CANCELLED) {
                    dao.upsert(cur.copy(status = JobStatus.CANCELLED, statusMessage = "Cancelled.", updatedAt = now()))
                }
                cur?.takeIf { it.remoteJobId > 0 && it.connectionType == ConnectionType.WIFI.name && it.protocol != PrinterProtocol.RAW9100.name }
                    ?.let { remoteCancel(it) }
            }
            throw e
        } catch (e: PrintFailure) {
            fail(id, e.message ?: "Printing failed.")
        } catch (e: TransportException) {
            fail(id, e.message ?: "Printing failed.")
        } catch (e: OutOfMemoryError) {
            fail(id, "This document is too large for this phone to print in one go. Try fewer pages.")
        } catch (e: Exception) {
            fail(id, e.message?.takeIf { it.length < 140 } ?: "Something went wrong while printing.")
        }
    }

    private suspend fun fail(id: String, message: String) = withContext(NonCancellable) {
        update(id) { it.copy(status = JobStatus.FAILED, statusMessage = message, progress = 0f) }
        Unit
    }

    private suspend fun ensurePdf(id: String): File {
        val file = pdfFile(id)
        val req = pending[id]
        if (req != null) {
            val sheets = SheetLayoutEngine.computeSheets(req.documents, req.settings, false)
            if (sheets.isEmpty()) throw PrintFailure("There is nothing to print. Check the page selection.", false)
            SheetLayoutEngine.exportToPdf(sheets, req.documents, req.settings, file)
            pending.remove(id)
        } else if (!file.exists()) {
            throw PrintFailure("The saved pages are gone. Print it again from the preview.", false)
        }
        val pages = PdfPageRenderer(file).use { it.pageCount }
        update(id) { it.copy(sheets = pages) }
        return file
    }

    private suspend fun transmit(job: PrintJobEntity, pdf: File) {
        val printer = printerOf(job)
        when {
            printer.connectionType == ConnectionType.PDF -> savePdf(job, pdf)
            printer.connectionType == ConnectionType.WIFI && (printer.protocol == PrinterProtocol.IPP || printer.protocol == PrinterProtocol.IPPS) ->
                sendIpp(job, printer, pdf)
            printer.connectionType == ConnectionType.WIFI && printer.protocol == PrinterProtocol.RAW9100 -> {
                update(job.id) { it.copy(status = JobStatus.SENDING, statusMessage = "Sending to printer…", method = "RAW 9100") }
                withContext(Dispatchers.IO) {
                    Transports.sendRaw9100(printer.host, printer.port, pdf) { p ->
                        scope.launch { update(job.id) { j -> j.copy(progress = 0.1f + 0.85f * p) } }
                    }
                }
                complete(job.id, "Sent to ${job.printerName}.")
            }
            printer.connectionType == ConnectionType.BLUETOOTH || printer.connectionType == ConnectionType.USB ->
                sendDirect(job, printer, pdf)
            else -> throw PrintFailure("This printer type is not supported yet. Use \"Print with system dialog\".", false)
        }
    }

    private suspend fun complete(id: String, message: String, uri: String? = null) {
        update(id) { it.copy(status = JobStatus.COMPLETED, statusMessage = message, progress = 1f, outputUri = uri ?: it.outputUri) }
    }

    private suspend fun savePdf(job: PrintJobEntity, pdf: File) {
        update(job.id) { it.copy(status = JobStatus.SENDING, statusMessage = "Saving PDF…", method = "Save as PDF", progress = 0.5f) }
        val uri = withContext(Dispatchers.IO) { PdfSaver.saveToDownloads(appContext, pdf, job.title) }
        complete(job.id, "Saved to Downloads/PrintGo.", uri.toString())
    }

    private fun dpiFor(job: PrintJobEntity): Int = if (job.quality == PrintQuality.DRAFT.name) 150 else 300

    private suspend fun sendIpp(job: PrintJobEntity, printer: Printer, pdf: File) {
        val formats = job.formatsCsv.lowercase().split(",").map { it.trim() }
        val fingerprint = printerDao.getPrinter(job.printerId)?.tlsFingerprint
        val sides = job.sidesCsv.split(",").map { it.trim() }
        val opts = IppJobOptions(
            jobName = job.title,
            copies = job.copies,
            duplex = runCatching { DuplexMode.valueOf(job.duplex) }.getOrDefault(DuplexMode.ONE_SIDED),
            colorMode = runCatching { PrintColorMode.valueOf(job.colorMode) }.getOrDefault(PrintColorMode.COLOR),
            quality = runCatching { PrintQuality.valueOf(job.quality) }.getOrDefault(PrintQuality.NORMAL),
            mediaKeyword = IppMedia.keywordFor(PaperSize(job.paperName, job.paperWidthMm, job.paperHeightMm)),
            collate = job.collate,
            supportedSides = sides,
            supportsColorModes = job.colorModesSupported
        )

        val sendPdf = formats.isEmpty() || "application/pdf" in formats
        val sendPwg = !sendPdf && "image/pwg-raster" in formats
        val sendJpeg = !sendPdf && !sendPwg && "image/jpeg" in formats
        if (!sendPdf && !sendPwg && !sendJpeg) {
            throw PrintFailure(
                "This printer only understands formats PrintGo cannot send directly (${formats.take(3).joinToString()}). Use \"Print with system dialog\" instead.",
                false
            )
        }

        data class Payload(val file: File, val mime: String, val label: String)
        val payloads = mutableListOf<Payload>()
        val temps = mutableListOf<File>()
        try {
            when {
                sendPdf -> payloads += Payload(pdf, "application/pdf", "PDF")
                sendPwg -> {
                    update(job.id) { it.copy(statusMessage = "Rendering pages…", progress = 0.15f) }
                    val out = File(appContext.cacheDir, "job_${job.id}.pwg").also { temps += it }
                    withContext(Dispatchers.Default) { renderPwg(job, pdf, out) }
                    payloads += Payload(out, "image/pwg-raster", "PWG raster")
                }
                else -> {
                    update(job.id) { it.copy(statusMessage = "Rendering pages…", progress = 0.15f) }
                    withContext(Dispatchers.Default) {
                        PdfPageRenderer(pdf).use { r ->
                            val dpi = dpiFor(job)
                            val widthPx = (job.paperWidthMm / 25.4f * dpi).toInt()
                            for (i in 0 until r.pageCount) {
                                val bmp = r.renderPage(i, widthPx)
                                val f = File(appContext.cacheDir, "job_${job.id}_$i.jpg").also { temps += it }
                                f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, o) }
                                r.clearCache()
                                payloads += Payload(f, "image/jpeg", "page ${i + 1}")
                            }
                        }
                    }
                }
            }

            payloads.forEachIndexed { index, p ->
                val base = 0.2f + 0.7f * index / payloads.size
                val span = 0.7f / payloads.size
                update(job.id) {
                    it.copy(
                        status = JobStatus.SENDING,
                        statusMessage = if (payloads.size > 1) "Sending ${p.label} (${index + 1}/${payloads.size})…" else "Sending to printer…",
                        method = "IPP • ${p.label}", progress = base
                    )
                }
                var lastTick = 0L
                val res = ipp.printJob(printer, fingerprint, p.file, p.mime, opts) { frac ->
                    val t = now()
                    if (t - lastTick > 400) {
                        lastTick = t
                        scope.launch { update(job.id) { j -> if (j.status == JobStatus.SENDING) j.copy(progress = base + span * frac) else j } }
                    }
                }
                when (res) {
                    is IppSendResult.Accepted -> {
                        update(job.id) { it.copy(remoteJobId = res.jobId, status = JobStatus.PRINTING, statusMessage = "Printer accepted the job…", progress = base + span) }
                        if (res.jobId > 0) track(job.id, printer, fingerprint, res.jobId)
                    }
                    is IppSendResult.Failed -> throw PrintFailure(res.message, res.retryable)
                    is IppSendResult.TlsUntrusted -> throw PrintFailure(
                        "PrintGo does not trust this printer's certificate yet. Open Printers, tap the printer and connect again.", false
                    )
                }
            }
            complete(job.id, "Printed on ${job.printerName}.")
        } finally {
            temps.forEach { it.delete() }
        }
    }

    private suspend fun track(id: String, printer: Printer, fp: String?, remoteId: Int) {
        var misses = 0
        val deadline = now() + 10 * 60 * 1000L
        while (now() < deadline) {
            delay(2000)
            val st = ipp.getJobStatus(printer, fp, remoteId)
            if (st == null) {
                if (++misses >= 4) return // printer does not report status; the job was accepted
                continue
            }
            misses = 0
            val friendly = st.reasons.mapNotNull { IppPrintClient.friendlyReason(it) }.firstOrNull()
            when (st.state) {
                IppPrintClient.JOB_COMPLETED -> return
                IppPrintClient.JOB_CANCELED -> throw PrintFailure(friendly ?: "The job was cancelled at the printer.", true)
                IppPrintClient.JOB_ABORTED -> throw PrintFailure(friendly ?: "The printer stopped the job.", true)
                IppPrintClient.JOB_STOPPED, IppPrintClient.JOB_HELD ->
                    update(id) { it.copy(status = JobStatus.PRINTING, statusMessage = friendly ?: "Waiting for the printer…") }
                IppPrintClient.JOB_PROCESSING ->
                    update(id) { it.copy(status = JobStatus.PRINTING, statusMessage = friendly ?: "Printing…", progress = 0.95f) }
                else ->
                    update(id) { it.copy(status = JobStatus.PRINTING, statusMessage = friendly ?: "In the printer's queue…") }
            }
        }
    }

    private fun renderPwg(job: PrintJobEntity, pdf: File, out: File) {
        val grayscale = job.colorMode == PrintColorMode.GRAYSCALE.name
        val dpi = dpiFor(job)
        val (ptW, ptH) = IppMedia.pointsFor(PaperSize(job.paperName, job.paperWidthMm, job.paperHeightMm))
        val twoSided = job.duplex != DuplexMode.ONE_SIDED.name
        val tumble = job.duplex == DuplexMode.TWO_SIDED_SHORT_EDGE.name
        val quality = when (job.quality) { PrintQuality.DRAFT.name -> 3; PrintQuality.HIGH.name -> 5; else -> 4 }
        PdfPageRenderer(pdf).use { r ->
            out.outputStream().buffered().use { os ->
                PwgRasterEncoder.writeDocumentStart(os)
                for (i in 0 until r.pageCount) {
                    val widthPx = (job.paperWidthMm / 25.4f * dpi).toInt()
                    val bmp = r.renderPage(i, widthPx)
                    val header = PwgRasterEncoder.buildPageHeader(
                        bmp.width, bmp.height, dpi, grayscale, twoSided, tumble, 1, ptW, ptH, quality
                    )
                    PwgRasterEncoder.writePage(os, bmp, header, grayscale)
                    r.clearCache()
                }
            }
        }
    }

    enum class RawLanguage(val label: String) {
        PDF("PDF"), POSTSCRIPT("PostScript"), PCL("PCL"), ESCPOS("ESC/POS")
    }

    private val thermalHints = listOf("ESC/POS", "ESCPOS", "STAR", "CPCL", "ZPL", "TSPL", "EPL")
    private val officeBrandHints = listOf(
        "laserjet", "deskjet", "officejet", "pixma", "envy", "photosmart", "brother", "epson", "canon",
        "samsung", "xerox", "ricoh", "kyocera", "lexmark", "oki", "konica", "sharp", "dell", "hp "
    )

    internal fun pickLanguage(deviceId: String?, name: String, btPref: String, strict: Boolean = false): RawLanguage? {
        val cmd = PclEncoder.parseCmd(deviceId)
        if (cmd.isNotEmpty()) {
            return when {
                cmd.any { c -> thermalHints.any { c.contains(it) } } -> RawLanguage.ESCPOS
                "PDF" in cmd -> RawLanguage.PDF
                cmd.any { it == "POSTSCRIPT" || it == "PS" || it.startsWith("POSTSCRIPT") || it == "PS3" || it == "PS2" } -> RawLanguage.POSTSCRIPT
                cmd.any { it.startsWith("PCL") } -> RawLanguage.PCL
                else -> null
            }
        }
        if (strict) {
            // No device id to go on: only a clearly thermal/receipt printer is safe to guess. Anything else
            // would print the raw commands as text, so refuse instead.
            val n = name.lowercase()
            val thermal = listOf("pos", "receipt", "thermal", "xprinter", "goojprt", "zjiang", "label", "tm-", "star ", "rongta", "sunmi")
            return if (thermal.any { n.contains(it) }) RawLanguage.ESCPOS else null
        }
        return when (btPref) {
            "ESCPOS" -> RawLanguage.ESCPOS
            "PCL" -> RawLanguage.PCL
            "POSTSCRIPT" -> RawLanguage.POSTSCRIPT
            "PDF" -> RawLanguage.PDF
            else -> if (officeBrandHints.any { name.lowercase().contains(it) }) RawLanguage.PCL else RawLanguage.ESCPOS
        }
    }

    private fun fileChunks(f: File): Sequence<ByteArray> = sequence {
        f.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                yield(if (n == buf.size) buf.copyOf() else buf.copyOf(n))
            }
        }
    }

    private fun duplexCode(job: PrintJobEntity) = when (job.duplex) {
        DuplexMode.TWO_SIDED_LONG_EDGE.name -> 1
        DuplexMode.TWO_SIDED_SHORT_EDGE.name -> 2
        else -> 0
    }

    private fun renderRawFile(job: PrintJobEntity, lang: RawLanguage, pdf: File, out: File, pjl: Boolean = false) {
        val copies = job.copies.coerceIn(1, 99)
        val grayscale = job.colorMode == PrintColorMode.GRAYSCALE.name
        out.outputStream().buffered(64 * 1024).use { os ->
            when (lang) {
                RawLanguage.PDF -> if (!pjl) {
                    pdf.inputStream().use { it.copyTo(os) }
                } else {
                    os.write(byteArrayOf(0x1B)); os.write("%-12345X".toByteArray())
                    os.write("@PJL SET COPIES=$copies\r\n".toByteArray())
                    when (duplexCode(job)) {
                        1 -> os.write("@PJL SET DUPLEX=ON\r\n@PJL SET BINDING=LONGEDGE\r\n".toByteArray())
                        2 -> os.write("@PJL SET DUPLEX=ON\r\n@PJL SET BINDING=SHORTEDGE\r\n".toByteArray())
                    }
                    os.write("@PJL ENTER LANGUAGE=PDF\r\n".toByteArray())
                    pdf.inputStream().use { it.copyTo(os) }
                    os.write(byteArrayOf(0x1B)); os.write("%-12345X".toByteArray())
                }
                RawLanguage.PCL -> {
                    PclEncoder.start(os, copies, duplexCode(job), PclEncoder.paperSizeCode(job.paperWidthMm, job.paperHeightMm), job.title)
                    val dpi = 300
                    PdfPageRenderer(pdf).use { r ->
                        for (i in 0 until r.pageCount) {
                            val bmp = r.renderPage(i, (job.paperWidthMm / 25.4f * dpi).toInt())
                            PclEncoder.writePage(os, bmp, dpi)
                            r.clearCache()
                        }
                    }
                    PclEncoder.end(os)
                }
                RawLanguage.POSTSCRIPT -> {
                    val (ptW, ptH) = IppMedia.pointsFor(PaperSize(job.paperName, job.paperWidthMm, job.paperHeightMm))
                    PostScriptEncoder.start(os, job.title)
                    val dpi = if (job.quality == PrintQuality.HIGH.name) 200 else 150
                    PdfPageRenderer(pdf).use { r ->
                        for (i in 0 until r.pageCount) {
                            val bmp = r.renderPage(i, (job.paperWidthMm / 25.4f * dpi).toInt())
                            PostScriptEncoder.writePage(os, bmp, ptW, ptH, grayscale, copies, duplexCode(job))
                            r.clearCache()
                        }
                    }
                    PostScriptEncoder.end(os)
                }
                RawLanguage.ESCPOS -> {
                    val width = if (job.paperWidthMm in 70..90) 576 else 384
                    os.write(EscPosEncoder.INIT)
                    repeat(copies) {
                        PdfPageRenderer(pdf).use { r ->
                            for (i in 0 until r.pageCount) {
                                val src = r.renderPage(i, width)
                                val bmp = EscPosEncoder.scaleForPaper(src, width)
                                os.write(EscPosEncoder.rasterBytes(bmp))
                                os.write(EscPosEncoder.feedAndCut())
                                if (bmp !== src) bmp.recycle()
                                r.clearCache()
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun sendDirect(job: PrintJobEntity, printer: Printer, pdf: File) {
        val isUsb = printer.connectionType == ConnectionType.USB
        val prefs = com.printgo.app.data.prefs.AppPreferences(appContext).current()
        update(job.id) {
            it.copy(status = JobStatus.SENDING, statusMessage = "Connecting to the printer…", progress = 0.15f,
                method = if (isUsb) "USB" else "Bluetooth")
        }
        if (isUsb) {
            val session = withContext(Dispatchers.IO) { UsbSession.open(appContext, printer.host) }
            session.use { s ->
                if (s.isIppUsb) { sendIppUsb(job, s, pdf); return }
                val id = withContext(Dispatchers.IO) { s.deviceId() }
                val lang = pickLanguage(id, job.printerName, "AUTO", strict = true) ?: throw PrintFailure(
                    "This printer uses its own USB language that PrintGo cannot create. Connect it over Wi-Fi, or use \"Print with system dialog\".", false
                )
                update(job.id) { it.copy(method = "USB • ${lang.label}", statusMessage = "Preparing pages…", progress = 0.25f) }
                val tmp = File(appContext.cacheDir, "job_${job.id}.raw")
                try {
                    val pjl = "PJL" in PclEncoder.parseCmd(id)
                    withContext(Dispatchers.Default) { renderRawFile(job, lang, pdf, tmp, pjl) }
                    update(job.id) { it.copy(statusMessage = "Sending to printer…", progress = 0.6f) }
                    withContext(Dispatchers.IO) { s.writeAll(fileChunks(tmp)) }
                } finally { tmp.delete() }
            }
        } else {
            val lang = pickLanguage(null, job.printerName, prefs.bluetoothLanguage)!!
            update(job.id) { it.copy(method = "Bluetooth • ${lang.label}", statusMessage = "Preparing pages…", progress = 0.25f) }
            val tmp = File(appContext.cacheDir, "job_${job.id}.raw")
            try {
                withContext(Dispatchers.Default) { renderRawFile(job, lang, pdf, tmp) }
                update(job.id) { it.copy(statusMessage = "Sending to printer…", progress = 0.6f) }
                withContext(Dispatchers.IO) { Transports.sendBluetooth(appContext, printer.host, fileChunks(tmp)) }
            } finally { tmp.delete() }
        }
        complete(job.id, "Sent to ${job.printerName}.")
    }

    /** Printers that expose the standard IPP-over-USB interface take the same jobs as Wi-Fi printers. */
    private suspend fun sendIppUsb(job: PrintJobEntity, s: UsbSession, pdf: File) {
        val uri = "ipp://localhost:631/ipp/print"
        update(job.id) { it.copy(method = "USB • IPP", statusMessage = "Asking the printer what it accepts…", progress = 0.2f) }
        val formats = withContext(Dispatchers.IO) {
            val req = IppEncoder.encode(IppEncoder.createGetPrinterAttributesRequest(uri, 1, listOf("document-format-supported", "sides-supported", "media-supported")))
            val resp = IppDecoder.decode(s.ippRequest(req))
            resp.getStrings("document-format-supported").map { it.lowercase() }
        }
        val sendPdf = formats.isEmpty() || "application/pdf" in formats
        val sendPwg = !sendPdf && "image/pwg-raster" in formats
        val sendJpeg = !sendPdf && !sendPwg && "image/jpeg" in formats
        if (!sendPdf && !sendPwg && !sendJpeg) throw PrintFailure("This printer's USB connection does not accept PDF, PWG Raster or JPEG. Use \"Print with system dialog\".", false)

        val opts = IppJobOptions(
            jobName = job.title,
            copies = job.copies,
            duplex = runCatching { DuplexMode.valueOf(job.duplex) }.getOrDefault(DuplexMode.ONE_SIDED),
            colorMode = runCatching { PrintColorMode.valueOf(job.colorMode) }.getOrDefault(PrintColorMode.COLOR),
            quality = runCatching { PrintQuality.valueOf(job.quality) }.getOrDefault(PrintQuality.NORMAL),
            mediaKeyword = IppMedia.keywordFor(PaperSize(job.paperName, job.paperWidthMm, job.paperHeightMm)),
            collate = job.collate,
            supportedSides = emptyList(),
            supportsColorModes = true
        )
        val temps = mutableListOf<File>()
        try {
            val files = mutableListOf<Pair<File, String>>()
            if (sendPdf) files += pdf to "application/pdf"
            else if (sendPwg) {
                val f = File(appContext.cacheDir, "job_${job.id}.pwg").also { temps += it }
                withContext(Dispatchers.Default) { renderPwg(job, pdf, f) }
                files += f to "image/pwg-raster"
            } else {
                withContext(Dispatchers.Default) {
                    PdfPageRenderer(pdf).use { r ->
                        val widthPx = (job.paperWidthMm / 25.4f * dpiFor(job)).toInt()
                        for (i in 0 until r.pageCount) {
                            val f = File(appContext.cacheDir, "job_${job.id}_$i.jpg").also { temps += it }
                            f.outputStream().use { o -> r.renderPage(i, widthPx).compress(Bitmap.CompressFormat.JPEG, 92, o) }
                            r.clearCache()
                            files += f to "image/jpeg"
                        }
                    }
                }
            }
            files.forEachIndexed { idx, (f, mime) ->
                update(job.id) { it.copy(statusMessage = "Sending to printer…", progress = 0.4f + 0.5f * idx / files.size) }
                val resp = withContext(Dispatchers.IO) {
                    IppDecoder.decode(s.ippRequest(ipp.buildPrintJob(uri, mime, opts), f))
                }
                if (!resp.isSuccess) throw PrintFailure(IppPrintClient.statusText(resp.code.toInt()), true)
            }
        } finally { temps.forEach { it.delete() } }
        complete(job.id, "Sent to ${job.printerName} over USB.")
    }
}
