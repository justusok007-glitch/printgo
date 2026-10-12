package com.printgo.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.data.drivers.DriverCatalog
import com.printgo.app.data.drivers.DriverEntry
import com.printgo.app.data.drivers.DriverKind
import com.printgo.app.data.drivers.DriverMatcher
import com.printgo.app.data.drivers.DriverPacks
import com.printgo.app.data.drivers.DriverPrefs
import com.printgo.app.data.drivers.DriverRunner
import com.printgo.app.model.Printer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "Printer driver" screen and the driver packs in Settings: which built-in driver a printer uses
 * (Automatic by default), suggestions from the printer's own device ID and name, search over every
 * supported model, and downloading or removing the data packs.
 */
class DriverViewModel(application: Application) : AndroidViewModel(application) {

    data class PackUi(
        val pack: DriverPacks.Pack,
        val installed: Boolean,
        val outdated: Boolean,
        val installedBytes: Long,
        val progress: Float? = null
    )

    data class Suggestion(val entry: DriverEntry, val exact: Boolean)

    /** A driver the user tapped that still needs its pack downloaded first. */
    data class PendingDownload(val entry: DriverEntry?, val pack: DriverPacks.Pack)

    data class UiState(
        val loading: Boolean = true,
        val driversInApp: Boolean = false,
        val printerName: String = "",
        val printerModel: String? = null,
        val choiceId: String? = null,
        val choice: DriverEntry? = null,
        val suggestions: List<Suggestion> = emptyList(),
        val query: String = "",
        val results: List<DriverEntry> = emptyList(),
        val moreResults: Boolean = false,
        val totalModels: Int = 0,
        val packs: List<PackUi> = emptyList(),
        val pending: PendingDownload? = null,
        val message: String? = null
    )

    private val app = application
    private val prefs = DriverPrefs(application)
    private val packs = DriverPacks(application)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var catalog: List<DriverEntry> = emptyList()
    private var printerId: String? = null
    private var downloadJob: Job? = null

    /** Loads everything for [printer]; [makeAndModel] is what the printer reported about itself, if known. */
    fun load(printer: Printer, makeAndModel: String?) {
        printerId = printer.id
        viewModelScope.launch {
            catalog = withContext(Dispatchers.IO) { DriverCatalog.load(app) }
            val deviceId = prefs.deviceId(printer.id)
            val names = listOfNotNull(makeAndModel, printer.model?.let { m -> printer.make?.let { "$it $m" } ?: m }, printer.name)
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val suggestions = suggest(deviceId, names, catalog)
            val choiceId = prefs.choice(printer.id)
            _state.update {
                it.copy(
                    loading = false,
                    driversInApp = driversInApp(),
                    printerName = printer.name,
                    printerModel = makeAndModel?.takeIf { m -> m.isNotBlank() } ?: printer.model,
                    choiceId = choiceId,
                    choice = choiceId?.let { id -> find(id) },
                    suggestions = suggestions,
                    totalModels = catalog.size,
                    packs = packUi()
                )
            }
        }
    }

    /** Loads only the driver packs (Settings). */
    fun loadPacks() {
        viewModelScope.launch {
            catalog = withContext(Dispatchers.IO) { DriverCatalog.load(app) }
            _state.update { it.copy(loading = false, driversInApp = driversInApp(), totalModels = catalog.size, packs = packUi()) }
        }
    }

    private fun driversInApp(): Boolean = catalog.isNotEmpty() && DriverKind.entries.all { DriverRunner.isAvailable(app, it.program) }

    private fun find(id: String): DriverEntry? = catalog.firstOrNull { it.id == id }

    fun setQuery(q: String) {
        val (results, more) = search(q, catalog)
        _state.update { it.copy(query = q, results = results, moreResults = more) }
    }

    /** Automatic: PrintGo talks to the printer itself and uses a built-in driver only when it must. */
    fun chooseAutomatic() {
        val id = printerId ?: return
        prefs.setChoice(id, null)
        _state.update { it.copy(choiceId = null, choice = null, message = "PrintGo will choose how to print automatically.") }
    }

    fun choose(entry: DriverEntry) {
        val name = entry.kind.pack
        if (name != null && !packs.isInstalled(name)) {
            val pack = packs.packs[name]
            if (pack == null) {
                _state.update { it.copy(message = "This driver is not part of this copy of PrintGo.") }
                return
            }
            _state.update { it.copy(pending = PendingDownload(entry, pack)) }
            return
        }
        apply(entry)
    }

