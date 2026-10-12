package com.printgo.app.data.print

import android.content.Context
import com.printgo.app.data.converter.PdfPageRenderer
import com.printgo.app.data.drivers.DriverEntry
import com.printgo.app.data.drivers.DriverKind
import com.printgo.app.data.drivers.DriverPacks
import com.printgo.app.data.drivers.DriverRunner
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PrintQuality
import java.io.File

/** What a driver needs to know about a job. The PDF already holds the laid-out sheets. */
data class DriverJobSpec(
    val pdf: File,
    val title: String,
    val paperWidthMm: Int,
    val paperHeightMm: Int,
    val color: Boolean,
    val duplex: DuplexMode,
    val quality: PrintQuality,
    val copies: Int,
    val collate: Boolean
)

class DriverFailure(message: String) : Exception(message)

/**
 * Prints a job's sheets with one of the built-in printer drivers and writes the printer's own data to a
 * file, ready to send over USB, Bluetooth or the network:
 *  - Epson ESC/P-R: Epson's own filter program, fed rows at 360 dpi.
 *  - Gutenprint: pg_gutenprint with the Gutenprint data pack.
 *  - HPLIP and SpliX: pg_cupsraster turns the pages into CUPS raster for the model's PPD and runs hpcups or
 *    rastertoqpdl on it.
 */
object NativeDrivers {

    /** Epson's ESC/P-R driver works the same for every ESC/P-R model, so one entry covers them all. */
    val EPSON_ESCPR = DriverEntry("escpr:generic", DriverKind.ESCPR, "Epson", "Epson inkjet (ESC/P-R)", duplex = true)

    fun canRun(context: Context, entry: DriverEntry): Boolean =
        DriverRunner.isAvailable(context, entry.kind.program) &&
            (entry.kind != DriverKind.HPCUPS && entry.kind != DriverKind.SPLIX || DriverRunner.isAvailable(context, "pg_cupsraster"))

    /** The data pack this driver still needs, or null when it is ready. */
    fun missingPack(context: Context, entry: DriverEntry): DriverPacks.Pack? {
        val name = entry.kind.pack ?: return null
        val packs = DriverPacks(context)
        if (packs.isInstalled(name)) return null
        return packs.packs[name] ?: DriverPacks.Pack(name, "", 0, "", "", "")
    }

    fun label(entry: DriverEntry) = "${entry.displayName} (${entry.kind.label})"

    /** A blank page in [pageOrder]. */
    const val BLANK = -1

    /**
     * Page order for copies made by repeating pages: 0,1,2,0,1,2 (collated) or 0,0,1,1,2,2. Two-sided copies
     * are always collated, and a document with an odd number of pages gets a blank back after each copy so
     * the next copy starts on a new sheet.
     */
    fun pageOrder(pages: Int, copies: Int, collate: Boolean, twoSided: Boolean = false): List<Int> {
        val c = copies.coerceIn(1, 99)
        if (pages <= 0) return emptyList()
        if (twoSided) {
            val one = (0 until pages).toList() + if (pages % 2 == 1 && c > 1) listOf(BLANK) else emptyList()
            return (0 until c).flatMap { one }.let { if (it.lastOrNull() == BLANK) it.dropLast(1) else it }
        }
        return if (collate) (0 until c).flatMap { 0 until pages } else (0 until pages).flatMap { p -> List(c) { p } }
    }

    fun paperPt(spec: DriverJobSpec): String {
        val w = minOf(spec.paperWidthMm, spec.paperHeightMm) * 72f / 25.4f
        val h = maxOf(spec.paperWidthMm, spec.paperHeightMm) * 72f / 25.4f
        return "%.0fx%.0f".format(java.util.Locale.US, w, h)
    }

    private fun sheetPixels(spec: DriverJobSpec, dpi: Int): Pair<Int, Int> =
        (minOf(spec.paperWidthMm, spec.paperHeightMm) * dpi / 25.4f).toInt() to
            (maxOf(spec.paperWidthMm, spec.paperHeightMm) * dpi / 25.4f).toInt()

    fun duplexKeyword(d: DuplexMode) = when (d) {
        DuplexMode.ONE_SIDED -> "None"
        DuplexMode.TWO_SIDED_LONG_EDGE -> "DuplexNoTumble"
        DuplexMode.TWO_SIDED_SHORT_EDGE -> "DuplexTumble"
    }

    /** CUPS options for pg_cupsraster (HPLIP, SpliX). */
    fun cupsOptions(spec: DriverJobSpec): String = listOf(
        "PaperPt=${paperPt(spec)}",
        "PGColor=${if (spec.color) "color" else "gray"}",
        "PGQuality=${when (spec.quality) { PrintQuality.DRAFT -> "draft"; PrintQuality.HIGH -> "high"; else -> "normal" }}",
        "Duplex=${duplexKeyword(spec.duplex)}"
    ).joinToString(" ")

    /** Arguments for pg_gutenprint. Values a model does not offer are skipped by the program. */
    fun gutenprintArgs(entry: DriverEntry, spec: DriverJobSpec): List<String> {
        val args = mutableListOf(entry.file ?: entry.id.removePrefix("gp:"), "PaperPt=${paperPt(spec)}")
        args += "PrintingMode=${if (spec.color) "Color" else "BW"}"
        args += "Quality=${when (spec.quality) { PrintQuality.DRAFT -> "Draft"; PrintQuality.HIGH -> "High"; else -> "Standard" }}"
        if (spec.duplex != DuplexMode.ONE_SIDED) args += "Duplex=${duplexKeyword(spec.duplex)}"
        return args
    }

