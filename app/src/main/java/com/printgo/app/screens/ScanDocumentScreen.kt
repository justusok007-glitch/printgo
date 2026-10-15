package com.printgo.app.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.components.PrimaryButton
import com.printgo.app.data.print.PdfSaver
import com.printgo.app.data.scan.ScanFilter
import com.printgo.app.data.scan.ScanImageIO
import com.printgo.app.model.PrintItem
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.theme.SurfaceVariantLight
import com.printgo.app.viewmodel.ScanPaper
import com.printgo.app.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Takes a photo with the phone's own camera app into a private file (no camera permission is needed because
 * PrintGo does not declare one). Calls [onPhoto] with the file, or nothing when the user cancels.
 */
@Composable
fun rememberScanCamera(onPhoto: (File) -> Unit, onMessage: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val photoCallback by rememberUpdatedState(onPhoto)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending?.let(::File)
        pending = null
        if (f != null && f.length() > 0 && ok) photoCallback(f) else f?.delete()
    }
    return remember(launcher) {
        {
            val f = File(ScanImageIO.scanDir(context, "camera"), "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, PdfSaver.authority(context), f)
            pending = f.absolutePath
            try {
                launcher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                pending = null
                onMessage("No camera app was found on this phone. Choose a picture from the gallery instead.")
            }
        }
    }
}

/** Picks pictures from the gallery and copies them into the app (the user's photos are never changed). */
@Composable
fun rememberScanGallery(onPictures: (List<File>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPictures)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) { uris.mapNotNull { ScanImageIO.copyIn(context, it) } }
            if (files.isNotEmpty()) callback(files)
        }
    }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** "Scan to PDF": the scanned pages as a list; add, reorder, delete, re-edit; print or save one PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDocumentScreen(
    scanViewModel: ScanViewModel,
    startWithCamera: Boolean,
    onBack: () -> Unit,
    onEditPage: (Int) -> Unit,
    onPrint: (PrintItem) -> Unit
) {
    val context = LocalContext.current
    val state by scanViewModel.state.collectAsStateWithLifecycle()
    val thumbs by scanViewModel.pageThumbs.collectAsStateWithLifecycle()
    val message by scanViewModel.messages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var cameraStarted by rememberSaveable { mutableStateOf(false) }
    var savedPdf by remember { mutableStateOf<File?>(null) }

    BackHandler(onBack = onBack)
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); scanViewModel.clearMessage() } }

    val camera = rememberScanCamera(
        onPhoto = { f ->
            scanViewModel.addPage(f, "Page ${scanViewModel.state.value.pages.size + 1}")
            onEditPage(scanViewModel.state.value.pages.size - 1)
        },
        onMessage = { scope.launch { snackbar.showSnackbar(it) } }
    )
    val gallery = rememberScanGallery { files ->
        val first = scanViewModel.state.value.pages.size
        scanViewModel.addPages(files)
        onEditPage(first)
    }

    LaunchedEffect(startWithCamera) {
        if (startWithCamera && !cameraStarted && state.pages.isEmpty()) {
            cameraStarted = true
            camera()
        }
    }

    fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                snackbar.showSnackbar("Could not make the PDF. ${e.message ?: ""}".trim())
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("scan_document_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Scan to PDF", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        bottomBar = {
            if (state.pages.isNotEmpty()) {
                Surface(color = Color.White, shadowElevation = 12.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runBusy {
                                    val pdf = scanViewModel.buildPdf()
                                    scanViewModel.savePdf(pdf)
                                    savedPdf = pdf
                                }
                            },
                            modifier = Modifier.weight(1f).height(54.dp).testTag("scan_save_pdf"),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, ActionBlue)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save as PDF", color = ActionBlue, fontWeight = FontWeight.Bold)
                        }
                        PrimaryButton(
                            text = "Print (${state.pages.size})",
                            icon = Icons.Default.Print,
                            onClick = { runBusy { onPrint(scanViewModel.pdfItem(scanViewModel.buildPdf())) } },
                            modifier = Modifier.weight(1f),
                            backgroundColor = ActionGreen,
                            testTag = "scan_print"
                        )
                    }
                }
            }
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { padding ->
        if (state.pages.isEmpty()) {
            EmptyScan(Modifier.padding(padding), onCamera = camera, onGallery = gallery)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag("scan_pages_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Paper size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanPaper.entries.forEach { p ->
                            ScanChip(p.label, state.paper == p, "scan_paper_${p.name}") { scanViewModel.setPaper(p) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Filter for all pages", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScanFilter.entries.forEach { f ->
                            ScanChip(f.label, state.pages.all { it.filter == f }, "scan_all_${f.name}") { scanViewModel.applyFilterToAllPages(f) }
                        }
                    }
                }
                itemsIndexed(state.pages, key = { _, p -> p.id }) { i, p ->
                    LaunchedEffect(p.renderKey) { scanViewModel.ensureThumb(p) }
                    PageRow(
                        index = i,
                        count = state.pages.size,
                        thumb = thumbs[p.renderKey],
                        filterLabel = p.filter.label,
                        onUp = { scanViewModel.moveUp(i) },
                        onDown = { scanViewModel.moveDown(i) },
                        onEdit = { onEditPage(i) },
                        onDelete = { scanViewModel.delete(i) }
                    )
                }
                item {
                    Text("Add page", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AddButton(Icons.Default.CameraAlt, "Camera", "scan_add_camera", Modifier.weight(1f), camera)
                        AddButton(Icons.Default.PhotoLibrary, "Gallery", "scan_add_gallery", Modifier.weight(1f), gallery)
                    }
                }
            }
        }
    }

    state.busy?.let { BusyDialog(it) }

    savedPdf?.let { pdf ->
        AlertDialog(
            onDismissRequest = { savedPdf = null },
            title = { Text("PDF saved") },
            text = { Text("\"${pdf.name}\" is in Downloads › PrintGo.") },
            confirmButton = {
                TextButton(onClick = {
                    savedPdf = null
                    try {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, PdfSaver.shareUri(context, pdf))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share PDF"))
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("Could not share the PDF.") }
                    }
                }) { Text("Share", color = ActionBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { savedPdf = null }) { Text("OK", color = BodyGray) } }
        )
    }
}

