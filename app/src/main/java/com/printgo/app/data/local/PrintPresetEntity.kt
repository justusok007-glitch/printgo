package com.printgo.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.printgo.app.model.PrintSettings

@Entity(
    tableName = "print_presets",
    indices = [Index(value = ["name"], unique = true)]
)
data class PrintPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val settingsJson: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toPrintSettings(): PrintSettings {
        return SettingsJsonAdapter.fromJson(settingsJson).copy(activePresetName = name)
    }

    companion object {
        fun fromSettings(id: String, name: String, settings: PrintSettings, isBuiltIn: Boolean = false): PrintPresetEntity {
            return PrintPresetEntity(
                id = id,
                name = name,
                isBuiltIn = isBuiltIn,
                settingsJson = SettingsJsonAdapter.toJson(settings)
            )
        }
    }
}
