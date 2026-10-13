package com.printgo.app.data.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A point. In crop data the coordinates are relative (0..1) to the turned image. */
data class Pt(val x: Double, val y: Double)

/** The four crop corners in the order top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points: List<Pt> get() = listOf(tl, tr, br, bl)

    fun with(index: Int, p: Pt): Quad = when (index) {
        0 -> copy(tl = p); 1 -> copy(tr = p); 2 -> copy(br = p); else -> copy(bl = p)
    }

    fun map(f: (Pt) -> Pt) = Quad(f(tl), f(tr), f(br), f(bl))

    val minX get() = points.minOf { it.x }
    val maxX get() = points.maxOf { it.x }
    val minY get() = points.minOf { it.y }
    val maxY get() = points.maxOf { it.y }

    /** Axis-aligned rectangle around the four points. */
    fun bounds(): Quad = rect(minX, minY, maxX, maxY)

    val isRectangle: Boolean
        get() = tl.y == tr.y && bl.y == br.y && tl.x == bl.x && tr.x == br.x

    companion object {
        val FULL = rect(0.0, 0.0, 1.0, 1.0)
        fun rect(l: Double, t: Double, r: Double, b: Double) = Quad(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b))
    }
}

/** Paper shapes for the ratio crop. */
enum class CropShape(val label: String, val widthMm: Double, val heightMm: Double) {
    A4("A4", 210.0, 297.0),
    LETTER("Letter", 215.9, 279.4),
    PHOTO_4X6("4x6", 101.6, 152.4),
    SQUARE("Square", 1.0, 1.0);

    /** Width ÷ height of the crop in pixels. */
    fun ratio(landscape: Boolean): Double = if (landscape) heightMm / widthMm else widthMm / heightMm
}

object ScanGeometry {

    /** Relative point on an image turned by [quarterTurns]×90° clockwise more → the same point after the turn. */
    fun rotatePoint(p: Pt, quarterTurns: Int): Pt {
        var q = p
        repeat(((quarterTurns % 4) + 4) % 4) { q = Pt(1.0 - q.y, q.x) }
        return q
    }

    /** Turns crop corners together with the image; the corner order stays top-left, top-right, … */
    fun rotateQuad(quad: Quad, quarterTurns: Int): Quad {
        val turned = quad.points.map { rotatePoint(it, quarterTurns) }
        return orderCorners(turned)
    }

