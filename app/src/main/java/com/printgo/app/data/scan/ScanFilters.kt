package com.printgo.app.data.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Looks the user can give a scanned page or photo. Pure maths on ARGB pixel arrays (tested on the JVM). */
enum class ScanFilter(val label: String, val hint: String) {
    ORIGINAL("Original", "No change"),
    VIBRANT("Vibrant", "Stronger colour, for photos and posters"),
    CARBON_BLACK("Carbon Black", "Black text on white paper, saves ink"),
    SOFT_TONE("Soft Tone", "Gentle and warm, for portraits"),
    CLEAN_COLOUR("Clean Colour", "White paper, keeps stamps and blue pen");

    /** Filters that need to know the paper colour around each pixel. */
    val needsBackground: Boolean get() = this == CARBON_BLACK || this == CLEAN_COLOUR
}

/**
 * Paper colour (lighting) across the page, estimated on a small copy of the image: the brightest colour in
 * a small neighbourhood (so text and lines disappear), then smoothed. Sampled with bilinear interpolation in
 * coordinates relative to the whole image, so one small map serves any output resolution.
 */
class BackgroundMap(val width: Int, val height: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray) {

    /** Background colour at the relative position ([fx], [fy] in 0..1). Returns r, g, b (0..255) into [out]. */
    fun sample(fx: Float, fy: Float, out: FloatArray) {
        val x = (fx * width - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val y = (fy * height - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt(); val y0 = y.toInt()
        val x1 = min(x0 + 1, width - 1); val y1 = min(y0 + 1, height - 1)
        val tx = x - x0; val ty = y - y0
        val i00 = y0 * width + x0; val i01 = y0 * width + x1
        val i10 = y1 * width + x0; val i11 = y1 * width + x1
        fun lerp(a: FloatArray): Float {
            val top = a[i00] + (a[i01] - a[i00]) * tx
            val bottom = a[i10] + (a[i11] - a[i10]) * tx
            return top + (bottom - top) * ty
        }
        out[0] = lerp(r); out[1] = lerp(g); out[2] = lerp(b)
    }

    companion object {
        /** Estimates the background from a small ARGB image (about 100–200 px on the long side is plenty). */
        fun estimate(pixels: IntArray, width: Int, height: Int): BackgroundMap {
            require(width > 0 && height > 0 && pixels.size >= width * height)
            val n = width * height
            val r = FloatArray(n); val g = FloatArray(n); val b = FloatArray(n)
            for (i in 0 until n) {
                val p = pixels[i]
                r[i] = ((p shr 16) and 0xFF).toFloat()
                g[i] = ((p shr 8) and 0xFF).toFloat()
                b[i] = (p and 0xFF).toFloat()
            }
            val radius = max(1, max(width, height) / 40)
            val out = arrayOf(r, g, b).map { ch ->
                val dilated = maxFilter(ch, width, height, radius)
                boxBlur(dilated, width, height, radius * 2)
            }
            return BackgroundMap(width, height, out[0], out[1], out[2])
        }

        /** Separable maximum filter (grey-scale dilation) with a square window of the given radius. */
        internal fun maxFilter(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
            val tmp = FloatArray(src.size)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var m = 0f
                    for (k in max(0, x - radius)..min(w - 1, x + radius)) m = max(m, src[row + k])
                    tmp[row + x] = m
                }
            }
            val dst = FloatArray(src.size)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var m = 0f
                    for (k in max(0, y - radius)..min(h - 1, y + radius)) m = max(m, tmp[k * w + x])
                    dst[y * w + x] = m
                }
            }
            return dst
        }

        /** Separable box blur with clamped edges. */
        internal fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
            if (radius <= 0) return src.copyOf()
            val tmp = FloatArray(src.size)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var s = 0f; var c = 0
                    for (k in max(0, x - radius)..min(w - 1, x + radius)) { s += src[row + k]; c++ }
                    tmp[row + x] = s / c
                }
            }
            val dst = FloatArray(src.size)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var s = 0f; var c = 0
                    for (k in max(0, y - radius)..min(h - 1, y + radius)) { s += tmp[k * w + x]; c++ }
                    dst[y * w + x] = s / c
                }
            }
            return dst
        }
    }
}

object ScanFilterEngine {

