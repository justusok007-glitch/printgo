package com.printgo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

object JobStatus {
    const val QUEUED = "QUEUED"
    const val PREPARING = "PREPARING"
    const val SENDING = "SENDING"
    const val PRINTING = "PRINTING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"

    val ACTIVE = setOf(QUEUED, PREPARING, SENDING, PRINTING)
    fun isActive(s: String) = s in ACTIVE
}

@Entity(tableName = "print_jobs")
data class PrintJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val statusMessage: String = "",
    val progress: Float = 0f,
    val sheets: Int = 0,
    val sourcePages: Int = 0,
    val copies: Int = 1,
    val collate: Boolean = true,
    val duplex: String = "ONE_SIDED",
    val colorMode: String = "COLOR",
    val quality: String = "NORMAL",
    val paperName: String = "A4",
    val paperWidthMm: Int = 210,
    val paperHeightMm: Int = 297,
    val settingsSummary: String = "",
    // printer snapshot
    val printerId: String,
    val printerName: String,
    val connectionType: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val resourcePath: String,
    // capability snapshot
    val formatsCsv: String = "application/pdf",
    val sidesCsv: String = "one-sided",
    val colorModesSupported: Boolean = true,
    // output
    val pdfPath: String? = null,
    val outputUri: String? = null,
    val remoteJobId: Int = 0,
    val attempts: Int = 0,
    val method: String = ""
)
