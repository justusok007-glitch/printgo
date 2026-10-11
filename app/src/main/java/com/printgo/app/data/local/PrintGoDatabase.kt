package com.printgo.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecentDocumentEntity::class,
        RecentWebPageEntity::class,
        SavedPrinterEntity::class,
        PrintPresetEntity::class,
        PrintJobEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PrintGoDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun recentWebPageDao(): RecentWebPageDao
    abstract fun savedPrinterDao(): SavedPrinterDao
    abstract fun printPresetDao(): PrintPresetDao
    abstract fun printJobDao(): PrintJobDao

    companion object {
        @Volatile
        private var INSTANCE: PrintGoDatabase? = null

        fun getInstance(context: Context): PrintGoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrintGoDatabase::class.java,
                    "printgo_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
