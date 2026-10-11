package com.printgo.app.screens

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.printgo.app.R
import com.printgo.app.components.PrimaryButton
import com.printgo.app.model.PrintItem
import com.printgo.app.model.PrintItemType
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightMint
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.DocumentFilter
import com.printgo.app.viewmodel.DocumentsViewModel
import com.printgo.app.viewmodel.SelectionViewModel

private val SUPPORTED_DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain",
    "text/html"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    selectionViewModel: SelectionViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    documentsViewModel: DocumentsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by documentsViewModel.uiState.collectAsStateWithLifecycle()
    val selectedItems by selectionViewModel.selectedItems.collectAsStateWithLifecycle()

    var itemToDelete by remember { mutableStateOf<PrintItem?>(null) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            documentsViewModel.processPickedDocuments(context, uris) { items ->
                selectionViewModel.addItems(items)
                selectionViewModel.showMessage("${items.size} document(s) added")
            }
        }
    }

    BackHandler(onBack = onBackClick)

    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Remove from Recents?") },
            text = { Text("This will remove \"${itemToDelete?.displayName}\" from your recent documents list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let {
                            documentsViewModel.deleteRecentItem(it)
                            selectionViewModel.deselectItem(it)
                        }
                        itemToDelete = null
                    }
                ) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("documents_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.documents_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("documents_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = if (selectedItems.isNotEmpty()) 90.dp else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Item 1: Big "Browse files" Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browse_documents_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LightMint
                        ),
                        border = BorderStroke(1.5.dp, ActionGreen.copy(alpha = 0.3f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = ActionGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Browse All Documents",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = HeadingNavy
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "PDF, Word, Excel, PowerPoint, Text & Google Drive",
                                fontSize = 13.sp,
                                color = Color(0xFF555555),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            PrimaryButton(
                                text = "Browse Files",
                                onClick = {
                                    documentPickerLauncher.launch(SUPPORTED_DOCUMENT_MIME_TYPES)
                                },
                                backgroundColor = ActionGreen,
                                testTag = "browse_files_button"
                            )
                        }
                    }
                }

                // Item 2: Search Box
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { documentsViewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("documents_search_input"),
                        placeholder = { Text("Search recent documents...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { documentsViewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = ActionGreen,
                            unfocusedBorderColor = Color(0xFFDDE3EA)
                        ),
                        singleLine = true
                    )
                }

                // Item 3: Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilter.values().forEach { filter ->
                            val isSelected = uiState.activeFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { documentsViewModel.onFilterSelected(filter) },
                                label = { Text(filter.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ActionGreen,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White,
                                    labelColor = HeadingNavy
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) ActionGreen else Color(0xFFDDE3EA)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                }

                // Item 4: Recent Files Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Files",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeadingNavy
                        )

                        if (uiState.recentFiles.isNotEmpty()) {
                            TextButton(onClick = { documentsViewModel.clearAllRecents() }) {
                                Text("Clear all", fontSize = 13.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                // Item 5: Empty State or Document List Items
                if (uiState.filteredFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (uiState.searchQuery.isNotEmpty()) "No matching documents" else "No recent documents yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HeadingNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Browse Files' above to open PDF, Word, or Excel documents to print.",
                                    fontSize = 13.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.filteredFiles, key = { it.uri.toString() }) { item ->
                        val isSelected = selectedItems.any { it.uri == item.uri }

                        DocumentListItem(
                            item = item,
                            isSelected = isSelected,
                            onToggle = { selectionViewModel.toggleItem(item) },
                            onDelete = { itemToDelete = item }
                        )
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
                        PrimaryButton(
                            text = "Print (${selectedItems.size})",
                            onClick = { selectionViewModel.onPrintClicked() },
                            backgroundColor = ActionGreen,
                            testTag = "documents_print_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentListItem(
    item: PrintItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) LightMint else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) ActionGreen else Color(0xFFE8EEF5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Type Icon Badge
            val (badgeBg, iconTint, icon) = getDocumentBadge(item)

            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(12.dp),
                color = badgeBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Details (Name, Size, Type)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = HeadingNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.type.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                    if (item.formattedSize().isNotEmpty()) {
                        Text(
                            text = " • ${item.formattedSize()}",
                            fontSize = 12.sp,
                            color = Color(0xFF777777)
                        )
                    }
                }
            }

            // Remove button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove from recent",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Selection Checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = ActionGreen,
                    checkmarkColor = Color.White
                )
            )
        }
    }
}

private fun getDocumentBadge(item: PrintItem): Triple<Color, Color, ImageVector> {
    val name = item.displayName.lowercase()
    val mime = item.mimeType.lowercase()

    return when {
        item.type == PrintItemType.PDF || name.endsWith(".pdf") || mime == "application/pdf" -> {
            Triple(Color(0xFFFFEBEE), Color(0xFFE53935), Icons.Default.PictureAsPdf)
        }
        name.endsWith(".doc") || name.endsWith(".docx") || mime.contains("word") -> {
            Triple(Color(0xFFE3F2FD), Color(0xFF1976D2), Icons.Default.Description)
        }
        name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv") || mime.contains("excel") || mime.contains("spreadsheet") -> {
            Triple(Color(0xFFE8F8EF), Color(0xFF2E7D32), Icons.Default.TableChart)
        }
        else -> {
            Triple(Color(0xFFECEFF1), Color(0xFF546E7A), Icons.Default.TextFields)
        }
    }
}
