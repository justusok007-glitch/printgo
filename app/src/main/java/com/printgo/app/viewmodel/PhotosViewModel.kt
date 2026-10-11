package com.printgo.app.viewmodel

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AlbumInfo(
    val name: String,
    val count: Int
)

data class PhotosUiState(
    val isLoading: Boolean = false,
    val allPhotos: List<PrintItem> = emptyList(),
    val filteredPhotos: List<PrintItem> = emptyList(),
    val albums: List<AlbumInfo> = listOf(AlbumInfo("All", 0)),
    val selectedAlbum: String = "All",
    val hasPermission: Boolean = false,
    val permissionRequested: Boolean = false,
    val errorMessage: String? = null
)

class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    fun updatePermission(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted, permissionRequested = true) }
        if (granted) {
            loadDevicePhotos()
        }
    }

    fun selectAlbum(albumName: String) {
        _uiState.update { state ->
            val filtered = if (albumName == "All") {
                state.allPhotos
            } else {
                state.allPhotos.filter { it.mimeType.contains(albumName, ignoreCase = true) || it.displayName.contains(albumName, ignoreCase = true) }
            }
            state.copy(selectedAlbum = albumName, filteredPhotos = filtered)
        }
    }

    fun addPickedPhotos(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val items = uris.mapNotNull { uri ->
                try {
                    var name = "Photo_${System.currentTimeMillis()}"
                    var size = 0L
                    var mime = context.contentResolver.getType(uri) ?: "image/jpeg"

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                            if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                        }
                    }

                    PrintItem(
                        uri = uri,
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        type = PrintItemType.IMAGE
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (items.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val combined = (items + state.allPhotos).distinctBy { it.uri }
                        state.copy(
                            allPhotos = combined,
                            filteredPhotos = combined
                        )
                    }
                }
            }
        }
    }

    fun loadDevicePhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val context = getApplication<Application>()

            val (photos, albumList) = withContext(Dispatchers.IO) {
                val photoItems = mutableListOf<PrintItem>()
                val albumMap = mutableMapOf<String, Int>()

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.MIME_TYPE,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                    } else {
                        MediaStore.Images.Media.DATA
                    }
                )

                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                try {
                    val queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    context.contentResolver.query(
                        queryUri,
                        projection,
                        null,
                        null,
                        sortOrder
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                        val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                        val bucketColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                        } else -1

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            val name = cursor.getString(nameColumn) ?: "Photo"
                            val size = cursor.getLong(sizeColumn)
                            val mime = cursor.getString(mimeColumn) ?: "image/jpeg"
                            val bucket = if (bucketColumn != -1) {
                                cursor.getString(bucketColumn) ?: "Camera"
                            } else "Camera"

                            val contentUri: Uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )

                            photoItems.add(
                                PrintItem(
                                    uri = contentUri,
                                    displayName = name,
                                    mimeType = mime,
                                    sizeBytes = size,
                                    type = PrintItemType.IMAGE
                                )
                            )

                            val count = albumMap.getOrDefault(bucket, 0)
                            albumMap[bucket] = count + 1
                        }
                    }
                } catch (e: Exception) {
                    // Handled gracefully
                }

                val albums = mutableListOf(AlbumInfo("All", photoItems.size))
                albumMap.entries.sortedByDescending { it.value }.forEach { (name, count) ->
                    albums.add(AlbumInfo(name, count))
                }

                Pair(photoItems, albums)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    allPhotos = photos,
                    filteredPhotos = photos,
                    albums = albumList,
                    selectedAlbum = "All"
                )
            }
        }
    }
}