    /**
     * Relative point (u, v) on the image shown turned by [rotationDegrees] clockwise → pixel position on the
     * stored (not turned) bitmap of [srcW]×[srcH] pixels.
     */
    fun toSourcePixels(p: Pt, rotationDegrees: Int, srcW: Int, srcH: Int): Pt {
        val u = p.x; val v = p.y
        return when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Pt(v * srcW, (1 - u) * srcH)
            180 -> Pt((1 - u) * srcW, (1 - v) * srcH)
            270 -> Pt((1 - v) * srcW, u * srcH)
            else -> Pt(u * srcW, v * srcH)
        }
    }

    /** Size of the turned image for a stored bitmap of [w]×[h]. */
    fun turnedSize(w: Int, h: Int, rotationDegrees: Int): Pair<Int, Int> =
        if ((((rotationDegrees % 360) + 360) % 360) % 180 == 90) h to w else w to h

    /** Sorts four points into top-left, top-right, bottom-right, bottom-left (by angle around the centre). */
    fun orderCorners(points: List<Pt>): Quad {
        require(points.size == 4)
        val cx = points.sumOf { it.x } / 4; val cy = points.sumOf { it.y } / 4
        val sorted = points.sortedBy { Math.atan2(it.y - cy, it.x - cx) } // clockwise on screen, from left
        // atan2 order (y down): left(-pi..) → top → right → bottom. Pick the start as the one with the smallest x+y.
        val start = sorted.indices.minByOrNull { sorted[it].x + sorted[it].y } ?: 0
        val r = List(4) { sorted[(start + it) % 4] }
        return Quad(r[0], r[1], r[2], r[3])
    }

    /** Signed area (shoelace) – positive for clockwise order on screen (y down). */
    fun area(q: Quad): Double {
        val p = q.points
        var s = 0.0
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }

    /** True if the four corners make a convex shape (no crossed edges, no dent). */
    fun isConvex(q: Quad): Boolean {
        val p = q.points
        var sign = 0
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-12) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    /**
     * Pixel size of the flattened page: the average of the opposite edge lengths, measured in pixels of an
     * image [imgW]×[imgH] (relative corners).
     */
    fun flatSize(q: Quad, imgW: Double, imgH: Double): Pair<Double, Double> {
        fun len(a: Pt, b: Pt) = hypot((a.x - b.x) * imgW, (a.y - b.y) * imgH)
        val w = (len(q.tl, q.tr) + len(q.bl, q.br)) / 2
        val h = (len(q.tl, q.bl) + len(q.tr, q.br)) / 2
        return w to h
    }

    /**
     * Output pixel size for a flattened page of [w]×[h] source pixels: never larger than the source (no blow-up)
     * and never longer than [maxLong] pixels on the long side.
     */
    fun outputSize(w: Double, h: Double, maxLong: Int): Pair<Int, Int> {
        val long = max(w, h)
        val k = if (long > maxLong) maxLong / long else 1.0
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }

    /**
     * Largest rectangle with pixel ratio [ratio] (width ÷ height) that fits the image, shrunk by [scale] and
     * centred on the centre of [current] (moved inside the image if needed). [imgAspect] = image width ÷ height.
     */
    fun fitRatio(current: Quad, ratio: Double, imgAspect: Double, scale: Double = 0.92): Quad {
        val rel = ratio / imgAspect // relative width per relative height
        var w = rel; var h = 1.0
        if (w > 1.0) { w = 1.0; h = 1.0 / rel }
        w *= scale; h *= scale
        val cx = (current.minX + current.maxX) / 2; val cy = (current.minY + current.maxY) / 2
        val l = (cx - w / 2).coerceIn(0.0, 1.0 - w)
        val t = (cy - h / 2).coerceIn(0.0, 1.0 - h)
        return Quad.rect(l, t, l + w, t + h)
    }

    /**
     * Ratio-locked crop while a corner is dragged: [anchor] is the opposite (fixed) corner, [pointer] where the
     * finger is. Keeps the pixel ratio [ratio], stays inside the image and at least [minSize] (relative) wide.
     */
    fun ratioRectFromCorner(anchor: Pt, pointer: Pt, ratio: Double, imgAspect: Double, minSize: Double = 0.08): Quad {
        val rel = ratio / imgAspect // relative width per relative height
        val dirX = if (pointer.x >= anchor.x) 1.0 else -1.0
        val dirY = if (pointer.y >= anchor.y) 1.0 else -1.0
        val maxW = if (dirX > 0) 1.0 - anchor.x else anchor.x
        val maxH = if (dirY > 0) 1.0 - anchor.y else anchor.y
        var h = max(abs(pointer.y - anchor.y), abs(pointer.x - anchor.x) / rel)
        h = min(h, min(maxH, maxW / rel))
        val minH = max(minSize, minSize / rel).coerceAtMost(min(maxH, maxW / rel))
        h = max(h, minH)
        val w = h * rel
        val x2 = anchor.x + dirX * w; val y2 = anchor.y + dirY * h
        return Quad.rect(min(anchor.x, x2), min(anchor.y, y2), max(anchor.x, x2), max(anchor.y, y2))
    }

    /** Corners placed slightly inside the image corners (used when no page is found). */
    fun insetQuad(inset: Double = 0.06): Quad = Quad.rect(inset, inset, 1 - inset, 1 - inset)

    /**
     * Solves the perspective transform (3×3, h33 = 1) mapping the four [src] corners onto [dst].
     * Returns the 9 values row by row. Used for tests and as a fallback to Android's Matrix.setPolyToPoly.
     */
    fun homography(src: Quad, dst: Quad): DoubleArray {
        val s = src.points; val d = dst.points
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val (x, y) = s[i].x to s[i].y
            val (u, v) = d[i].x to d[i].y
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gauss-Jordan elimination with partial pivoting
        for (col in 0 until 8) {
            var piv = col
            for (r in col + 1 until 8) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            require(abs(a[piv][col]) > 1e-12) { "Corners do not form a page" }
            val t = a[col]; a[col] = a[piv]; a[piv] = t
            val div = a[col][col]
            for (k in col until 9) a[col][k] /= div
            for (r in 0 until 8) if (r != col) {
                val f = a[r][col]
                if (f != 0.0) for (k in col until 9) a[r][k] -= f * a[col][k]
            }
        }
        return DoubleArray(9) { if (it < 8) a[it][8] else 1.0 }
    }

    fun applyHomography(h: DoubleArray, p: Pt): Pt {
        val w = h[6] * p.x + h[7] * p.y + h[8]
        return Pt((h[0] * p.x + h[1] * p.y + h[2]) / w, (h[3] * p.x + h[4] * p.y + h[5]) / w)
    }

    /** Fits a picture of [imgW]×[imgH] into a box of [boxW]×[boxH] keeping its shape; returns left, top, w, h. */
    fun fitInside(imgW: Double, imgH: Double, boxW: Double, boxH: Double): DoubleArray {
        val k = min(boxW / imgW, boxH / imgH)
        val w = imgW * k; val h = imgH * k
        return doubleArrayOf((boxW - w) / 2, (boxH - h) / 2, w, h)
    }
}