    /**
     * Applies [filter] to a band of an image. [src] holds [rows] rows of [width] pixels (ARGB), starting at row
     * [yOffset] of an image that is [fullHeight] rows high (used to look up the background map). [strength]
     * (0..1) blends between the original (0) and the full filter (1). Returns a new array; [src] is not changed.
     */
    fun apply(
        src: IntArray,
        width: Int,
        rows: Int,
        filter: ScanFilter,
        strength: Float,
        background: BackgroundMap? = null,
        yOffset: Int = 0,
        fullHeight: Int = rows
    ): IntArray {
        val s = strength.coerceIn(0f, 1f)
        if (filter == ScanFilter.ORIGINAL || s == 0f) return src.copyOf(width * rows)
        val out = IntArray(width * rows)
        val bg = FloatArray(3)
        val rgb = FloatArray(3)
        for (y in 0 until rows) {
            val fy = (yOffset + y + 0.5f) / fullHeight
            for (x in 0 until width) {
                val i = y * width + x
                val p = src[i]
                val r0 = ((p shr 16) and 0xFF).toFloat()
                val g0 = ((p shr 8) and 0xFF).toFloat()
                val b0 = (p and 0xFF).toFloat()
                rgb[0] = r0; rgb[1] = g0; rgb[2] = b0
                when (filter) {
                    ScanFilter.VIBRANT -> {
                        sharpen(src, width, rows, x, y, rgb, 0.6f)
                        contrast(rgb, 1.18f)
                        saturate(rgb, 1.45f)
                    }
                    ScanFilter.SOFT_TONE -> {
                        contrast(rgb, 0.85f)
                        saturate(rgb, 0.92f)
                        rgb[0] += 14f; rgb[1] += 9f; rgb[2] += 2f
                    }
                    ScanFilter.CARBON_BLACK -> {
                        val lum = luma(r0, g0, b0)
                        val paper = if (background != null) {
                            background.sample((x + 0.5f) / width, fy, bg)
                            max(luma(bg[0], bg[1], bg[2]), 24f)
                        } else 255f
                        val v = carbonCurve(lum / paper) * 255f
                        rgb[0] = v; rgb[1] = v; rgb[2] = v
                    }
                    ScanFilter.CLEAN_COLOUR -> {
                        if (background != null) {
                            background.sample((x + 0.5f) / width, fy, bg)
                        } else {
                            bg[0] = 255f; bg[1] = 255f; bg[2] = 255f
                        }
                        for (c in 0..2) {
                            val n = rgb[c] / max(bg[c], 24f)
                            rgb[c] = whitenCurve(n) * 255f
                        }
                        saturate(rgb, 1.2f)
                    }
                    ScanFilter.ORIGINAL -> Unit
                }
                val r = r0 + (rgb[0] - r0) * s
                val g = g0 + (rgb[1] - g0) * s
                val b = b0 + (rgb[2] - b0) * s
                out[i] = (p and 0xFF000000.toInt()) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
            }
        }
        return out
    }

    /** Applies the filter to a whole image in one go (small images, thumbnails, tests). */
    fun applyWhole(pixels: IntArray, width: Int, height: Int, filter: ScanFilter, strength: Float): IntArray {
        val bg = if (filter.needsBackground) BackgroundMap.estimate(pixels, width, height) else null
        return apply(pixels, width, height, filter, strength, bg, 0, height)
    }

    fun luma(r: Float, g: Float, b: Float): Float = 0.299f * r + 0.587f * g + 0.114f * b

    /**
     * Brightness relative to the paper (1 = paper) → output. Paper and light shadows become pure white, ink
     * becomes black, with a short smooth ramp in between so letter edges stay smooth.
     */
    fun carbonCurve(ratio: Float): Float {
        val lo = 0.52f; val hi = 0.80f
        if (ratio <= lo) return 0f
        if (ratio >= hi) return 1f
        val t = (ratio - lo) / (hi - lo)
        return t * t * (3f - 2f * t)
    }

    /** Relative to the paper colour: near-paper → white, darker ink is kept (slightly deepened). */
    fun whitenCurve(ratio: Float): Float {
        if (ratio >= 0.88f) return 1f
        // 0..0.88 → 0..1 with a gentle gamma that keeps ink colours rich
        val t = (ratio / 0.88f).coerceIn(0f, 1f)
        return Math.pow(t.toDouble(), 1.25).toFloat()
    }

    private fun contrast(rgb: FloatArray, k: Float) {
        for (c in 0..2) rgb[c] = (rgb[c] - 128f) * k + 128f
    }

    private fun saturate(rgb: FloatArray, k: Float) {
        val l = luma(rgb[0], rgb[1], rgb[2])
        for (c in 0..2) rgb[c] = l + (rgb[c] - l) * k
    }

    /** Unsharp mask with a 3×3 neighbourhood (edges clamp to the band). */
    private fun sharpen(src: IntArray, w: Int, h: Int, x: Int, y: Int, rgb: FloatArray, amount: Float) {
        var sr = 0f; var sg = 0f; var sb = 0f
        for (dy in -1..1) {
            val yy = (y + dy).coerceIn(0, h - 1)
            for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val q = src[yy * w + xx]
                sr += (q shr 16) and 0xFF; sg += (q shr 8) and 0xFF; sb += q and 0xFF
            }
        }
        rgb[0] += (rgb[0] - sr / 9f) * amount * 2f
        rgb[1] += (rgb[1] - sg / 9f) * amount * 2f
        rgb[2] += (rgb[2] - sb / 9f) * amount * 2f
    }

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)

    /** True when every pixel is grey (R = G = B) — used by tests and by the PDF writer hint. */
    fun isGrey(pixels: IntArray): Boolean = pixels.all {
        val r = (it shr 16) and 0xFF; val g = (it shr 8) and 0xFF; val b = it and 0xFF
        abs(r - g) <= 1 && abs(g - b) <= 1
    }
}
