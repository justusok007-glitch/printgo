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
        if (s.orientation != com.printgo.app.model.PrintOrientation.AUTO) parts.add(s.orientation.label)
        when (val sel = s.pageSelection) {
            is com.printgo.app.model.PageSelection.Odd -> parts.add("Odd pages")
            is com.printgo.app.model.PageSelection.Even -> parts.add("Even pages")
            is com.printgo.app.model.PageSelection.Range -> parts.add("Pages ${sel.text}")
            else -> {}
        }
        if (s.reverseOrder) parts.add("Reversed")
        if (s.scale is com.printgo.app.model.PrintScale.CustomPercent) parts.add(s.scale.label)
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
            printer.connectionType == ConnectionType.WIFI && printer.protocol == PrinterProtocol.RAW9100 -> sendRawNetwork(job, printer, pdf)
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

    private fun isDraft(job: PrintJobEntity) = job.quality == PrintQuality.DRAFT.name
    private fun isGray(job: PrintJobEntity) = job.colorMode == PrintColorMode.GRAYSCALE.name
    private fun duplexOf(job: PrintJobEntity) = runCatching { DuplexMode.valueOf(job.duplex) }.getOrDefault(DuplexMode.ONE_SIDED)
    private fun paperOf(job: PrintJobEntity) = PaperSize(job.paperName, job.paperWidthMm, job.paperHeightMm)

    private fun ippOptions(job: PrintJobEntity, probe: com.printgo.app.data.ipp.IppProbe?) = IppJobOptions(
        jobName = job.title,
        copies = job.copies,
        duplex = duplexOf(job),
        colorMode = runCatching { PrintColorMode.valueOf(job.colorMode) }.getOrDefault(PrintColorMode.COLOR),
        quality = runCatching { PrintQuality.valueOf(job.quality) }.getOrDefault(PrintQuality.NORMAL),
        mediaKeyword = IppMedia.keywordFor(paperOf(job)),
        collate = job.collate,
        supportedSides = job.sidesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        supportsColorModes = job.colorModesSupported,
        probe = probe
    )

    /** Thrown when the printer answered but refused every format and option set we could offer. */
    private class AllFormatsRefused(message: String) : Exception(message)

    /**
     * Wi-Fi IPP. Ask the printer what it reads, then send the best format it lists. A printer that does not
     * answer IPP at all, but takes raw data on port 9100, is printed to that way instead.
     */
    private suspend fun sendIpp(job: PrintJobEntity, printer0: Printer, pdf: File, allowRawFallback: Boolean = true) {
        val fingerprint = printerDao.getPrinter(job.printerId)?.tlsFingerprint

        update(job.id) { it.copy(statusMessage = "Asking the printer what it accepts…", progress = 0.12f) }
        val probe = ipp.probe(printer0, fingerprint)
        if (probe == null && allowRawFallback && printer0.host.isNotBlank()) {
            val rawOpen = withContext(Dispatchers.IO) { Transports.isPortOpen(printer0.host, 9100) }
            if (rawOpen) {
                val lang = rawLanguageFor(job, printer0.host)
                if (lang != null) {
                    sendRaw(job, printer0.copy(protocol = PrinterProtocol.RAW9100, port = 9100), pdf, lang)
                    return
                }
            }
        }
        val printer = if (probe != null && probe.path.isNotBlank()) printer0.copy(resourcePath = probe.path) else printer0
        val sender: IppSend = { f, mime, o, cb -> ipp.printJob(printer, fingerprint, f, mime, o, cb) }
        try {
            runIppJob(job, pdf, probe, "IPP", sender) { remoteId -> track(job.id, printer, fingerprint, remoteId) }
        } catch (e: AllFormatsRefused) {
            throw PrintFailure(e.message ?: "The printer refused the job.", false)
        }
        complete(job.id, "Printed on ${job.printerName}.")
    }

    /**
     * The format loop shared by Wi-Fi and USB IPP: render the best format the printer lists, send it, and
     * when the printer says "not like this" retry with only the essential options, then move on to the
     * next format. Throws [AllFormatsRefused] when nothing was accepted.
     */
    private suspend fun runIppJob(
        job: PrintJobEntity,
        pdf: File,
        probe: com.printgo.app.data.ipp.IppProbe?,
        via: String,
        sender: IppSend,
        onAccepted: suspend (Int) -> Unit
    ) {
        val appPrefs = com.printgo.app.data.prefs.AppPreferences(appContext).current()
        val plans = IppFormatPlanner.plan(probe, appPrefs.dataFormat, isGray(job), isDraft(job))
        if (plans.isEmpty()) {
            val listed = probe?.formats?.filterNot { it.equals("application/octet-stream", true) }?.take(3)?.joinToString() ?: ""
            throw AllFormatsRefused(
                "This printer only accepts formats PrintGo cannot create ($listed). Use \"Print with system dialog\" on the job screen."
            )
        }
        val baseOpts = ippOptions(job, probe)
        val wantSides = when (duplexOf(job)) {
            DuplexMode.ONE_SIDED -> null
            DuplexMode.TWO_SIDED_LONG_EDGE -> "two-sided-long-edge"
            DuplexMode.TWO_SIDED_SHORT_EDGE -> "two-sided-short-edge"
        }
        // Raster duplex flags only when the printer can really print two-sided.
        val duplexOk = wantSides != null &&
            (probe == null || wantSides in probe.sides || probe.sides.none { it.startsWith("two-sided") })

        var lastError: String? = null
        val temps = mutableListOf<File>()
        try {
            for (plan in plans) {
                data class Payload(val file: File, val label: String)
                val payloads = mutableListOf<Payload>()
                // Printers that cannot make enough copies of a raster stream get the pages repeated here.
                val rasterCopies = if (probe != null && job.copies > 1 && probe.copiesMax in 1 until job.copies) job.copies else 1
                var opts = baseOpts
                val detail = when (plan.kind) {
                    SendKind.PWG, SendKind.URF -> "${plan.kind.label} ${plan.dpi} dpi${if (plan.grayscale) ", grey" else ""}"
                    else -> plan.kind.label
                }
                if (plan.kind != SendKind.PDF) update(job.id) { it.copy(statusMessage = "Rendering pages…", progress = 0.15f, method = "$via • $detail") }
                when (plan.kind) {
                    SendKind.PDF -> payloads += Payload(pdf, "PDF")
                    SendKind.PWG -> {
                        val out = File(appContext.cacheDir, "job_${job.id}.pwg").also { temps += it }
                        withContext(Dispatchers.Default) { renderPwg(job, pdf, out, plan.dpi, plan.grayscale, duplexOk, rasterCopies) }
                        payloads += Payload(out, "PWG raster")
                        opts = opts.copy(printScaling = null, copies = if (rasterCopies > 1) 1 else opts.copies)
                    }
                    SendKind.URF -> {
                        val out = File(appContext.cacheDir, "job_${job.id}.urf").also { temps += it }
                        val urfDuplex = duplexOk && (probe == null || probe.urfDuplex || probe.urf.isEmpty())
                        withContext(Dispatchers.Default) { renderUrf(job, pdf, out, plan.dpi, plan.grayscale, urfDuplex, rasterCopies) }
                        payloads += Payload(out, "AirPrint raster")
                        opts = opts.copy(printScaling = null, copies = if (rasterCopies > 1) 1 else opts.copies)
                    }
                    SendKind.PCL, SendKind.POSTSCRIPT, SendKind.ESCPR -> {
                        val lang = when (plan.kind) { SendKind.PCL -> RawLanguage.PCL; SendKind.POSTSCRIPT -> RawLanguage.POSTSCRIPT; else -> RawLanguage.ESCPR }
                        val out = File(appContext.cacheDir, "job_${job.id}.${lang.name.lowercase()}").also { temps += it }
                        withContext(Dispatchers.Default) { renderRawFile(job, lang, pdf, out) }
                        payloads += Payload(out, lang.label)
                        // Copies are already inside the data; asking again would multiply them.
                        opts = opts.copy(printScaling = null, copies = 1)
                    }
                    SendKind.JPEG -> {
                        withContext(Dispatchers.Default) {
                            PdfPageRenderer(pdf).use { r ->
                                val widthPx = (minOf(job.paperWidthMm, job.paperHeightMm) / 25.4f * dpiFor(job)).toInt()
                                for (i in 0 until r.pageCount) {
                                    val bmp = r.renderForPortraitPaper(i, widthPx)
                                    val f = File(appContext.cacheDir, "job_${job.id}_$i.jpg").also { temps += it }
                                    f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, o) }
                                    r.clearCache()
                                    payloads += Payload(f, "JPEG page ${i + 1}")
                                }
                            }
                        }
                        // Pictures carry no paper size: let the printer fit them to the page.
                        opts = opts.copy(printScaling = "fit")
                    }
                }

                var refused = false
                for ((index, p) in payloads.withIndex()) {
                    val base = 0.2f + 0.7f * index / payloads.size
                    val span = 0.7f / payloads.size
                    update(job.id) {
                        it.copy(
                            status = JobStatus.SENDING,
                            statusMessage = if (payloads.size > 1) "Sending ${p.label} (${index + 1}/${payloads.size})…" else "Sending to printer…",
                            method = "$via • $detail", progress = base
                        )
                    }
                    var lastTick = 0L
                    val progress: (Float) -> Unit = { frac ->
                        val t = now()
                        if (t - lastTick > 400) {
                            lastTick = t
                            scope.launch { update(job.id) { j -> if (j.status == JobStatus.SENDING) j.copy(progress = base + span * frac) else j } }
                        }
                    }
                    val first = sender(p.file, plan.mime, opts, progress)
                    // Strict printers refuse a whole job over one option they do not know: try again bare.
                    val res = if (first is IppSendResult.Failed && index == 0 &&
                        IppPrintClient.isFormatOrOptionRejection(first.statusCode) && first.statusCode != 0x040A
                    ) sender(p.file, plan.mime, opts.copy(minimal = true), progress) else first
                    when (res) {
                        is IppSendResult.Accepted -> {
                            update(job.id) { it.copy(remoteJobId = res.jobId, status = JobStatus.PRINTING, statusMessage = "Printer accepted the job…", progress = base + span) }
                            if (res.jobId > 0) onAccepted(res.jobId)
                        }
                        is IppSendResult.Failed -> {
                            if (index == 0 && IppPrintClient.isFormatOrOptionRejection(res.statusCode)) {
                                refused = true
                                lastError = res.message
                            } else throw PrintFailure(res.message, res.retryable)
                        }
                        is IppSendResult.TlsUntrusted -> throw PrintFailure(
                            "PrintGo does not trust this printer's certificate yet. Open Printers, tap the printer and connect again.", false
                        )
                    }
                    if (refused) break
                }
                temps.forEach { it.delete() }
                temps.clear()
                if (!refused) return
            }
            throw AllFormatsRefused(lastError ?: "The printer rejected every file format PrintGo can send. Use \"Print with system dialog\".")
        } finally {
            temps.forEach { it.delete() }
        }
    }

    /** The raw language of a network printer: Settings choice, then what the printer says about itself. */
    private suspend fun rawLanguageFor(job: PrintJobEntity, host: String): RawLanguage? {
        val prefs = com.printgo.app.data.prefs.AppPreferences(appContext).current()
        if (prefs.bluetoothLanguage != "AUTO") return LanguagePicker.pick(null, job.printerName, prefs.bluetoothLanguage)
        val deviceId = withContext(Dispatchers.IO) { com.printgo.app.data.discovery.SnmpDeviceId.query(host) }
        val formats = job.formatsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return LanguagePicker.pick(deviceId, job.printerName, "AUTO", strict = false, formats = formats)
    }

    /**
     * Port-9100 printers take raw data in their own language. Office lasers (PCL/PostScript) are sent raw
     * directly: that is the proven route. Printers whose language is unknown or harder (Epson, PDF) are
     * asked over IPP first, which most of them also answer. Raw PDF is never guessed.
     */
    private suspend fun sendRawNetwork(job: PrintJobEntity, printer: Printer, pdf: File) {
        update(job.id) { it.copy(statusMessage = "Finding out which language the printer reads…", progress = 0.12f) }
        val lang = rawLanguageFor(job, printer.host)
        if (lang == RawLanguage.PCL || lang == RawLanguage.POSTSCRIPT || lang == RawLanguage.ESCPOS) {
            sendRaw(job, printer, pdf, lang)
            return
        }
        val ippOpen = withContext(Dispatchers.IO) { Transports.isPortOpen(printer.host, 631) }
        if (ippOpen) {
            val ippTry = printer.copy(protocol = PrinterProtocol.IPP, port = 631, resourcePath = "ipp/print")
            val probe = ipp.probe(ippTry, null)
            if (probe != null) {
                val target = ippTry.copy(resourcePath = probe.path.ifBlank { "ipp/print" })
                val sender: IppSend = { f, mime, o, cb -> ipp.printJob(target, null, f, mime, o, cb) }
                try {
                    runIppJob(job, pdf, probe, "IPP", sender) { remoteId -> track(job.id, target, null, remoteId) }
                    complete(job.id, "Printed on ${job.printerName}.")
                    return
                } catch (e: AllFormatsRefused) {
                    // Nothing was accepted over IPP; the printer's own raw language is still worth a try.
                    if (lang == null) throw PrintFailure(e.message ?: "The printer refused the job.", false)
                }
            }
        }
        if (lang == null) throw PrintFailure(
            "This printer does not say which language it reads. " +
                "Choose it in Settings → Raw printer language, or use \"Print with system dialog\".", false
        )
        sendRaw(job, printer, pdf, lang)
    }

    private suspend fun sendRaw(job: PrintJobEntity, printer: Printer, pdf: File, lang: RawLanguage) {
        // Many printers announce LPD (port 515) as well as raw 9100. LPD is a different protocol, so raw data
        // sent there comes out as a few lines of junk. Prefer 9100; speak real LPD only when 9100 is closed.
        val savedPort = if (printer.port > 0 && printer.port != 631) printer.port else 9100
        val (port, useLpd) = withContext(Dispatchers.IO) {
            when {
                savedPort == 515 && Transports.isPortOpen(printer.host, 9100) -> 9100 to false
                savedPort == 515 -> 515 to true
                else -> savedPort to false
            }
        }
        val via = if (useLpd) "LPD 515" else "port $port"
        update(job.id) { it.copy(method = "Network ($via) • ${lang.label}", statusMessage = "Preparing pages…", progress = 0.25f) }
        val tmp = File(appContext.cacheDir, "job_${job.id}.raw")
        try {
            withContext(Dispatchers.Default) { renderRawFile(job, lang, pdf, tmp, pjl = false) }
            update(job.id) { it.copy(status = JobStatus.SENDING, statusMessage = "Sending to printer…", progress = 0.5f) }
            withContext(Dispatchers.IO) {
                val onProgress: (Float) -> Unit = { p -> scope.launch { update(job.id) { j -> j.copy(progress = 0.5f + 0.45f * p) } } }
                if (useLpd) {
                    val q = printer.resourcePath.trim().trim('/').takeIf { it.isNotBlank() && !it.equals("text", true) && !it.startsWith("ipp") } ?: "raw"
                    Transports.sendLpd(printer.host, 515, q, job.title, tmp, onProgress)
                } else {
                    Transports.sendRaw9100(printer.host, port, tmp, onProgress)
                }
            }
        } finally { tmp.delete() }
        complete(job.id, "Sent to ${job.printerName}.")
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

    /** Page order for client-side copies: 0,1,2,0,1,2 (collated) or 0,0,1,1,2,2. */
    private fun pageOrder(pages: Int, copies: Int, collate: Boolean): List<Int> {
        val c = copies.coerceAtLeast(1)
        return if (collate) (0 until c).flatMap { 0 until pages } else (0 until pages).flatMap { p -> List(c) { p } }
    }

    /** Raster size of the (portrait) paper at [dpi], rounded down like CUPS does. */
    private fun rasterSize(job: PrintJobEntity, dpi: Int): Pair<Int, Int> {
        val shortMm = minOf(job.paperWidthMm, job.paperHeightMm)
        val longMm = maxOf(job.paperWidthMm, job.paperHeightMm)
        return (shortMm * dpi / 25.4f).toInt() to (longMm * dpi / 25.4f).toInt()
    }

    private fun renderPwg(job: PrintJobEntity, pdf: File, out: File, dpi: Int, grayscale: Boolean, twoSided: Boolean, copies: Int) {
        val (ptW, ptH) = IppMedia.pointsFor(PaperSize(job.paperName, minOf(job.paperWidthMm, job.paperHeightMm), maxOf(job.paperWidthMm, job.paperHeightMm)))
        val tumble = twoSided && job.duplex == DuplexMode.TWO_SIDED_SHORT_EDGE.name
        val quality = when (job.quality) { PrintQuality.DRAFT.name -> 3; PrintQuality.HIGH.name -> 5; else -> 4 }
        val sizeName = IppMedia.keywordFor(paperOf(job))
        PdfPageRenderer(pdf).use { r ->
            val order = pageOrder(r.pageCount, copies, job.collate)
            out.outputStream().buffered(256 * 1024).use { os ->
                PwgRasterEncoder.writeDocumentStart(os)
                val (w, h) = rasterSize(job, dpi)
                for (i in order) {
                    r.openRows(i, w, h).use { rows ->
                        val header = PwgRasterEncoder.buildPageHeader(
                            rows.width, rows.height, dpi, grayscale, twoSided, tumble, 1, ptW, ptH, quality, order.size, sizeName
                        )
                        PwgRasterEncoder.writePage(os, rows, header, grayscale)
                    }
                }
            }
        }
    }

    private fun renderUrf(job: PrintJobEntity, pdf: File, out: File, dpi: Int, grayscale: Boolean, twoSided: Boolean, copies: Int) {
        val quality = when (job.quality) { PrintQuality.DRAFT.name -> 3; PrintQuality.HIGH.name -> 5; else -> 4 }
        val duplex = if (!twoSided) 0 else if (job.duplex == DuplexMode.TWO_SIDED_SHORT_EDGE.name) 2 else 1
        PdfPageRenderer(pdf).use { r ->
            val order = pageOrder(r.pageCount, copies, job.collate)
            out.outputStream().buffered(256 * 1024).use { os ->
                UrfEncoder.writeDocumentStart(os, order.size)
                val (w, h) = rasterSize(job, dpi)
                for (i in order) {
                    r.openRows(i, w, h).use { rows ->
                        UrfEncoder.writePage(os, rows, dpi, grayscale, duplex, quality)
                    }
                }
            }
        }
    }

    internal fun pickLanguage(deviceId: String?, name: String, btPref: String, strict: Boolean = false): RawLanguage? =
        LanguagePicker.pick(deviceId, name, btPref, strict)

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
                            val bmp = r.renderForPortraitPaper(i, (minOf(job.paperWidthMm, job.paperHeightMm) / 25.4f * dpi).toInt())
                            PclEncoder.writePage(os, bmp, dpi, PclEncoder.logicalOffset300(job.paperWidthMm) * dpi / 300)
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
                            val bmp = r.renderForPortraitPaper(i, (minOf(job.paperWidthMm, job.paperHeightMm) / 25.4f * dpi).toInt())
                            PostScriptEncoder.writePage(os, bmp, ptW, ptH, grayscale, copies, duplexCode(job))
                            r.clearCache()
                        }
                    }
                    PostScriptEncoder.end(os)
                }
                RawLanguage.ESCPR -> {
                    val g = EscprEncoder.geometry(minOf(job.paperWidthMm, job.paperHeightMm), maxOf(job.paperWidthMm, job.paperHeightMm), 360)
                    val c = java.util.Calendar.getInstance()
                    val time = intArrayOf(
                        c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH),
                        c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND)
                    )
                    val quality = when (job.quality) {
                        PrintQuality.DRAFT.name -> EscprEncoder.QUALITY_DRAFT
                        PrintQuality.HIGH.name -> EscprEncoder.QUALITY_HIGH
                        else -> EscprEncoder.QUALITY_NORMAL
                    }
                    EscprEncoder.startJob(os, g, color = !grayscale, quality = quality, time = time)
                    PdfPageRenderer(pdf).use { r ->
                        val order = pageOrder(r.pageCount, copies, job.collate)
                        order.forEachIndexed { n, i ->
                            r.openRows(i, g.paperW, g.paperH).use { rows ->
                                EscprEncoder.writePage(os, rows, g, n + 1, order.size - 1 - n, grayscale)
                            }
                        }
                    }
                    EscprEncoder.endJob(os)
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
            // 1) Printers with the standard IPP-over-USB interface take the same jobs as Wi-Fi printers.
            var ippProblem: String? = null
            val session = withContext(Dispatchers.IO) { UsbSession.open(appContext, printer.host) }
            val handled = session.use { s ->
                if (!s.isIppUsb) false
                else try {
                    sendIppUsb(job, s, pdf)
                } catch (e: AllFormatsRefused) {
                    ippProblem = e.message
                    false
                }
            }
            if (handled) { complete(job.id, "Sent to ${job.printerName} over USB."); return }

            // 2) Otherwise the printer's own language on the classic printer interface.
            val raw = try {
                withContext(Dispatchers.IO) { UsbSession.open(appContext, printer.host, preferIppUsb = false) }
            } catch (e: TransportException) {
                throw PrintFailure(ippProblem ?: e.message ?: "Could not open the USB printer.", false)
            }
            raw.use { s ->
                val id = withContext(Dispatchers.IO) { s.deviceId() } ?: s.namesAsDeviceId()
                val lang = LanguagePicker.pick(id, job.printerName, "AUTO", strict = true) ?: throw PrintFailure(
                    ippProblem ?: "This printer uses its own USB language that PrintGo cannot create. Connect it over Wi-Fi, or use \"Print with system dialog\".",
                    false
                )
                update(job.id) { it.copy(method = "USB • ${lang.label}", statusMessage = "Preparing pages…", progress = 0.25f) }
                val tmp = File(appContext.cacheDir, "job_${job.id}.raw")
                try {
                    val pjl = "PJL" in PclEncoder.parseCmd(id)
                    withContext(Dispatchers.Default) { renderRawFile(job, lang, pdf, tmp, pjl) }
                    update(job.id) { it.copy(statusMessage = "Sending to printer…", progress = 0.6f) }
                    val total = tmp.length().coerceAtLeast(1)
                    var sent = 0L
                    withContext(Dispatchers.IO) {
                        for (c in fileChunks(tmp)) {
                            s.write(c)
                            sent += c.size
                            val frac = sent.toFloat() / total
                            scope.launch { update(job.id) { j -> j.copy(progress = 0.6f + 0.35f * frac) } }
                        }
                    }
                } finally { tmp.delete() }
            }
        } else {
            val lang = LanguagePicker.pick(null, job.printerName, prefs.bluetoothLanguage) ?: RawLanguage.ESCPOS
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

    /**
     * IPP over USB: same format choice as Wi-Fi. Returns false when the printer could not be asked, so the
     * caller can fall back to the printer's raw language. Throws [AllFormatsRefused] when it refused all.
     */
    private suspend fun sendIppUsb(job: PrintJobEntity, s: UsbSession, pdf: File): Boolean {
        val uri = "ipp://localhost/ipp/print"
        update(job.id) { it.copy(method = "USB • IPP", statusMessage = "Asking the printer what it accepts…", progress = 0.2f) }
        val probe = try {
            withContext(Dispatchers.IO) {
                val req = IppEncoder.encode(IppEncoder.createGetPrinterAttributesRequest(uri, 1, listOf("all", "media-col-database")))
                val resp = IppDecoder.decode(s.ippRequest(req))
                if (resp.isSuccess) com.printgo.app.data.ipp.IppProbe.from(resp, "ipp/print") else null
            }
        } catch (e: TransportException) {
            null
        } catch (e: java.io.IOException) {
            null
        }
        if (probe == null) return false

        val sender: IppSend = { f, mime, o, cb ->
            withContext(Dispatchers.IO) {
                try {
                    val resp = IppDecoder.decode(s.ippRequest(ipp.buildPrintJob(uri, mime, o), f, cb))
                    if (resp.isSuccess) IppSendResult.Accepted(resp.getInt("job-id") ?: 0, resp.getString("job-uri"), resp.code.toInt())
                    else {
                        val code = resp.code.toInt()
                        IppSendResult.Failed(IppPrintClient.statusText(code), code, code == 0x0502 || code == 0x0501 || code == 0x0507)
                    }
                } catch (e: TransportException) {
                    IppSendResult.Failed(e.message ?: "The USB printer stopped answering.", IppPrintClient.NO_CONNECTION)
                }
            }
        }
        runIppJob(job, pdf, probe, "USB • IPP", sender) { }
        return true
    }
}

/** Sends one prepared file with Print-Job over some IPP channel (Wi-Fi or USB). */
private typealias IppSend = suspend (file: File, mime: String, options: IppJobOptions, onProgress: (Float) -> Unit) -> IppSendResult
