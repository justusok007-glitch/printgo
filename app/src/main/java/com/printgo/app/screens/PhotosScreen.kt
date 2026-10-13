package com.printgo.app.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.printgo.app.R
import com.printgo.app.components.PrimaryButton
import com.printgo.app.model.PrintItem
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.PhotosViewModel
import com.printgo.app.viewmodel.SelectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    selectionViewModel: SelectionViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    photosViewModel: PhotosViewModel = viewModel(),
    onScanBeforePrint: () -> Unit = {},
    onScanWithCamera: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by photosViewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems by selectionViewModel.selectedItems.collectAsStateWithLifecycle()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        photosViewModel.updatePermission(isGranted)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            photosViewModel.addPickedPhotos(uris)
            selectionViewModel.showMessage("${uris.size} photo(s) selected")
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED
        photosViewModel.updatePermission(granted)
    }

    BackHandler(onBack = onBackClick)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("photos_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.photos_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("photos_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onScanWithCamera,
                        modifier = Modifier.testTag("photos_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Scan with camera",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("photo_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Pick with system picker",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = PrimaryBlue
                )
            )
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Controls Header: Album dropdown & Select All / Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var albumMenuExpanded by remember { mutableStateOf(false) }

                    // Album Dropdown Button
                    Box {
                        Surface(
                            onClick = { albumMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = LightBlue,
                            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.selectedAlbum,
                                    color = HeadingNavy,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = HeadingNavy
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = albumMenuExpanded,
                            onDismissRequest = { albumMenuExpanded = false }
                        ) {
                            uiState.albums.forEach { album ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(album.name, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text("(${album.count})", color = Color.Gray)
                                        }
                                    },
                                    onClick = {
                                        photosViewModel.selectAlbum(album.name)
                                        albumMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Selection Actions (Select All, Clear)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                selectionViewModel.selectAll(uiState.filteredPhotos)
                            },
                            enabled = uiState.filteredPhotos.isNotEmpty()
                        ) {
                            Text("Select all", fontSize = 13.sp, color = ActionBlue)
                        }

                        if (selectedItems.isNotEmpty()) {
                            TextButton(
                                onClick = { selectionViewModel.clearSelection() }
                            ) {
                                Text("Clear", fontSize = 13.sp, color = Color.Red.copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                // Main Content: Grid or Empty / Permission State
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ActionBlue)
                    }
                } else if (!uiState.hasPermission && uiState.allPhotos.isEmpty()) {
                    // Permission Rationale Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    modifier = Modifier.size(64.dp),
                                    shape = CircleShape,
                                    color = LightBlue
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Collections,
                                            contentDescription = null,
                                            tint = ActionBlue,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Access Your Photos",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Allow PrintGo to browse your device photos so you can select and print your memories with ease.",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                PrimaryButton(
                                    text = "Grant Permission",
                                    onClick = { permissionLauncher.launch(permissionToRequest) },
                                    backgroundColor = ActionBlue
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Pick with system picker", color = ActionBlue)
                                }
                            }
                        }
                    }
                } else if (uiState.filteredPhotos.isEmpty()) {
                    // Empty Photos State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Photos Found",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = HeadingNavy
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No images found in this album. You can select photos from anywhere using the system picker.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            PrimaryButton(
                                text = "Pick with system picker",
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                backgroundColor = ActionBlue
                            )
                        }
                    }
                } else {
                    // 3-Column Photos Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("photos_grid"),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = if (selectedItems.isNotEmpty()) 90.dp else 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.filteredPhotos, key = { it.uri.toString() }) { photo ->
                            val isSelected = selectedItems.any { it.uri == photo.uri }

                            PhotoGridItem(
                                photo = photo,
                                isSelected = isSelected,
                                onClick = { selectionViewModel.toggleItem(photo) }
                            )
                        }
                    }
                }
            }

            // Floating Bottom Bar: "Print (N)" button
            AnimatedVisibility(
                visible = selectedItems.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onScanBeforePrint,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("photos_scan_button"),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, ActionBlue),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = ActionBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Scan before Print",
                                    color = ActionBlue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            PrimaryButton(
                                text = "Print (${selectedItems.size})",
                                onClick = { selectionViewModel.onPrintClicked() },
                                modifier = Modifier.weight(1f),
                                backgroundColor = ActionBlue,
                                testTag = "photos_print_button"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoGridItem(
    photo: PrintItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                border = if (isSelected) BorderStroke(3.dp, AccentGreen) else BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dim overlay when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
            )

            // Green Check Badge top-right
            Surface(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .size(24.dp),
                shape = CircleShape,
                color = AccentGreen,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = HeadingNavy,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
