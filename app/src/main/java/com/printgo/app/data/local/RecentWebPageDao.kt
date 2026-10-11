package com.printgo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentWebPageDao {
    @Query("SELECT * FROM recent_webpages ORDER BY timestamp DESC LIMIT 20")
    fun getRecentWebPages(): Flow<List<RecentWebPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebPage(page: RecentWebPageEntity)

    @Query("DELETE FROM recent_webpages WHERE url = :url")
    suspend fun deleteWebPage(url: String)

    @Query("DELETE FROM recent_webpages WHERE url NOT IN (SELECT url FROM recent_webpages ORDER BY timestamp DESC LIMIT 20)")
    suspend fun trimOldWebPages()

    @Query("DELETE FROM recent_webpages")
    suspend fun clearAll()
}
