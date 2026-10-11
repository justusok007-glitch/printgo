package com.printgo.app.data.repository

import com.printgo.app.data.local.RecentDocumentDao
import com.printgo.app.data.local.RecentDocumentEntity
import com.printgo.app.model.PrintItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecentDocumentRepository(private val dao: RecentDocumentDao) {
    val recentDocuments: Flow<List<PrintItem>> = dao.getRecentDocuments().map { entities ->
        entities.map { it.toPrintItem() }
    }

    suspend fun saveRecentDocument(item: PrintItem) {
        dao.insertDocument(RecentDocumentEntity.fromPrintItem(item))
        dao.trimOldDocuments()
    }

    suspend fun removeRecentDocument(uriString: String) {
        dao.deleteDocumentByUri(uriString)
    }

    suspend fun clearRecentDocuments() {
        dao.clearAll()
    }
}
