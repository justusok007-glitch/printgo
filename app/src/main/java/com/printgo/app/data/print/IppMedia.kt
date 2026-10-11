package com.printgo.app.data.print

import com.printgo.app.model.PaperSize
import kotlin.math.abs

/** Maps a paper size to the PWG self-describing media keyword the printer understands. */
object IppMedia {
    private val known = listOf(
        Triple("iso_a3_297x420mm", 297, 420),
        Triple("iso_a4_210x297mm", 210, 297),
        Triple("iso_a5_148x210mm", 148, 210),
        Triple("iso_b5_176x250mm", 176, 250),
        Triple("na_letter_8.5x11in", 216, 279),
        Triple("na_legal_8.5x14in", 216, 356),
        Triple("na_executive_7.25x10.5in", 184, 267),
        Triple("na_index-4x6_4x6in", 102, 152),
        Triple("na_5x7_5x7in", 127, 178),
        Triple("oe_photo-l_3.5x5in", 89, 127)
    )

    fun keywordFor(paper: PaperSize): String? {
        known.firstOrNull { abs(it.second - paper.widthMm) <= 2 && abs(it.third - paper.heightMm) <= 2 }?.let { return it.first }
        return null
    }

    fun pointsFor(paper: PaperSize): Pair<Int, Int> =
        Pair((paper.widthMm * 72f / 25.4f).toInt(), (paper.heightMm * 72f / 25.4f).toInt())
}