    private fun apply(entry: DriverEntry) {
        val id = printerId ?: return
        prefs.setChoice(id, entry.id)
        _state.update { it.copy(choiceId = entry.id, choice = entry, message = "${entry.displayName} will be used for this printer.") }
    }

    fun askDownload(pack: DriverPacks.Pack) {
        _state.update { it.copy(pending = PendingDownload(null, pack)) }
    }

    fun dismissPending() {
        _state.update { it.copy(pending = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Downloads the pending pack, then selects the driver that asked for it. */
    fun confirmDownload() {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            setProgress(pending.pack.name, 0f)
            try {
                packs.download(pending.pack.name) { p -> setProgress(pending.pack.name, p) }
                setProgress(pending.pack.name, null)
                _state.update { it.copy(packs = packUi(), message = "${pending.pack.label}: ready.") }
                pending.entry?.let { apply(it) }
            } catch (e: CancellationException) {
                setProgress(pending.pack.name, null)
                throw e
            } catch (e: Exception) {
                setProgress(pending.pack.name, null)
                _state.update { it.copy(packs = packUi(), message = e.message ?: "The download failed. Please try again.") }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun deletePack(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packs.delete(name) }
            _state.update { it.copy(packs = packUi(), message = "Driver pack removed. It downloads again when a printer needs it.") }
        }
    }

    /** A pack the user saved on the phone (for example downloaded on a computer). */
    fun installFromFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = packs.installFromUri(uri)
                val label = packs.packs[name]?.label ?: name
                _state.update { it.copy(packs = packUi(), message = "$label: installed.") }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "That file could not be installed.") }
            }
        }
    }

    private fun setProgress(name: String, p: Float?) {
        _state.update { s -> s.copy(packs = s.packs.map { if (it.pack.name == name) it.copy(progress = p) else it }) }
    }

    private fun packUi(): List<PackUi> = listOf("gutenprint", "hplip", "splix").mapNotNull { n ->
        val p = packs.packs[n] ?: return@mapNotNull null
        val installed = packs.isInstalled(n)
        PackUi(p, installed, packs.isOutdated(n), if (installed) packs.installedSize(n) else 0L)
    }

    companion object {
        /** Drivers that fit a printer, best first, from its device ID and every name it goes by. */
        fun suggest(deviceId: String?, names: List<String>, catalog: List<DriverEntry>, limit: Int = 6): List<Suggestion> {
            if (catalog.isEmpty()) return emptyList()
            val best = LinkedHashMap<String, DriverMatcher.Match>()
            val tries = if (names.isEmpty()) listOf<String?>(null) else names
            for (n in tries) {
                for (m in DriverMatcher.candidates(deviceId, n, catalog, limit)) {
                    val old = best[m.entry.id]
                    if (old == null || m.score > old.score) best[m.entry.id] = m
                }
            }
            return best.values.sortedWith(compareByDescending<DriverMatcher.Match> { it.score }.thenBy { it.entry.model.length })
                .take(limit).map { Suggestion(it.entry, it.score >= DriverMatcher.EXACT) }
        }

        /** Models whose name contains every word of [query]; at most [limit] (the flag says if there were more). */
        fun search(query: String, catalog: List<DriverEntry>, limit: Int = 80): Pair<List<DriverEntry>, Boolean> {
            val words = query.lowercase().split(' ', '-', '_', '/').map { it.trim() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) return emptyList<DriverEntry>() to false
            val hits = catalog.filter { e ->
                val hay = (e.displayName + " " + e.kind.label).lowercase()
                val compact = hay.filter { it.isLetterOrDigit() }
                words.all { w -> hay.contains(w) || compact.contains(w.filter { it.isLetterOrDigit() }) }
            }
            val order = compareBy<DriverEntry>({ kindOrder(it.kind) }, { it.displayName.length }, { it.displayName.lowercase() })
            val sorted = hits.sortedWith(order)
            return sorted.take(limit) to (sorted.size > limit)
        }

        private fun kindOrder(k: DriverKind) = when (k) {
            DriverKind.ESCPR -> 0
            DriverKind.HPCUPS -> 1
            DriverKind.SPLIX -> 2
            DriverKind.GUTENPRINT -> 3
        }
    }
}
