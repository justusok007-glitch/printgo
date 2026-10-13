package com.printgo.app.data.scan

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the edges of a sheet of paper in a photo, in pure Kotlin (no closed-source or ML libraries).
 *
 * Works on a small grey copy (about 500–800 px): blur → gradients (Sobel) → candidate regions from two
 * independent ideas — the bright/dark areas after an automatic (Otsu) threshold, and the areas fully enclosed
 * by strong edges — → convex hull of each region → the largest 4-cornered shape inside the hull. Every
 * candidate must be convex, reasonably large, with sensible angles, and have strong edges along all four
 * sides; the best one wins. If nothing passes, `detect` returns null and the caller places the corners
 * slightly inside the photo.
 */
object PageDetector {

    data class Detection(val quad: Quad, val score: Double)

    /** [lum] = grey values 0..255 of a [w]×[h] image. Returns relative corners (0..1) or null. */
    fun detect(lum: IntArray, w: Int, h: Int): Detection? {
        if (w < 16 || h < 16 || lum.size < w * h) return null
        val n = w * h
        var grey = FloatArray(n) { lum[it].toFloat() }
        val r = max(1, max(w, h) / 250)
        grey = BackgroundMap.boxBlur(grey, w, h, r)
        grey = BackgroundMap.boxBlur(grey, w, h, r)

        val mag = sobel(grey, w, h)
        // Weak page edges (light table, shadow) must still count; the contrast check below rejects texture.
        val edgeT = (otsu(mag, maxValue = mag.maxOrNull() ?: 0f) * 0.5f).coerceIn(5f, 25f)

        val candidates = ArrayList<Pair<Quad, Double>>() // pixel-space quad, fill ratio
        // 1. Brightness: page brighter (usual) or darker than the background
        val t = otsu(grey, 255f)
        for (bright in listOf(true, false)) {
            val mask = BooleanArray(n) { if (bright) grey[it] > t else grey[it] <= t }
            collectRegions(mask, w, h, candidates)
        }
        // 2. Areas enclosed by strong edges
        val edges = BooleanArray(n) { mag[it] >= edgeT }
        val thick = dilate(edges, w, h)
        val outside = floodFromBorder(thick, w, h)
        val enclosed = BooleanArray(n) { !thick[it] && !outside[it] }
        collectRegions(enclosed, w, h, candidates, grow = 2.0)

        var best: Detection? = null
        for ((q, fill) in candidates) {
            val s = score(q, fill, grey, mag, w, h, edgeT) ?: continue
            if (best == null || s > best.score) {
                best = Detection(q.map { Pt((it.x / w).coerceIn(0.0, 1.0), (it.y / h).coerceIn(0.0, 1.0)) }, s)
            }
        }
        return best
    }

    // ---- scoring ----

    private fun score(q: Quad, fill: Double, grey: FloatArray, mag: FloatArray, w: Int, h: Int, edgeT: Float): Double? {
        if (!ScanGeometry.isConvex(q)) return null
        val areaFrac = abs(ScanGeometry.area(q)) / (w.toDouble() * h)
        if (areaFrac < 0.10 || areaFrac > 0.985) return null
        if (fill < 0.80) return null
        val p = q.points
        for (i in 0 until 4) {
            val a = p[(i + 3) % 4]; val b = p[i]; val c = p[(i + 1) % 4]
            val ang = angle(a, b, c)
            if (ang < 35.0 || ang > 145.0) return null
        }
        val supports = (0 until 4).map { edgeSupport(p[it], p[(it + 1) % 4], mag, w, h, edgeT) }
        val minS = supports.min()
        if (minS < 0.55) return null
        // A real page edge has a clear, steady brightness step across it (paper on one side, table on the other).
        val cx = p.sumOf { it.x } / 4; val cy = p.sumOf { it.y } / 4
        for (i in 0 until 4) {
            if (sideContrast(p[i], p[(i + 1) % 4], cx, cy, grey, w, h) < 0.6) return null
        }
        return minS * 0.6 + supports.average() * 0.2 + areaFrac * 0.2
    }

