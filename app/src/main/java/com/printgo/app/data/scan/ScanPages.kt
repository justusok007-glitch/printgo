package com.printgo.app.data.scan

/** How the crop is edited. */
enum class CropMode(val label: String) { FREE("Free"), PAPER("Paper shape"), CORNERS("4 corners") }

/**
 * One scanned page or photo being edited. Only the edit choices are kept here; the user's original picture is
 * never changed. [quad] is relative (0..1) to the picture turned by [quarterTurns]×90° clockwise.
 */
data class ScanPage(
    val id: String,
    val source: String,
    val displayName: String,
    val quarterTurns: Int = 0,
    val quad: Quad = Quad.FULL,
    val cropMode: CropMode = CropMode.FREE,
    val shape: CropShape = CropShape.A4,
    val landscape: Boolean = false,
    val filter: ScanFilter = ScanFilter.ORIGINAL,
    val strength: Float = 1f,
    /** Automatic page detection has already run for this page (it runs once, then the user's corners stay). */
    val detected: Boolean = false
) {
    /** Everything that changes the printed result (used to reuse an already prepared file). */
    val renderKey: String
        get() = listOf(
            source, ((quarterTurns % 4) + 4) % 4,
            quad.points.joinToString(";") { "%.4f,%.4f".format(java.util.Locale.US, it.x, it.y) },
            filter.name, "%.2f".format(java.util.Locale.US, strength)
        ).joinToString("|")

    /** True when nothing was changed (the original picture can be printed as it is). */
    val isUnchanged: Boolean
        get() = ((quarterTurns % 4) + 4) % 4 == 0 && quad == Quad.FULL &&
            (filter == ScanFilter.ORIGINAL || strength <= 0f)
}

/** List operations for the pages of a scan (pure, tested). */
object ScanPageList {

    fun move(pages: List<ScanPage>, from: Int, to: Int): List<ScanPage> {
        if (from !in pages.indices) return pages
        val target = to.coerceIn(0, pages.size - 1)
        if (from == target) return pages
        val m = pages.toMutableList()
        val p = m.removeAt(from)
        m.add(target, p)
        return m
    }

    fun moveUp(pages: List<ScanPage>, index: Int) = move(pages, index, index - 1)
    fun moveDown(pages: List<ScanPage>, index: Int) = move(pages, index, index + 1)

    fun remove(pages: List<ScanPage>, id: String): List<ScanPage> = pages.filterNot { it.id == id }

    fun replace(pages: List<ScanPage>, page: ScanPage): List<ScanPage> =
        pages.map { if (it.id == page.id) page else it }

    fun applyFilterToAll(pages: List<ScanPage>, filter: ScanFilter, strength: Float): List<ScanPage> =
        pages.map { it.copy(filter = filter, strength = strength) }

    /**
     * Result of automatic page detection for [page]. [quadAtStart] is the crop when detection started; if the
     * user changed the crop meanwhile, or the page was already detected, nothing changes (returns the page as it
     * is). A found page switches to the 4-corner crop. When nothing is found, a document page gets corners
     * slightly inside the picture; an ordinary photo ([keepIfNotFound]) is left untouched so it is not cut.
     */
    fun applyDetection(page: ScanPage, quadAtStart: Quad, found: Quad?, keepIfNotFound: Boolean): ScanPage {
        if (page.detected || page.quad != quadAtStart) return page
        return when {
            found != null -> page.copy(quad = found, cropMode = CropMode.CORNERS, detected = true)
            keepIfNotFound -> page
            else -> page.copy(quad = ScanGeometry.insetQuad(), cropMode = CropMode.CORNERS, detected = true)
        }
    }

    /** Index to show after the page at [removedIndex] was deleted from a list that now has [newSize] pages. */
    fun indexAfterRemove(removedIndex: Int, newSize: Int): Int =
        if (newSize <= 0) 0 else removedIndex.coerceIn(0, newSize - 1)
}
