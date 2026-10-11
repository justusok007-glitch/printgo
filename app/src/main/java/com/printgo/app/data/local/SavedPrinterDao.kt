package com.printgo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPrinterDao {
    @Query("SELECT * FROM saved_printers ORDER BY isDefault DESC, lastUsedAt DESC")
    fun getAllPrinters(): Flow<List<SavedPrinterEntity>>

    @Query("SELECT * FROM saved_printers WHERE id = :id LIMIT 1")
    fun getPrinterById(id: String): Flow<SavedPrinterEntity?>

    @Query("SELECT * FROM saved_printers WHERE id = :id LIMIT 1")
    suspend fun getPrinter(id: String): SavedPrinterEntity?

    @Query("SELECT * FROM saved_printers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPrinter(): SavedPrinterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrinter(printer: SavedPrinterEntity)

    @Update
    suspend fun updatePrinter(printer: SavedPrinterEntity)

    @Query("DELETE FROM saved_printers WHERE id = :id")
    suspend fun deletePrinter(id: String)

    @Query("UPDATE saved_printers SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE saved_printers SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: String)

    @Query("UPDATE saved_printers SET name = :newName WHERE id = :id")
    suspend fun renamePrinter(id: String, newName: String)

    @Query("UPDATE saved_printers SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_printers SET resourcePath = :newPath WHERE id = :id")
    suspend fun updateResourcePath(id: String, newPath: String)

    @Query("UPDATE saved_printers SET capabilitiesJson = :json, capabilitiesUpdatedAt = :timestamp WHERE id = :id")
    suspend fun updateCapabilities(id: String, json: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE saved_printers SET tlsFingerprint = :fingerprint WHERE id = :id")
    suspend fun updateTlsFingerprint(id: String, fingerprint: String)
}
