package com.printgo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_webpages")
data class RecentWebPageEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