    private fun angle(a: Pt, b: Pt, c: Pt): Double {
        val v1x = a.x - b.x; val v1y = a.y - b.y; val v2x = c.x - b.x; val v2y = c.y - b.y
        val d = hypot(v1x, v1y) * hypot(v2x, v2y)
        if (d == 0.0) return 0.0
        return Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / d).coerceIn(-1.0, 1.0)))
    }

    /**
     * Share of points along a side where the brightness 5 px inside differs clearly (≥ 12 levels) from 5 px
     * outside, all in the same direction. Sides on the photo border fail (there is no outside).
     */
    private fun sideContrast(a: Pt, b: Pt, cx: Double, cy: Double, grey: FloatArray, w: Int, h: Int): Double {
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < 4) return 0.0
        var nx = -(b.y - a.y) / len; var ny = (b.x - a.x) / len
        val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
        if (nx * (cx - mx) + ny * (cy - my) < 0) { nx = -nx; ny = -ny } // point inwards
        val samples = 48
        val diffs = ArrayList<Float>()
        for (s in 2 until samples - 1) {
            val t = s.toDouble() / samples
            val x = a.x + (b.x - a.x) * t; val y = a.y + (b.y - a.y) * t
            val xi = (x + nx * 5).toInt(); val yi = (y + ny * 5).toInt()
            val xo = (x - nx * 5).toInt(); val yo = (y - ny * 5).toInt()
            if (xi !in 0 until w || yi !in 0 until h || xo !in 0 until w || yo !in 0 until h) { diffs += 0f; continue }
            diffs += grey[yi * w + xi] - grey[yo * w + xo]
        }
        if (diffs.isEmpty()) return 0.0
        val sign = if (diffs.sum() >= 0) 1f else -1f
        return diffs.count { it * sign >= 12f }.toDouble() / diffs.size
    }

    /** Share of points along a side that sit on (or within 3 px of) a strong edge. */
    private fun edgeSupport(a: Pt, b: Pt, mag: FloatArray, w: Int, h: Int, edgeT: Float): Double {
        val len = hypot(b.x - a.x, b.y - a.y)
        if (len < 4) return 0.0
        val nx = -(b.y - a.y) / len; val ny = (b.x - a.x) / len
        val samples = 48
        var hit = 0
        for (s in 1 until samples) { // leave out the corners themselves
            val t = s.toDouble() / samples
            val x = a.x + (b.x - a.x) * t; val y = a.y + (b.y - a.y) * t
            var ok = false
            for (o in -3..3) {
                val xi = (x + nx * o).toInt(); val yi = (y + ny * o).toInt()
                if (xi in 1 until w - 1 && yi in 1 until h - 1 && mag[yi * w + xi] >= edgeT) { ok = true; break }
            }
            if (ok) hit++
        }
        return hit.toDouble() / (samples - 1)
    }

    // ---- regions ----

    private fun collectRegions(
        mask: BooleanArray, w: Int, h: Int, out: MutableList<Pair<Quad, Double>>, grow: Double = 0.0
    ) {
        val labels = IntArray(w * h)
        val stack = IntArray(w * h)
        val comp = IntArray(w * h)
        val minArea = (w * h * 0.08).toInt()
        val left = IntArray(h)
        val right = IntArray(h)
        var label = 0
        for (start in 0 until w * h) {
            if (!mask[start] || labels[start] != 0) continue
            label++
            var sp = 0
            stack[sp++] = start
            labels[start] = label
            var area = 0
            while (sp > 0) {
                val i = stack[--sp]
                comp[area++] = i
                val x = i % w; val y = i / w
                if (x > 0) { val j = i - 1; if (mask[j] && labels[j] == 0) { labels[j] = label; stack[sp++] = j } }
                if (x < w - 1) { val j = i + 1; if (mask[j] && labels[j] == 0) { labels[j] = label; stack[sp++] = j } }
                if (y > 0) { val j = i - w; if (mask[j] && labels[j] == 0) { labels[j] = label; stack[sp++] = j } }
                if (y < h - 1) { val j = i + w; if (mask[j] && labels[j] == 0) { labels[j] = label; stack[sp++] = j } }
            }
            if (area < minArea) continue
            left.fill(Int.MAX_VALUE); right.fill(-1)
            for (k in 0 until area) {
                val i = comp[k]; val x = i % w; val y = i / w
                if (x < left[y]) left[y] = x
                if (x > right[y]) right[y] = x
            }
            // Holes (text, lines) do not count against the region: measure it row span by row span.
            var filled = 0L
            val pts = ArrayList<Pt>()
            for (y in 0 until h) if (right[y] >= 0) {
                filled += right[y] - left[y] + 1
                pts += Pt(left[y].toDouble(), y.toDouble())
                pts += Pt(right[y] + 1.0, y.toDouble())
                pts += Pt(left[y].toDouble(), y + 1.0)
                pts += Pt(right[y] + 1.0, y + 1.0)
            }
            val hull = convexHull(pts)
            if (hull.size < 4) continue
            val quad = maxAreaQuad(hull) ?: continue
            val qa = abs(ScanGeometry.area(quad))
            if (qa <= 0) continue
            val fill = min(1.0, filled / qa)
            out += (if (grow > 0) grow(quad, grow) else quad) to fill
        }
    }

    /** Moves every corner [d] pixels away from the centre (edge regions sit a little inside the real edge). */
    private fun grow(q: Quad, d: Double): Quad {
        val cx = q.points.sumOf { it.x } / 4; val cy = q.points.sumOf { it.y } / 4
        return q.map {
            val dx = it.x - cx; val dy = it.y - cy; val l = hypot(dx, dy)
            if (l == 0.0) it else Pt(it.x + dx / l * d * 1.41, it.y + dy / l * d * 1.41)
        }
    }

    /** Andrew's monotone chain. Returns the hull in clockwise screen order without repeating the first point. */
    fun convexHull(points: List<Pt>): List<Pt> {
        val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<Pt>()
        for (pt in p) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], pt) <= 0) lower.removeAt(lower.size - 1)
            lower += pt
        }
        val upper = ArrayList<Pt>()
        for (pt in p.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], pt) <= 0) upper.removeAt(upper.size - 1)
            upper += pt
        }
        lower.removeAt(lower.size - 1); upper.removeAt(upper.size - 1)
        return lower + upper
    }

    /**
     * Largest-area quadrilateral with corners on the hull: start from the extreme points along the diagonals,
     * then move one corner at a time to the hull point that increases the area most, until nothing improves.
     */
    fun maxAreaQuad(hull: List<Pt>): Quad? {
        if (hull.size < 4) return null
        val idx = intArrayOf(
            hull.indices.minBy { hull[it].x + hull[it].y },
            hull.indices.maxBy { hull[it].x - hull[it].y },
            hull.indices.maxBy { hull[it].x + hull[it].y },
            hull.indices.minBy { hull[it].x - hull[it].y }
        )
        fun areaOf(ix: IntArray): Double {
            if (ix.toSet().size < 4) return 0.0
            return abs(ScanGeometry.area(ScanGeometry.orderCorners(ix.map { hull[it] })))
        }
        var bestArea = areaOf(idx)
        var improved = true
        var passes = 0
        while (improved && passes < 8) {
            improved = false; passes++
            for (k in 0 until 4) {
                val keep = idx[k]
                var bestJ = keep
                for (j in hull.indices) {
                    idx[k] = j
                    val a = areaOf(idx)
                    if (a > bestArea + 1e-9) { bestArea = a; bestJ = j; improved = true }
                }
                idx[k] = bestJ
            }
        }
        if (bestArea <= 0) return null
        return ScanGeometry.orderCorners(idx.map { hull[it] })
    }

    // ---- image helpers ----

    internal fun sobel(g: FloatArray, w: Int, h: Int): FloatArray {
        val m = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (g[i - w + 1] + 2 * g[i + 1] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - 1] + g[i + w - 1])
            val gy = (g[i + w - 1] + 2 * g[i + w] + g[i + w + 1]) - (g[i - w - 1] + 2 * g[i - w] + g[i - w + 1])
            m[i] = sqrt(gx * gx + gy * gy) / 4f
        }
        return m
    }

    /** Otsu's threshold for values in 0..[maxValue] (256 bins). */
    internal fun otsu(v: FloatArray, maxValue: Float): Float {
        if (maxValue <= 0f) return 0f
        val hist = IntArray(256)
        for (x in v) hist[((x / maxValue) * 255f).toInt().coerceIn(0, 255)]++
        val total = v.size.toDouble()
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 0.0; var thr = 0
        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; thr = i }
        }
        return (thr + 0.5f) / 255f * maxValue
    }

    private fun dilate(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val o = BooleanArray(m.size)
        for (y in 0 until h) for (x in 0 until w) {
            if (!m[y * w + x]) continue
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) o[yy * w + xx] = true
            }
        }
        return o
    }

    /** Pixels reachable from the image border without crossing a wall pixel. */
    private fun floodFromBorder(wall: BooleanArray, w: Int, h: Int): BooleanArray {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        fun push(i: Int) { if (!wall[i] && !seen[i]) { seen[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return seen
    }
}
