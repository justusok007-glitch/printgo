package com.printgo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<PrintJobEntity?>

    @Query("SELECT * FROM print_jobs WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PrintJobEntity?

    @Query("SELECT * FROM print_jobs ORDER BY createdAt DESC")
    suspend fun getAll(): List<PrintJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: PrintJobEntity)

    @Query("DELETE FROM print_jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM print_jobs WHERE createdAt < :cutoff AND status NOT IN ('QUEUED','PREPARING','SENDING','PRINTING')")
    suspend fun findOlderThan(cutoff: Long): List<PrintJobEntity>

    @Query("DELETE FROM print_jobs WHERE createdAt < :cutoff AND status NOT IN ('QUEUED','PREPARING','SENDING','PRINTING')")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM print_jobs WHERE status IN ('COMPLETED','FAILED','CANCELLED')")
    suspend fun findFinished(): List<PrintJobEntity>

    @Query("DELETE FROM print_jobs WHERE status IN ('COMPLETED','FAILED','CANCELLED')")
    suspend fun deleteFinished()

    @Query("SELECT * FROM print_jobs WHERE status IN ('QUEUED','PREPARING','SENDING','PRINTING')")
    suspend fun findActive(): List<PrintJobEntity>
}
