package com.printgo.app.model

import android.net.Uri

enum class PrintItemType {
    IMAGE,
    PDF,
    OFFICE,
    TEXT,
    WEB
}

data class PrintItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val type: PrintItemType = PrintItemType.IMAGE
) {
    fun formattedSize(): String {
        if (sizeBytes <= 0L) return ""
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.1f KB", kb)
            else -> "$sizeBytes B"
        }
    }

    companion object {
        fun resolveType(mimeType: String, name: String): PrintItemType {
            val lowerMime = mimeType.lowercase()
            val lowerName = name.lowercase()
            return when {
                lowerMime.startsWith("image/") ||
                        lowerName.endsWith(".jpg") ||
                        lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") ||
                        lowerName.endsWith(".webp") ||
                        lowerName.endsWith(".bmp") -> PrintItemType.IMAGE

                lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> PrintItemType.PDF

                lowerMime.contains("word") ||
                        lowerMime.contains("excel") ||
                        lowerMime.contains("powerpoint") ||
                        lowerMime.contains("officedocument") ||
                        lowerMime.contains("msword") ||
                        lowerMime.contains("ms-excel") ||
                        lowerName.endsWith(".doc") ||
                        lowerName.endsWith(".docx") ||
                        lowerName.endsWith(".xls") ||
                        lowerName.endsWith(".xlsx") ||
                        lowerName.endsWith(".ppt") ||
                        lowerName.endsWith(".pptx") -> PrintItemType.OFFICE

                lowerMime.startsWith("text/") ||
                        lowerName.endsWith(".txt") ||
                        lowerName.endsWith(".csv") ||
                        lowerName.endsWith(".html") ||
                        lowerName.endsWith(".htm") -> PrintItemType.TEXT

                else -> PrintItemType.OFFICE
            }
        }
    }
}
