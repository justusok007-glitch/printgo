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
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val _navigateToPreview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToPreview: SharedFlow<Unit> = _navigateToPreview.asSharedFlow()

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

    fun clearSelection() {
        _selectedItems.value = emptyList()
    }

    fun onPrintClicked() {
        if (_selectedItems.value.isNotEmpty()) {
            _navigateToPreview.tryEmit(Unit)
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
        val type = intent.type ?: "*/*"

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

            if (items.isNotEmpty()) {
                // Save documents to recent
                items.forEach { item ->
                    if (item.type != PrintItemType.IMAGE) {
                        saveRecentDocument(item)
                    }
                }
                _selectedItems.value = items
                _navigateToPreview.tryEmit(Unit)
                showMessage("Received ${items.size} file(s) to print")
            }
        }
    }

    private fun resolvePrintItem(context: Context, uri: Uri, fallbackMime: String): PrintItem? {
        return try {
            var name = "Document"
            var size = 0L
            val mime = context.contentResolver.getType(uri) ?: fallbackMime

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

            val itemType = PrintItem.resolveType(mime, name)
            PrintItem(
                uri = uri,
                displayName = name,
                mimeType = mime,
                sizeBytes = size,
                type = itemType
            )
        } catch (e: Exception) {
            null
        }
    }
}
