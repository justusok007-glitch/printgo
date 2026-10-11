package com.printgo.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.data.local.PrintGoDatabase
import com.printgo.app.data.local.PrintPresetEntity
import com.printgo.app.data.local.SettingsJsonAdapter
import com.printgo.app.data.repository.PrintPresetRepository
import com.printgo.app.model.DuplexMode
import com.printgo.app.model.PageNumberPosition
import com.printgo.app.model.PageOrder
import com.printgo.app.model.PageSelection
import com.printgo.app.model.PagesPerSheet
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrintAlignment
import com.printgo.app.model.PrintColorMode
import com.printgo.app.model.PrintMargins
import com.printgo.app.model.PrintOrientation
import com.printgo.app.model.PrintQuality
import com.printgo.app.model.PrintScale
import com.printgo.app.model.PrintSettings
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import com.printgo.app.model.WatermarkSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PrintSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PrintGoDatabase.getInstance(application)
    val presetRepository = PrintPresetRepository(db.printPresetDao())

    private val prefs = application.getSharedPreferences("printer_settings_cache", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(PrintSettings.DEFAULT)
    val settings: StateFlow<PrintSettings> = _settings.asStateFlow()

    private val _activePrinterId = MutableStateFlow<String?>(null)
    val activePrinterId: StateFlow<String?> = _activePrinterId.asStateFlow()

    private val _capabilities = MutableStateFlow<PrinterCapabilities?>(null)
    val capabilities: StateFlow<PrinterCapabilities?> = _capabilities.asStateFlow()

    private val _clampedNotice = MutableStateFlow<String?>(null)
    val clampedNotice: StateFlow<String?> = _clampedNotice.asStateFlow()

    val allPresets: StateFlow<List<PrintPresetEntity>> = presetRepository.allPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            presetRepository.ensureBuiltInPresets()
            loadGlobalDefaultSettings()
        }
    }

    private fun loadGlobalDefaultSettings() {
        val globalJson = prefs.getString("global_default_settings", null)
        if (globalJson != null) {
            val loaded = SettingsJsonAdapter.fromJson(globalJson)
            _settings.value = loaded
        }
    }

    /**
     * Called whenever the user switches printers on the Preview screen or Printers screen.
     * Restores the per-printer settings remembered for this printer id.
     */
    fun onPrinterSelected(printer: Printer?, printerCapabilities: PrinterCapabilities?) {
        _capabilities.value = printerCapabilities
        val printerId = printer?.id
        _activePrinterId.value = printerId

        viewModelScope.launch(Dispatchers.IO) {
            val restoredSettings = if (printerId != null) {
                val savedJson = prefs.getString("settings_$printerId", null)
                if (savedJson != null) {
                    SettingsJsonAdapter.fromJson(savedJson)
                } else {
                    // Global default for new printers with printer default media if present
                    val defaultMedia = printerCapabilities?.mediaSupported?.find {
                        it.name.equals(printerCapabilities.mediaDefault, ignoreCase = true)
                    }
                    if (defaultMedia != null) {
                        PrintSettings.DEFAULT.copy(paperSize = defaultMedia)
                    } else {
                        PrintSettings.DEFAULT
                    }
                }
            } else {
                PrintSettings.DEFAULT
            }

            // Validate against capabilities
            val clamped = validateAndDetectClamping(restoredSettings, printerCapabilities)
            _settings.update { current ->
                clamped.copy(
                    pageRotations = current.pageRotations,
                    excludedPages = current.excludedPages,
                    pageSelection = current.pageSelection
                )
            }
        }
    }

    fun updateSettings(transform: (PrintSettings) -> PrintSettings) {
        _settings.update { current ->
            val candidate = transform(current)
            val validated = validateAndDetectClamping(candidate, _capabilities.value)

            val pId = _activePrinterId.value
            viewModelScope.launch(Dispatchers.IO) {
                val json = SettingsJsonAdapter.toJson(validated)
                prefs.edit().apply {
                    putString("global_default_settings", json)
                    if (pId != null) {
                        putString("settings_$pId", json)
                    }
                }.apply()
            }

            validated
        }
    }

    private fun validateAndDetectClamping(
        candidate: PrintSettings,
        capabilities: PrinterCapabilities?
    ): PrintSettings {
        if (capabilities == null) return candidate

        val validated = candidate.validateAgainst(capabilities)
        val reasons = mutableListOf<String>()

        if (candidate.duplex != validated.duplex) {
            reasons.add("Duplex not supported by this printer; reset to ${validated.duplex.label}")
        }
        if (candidate.colorMode != validated.colorMode) {
            reasons.add("Color not supported by this printer; reset to Grayscale")
        }
        if (candidate.quality != validated.quality) {
            reasons.add("Selected quality not supported; reset to ${validated.quality.label}")
        }
        if (candidate.copies != validated.copies) {
            reasons.add("Max copies is ${capabilities.copiesMax}")
        }

        _clampedNotice.value = if (reasons.isNotEmpty()) reasons.joinToString(". ") else null
        return validated
    }

    fun clearClampedNotice() {
        _clampedNotice.value = null
    }

    // --- Granular Setting Setters ---

    fun setCopies(copies: Int) {
        updateSettings { it.copy(copies = copies.coerceIn(1, 99)) }
    }

    fun setCollate(collate: Boolean) {
        updateSettings { it.copy(collate = collate) }
    }

    fun setPageSelection(pageSelection: PageSelection) {
        _settings.update { it.copy(pageSelection = pageSelection) }
    }

    fun setReverseOrder(reverse: Boolean) {
        updateSettings { it.copy(reverseOrder = reverse) }
    }

    fun setPagesPerSheet(pps: PagesPerSheet) {
        updateSettings { it.copy(pagesPerSheet = pps) }
    }

    fun setPageOrder(order: PageOrder) {
        updateSettings { it.copy(pageOrder = order) }
    }

    fun setDuplex(duplex: DuplexMode) {
        updateSettings { it.copy(duplex = duplex) }
    }

    fun setPaperSize(paperSize: PaperSize) {
        updateSettings { it.copy(paperSize = paperSize) }
    }

    fun setOrientation(orientation: PrintOrientation) {
        updateSettings { it.copy(orientation = orientation) }
    }

    fun setScale(scale: PrintScale) {
        updateSettings { it.copy(scale = scale) }
    }

    fun setMargins(margins: PrintMargins) {
        updateSettings { it.copy(margins = margins) }
    }

    fun setAlignment(alignment: PrintAlignment) {
        updateSettings { it.copy(alignment = alignment) }
    }

    fun setColorMode(colorMode: PrintColorMode) {
        updateSettings { it.copy(colorMode = colorMode) }
    }

    fun setQuality(quality: PrintQuality) {
        updateSettings { it.copy(quality = quality) }
    }

    fun setBorderAroundPages(border: Boolean) {
        updateSettings { it.copy(borderAroundPages = border) }
    }

    fun setPageNumbers(position: PageNumberPosition?) {
        updateSettings { it.copy(pageNumbers = position) }
    }

    fun setDateStamp(enabled: Boolean) {
        updateSettings { it.copy(dateStamp = enabled) }
    }

    fun setFileNameHeader(enabled: Boolean) {
        updateSettings { it.copy(fileNameHeader = enabled) }
    }

    fun setWatermark(watermark: WatermarkSettings?) {
        updateSettings { it.copy(watermark = watermark) }
    }

    private var lastDocumentsKey: String? = null

    /**
     * Page choices (range, odd/even, rotations, removed pages) belong to one set of documents; start fresh
     * when the user opens a different selection, but keep them while moving around the same one.
     */
    fun onDocumentsChanged(key: String) {
        if (key == lastDocumentsKey) return
        val first = lastDocumentsKey == null
        lastDocumentsKey = key
        if (first && _settings.value.pageSelection == PageSelection.All) return
        _settings.update {
            it.copy(pageSelection = PageSelection.All, pageRotations = emptyMap(), excludedPages = emptySet())
        }
    }

    fun resetToDefaults() {
        _settings.update { current ->
            val def = PrintSettings.DEFAULT.copy(
                paperSize = current.paperSize,
                activePresetName = null
            )
            val pId = _activePrinterId.value
            viewModelScope.launch(Dispatchers.IO) {
                val json = SettingsJsonAdapter.toJson(def)
                prefs.edit().apply {
                    putString("global_default_settings", json)
                    if (pId != null) putString("settings_$pId", json)
                }.apply()
            }
            def
        }
    }

    fun resetExcludedPages() {
        _settings.update { it.copy(excludedPages = emptySet()) }
    }

    // --- Page Manipulations ---

    fun rotatePage(globalPageIndex: Int, degreesDelta: Int) {
        _settings.update { current ->
            val currentRot = current.pageRotations[globalPageIndex] ?: 0
            val newRot = (currentRot + degreesDelta + 360) % 360
            val newRotations = current.pageRotations.toMutableMap()
            if (newRot == 0) {
                newRotations.remove(globalPageIndex)
            } else {
                newRotations[globalPageIndex] = newRot
            }
            current.copy(pageRotations = newRotations)
        }
    }

    fun rotateAllPages(degreesDelta: Int, totalPages: Int) {
        _settings.update { current ->
            val newRotations = mutableMapOf<Int, Int>()
            for (i in 0 until totalPages) {
                val currentRot = current.pageRotations[i] ?: 0
                val newRot = (currentRot + degreesDelta + 360) % 360
                if (newRot != 0) {
                    newRotations[i] = newRot
                }
            }
            current.copy(pageRotations = newRotations)
        }
    }

    fun togglePageExclusion(globalPageIndex: Int) {
        _settings.update { current ->
            val newExcluded = current.excludedPages.toMutableSet()
            if (newExcluded.contains(globalPageIndex)) {
                newExcluded.remove(globalPageIndex)
            } else {
                newExcluded.add(globalPageIndex)
            }
            current.copy(excludedPages = newExcluded)
        }
    }

    // --- Presets Actions ---

    fun applyPreset(preset: PrintPresetEntity) {
        val targetSettings = preset.toPrintSettings()
        updateSettings { current ->
            targetSettings.copy(
                pageRotations = current.pageRotations,
                excludedPages = current.excludedPages,
                pageSelection = current.pageSelection
            )
        }
    }

    suspend fun saveCurrentAsPreset(name: String): Result<Unit> {
        val current = _settings.value
        val existingNames = allPresets.value.map { it.name }
        val result = presetRepository.saveCustomPreset(name, current, existingNames)
        return if (result.isSuccess) {
            _settings.update { it.copy(activePresetName = result.getOrNull()?.name) }
            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: IllegalArgumentException("Failed to save preset"))
        }
    }

    suspend fun renamePreset(id: String, newName: String): Result<Unit> {
        val existingNames = allPresets.value.map { it.name }
        return presetRepository.renamePreset(id, newName, existingNames)
    }

    suspend fun deletePreset(id: String): Result<Unit> {
        return presetRepository.deletePreset(id)
    }
}
