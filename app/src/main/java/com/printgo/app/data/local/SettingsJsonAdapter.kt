package com.printgo.app.data.local

import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageNumberPosition
import com.printgo.app.model.PageOrder
import com.printgo.app.model.PageSelection
import com.printgo.app.model.PagesPerSheet
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrintAlignment
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintMargins
import com.printgo.app.model.PrintOrientation
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.PrintScale
import com.printgo.app.model.PrintSettings
import com.printgo.app.model.WatermarkSettings
import org.json.JSONObject

object SettingsJsonAdapter {

    fun toJson(settings: PrintSettings): String {
        val root = JSONObject()
        root.put("copies", settings.copies)
        root.put("collate", settings.collate)
        root.put("reverseOrder", settings.reverseOrder)
        root.put("pagesPerSheet", settings.pagesPerSheet.count)
        root.put("pageOrder", settings.pageOrder.name)
        root.put("duplex", settings.duplex.name)
        root.put("paperName", settings.paperSize.name)
        root.put("paperW", settings.paperSize.widthMm)
        root.put("paperH", settings.paperSize.heightMm)
        root.put("orientation", settings.orientation.name)

        when (val s = settings.scale) {
            is PrintScale.FitToPage -> root.put("scaleType", "fit")
            is PrintScale.FillPage -> root.put("scaleType", "fill")
            is PrintScale.ActualSize -> root.put("scaleType", "actual")
            is PrintScale.CustomPercent -> {
                root.put("scaleType", "custom")
                root.put("scalePercent", s.percent)
            }
        }

        when (val m = settings.margins) {
            is PrintMargins.None -> root.put("marginsType", "none")
            is PrintMargins.Narrow -> root.put("marginsType", "narrow")
            is PrintMargins.Normal -> root.put("marginsType", "normal")
            is PrintMargins.Wide -> root.put("marginsType", "wide")
            is PrintMargins.Custom -> {
                root.put("marginsType", "custom")
                root.put("mTop", m.topMm.toDouble())
                root.put("mBottom", m.bottomMm.toDouble())
                root.put("mLeft", m.leftMm.toDouble())
                root.put("mRight", m.rightMm.toDouble())
            }
        }

        root.put("alignment", settings.alignment.name)
        root.put("colorMode", settings.colorMode.name)
        root.put("quality", settings.quality.name)
        root.put("border", settings.borderAroundPages)
        root.put("pageNumbers", settings.pageNumbers?.name ?: "")
        root.put("dateStamp", settings.dateStamp)
        root.put("fileNameHeader", settings.fileNameHeader)

        settings.watermark?.let { w ->
            val wo = JSONObject()
            wo.put("text", w.text)
            wo.put("opacity", w.opacity.toDouble())
            wo.put("diagonal", w.isDiagonal)
            root.put("watermark", wo)
        }

        root.put("activePresetName", settings.activePresetName ?: "")
        return root.toString()
    }

    fun fromJson(jsonStr: String): PrintSettings {
        return try {
            val root = JSONObject(jsonStr)
            val copies = root.optInt("copies", 1)
            val collate = root.optBoolean("collate", true)
            val reverseOrder = root.optBoolean("reverseOrder", false)
            val ppsCount = root.optInt("pagesPerSheet", 1)
            val pageOrderStr = root.optString("pageOrder", PageOrder.LEFT_TO_RIGHT.name)
            val duplexStr = root.optString("duplex", DuplexMode.ONE_SIDED.name)
            val paperName = root.optString("paperName", "A4")
            val paperW = root.optInt("paperW", 210)
            val paperH = root.optInt("paperH", 297)
            val orientationStr = root.optString("orientation", PrintOrientation.AUTO.name)

            val scaleType = root.optString("scaleType", "fit")
            val scale = when (scaleType) {
                "fill" -> PrintScale.FillPage
                "actual" -> PrintScale.ActualSize
                "custom" -> PrintScale.CustomPercent(root.optInt("scalePercent", 100).coerceIn(25, 400))
                else -> PrintScale.FitToPage
            }

            val marginsType = root.optString("marginsType", "normal")
            val margins = when (marginsType) {
                "none" -> PrintMargins.None
                "narrow" -> PrintMargins.Narrow
                "wide" -> PrintMargins.Wide
                "custom" -> PrintMargins.Custom(
                    topMm = root.optDouble("mTop", 12.0).toFloat(),
                    bottomMm = root.optDouble("mBottom", 12.0).toFloat(),
                    leftMm = root.optDouble("mLeft", 12.0).toFloat(),
                    rightMm = root.optDouble("mRight", 12.0).toFloat()
                )
                else -> PrintMargins.Normal
            }

            val alignmentStr = root.optString("alignment", PrintAlignment.CENTER.name)
            val colorModeStr = root.optString("colorMode", PrintColorMode.COLOR.name)
            val qualityStr = root.optString("quality", PrintQuality.NORMAL.name)
            val border = root.optBoolean("border", false)

            val pageNumbersStr = root.optString("pageNumbers", "")
            val pageNumbers = if (pageNumbersStr.isNotEmpty()) {
                runCatching { PageNumberPosition.valueOf(pageNumbersStr) }.getOrNull()
            } else null

            val dateStamp = root.optBoolean("dateStamp", false)
            val fileNameHeader = root.optBoolean("fileNameHeader", false)

            val watermark = if (root.has("watermark")) {
                val wo = root.getJSONObject("watermark")
                WatermarkSettings(
                    text = wo.optString("text", ""),
                    opacity = wo.optDouble("opacity", 0.20).toFloat().coerceIn(0.05f, 0.50f),
                    isDiagonal = wo.optBoolean("diagonal", true)
                )
            } else null

            val activePresetName = root.optString("activePresetName", "").ifEmpty { null }

            PrintSettings(
                copies = copies.coerceIn(1, 99),
                collate = collate,
                pageSelection = PageSelection.All,
                reverseOrder = reverseOrder,
                pagesPerSheet = PagesPerSheet.fromCount(ppsCount),
                pageOrder = runCatching { PageOrder.valueOf(pageOrderStr) }.getOrDefault(PageOrder.LEFT_TO_RIGHT),
                duplex = runCatching { DuplexMode.valueOf(duplexStr) }.getOrDefault(DuplexMode.ONE_SIDED),
                paperSize = PaperSize(paperName, paperW, paperH),
                orientation = runCatching { PrintOrientation.valueOf(orientationStr) }.getOrDefault(PrintOrientation.AUTO),
                scale = scale,
                margins = margins,
                alignment = runCatching { PrintAlignment.valueOf(alignmentStr) }.getOrDefault(PrintAlignment.CENTER),
                colorMode = runCatching { PrintColorMode.valueOf(colorModeStr) }.getOrDefault(PrintColorMode.COLOR),
                quality = runCatching { PrintQuality.valueOf(qualityStr) }.getOrDefault(PrintQuality.NORMAL),
                borderAroundPages = border,
                pageNumbers = pageNumbers,
                dateStamp = dateStamp,
                fileNameHeader = fileNameHeader,
                watermark = watermark,
                activePresetName = activePresetName
            )
        } catch (e: Exception) {
            PrintSettings.DEFAULT
        }
    }
}
