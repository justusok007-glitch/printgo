package com.printgo.app.data.converter

import java.io.InputStream
import java.util.zip.ZipInputStream

object OfficeDocumentParser {

    fun parseDocxToHtml(inputStream: InputStream, title: String): String? {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var documentXml: String? = null

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (documentXml == null) return null

            // Simple XML to HTML extraction
            val sb = StringBuilder()
            sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            sb.append("<style>")
            sb.append("body { font-family: -apple-system, Roboto, sans-serif; margin: 36px; font-size: 13pt; line-height: 1.5; color: #1e293b; }")
            sb.append("h1 { font-size: 18pt; color: #0f172a; margin-bottom: 16px; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; }")
            sb.append("p { margin-bottom: 12px; }")
            sb.append("table { border-collapse: collapse; width: 100%; margin: 16px 0; }")
            sb.append("td, th { border: 1px solid #cbd5e1; padding: 8px 12px; text-align: left; }")
            sb.append("</style></head><body>")
            sb.append("<h1>").append(escapeHtml(title)).append("</h1>")

            // Parse tables and paragraphs
            val pRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
            val tRegex = Regex("<w:t[ >](.*?)</w:t>")

            val paragraphs = pRegex.findAll(documentXml).toList()
            if (paragraphs.isEmpty()) return null

            for (p in paragraphs) {
                val textRuns = tRegex.findAll(p.value).map { it.groupValues[1] }.joinToString("")
                if (textRuns.isNotBlank()) {
                    sb.append("<p>").append(escapeHtml(textRuns)).append("</p>")
                }
            }

            sb.append("</body></html>")
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun parseXlsxToHtml(inputStream: InputStream, title: String): String? {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            var sharedStringsXml: String? = null
            var sheetXml: String? = null

            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> {
                        sharedStringsXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    }
                    "xl/worksheets/sheet1.xml" -> {
                        sheetXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (sheetXml == null) return null

            // Extract shared strings dictionary
            val sharedStrings = mutableListOf<String>()
            if (sharedStringsXml != null) {
                val tRegex = Regex("<t[ >](.*?)</t>")
                tRegex.findAll(sharedStringsXml).forEach {
                    sharedStrings.add(it.groupValues[1])
                }
            }

            val sb = StringBuilder()
            sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            sb.append("<style>")
            sb.append("body { font-family: -apple-system, Roboto, sans-serif; margin: 30px; font-size: 11pt; color: #1e293b; }")
            sb.append("h1 { font-size: 16pt; color: #0f172a; margin-bottom: 16px; }")
            sb.append("table { border-collapse: collapse; width: 100%; margin-top: 12px; }")
            sb.append("th, td { border: 1px solid #cbd5e1; padding: 6px 10px; font-size: 10pt; }")
            sb.append("tr:nth-child(even) { background-color: #f8fafc; }")
            sb.append("</style></head><body>")
            sb.append("<h1>").append(escapeHtml(title)).append("</h1>")
            sb.append("<table>")

            // Parse rows
            val rowRegex = Regex("<row[ >](.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            val cRegex = Regex("<c[^>]*?(?:t=\"([^\"]*)\")?[^>]*>(?:<v>([^<]*)</v>)?", RegexOption.DOT_MATCHES_ALL)

            val rows = rowRegex.findAll(sheetXml).toList()
            if (rows.isEmpty()) return null

            for (row in rows) {
                sb.append("<tr>")
                val cells = cRegex.findAll(row.value).toList()
                for (cell in cells) {
                    val type = cell.groupValues[1]
                    val value = cell.groupValues[2]
                    val text = if (type == "s") {
                        val index = value.toIntOrNull()
                        if (index != null && index < sharedStrings.size) sharedStrings[index] else value
                    } else {
                        value
                    }
                    sb.append("<td>").append(escapeHtml(text)).append("</td>")
                }
                sb.append("</tr>")
            }

            sb.append("</table></body></html>")
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun parsePptxToHtml(inputStream: InputStream, title: String): String? {
        return try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            val slideContents = mutableListOf<String>()

            while (entry != null) {
                if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                    val slideXml = zip.bufferedReader(Charsets.UTF_8).readText()
                    val tRegex = Regex("<a:t[ >](.*?)</a:t>")
                    val texts = tRegex.findAll(slideXml).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
                    if (texts.isNotEmpty()) {
                        slideContents.add(texts.joinToString("<br>"))
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            if (slideContents.isEmpty()) return null

            val sb = StringBuilder()
            sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
            sb.append("<style>")
            sb.append("body { font-family: -apple-system, Roboto, sans-serif; margin: 30px; font-size: 12pt; color: #1e293b; }")
            sb.append("h1 { font-size: 16pt; color: #0f172a; margin-bottom: 20px; }")
            sb.append(".slide { border: 1px solid #cbd5e1; border-radius: 8px; padding: 20px; margin-bottom: 20px; background: #ffffff; page-break-after: always; }")
            sb.append(".slide-title { font-size: 14pt; font-weight: bold; color: #1e3a8a; margin-bottom: 12px; }")
            sb.append("</style></head><body>")
            sb.append("<h1>").append(escapeHtml(title)).append("</h1>")

            slideContents.forEachIndexed { index, content ->
                sb.append("<div class='slide'>")
                sb.append("<div class='slide-title'>Slide ${index + 1}</div>")
                sb.append("<p>").append(content).append("</p>")
                sb.append("</div>")
            }

            sb.append("</body></html>")
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
