package com.printgo.app.data.layout

import android.graphics.RectF

data class PagePlacement(
    val docIndex: Int,
    val pageIndexInDoc: Int,
    val globalPageIndex: Int,
    val destRectPt: RectF,
    val rotationDegrees: Int,
    val isExcluded: Boolean = false,
    val docTitle: String = ""
)

data class Sheet(
    val sheetIndex: Int,
    val placements: List<PagePlacement>,
    val paperWidthPt: Float,
    val paperHeightPt: Float,
    val isBackOfDuplex: Boolean = false,
    val isBlankPad: Boolean = false
) {
    val aspectRatio: Float
        get() = if (paperWidthPt > 0) paperHeightPt / paperWidthPt else 1.414f
}