@Composable
private fun EmptyScan(modifier: Modifier, onCamera: () -> Unit, onGallery: () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(72.dp), shape = CircleShape, color = LightBlue) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Scan a document", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Take a photo of each page. PrintGo finds the page edges, straightens the page and makes one PDF you can print or save.",
                    fontSize = 14.sp, color = BodyGray, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Take photo", icon = Icons.Default.CameraAlt, onClick = onCamera, backgroundColor = ActionBlue, testTag = "scan_take_photo")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("scan_pick_gallery"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Choose from gallery", color = ActionBlue) }
            }
        }
    }
}

@Composable
private fun PageRow(
    index: Int,
    count: Int,
    thumb: android.graphics.Bitmap?,
    filterLabel: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("scan_page_$index")
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariantLight)
                    .border(BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(thumb.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                } else {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ActionBlue)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Page ${index + 1}", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 15.sp)
                Text(filterLabel, color = BodyGray, fontSize = 12.sp)
                TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit", color = ActionBlue, fontSize = 13.sp)
                }
            }
            IconButton(onClick = onUp, enabled = index > 0) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = if (index > 0) ActionBlue else Color.LightGray)
            }
            IconButton(onClick = onDown, enabled = index < count - 1) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = if (index < count - 1) ActionBlue else Color.LightGray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = Color(0xFFD32F2F))
            }
        }
    }
}

@Composable
private fun AddButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tag: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp).testTag(tag),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AccentGreen)
    ) {
        Icon(icon, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = ActionGreen, fontWeight = FontWeight.SemiBold)
    }
}
