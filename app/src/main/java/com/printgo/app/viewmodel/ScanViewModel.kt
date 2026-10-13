package com.printgo.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.data.print.PdfSaver
import com.printgo.app.data.scan.CropMode
import com.printgo.app.data.scan.CropShape
import com.printgo.app.data.scan.JpegPdfWriter
import com.printgo.app.data.scan.PageDetector
import com.printgo.app.data.scan.Quad
import com.printgo.app.data.scan.ScanFilter
import com.printgo.app.data.scan.ScanGeometry
import com.printgo.app.data.scan.ScanImageIO
import com.printgo.app.data.scan.ScanPage
import com.printgo.app.data.scan.ScanPageList
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Why the scanner was opened. */
enum class ScanPurpose { PHOTOS, DOCUMENT }

/** Paper for a scanned PDF. */
enum class ScanPaper(val label: String, val widthMm: Double, val heightMm: Double) {
    A4("A4", 210.0, 297.0),
    LETTER("Letter", 215.9, 279.4),
    A5("A5", 148.0, 210.0),
    LEGAL("Legal", 215.9, 355.6),
    PHOTO_4X6("4x6", 101.6, 152.4);

    /** Long side in pixels at 300 dpi, after the PDF margins. */
    fun maxLongPx(marginMm: Double): Int = ((maxOf(widthMm, heightMm) - 2 * marginMm) / 25.4 * 300).toInt()
}

data class ScanUiState(
    val purpose: ScanPurpose = ScanPurpose.DOCUMENT,
    val pages: List<ScanPage> = emptyList(),
    val index: Int = 0,
    val paper: ScanPaper = ScanPaper.A4,
    /** A plain message while pages are being prepared ("Preparing page 2 of 3…"), or null. */
    val busy: String? = null,
    val detecting: Boolean = false
) {
    val current: ScanPage? get() = pages.getOrNull(index)
}

/**
 * State of the scanner: the pages, their crop/filter choices, small pictures for editing and the finished
 * files. Shared by the Scan/Edit screen and the multi-page "Scan to PDF" screen.
 */
