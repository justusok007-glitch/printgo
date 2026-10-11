package com.printgo.app.data.repository

import com.printgo.app.data.local.PrintPresetDao
import com.printgo.app.data.local.PrintPresetEntity
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PagesPerSheet
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintMargins
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.PrintScale
import com.printgo.app.model.PrintSettings
import com.printgo.app.model.PrintSettingsValidator
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PrintPresetRepository(private val dao: PrintPresetDao) {

    val allPresets: Flow<List<PrintPresetEntity>> = dao.getAllPresets()

    suspend fun ensureBuiltInPresets() {
        val count = dao.getCount()
        if (count == 0) {
            // Seed built-in presets
            val draftSaver = PrintPresetEntity.fromSettings(
                id = "builtin_draft_saver",
                name = "Draft saver",
                settings = PrintSettings.DEFAULT.copy(
                    colorMode = PrintColorMode.GRAYSCALE,
                    quality = PrintQuality.DRAFT,
                    pagesPerSheet = PagesPerSheet.TWO,
                    duplex = DuplexMode.TWO_SIDED_LONG_EDGE,
                    activePresetName = "Draft saver"
                ),
                isBuiltIn = true
            )

            val photo = PrintPresetEntity.fromSettings(
                id = "builtin_photo",
                name = "Photo",
                settings = PrintSettings.DEFAULT.copy(
                    colorMode = PrintColorMode.COLOR,
                    quality = PrintQuality.HIGH,
                    scale = PrintScale.FitToPage,
                    margins = PrintMargins.None,
                    pagesPerSheet = PagesPerSheet.ONE,
                    activePresetName = "Photo"
                ),
                isBuiltIn = true
            )

            val document = PrintPresetEntity.fromSettings(
                id = "builtin_document",
                name = "Document",
                settings = PrintSettings.DEFAULT.copy(
                    colorMode = PrintColorMode.COLOR,
                    quality = PrintQuality.NORMAL,
                    scale = PrintScale.FitToPage,
                    margins = PrintMargins.Narrow,
                    pagesPerSheet = PagesPerSheet.ONE,
                    activePresetName = "Document"
                ),
                isBuiltIn = true
            )

            dao.insertPreset(draftSaver)
            dao.insertPreset(photo)
            dao.insertPreset(document)
        }
    }

    suspend fun saveCustomPreset(name: String, currentSettings: PrintSettings, existingNames: List<String>): Result<PrintPresetEntity> {
        val validation = PrintSettingsValidator.validatePresetName(name, existingNames)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid preset name"))
        }
        val validatedName = validation.getOrThrow()
        val id = UUID.randomUUID().toString()
        val entity = PrintPresetEntity.fromSettings(
            id = id,
            name = validatedName,
            settings = currentSettings.copy(activePresetName = validatedName),
            isBuiltIn = false
        )
        dao.insertPreset(entity)
        return Result.success(entity)
    }

    suspend fun renamePreset(id: String, newName: String, existingNames: List<String>): Result<Unit> {
        val validation = PrintSettingsValidator.validatePresetName(newName, existingNames)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull() ?: IllegalArgumentException("Invalid preset name"))
        }
        val entity = dao.getPresetById(id)
        if (entity == null || entity.isBuiltIn) {
            return Result.failure(IllegalStateException("Cannot rename built-in preset or preset not found"))
        }
        dao.renamePreset(id, validation.getOrThrow())
        return Result.success(Unit)
    }

    suspend fun deletePreset(id: String): Result<Unit> {
        val entity = dao.getPresetById(id)
        if (entity == null || entity.isBuiltIn) {
            return Result.failure(IllegalStateException("Cannot delete built-in preset or preset not found"))
        }
        dao.deletePreset(id)
        return Result.success(Unit)
    }
}
