package com.printgo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType

@Entity(tableName = "recent_documents")
data class RecentDocumentEntity(
    @PrimaryKey
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val documentType: String,
    val lastOpenedTimestamp: Long = System.currentTimeMillis()
) {
    fun toPrintItem(): PrintItem {
        val type = try {
            PrintItemType.valueOf(documentType)
        } catch (e: Exception) {
            PrintItem.resolveType(mimeType, displayName)
        }
        return PrintItem(
            uri = android.net.Uri.parse(uriString),
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            type = type
        )
    }

    companion object {
        fun fromPrintItem(item: PrintItem): RecentDocumentEntity {
            return RecentDocumentEntity(
                uriString = item.uri.toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                documentType = item.type.name,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
        }
    }
}
