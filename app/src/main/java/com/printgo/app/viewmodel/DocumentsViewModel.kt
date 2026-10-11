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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DocumentFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    OTHER("Other")
}

data class DocumentsUiState(
    val recentFiles: List<PrintItem> = emptyList(),
    val filteredFiles: List<PrintItem> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: DocumentFilter = DocumentFilter.ALL,
    val isLoading: Boolean = false
)

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecentDocumentRepository(
        PrintGoDatabase.getInstance(application).recentDocumentDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _activeFilter = MutableStateFlow(DocumentFilter.ALL)
    val activeFilter: StateFlow<DocumentFilter> = _activeFilter

    val uiState: StateFlow<DocumentsUiState> = combine(
        repository.recentDocuments,
        _searchQuery,
        _activeFilter
    ) { recents, query, filter ->
        val filtered = recents.filter { item ->
            val matchesQuery = query.isBlank() || item.displayName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                DocumentFilter.ALL -> true
                DocumentFilter.PDF -> item.type == PrintItemType.PDF
                DocumentFilter.WORD -> item.type == PrintItemType.OFFICE && (item.displayName.contains("doc", ignoreCase = true) || item.mimeType.contains("word", ignoreCase = true))
                DocumentFilter.EXCEL -> item.type == PrintItemType.OFFICE && (item.displayName.contains("xls", ignoreCase = true) || item.mimeType.contains("excel", ignoreCase = true) || item.displayName.contains("csv", ignoreCase = true))
                DocumentFilter.OTHER -> item.type == PrintItemType.TEXT || (item.type == PrintItemType.OFFICE && !item.displayName.contains("doc", ignoreCase = true) && !item.displayName.contains("xls", ignoreCase = true))
            }
            matchesQuery && matchesFilter
        }
        DocumentsUiState(
            recentFiles = recents,
            filteredFiles = filtered,
            searchQuery = query,
            activeFilter = filter,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DocumentsUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelected(filter: DocumentFilter) {
        _activeFilter.value = filter
    }

    fun deleteRecentItem(item: PrintItem) {
        viewModelScope.launch {
            repository.removeRecentDocument(item.uri.toString())
        }
    }

    fun clearAllRecents() {
        viewModelScope.launch {
            repository.clearRecentDocuments()
        }
    }

    fun processPickedDocuments(context: Context, uris: List<Uri>, onCompleted: (List<PrintItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = mutableListOf<PrintItem>()
            for (uri in uris) {
                try {
                    // Persist permission
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: Exception) {
                        // Not all providers support persistable permissions
                    }

                    var name = "Document_${System.currentTimeMillis()}"
                    var size = 0L
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                            if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                        }
                    }

                    val printItem = PrintItem(
                        uri = uri,
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        type = PrintItem.resolveType(mime, name)
                    )

                    items.add(printItem)
                    repository.saveRecentDocument(printItem)
                } catch (e: Exception) {
                    // Ignore unreadable document
                }
            }

            withContext(Dispatchers.Main) {
                onCompleted(items)
            }
        }
    }
}
