package com.printgo.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.data.local.PrintGoDatabase
import com.printgo.app.data.repository.RecentDocumentRepository
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val recentRepo: RecentDocumentRepository = RecentDocumentRepository(
        PrintGoDatabase.getInstance(application).recentDocumentDao()
    )

    private val _selectedItems = MutableStateFlow<List<PrintItem>>(emptyList())
    val selectedItems: StateFlow<List<PrintItem>> = _selectedItems.asStateFlow()

    // A channel keeps the request until the screen is ready to collect it (a shared file can arrive before the
    // navigation is set up when PrintGo is started from another app).
    private val _navigateToPreview = Channel<Unit>(Channel.CONFLATED)
    val navigateToPreview: Flow<Unit> = _navigateToPreview.receiveAsFlow()

    /**
     * True from the moment a file is shared to PrintGo until the print preview is shown. The Home screen clears
     * the selection when it appears; it must not do that while a shared file is on its way.
     */
    @Volatile
    var receivingShare: Boolean = false
        private set

    fun onPreviewShown() {
        receivingShare = false
    }

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun showMessage(msg: String) {
        _snackbarMessage.tryEmit(msg)
    }

    fun isSelected(item: PrintItem): Boolean {
        return _selectedItems.value.any { it.uri == item.uri }
    }

    fun toggleItem(item: PrintItem) {
        _selectedItems.update { current ->
            if (current.any { it.uri == item.uri }) {
                current.filterNot { it.uri == item.uri }
            } else {
                current + item
            }
        }
    }

    fun selectItem(item: PrintItem) {
        _selectedItems.update { current ->
            if (current.none { it.uri == item.uri }) current + item else current
        }
    }

    fun deselectItem(item: PrintItem) {
        _selectedItems.update { current ->
            current.filterNot { it.uri == item.uri }
        }
    }

    fun addItems(items: List<PrintItem>) {
        _selectedItems.update { current ->
            val existingUris = current.map { it.uri }.toSet()
            val newItems = items.filterNot { it.uri in existingUris }
            current + newItems
        }
    }

    fun selectAll(items: List<PrintItem>) {
        _selectedItems.update { current ->
            val existingUris = current.map { it.uri }.toSet()
            val newOnes = items.filterNot { it.uri in existingUris }
            current + newOnes
        }
    }

    /** Swaps items for edited copies (Scan before Print), keeping their order. */
    fun replaceItems(replacements: Map<Uri, PrintItem>) {
        if (replacements.isEmpty()) return
        _selectedItems.update { current -> current.map { replacements[it.uri] ?: it } }
    }

    /** Selects only [items] (a scanned PDF to print). */
    fun setItems(items: List<PrintItem>) {
        _selectedItems.value = items
    }

    fun clearSelection() {
        _selectedItems.value = emptyList()
    }

    fun onPrintClicked() {
        if (_selectedItems.value.isNotEmpty()) {
            _navigateToPreview.trySend(Unit)
        }
    }

    fun saveRecentDocument(item: PrintItem) {
        viewModelScope.launch {
            try {
                recentRepo.saveRecentDocument(item)
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    fun removeRecentDocument(uriString: String) {
        viewModelScope.launch {
            try {
                recentRepo.removeRecentDocument(uriString)
            } catch (e: Exception) {
                // Ignore DB error
            }
        }
    }

    fun handleIncomingIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE && action != Intent.ACTION_VIEW) return
        val type = intent.type ?: "*/*"
        receivingShare = true

        viewModelScope.launch {
            val items = mutableListOf<PrintItem>()
            when (action) {
                Intent.ACTION_SEND -> {
                    val streamUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (streamUri != null) {
                        resolvePrintItem(context, streamUri, type)?.let { items.add(it) }
                    } else {
                        // Could be plain text shared
                        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
                        if (!extraText.isNullOrBlank()) {
                            items.add(
                                PrintItem(
                                    uri = Uri.parse("text://printgo/shared_snippet"),
                                    displayName = "Shared Text Snippet",
                                    mimeType = "text/plain",
                                    sizeBytes = extraText.toByteArray().size.toLong(),
                                    type = PrintItemType.TEXT
                                )
                            )
                        }
                    }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    val streamUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    streamUris?.forEach { uri ->
                        resolvePrintItem(context, uri, type)?.let { items.add(it) }
                    }
                }

                Intent.ACTION_VIEW -> {
                    intent.data?.let { uri ->
                        resolvePrintItem(context, uri, type)?.let { items.add(it) }
                    }
                }
            }

            // Some file managers put the file only in the intent's ClipData
            if (items.isEmpty() && action != Intent.ACTION_VIEW) {
                val clip = intent.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(i).uri ?: continue
                        resolvePrintItem(context, uri, type)?.let { items.add(it) }
                    }
                }
            }

            if (items.isEmpty()) {
                receivingShare = false
                if (action == Intent.ACTION_VIEW || intent.hasExtra(Intent.EXTRA_STREAM) || intent.clipData != null) {
                    showMessage("Could not open the shared file. Please try again.")
                }
            }

            if (items.isNotEmpty()) {
                // Save documents to recent
                items.forEach { item ->
                    if (item.type != PrintItemType.IMAGE && item.uri.scheme != "file") {
                        saveRecentDocument(item)
                    }
                }
                _selectedItems.value = items
                _navigateToPreview.trySend(Unit)
                showMessage("Received ${items.size} file(s) to print")
            }
        }
    }

    /**
     * Reads the shared content right away (permissions on shared URIs are temporary), sniffs the real
     * file type when the sender gave a vague one, and keeps a private copy so printing still works later.
     */
    private suspend fun resolvePrintItem(context: Context, uri: Uri, fallbackMime: String): PrintItem? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var name = "Document"
                var size = 0L
                var mime = context.contentResolver.getType(uri) ?: fallbackMime

                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                            if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                        }
                    }
                } else if (uri.scheme == "file") {
                    uri.lastPathSegment?.let { name = it }
                }

                var finalUri = uri
                if (uri.scheme == "content") {
                    val dir = java.io.File(context.cacheDir, "printgo_shared").apply { mkdirs() }
                    val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "shared" }
                    val copy = java.io.File(dir, "${java.util.UUID.randomUUID().toString().take(8)}_$safe")
                    val ok = try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            copy.outputStream().use { out -> input.copyTo(out) }
                            true
                        } ?: false
                    } catch (e: Exception) { false }
                    if (ok && copy.length() > 0) {
                        finalUri = Uri.fromFile(copy)
                        size = copy.length()
                        if (mime.isBlank() || mime == "*/*" || mime == "application/octet-stream") {
                            sniffMime(copy)?.let { mime = it }
                        }
                    }
                }

                PrintItem(
                    uri = finalUri,
                    displayName = name,
                    mimeType = mime,
                    sizeBytes = size,
                    type = PrintItem.resolveType(mime, name)
                )
            } catch (e: Exception) {
                null
            }
        }

    private fun sniffMime(f: java.io.File): String? {
        val head = ByteArray(8)
        val n = try { f.inputStream().use { it.read(head) } } catch (e: Exception) { return null }
        if (n < 4) return null
        fun b(i: Int) = head[i].toInt() and 0xFF
        return when {
            b(0) == 0x25 && b(1) == 0x50 && b(2) == 0x44 && b(3) == 0x46 -> "application/pdf"
            b(0) == 0xFF && b(1) == 0xD8 && b(2) == 0xFF -> "image/jpeg"
            b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 -> "image/png"
            b(0) == 0x47 && b(1) == 0x49 && b(2) == 0x46 -> "image/gif"
            b(0) == 0x52 && b(1) == 0x49 && b(2) == 0x46 && b(3) == 0x46 -> "image/webp"
            b(0) == 0x50 && b(1) == 0x4B -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> null
        }
    }
}
