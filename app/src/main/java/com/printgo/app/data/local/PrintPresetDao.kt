package com.printgo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintPresetDao {

    @Query("SELECT * FROM print_presets ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getAllPresets(): Flow<List<PrintPresetEntity>>

    @Query("SELECT * FROM print_presets WHERE id = :id LIMIT 1")
    suspend fun getPresetById(id: String): PrintPresetEntity?

    @Query("SELECT * FROM print_presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): PrintPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PrintPresetEntity)

    @Update
    suspend fun updatePreset(preset: PrintPresetEntity)

    @Query("UPDATE print_presets SET name = :newName WHERE id = :id AND isBuiltIn = 0")
    suspend fun renamePreset(id: String, newName: String)

    @Query("DELETE FROM print_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deletePreset(id: String)

    @Query("SELECT COUNT(*) FROM print_presets")
    suspend fun getCount(): Int
}
