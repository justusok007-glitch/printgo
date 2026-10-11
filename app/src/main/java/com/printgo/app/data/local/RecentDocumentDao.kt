package com.printgo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDocumentDao {
    @Query("SELECT * FROM recent_documents ORDER BY lastOpenedTimestamp DESC LIMIT 20")
    fun getRecentDocuments(): Flow<List<RecentDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: RecentDocumentEntity)

    @Query("DELETE FROM recent_documents WHERE uriString = :uriString")
    suspend fun deleteDocumentByUri(uriString: String)

    @Query("DELETE FROM recent_documents WHERE uriString NOT IN (SELECT uriString FROM recent_documents ORDER BY lastOpenedTimestamp DESC LIMIT 20)")
    suspend fun trimOldDocuments()

    @Query("DELETE FROM recent_documents")
    suspend fun clearAll()
}