@OptIn(FlowPreview::class)
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    /** Upright picture of the current page with the user's turns, for the crop view. */
    private val _display = MutableStateFlow<Bitmap?>(null)
    val display: StateFlow<Bitmap?> = _display.asStateFlow()

    /** Cropped, straightened and filtered preview of the current page. */
    private val _result = MutableStateFlow<Bitmap?>(null)
    val result: StateFlow<Bitmap?> = _result.asStateFlow()

    /** Small previews of every filter, made from the current page. */
    private val _filterThumbs = MutableStateFlow<Map<ScanFilter, Bitmap>>(emptyMap())
    val filterThumbs: StateFlow<Map<ScanFilter, Bitmap>> = _filterThumbs.asStateFlow()

    /** Finished-looking thumbnails per page (key = [ScanPage.renderKey]). */
    private val _pageThumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val pageThumbs: StateFlow<Map<String, Bitmap>> = _pageThumbs.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /** Pages as they were when the edit screen opened (for Cancel). */
    private var beforeEdit: ScanUiState? = null

    /** Original print items for the photos being edited (page id → item). */
    private val originals = HashMap<String, PrintItem>()

    /** Finished files already made (render key → file), so Print and Save do not repeat the work. */
    private val rendered = HashMap<String, File>()
    private val renderLock = Mutex()

    private val uprightCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtLeast(16 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { runCatching { ScanImageIO.cleanOldFiles(app) } }
        // Upright picture follows the current page and its turns
        viewModelScope.launch {
            _state.map { it.current?.let { p -> p.source to p.quarterTurns } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null) { _display.value = null; return@collectLatest }
                    _display.value = withContext(Dispatchers.Default) {
                        try { turned(key.first, key.second) } catch (e: Throwable) { null }
                    }
                    if (_display.value == null) _messages.value = "Could not open this picture."
                }
        }
        // Live preview and filter thumbnails follow crop, filter and strength
        viewModelScope.launch {
            _state.map { it.current?.let { p -> Triple(p.renderKey, p, Unit) } }
                .distinctUntilChanged { a, b -> a?.first == b?.first }
                .debounce(120)
                .collectLatest { t ->
                    val page = t?.second
                    if (page == null) { _result.value = null; _filterThumbs.value = emptyMap(); return@collectLatest }
                    try {
                        val disp = withContext(Dispatchers.Default) { turned(page.source, page.quarterTurns) } ?: return@collectLatest
                        val flat = withContext(Dispatchers.Default) { ScanImageIO.warp(disp, 0, page.quad, 1400) }
                        _result.value = withContext(Dispatchers.Default) {
                            ScanImageIO.applyFilter(flat, page.filter, page.strength); flat
                        }
                        val small = withContext(Dispatchers.Default) { ScanImageIO.warp(disp, 0, page.quad, 220) }
                        _filterThumbs.value = withContext(Dispatchers.Default) {
                            ScanFilter.entries.associateWith { ScanImageIO.filtered(small, it, 1f) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // A preview that cannot be made (e.g. low memory) must not close the app.
                    }
                }
        }
    }

    // ---------------- starting ----------------

    /** "Scan before Print": edit the selected photos. Non-picture items are left out. */
    fun startPhotos(items: List<PrintItem>) {
        originals.clear()
        val pages = items.filter { it.type == PrintItemType.IMAGE }.map { item ->
            val id = UUID.randomUUID().toString()
            originals[id] = item
            ScanPage(id = id, source = item.uri.toString(), displayName = item.displayName)
        }
        _state.value = ScanUiState(purpose = ScanPurpose.PHOTOS, pages = pages, index = 0)
    }

    /** New multi-page scan (from Home or the camera button). */
    fun startDocument() {
        originals.clear()
        _state.value = ScanUiState(purpose = ScanPurpose.DOCUMENT)
    }

    /** Adds a camera photo or picked picture as a new page (opened in 4-corner crop with page detection). */
    fun addPage(file: File, name: String) {
        val page = ScanPage(
            id = UUID.randomUUID().toString(),
            source = Uri.fromFile(file).toString(),
            displayName = name,
            cropMode = CropMode.CORNERS
        )
        _state.update { it.copy(pages = it.pages + page, index = it.pages.size) }
    }

    fun beginEdit() { beforeEdit = _state.value }

    fun cancelEdit() {
        beforeEdit?.let { saved -> _state.update { saved.copy(busy = null, detecting = false) } }
        beforeEdit = null
    }

    fun select(index: Int) = _state.update { if (index in it.pages.indices) it.copy(index = index) else it }

    // ---------------- editing ----------------

    private fun updateCurrent(f: (ScanPage) -> ScanPage) = _state.update { s ->
        val p = s.current ?: return@update s
        s.copy(pages = ScanPageList.replace(s.pages, f(p)))
    }

    fun setQuad(q: Quad) = updateCurrent { it.copy(quad = q) }

    fun rotate(quarterTurns: Int) = updateCurrent {
        it.copy(quarterTurns = ((it.quarterTurns + quarterTurns) % 4 + 4) % 4, quad = ScanGeometry.rotateQuad(it.quad, quarterTurns))
    }

    fun resetCrop() = updateCurrent { it.copy(quad = Quad.FULL, cropMode = CropMode.FREE) }

    fun setCropMode(mode: CropMode) {
        val page = _state.value.current ?: return
        when (mode) {
            CropMode.FREE -> updateCurrent { it.copy(cropMode = mode, quad = it.quad.bounds()) }
            CropMode.PAPER -> updateCurrent { it.copy(cropMode = mode) }.also { fitShape() }
            CropMode.CORNERS -> {
                updateCurrent { it.copy(cropMode = mode) }
                if (!page.detected) detectPage()
            }
        }
    }

    fun setShape(shape: CropShape) { updateCurrent { it.copy(shape = shape) }; fitShape() }

    fun setLandscape(landscape: Boolean) { updateCurrent { it.copy(landscape = landscape) }; fitShape() }

    /** Fits a crop of the chosen paper shape onto the picture. */
    private fun fitShape() {
        val b = _display.value
        updateCurrent { p ->
            val aspect = imageAspect(p, b)
            p.copy(quad = ScanGeometry.fitRatio(p.quad.bounds(), p.shape.ratio(p.landscape), aspect))
        }
    }

    /** Width ÷ height of the current page as shown (after turns). */
    fun imageAspect(p: ScanPage, shown: Bitmap? = _display.value): Double {
        if (shown != null) return shown.width.toDouble() / shown.height
        val (w, h) = ScanImageIO.bounds(app, Uri.parse(p.source)) ?: return 0.75
        val (tw, th) = ScanGeometry.turnedSize(w, h, ScanImageIO.exifRotation(app, Uri.parse(p.source)) + p.quarterTurns * 90)
        return tw.toDouble() / th
    }

    fun setFilter(filter: ScanFilter) = updateCurrent { it.copy(filter = filter) }
    fun setStrength(strength: Float) = updateCurrent { it.copy(strength = strength.coerceIn(0f, 1f)) }

    fun applyFilterToAll() {
        val p = _state.value.current ?: return
        _state.update { it.copy(pages = ScanPageList.applyFilterToAll(it.pages, p.filter, p.strength)) }
        _messages.value = "${p.filter.label} used on all ${_state.value.pages.size} pages"
    }

    fun applyFilterToAllPages(filter: ScanFilter) {
        _state.update { it.copy(pages = ScanPageList.applyFilterToAll(it.pages, filter, 1f)) }
    }

    /** Finds the page edges and places the four corners there (or slightly inside the photo). */
    fun detectPage() {
        val page = _state.value.current ?: return
        if (_state.value.detecting) return
        _state.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                val b = runCatching { turned(page.source, page.quarterTurns) }.getOrNull() ?: return@withContext null
                runCatching {
                    val (lum, w, h) = ScanImageIO.luma(b, 640)
                    PageDetector.detect(lum, w, h)
                }.getOrNull()
            }
            _state.update { s ->
                val p = s.pages.firstOrNull { it.id == page.id } ?: return@update s.copy(detecting = false)
                val quad = found?.quad ?: ScanGeometry.insetQuad()
                s.copy(
                    detecting = false,
                    pages = ScanPageList.replace(s.pages, p.copy(quad = quad, detected = true, cropMode = CropMode.CORNERS))
                )
            }
            _messages.value = if (found != null) "Page edges found. Check the corners." else
                "Could not find the page edges. Drag the corners onto the page."
        }
    }

    // ---------------- pages ----------------

    fun moveUp(index: Int) = _state.update { it.copy(pages = ScanPageList.moveUp(it.pages, index)) }
    fun moveDown(index: Int) = _state.update { it.copy(pages = ScanPageList.moveDown(it.pages, index)) }

    fun delete(index: Int) = _state.update { s ->
        val p = s.pages.getOrNull(index) ?: return@update s
        val left = ScanPageList.remove(s.pages, p.id)
        s.copy(pages = left, index = ScanPageList.indexAfterRemove(s.index.coerceAtMost(left.size - 1), left.size))
    }

    fun setPaper(paper: ScanPaper) = _state.update { it.copy(paper = paper) }

    fun clearMessage() { _messages.value = null }

    /** Makes a small finished-looking thumbnail of a page for the page list. */
    fun ensureThumb(page: ScanPage) {
        if (_pageThumbs.value.containsKey(page.renderKey)) return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                runCatching {
                    val disp = turned(page.source, page.quarterTurns) ?: return@runCatching null
                    ScanImageIO.warp(disp, 0, page.quad, 300).also { ScanImageIO.applyFilter(it, page.filter, page.strength) }
                }.getOrNull()
            } ?: return@launch
            _pageThumbs.update { it + (page.renderKey to bmp) }
        }
    }

    // ---------------- finishing ----------------

    /**
     * "Done" for Scan before Print: makes the edited photos at print resolution and returns the print item to use
     * for each original (unchanged photos keep the original item).
     */
    suspend fun finishPhotos(): Map<Uri, PrintItem> {
        val pages = _state.value.pages
        val out = LinkedHashMap<Uri, PrintItem>()
        val files = renderAll(pages, A4_MAX_LONG, skipUnchanged = true)
        pages.forEachIndexed { i, p ->
            val original = originals[p.id] ?: return@forEachIndexed
            val f = files[i] ?: return@forEachIndexed
            out[original.uri] = PrintItem(
                uri = Uri.fromFile(f),
                displayName = "Scan ${original.displayName}",
                mimeType = "image/jpeg",
                sizeBytes = f.length(),
                type = PrintItemType.IMAGE
            )
        }
        beforeEdit = null
        return out
    }

    fun endEdit() { beforeEdit = null }

    /** Builds one PDF from all pages (paper size from the state, small margins). */
    suspend fun buildPdf(): File {
        val s = _state.value
        require(s.pages.isNotEmpty()) { "Add a page first." }
        val files = renderAll(s.pages, s.paper.maxLongPx(MARGIN_MM), skipUnchanged = false).map { it!! }
        _state.update { it.copy(busy = "Making the PDF…") }
        try {
            return withContext(Dispatchers.IO) {
                val name = "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
                val pdf = File(ScanImageIO.scanDir(app, "pdf"), "$name.pdf")
                pdf.outputStream().buffered().use { JpegPdfWriter.write(it, files, s.paper.widthMm, s.paper.heightMm, MARGIN_MM) }
                pdf
            }
        } finally {
            _state.update { it.copy(busy = null) }
        }
    }

    /** The PDF as an item for the print preview. */
    fun pdfItem(pdf: File) = PrintItem(
        uri = Uri.fromFile(pdf),
        displayName = pdf.name,
        mimeType = "application/pdf",
        sizeBytes = pdf.length(),
        type = PrintItemType.PDF
    )

    /** Saves the PDF in Downloads/PrintGo (same route as "Save as PDF" printing). */
    suspend fun savePdf(pdf: File): Uri = withContext(Dispatchers.IO) {
        PdfSaver.saveToDownloads(app, pdf, pdf.nameWithoutExtension)
    }

    private suspend fun renderAll(pages: List<ScanPage>, maxLong: Int, skipUnchanged: Boolean): List<File?> =
        renderLock.withLock {
            val out = ArrayList<File?>()
            try {
                pages.forEachIndexed { i, p ->
                    if (skipUnchanged && p.isUnchanged) { out += null; return@forEachIndexed }
                    _state.update { it.copy(busy = if (pages.size > 1) "Preparing page ${i + 1} of ${pages.size}…" else "Preparing your picture…") }
                    val key = p.renderKey + "|" + maxLong
                    val ready = rendered[key]?.takeIf { it.exists() }
                    out += ready ?: withContext(Dispatchers.Default) {
                        val f = File(ScanImageIO.scanDir(app, "out"), "page_${UUID.randomUUID().toString().take(12)}.jpg")
                        ScanImageIO.renderPage(app, p, maxLong, f)
                    }.also { rendered[key] = it }
                }
            } finally {
                _state.update { it.copy(busy = null) }
            }
            out
        }

    // ---------------- pictures ----------------

    @Synchronized
    private fun upright(source: String): Bitmap? {
        uprightCache.get(source)?.let { return it }
        val b = ScanImageIO.loadUpright(app, Uri.parse(source), 1600) ?: return null
        uprightCache.put(source, b)
        return b
    }

    @Synchronized
    private fun turned(source: String, quarterTurns: Int): Bitmap? {
        val t = ((quarterTurns % 4) + 4) % 4
        if (t == 0) return upright(source)
        val key = "$source#$t"
        uprightCache.get(key)?.let { return it }
        val b = ScanImageIO.rotate(upright(source) ?: return null, t * 90)
        uprightCache.put(key, b)
        return b
    }

    companion object {
        /** PDF page margins. */
        const val MARGIN_MM = 6.0
        /** Long side of an edited photo: A4 at 300 dpi. */
        const val A4_MAX_LONG = 3508
    }
}