    private fun failure(entry: DriverEntry, r: DriverRunner.Result): DriverFailure {
        val why = r.error?.let { " ($it)" } ?: ""
        return DriverFailure("The ${entry.kind.label} driver could not prepare this job$why.")
    }

    /** Renders all sheets of [spec] with [entry]; the printer data ends up in [out]. */
    suspend fun render(context: Context, entry: DriverEntry, spec: DriverJobSpec, out: File) {
        if (!canRun(context, entry)) throw DriverFailure("The printer driver is missing from this copy of PrintGo.")
        missingPack(context, entry)?.let { throw DriverFailure("The ${it.label} driver pack is not installed yet.") }
        val pages = PdfPageRenderer(spec.pdf).use { it.pageCount }
        val order = pageOrder(pages, spec.copies, spec.collate, spec.duplex != DuplexMode.ONE_SIDED)
        val result = when (entry.kind) {
            DriverKind.ESCPR -> {
                val (w, h) = EscprStream.sheetPixels(spec.paperWidthMm, spec.paperHeightMm)
                val args = EscprStream.args(
                    w, h, spec.color, EscprStream.pageSizeName(spec.paperWidthMm, spec.paperHeightMm),
                    spec.quality == PrintQuality.HIGH, duplexKeyword(spec.duplex)
                )
                DriverRunner.run(context, entry.kind.program, args, out) { os ->
                    EscprStream.start(os, order.size)
                    PdfPageRenderer(spec.pdf).use { r ->
                        order.forEachIndexed { n, i ->
                            // Epson's driver turns two-sided back pages itself.
                            if (i == BLANK) EscprStream.writePage(os, BlankRowSource(w, h), order.size - 1 - n, spec.color)
                            else r.openRows(i, w, h).use { rows -> EscprStream.writePage(os, rows, order.size - 1 - n, spec.color) }
                        }
                    }
                }
            }
            DriverKind.GUTENPRINT -> {
                val dpi = if (spec.quality == PrintQuality.DRAFT) 150 else 300
                val data = File(DriverPacks(context).dir("gutenprint"), "xml")
                // Gutenprint handles two-sided pages itself (from PageNumber).
                writePgrJob(context, entry.kind.program, gutenprintArgs(entry, spec), mapOf("STP_DATA_PATH" to data.absolutePath),
                    spec, order, dpi, SheetFlip.NONE, out)
            }
            DriverKind.HPCUPS, DriverKind.SPLIX -> {
                val ppd = File(DriverPacks(context).dir(entry.kind.pack!!), entry.file ?: "")
                if (!ppd.isFile) throw DriverFailure("The driver pack is incomplete. Delete it in Settings and download it again.")
                val opts = cupsOptions(spec)
                val q = query(context, ppd, opts, spec)
                val filter = DriverRunner.program(context, entry.kind.program).absolutePath
                writePgrJob(context, "pg_cupsraster", listOf(filter, ppd.absolutePath, opts, spec.title.take(60)), emptyMap(),
                    spec, order, q.dpi, q.backSide, out)
            }
        }
        if (!result.ok) throw failure(entry, result)
        if (out.length() == 0L) throw DriverFailure("The ${entry.kind.label} driver produced nothing to print.")
    }

    /** What the PPD asks for: resolution and how back sides must be turned. */
    data class CupsQuery(val dpi: Int, val backSide: SheetFlip)

    /** Reads pg_cupsraster's --query answer. Resolution is capped at 600 dpi (300 for draft). */
    fun parseQuery(lines: List<String>, draft: Boolean): CupsQuery {
        fun value(key: String) = lines.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
        val dpi = value("hwres")?.split('x')?.mapNotNull { it.trim().toIntOrNull() }?.maxOrNull()?.takeIf { it > 0 }
        return CupsQuery((dpi ?: 300).coerceAtMost(if (draft) 300 else 600), SheetFlip.fromQuery(value("backside")))
    }

    private suspend fun query(context: Context, ppd: File, opts: String, spec: DriverJobSpec): CupsQuery {
        val tmp = File(context.cacheDir, "driver_query.txt")
        val draft = spec.quality == PrintQuality.DRAFT
        try {
            val r = DriverRunner.run(context, "pg_cupsraster", listOf("--query", ppd.absolutePath, opts), tmp, timeoutMinutes = 1) { }
            if (r.ok) return parseQuery(tmp.readLines(), draft)
        } finally {
            tmp.delete()
        }
        return CupsQuery(300, SheetFlip.NONE)
    }

    private suspend fun writePgrJob(
        context: Context,
        program: String,
        args: List<String>,
        env: Map<String, String>,
        spec: DriverJobSpec,
        order: List<Int>,
        dpi: Int,
        backFlip: SheetFlip,
        out: File
    ): DriverRunner.Result {
        val (w, h) = sheetPixels(spec, dpi)
        val twoSided = spec.duplex != DuplexMode.ONE_SIDED
        return DriverRunner.run(context, program, args, out, env) { os ->
            PgrWriter.start(os)
            PdfPageRenderer(spec.pdf).use { r ->
                order.forEachIndexed { n, i ->
                    val back = twoSided && n % 2 == 1
                    if (i == BLANK) {
                        PgrWriter.writePage(os, BlankRowSource(w, h), dpi, !spec.color, backSide = back)
                    } else {
                        r.openRows(i, w, h, if (back) backFlip else SheetFlip.NONE).use { rows ->
                            PgrWriter.writePage(os, rows, dpi, !spec.color, backSide = back)
                        }
                    }
                }
            }
            PgrWriter.end(os)
        }
    }
}
