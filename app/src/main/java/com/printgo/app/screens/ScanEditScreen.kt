package com.printgo.app.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.data.scan.CropMode
import com.printgo.app.data.scan.CropShape
import com.printgo.app.data.scan.Pt
import com.printgo.app.data.scan.Quad
import com.printgo.app.data.scan.ScanFilter
import com.printgo.app.data.scan.ScanGeometry
import com.printgo.app.data.scan.ScanPage
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.SurfaceVariantLight
import com.printgo.app.viewmodel.ScanPurpose
import com.printgo.app.viewmodel.ScanViewModel
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scan/Edit screen: big picture on top, Crop and Filters tabs at the bottom, a page strip when several
 * pictures are edited. Works on copies only; "Done" hands the edited pages back (see [onDone]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanEditScreen(
    scanViewModel: ScanViewModel,
    onCancel: () -> Unit,
    onDone: suspend () -> Unit
) {
    val state by scanViewModel.state.collectAsStateWithLifecycle()
    val display by scanViewModel.display.collectAsStateWithLifecycle()
    val result by scanViewModel.result.collectAsStateWithLifecycle()
    val thumbs by scanViewModel.filterThumbs.collectAsStateWithLifecycle()
    val pageThumbs by scanViewModel.pageThumbs.collectAsStateWithLifecycle()
    val message by scanViewModel.messages.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val page = state.current

    BackHandler(onBack = onCancel)

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); scanViewModel.clearMessage() }
    }
    // A page opened in the 4-corner crop looks for its edges once by itself.
    LaunchedEffect(page?.id, page?.cropMode) {
        val p = page ?: return@LaunchedEffect
        if (p.cropMode == CropMode.CORNERS && !p.detected && !state.detecting) scanViewModel.detectPage()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("scan_edit_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.pages.size > 1) "Page ${state.index + 1} of ${state.pages.size}" else
                            if (state.purpose == ScanPurpose.PHOTOS) "Edit photo" else "Edit scan",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("scan_cancel")) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = page != null && state.busy == null) {
                                scope.launch {
                                    try {
                                        onDone()
                                    } catch (e: Throwable) {
                                        snackbar.showSnackbar("Could not prepare the picture. Please try again.")
                                    }
                                }
                            }
                            .testTag("scan_done"),
                        shape = RoundedCornerShape(18.dp),
                        color = AccentGreen
                    ) {
                        Text(
                            "Done", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            color = HeadingNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = SurfaceVariantLight
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val shown = display
                when {
                    page == null -> Text("No picture", color = BodyGray)
                    tab == 0 && shown != null -> CropCanvas(
                        bitmap = shown,
                        page = page,
                        imageAspect = shown.width.toDouble() / shown.height,
                        onQuadChange = scanViewModel::setQuad,
                        modifier = Modifier.fillMaxSize()
                    )
                    tab == 1 && result != null -> Image(
                        bitmap = result!!.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                    else -> CircularProgressIndicator(color = ActionBlue)
                }
                if (state.detecting) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                        shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 3.dp
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ActionBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Finding the page edges…", fontSize = 13.sp, color = HeadingNavy)
                        }
                    }
                }
            }

            if (state.pages.size > 1) {
                PageStrip(
                    pages = state.pages,
                    selected = state.index,
                    thumbs = pageThumbs,
                    onEnsureThumb = scanViewModel::ensureThumb,
                    onSelect = scanViewModel::select
                )
            }

            Surface(color = Color.White, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    if (page != null) {
                        if (tab == 0) CropPanel(page, scanViewModel) else FilterPanel(page, thumbs, state.pages.size, scanViewModel)
                    }
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.White,
                        contentColor = PrimaryBlue,
                        indicator = { positions ->
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = AccentGreen)
                        }
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Crop") },
                            icon = { Icon(Icons.Default.Crop, contentDescription = null) },
                            modifier = Modifier.testTag("scan_tab_crop"))
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Filters") },
                            icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                            modifier = Modifier.testTag("scan_tab_filters"))
                    }
                }
            }
        }
    }

    state.busy?.let { BusyDialog(it) }
}

@Composable
internal fun BusyDialog(text: String) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), color = ActionBlue, strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(text, color = HeadingNavy, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PageStrip(
    pages: List<ScanPage>,
    selected: Int,
    thumbs: Map<String, Bitmap>,
    onEnsureThumb: (ScanPage) -> Unit,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Color.White).testTag("scan_page_strip"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
            LaunchedEffect(p.renderKey) { onEnsureThumb(p) }
            val thumb = thumbs[p.renderKey]
            Box(
                Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(if (i == selected) BorderStroke(3.dp, AccentGreen) else BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(8.dp))
                    .background(SurfaceVariantLight)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(thumb.asImageBitmap(), contentDescription = "Page ${i + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                    shape = CircleShape, color = if (i == selected) AccentGreen else Color.White
                ) {
                    Text("${i + 1}", fontSize = 10.sp, color = HeadingNavy, modifier = Modifier.padding(horizontal = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun CropPanel(page: ScanPage, vm: ScanViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropMode.entries.forEach { m ->
                ScanChip(m.label, page.cropMode == m, "crop_mode_${m.name}") { vm.setCropMode(m) }
            }
        }
        if (page.cropMode == CropMode.PAPER) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropShape.entries.forEach { s -> ScanChip(s.label, page.shape == s, "crop_shape_${s.name}") { vm.setShape(s) } }
                if (page.shape != CropShape.SQUARE) {
                    ScanChip(if (page.landscape) "Landscape" else "Portrait", page.landscape, "crop_landscape") {
                        vm.setLandscape(!page.landscape)
                    }
                }
            }
        }
        if (page.cropMode == CropMode.CORNERS) {
            Text(
                "Drag the 4 dots onto the corners of the page. It will be straightened.",
                fontSize = 12.sp, color = BodyGray, modifier = Modifier.padding(top = 6.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolButton(Icons.Default.RotateLeft, "Turn left", "rotate_left") { vm.rotate(-1) }
            ToolButton(Icons.Default.RotateRight, "Turn right", "rotate_right") { vm.rotate(1) }
            if (page.cropMode == CropMode.CORNERS) {
                ToolButton(Icons.Default.CenterFocusStrong, "Find page", "detect_page") { vm.detectPage() }
            }
            ToolButton(Icons.Default.Refresh, "Reset", "reset_crop") { vm.resetCrop() }
        }
    }
}

@Composable
private fun FilterPanel(page: ScanPage, thumbs: Map<ScanFilter, Bitmap>, pageCount: Int, vm: ScanViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanFilter.entries.forEach { f ->
                val selected = page.filter == f
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.setFilter(f) }
                        .padding(2.dp)
                        .testTag("filter_${f.name}")
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(if (selected) BorderStroke(3.dp, AccentGreen) else BorderStroke(1.dp, Color.LightGray), RoundedCornerShape(10.dp))
                            .background(SurfaceVariantLight),
                        contentAlignment = Alignment.Center
                    ) {
                        val t = thumbs[f]
                        if (t != null) Image(t.asImageBitmap(), contentDescription = f.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ActionBlue)
                    }
                    Text(
                        f.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) PrimaryBlue else HeadingNavy,
                        modifier = Modifier.width(72.dp).padding(top = 3.dp), textAlign = TextAlign.Center
                    )
                }
            }
        }
        Text(page.filter.hint, fontSize = 12.sp, color = BodyGray, modifier = Modifier.padding(top = 4.dp))
        if (page.filter != ScanFilter.ORIGINAL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", fontSize = 13.sp, color = HeadingNavy, modifier = Modifier.width(66.dp))
                Slider(
                    value = page.strength,
                    onValueChange = vm::setStrength,
                    modifier = Modifier.weight(1f).testTag("filter_strength"),
                    colors = SliderDefaults.colors(thumbColor = ActionBlue, activeTrackColor = ActionBlue)
                )
                Text("${(page.strength * 100).roundToInt()}%", fontSize = 13.sp, color = HeadingNavy, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
            }
        }
        if (pageCount > 1) {
            OutlinedButton(
                onClick = vm::applyFilterToAll,
                modifier = Modifier.fillMaxWidth().testTag("filter_apply_all"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, ActionBlue)
            ) { Text("Apply to all $pageCount pages", color = ActionBlue, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
internal fun ScanChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 13.sp) },
        modifier = Modifier.testTag(tag),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LightBlue,
            selectedLabelColor = PrimaryBlue
        )
    )
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, ActionBlue.copy(alpha = 0.5f))
    ) {
        Icon(icon, contentDescription = null, tint = ActionBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = ActionBlue, fontSize = 13.sp)
    }
}

// ---------------- crop canvas ----------------

private sealed interface DragTarget {
    data class Corner(val index: Int) : DragTarget
    /** 0 top, 1 right, 2 bottom, 3 left */
    data class Edge(val side: Int) : DragTarget
    data object Move : DragTarget
}

private const val MIN_SIZE = 0.05

/** New crop after a drag, or null to ignore the move (too small, crossed corners). */
private fun dragQuad(target: DragTarget, page: ScanPage, rel: Pt, delta: Pt, aspect: Double): Quad? {
    val q = page.quad
    return when (target) {
        is DragTarget.Corner -> when (page.cropMode) {
            CropMode.CORNERS -> q.with(target.index, rel).takeIf { ScanGeometry.isConvex(it) && ScanGeometry.area(it) > 0.002 }
            CropMode.FREE -> {
                val opp = q.bounds().points[(target.index + 2) % 4]
                val l = min(opp.x, rel.x); val r = max(opp.x, rel.x)
                val t = min(opp.y, rel.y); val b = max(opp.y, rel.y)
                if (r - l < MIN_SIZE || b - t < MIN_SIZE) null else Quad.rect(l, t, r, b)
            }
            CropMode.PAPER -> {
                val opp = q.bounds().points[(target.index + 2) % 4]
                ScanGeometry.ratioRectFromCorner(opp, rel, page.shape.ratio(page.landscape), aspect)
            }
        }
        is DragTarget.Edge -> {
            val b = q.bounds()
            var l = b.minX; var t = b.minY; var r = b.maxX; var bt = b.maxY
            when (target.side) {
                0 -> t = min(rel.y, bt - MIN_SIZE)
                1 -> r = max(rel.x, l + MIN_SIZE)
                2 -> bt = max(rel.y, t + MIN_SIZE)
                else -> l = min(rel.x, r - MIN_SIZE)
            }
            Quad.rect(l, t, r, bt)
        }
        DragTarget.Move -> {
            val dx = delta.x.coerceIn(-q.minX, 1 - q.maxX)
            val dy = delta.y.coerceIn(-q.minY, 1 - q.maxY)
            q.map { Pt(it.x + dx, it.y + dy) }
        }
    }
}

private fun insideQuad(q: Quad, p: Pt): Boolean {
    val pts = q.points
    var sign = 0
    for (i in 0 until 4) {
        val a = pts[i]; val b = pts[(i + 1) % 4]
        val c = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
        val s = if (c >= 0) 1 else -1
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

@Composable
private fun CropCanvas(
    bitmap: Bitmap,
    page: ScanPage,
    imageAspect: Double,
    onQuadChange: (Quad) -> Unit,
    modifier: Modifier = Modifier
) {
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val handleR = with(density) { 11.dp.toPx() }
    val touchR = with(density) { 36.dp.toPx() }
    val pad = with(density) { 22.dp.toPx() }
    val magR = with(density) { 56.dp.toPx() }
    val magGap = with(density) { 12.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(page)
    val onChange by rememberUpdatedState(onQuadChange)
    var target by remember { mutableStateOf<DragTarget?>(null) }
    var finger by remember { mutableStateOf<Offset?>(null) }

    fun imageRect(): Rect {
        val box = ScanGeometry.fitInside(
            bitmap.width.toDouble(), bitmap.height.toDouble(),
            max(1.0, canvasSize.width - 2.0 * pad), max(1.0, canvasSize.height - 2.0 * pad)
        )
        return Rect(Offset((pad + box[0]).toFloat(), (pad + box[1]).toFloat()), Size(box[2].toFloat(), box[3].toFloat()))
    }

    fun toScreen(r: Rect, p: Pt) = Offset(r.left + (p.x * r.width).toFloat(), r.top + (p.y * r.height).toFloat())

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .testTag("scan_crop_canvas")
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val r = imageRect()
                        val p = current
                        val pts = p.quad.points.map { toScreen(r, it) }
                        val nearest = pts.indices.minBy { hypot((pts[it].x - pos.x).toDouble(), (pts[it].y - pos.y).toDouble()) }
                        val dist = hypot((pts[nearest].x - pos.x).toDouble(), (pts[nearest].y - pos.y).toDouble())
                        target = when {
                            dist <= touchR -> DragTarget.Corner(nearest)
                            p.cropMode == CropMode.FREE -> {
                                val mids = (0 until 4).map { (pts[it] + pts[(it + 1) % 4]) / 2f }
                                val e = mids.indices.minBy { hypot((mids[it].x - pos.x).toDouble(), (mids[it].y - pos.y).toDouble()) }
                                if (hypot((mids[e].x - pos.x).toDouble(), (mids[e].y - pos.y).toDouble()) <= touchR) DragTarget.Edge(e)
                                else if (insideQuad(p.quad, Pt(((pos.x - r.left) / r.width).toDouble(), ((pos.y - r.top) / r.height).toDouble()))) DragTarget.Move
                                else null
                            }
                            insideQuad(p.quad, Pt(((pos.x - r.left) / r.width).toDouble(), ((pos.y - r.top) / r.height).toDouble())) -> DragTarget.Move
                            else -> null
                        }
                        finger = pos
                    },
                    onDragEnd = { target = null; finger = null },
                    onDragCancel = { target = null; finger = null },
                    onDrag = { change, amount ->
                        val t = target ?: return@detectDragGestures
                        change.consume()
                        val r = imageRect()
                        if (r.width <= 0f || r.height <= 0f) return@detectDragGestures
                        val pos = change.position
                        finger = pos
                        val rel = Pt(
                            ((pos.x - r.left) / r.width).toDouble().coerceIn(0.0, 1.0),
                            ((pos.y - r.top) / r.height).toDouble().coerceIn(0.0, 1.0)
                        )
                        val delta = Pt((amount.x / r.width).toDouble(), (amount.y / r.height).toDouble())
                        dragQuad(t, current, rel, delta, imageAspect)?.let(onChange)
                    }
                )
            }
    ) {
        val r = imageRect()
        drawImage(
            image,
            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
            dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
            filterQuality = FilterQuality.Medium
        )
        val pts = page.quad.points.map { toScreen(r, it) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until 4) lineTo(pts[i].x, pts[i].y)
            close()
        }
        clipPath(path, ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.45f), topLeft = r.topLeft, size = r.size)
        }
        drawPath(path, AccentGreen, style = Stroke(width = 2.5.dp.toPx()))
        if (page.cropMode != CropMode.CORNERS) {
            // thirds grid helps lining up
            for (k in 1..2) {
                val f = k / 3f
                drawLine(Color.White.copy(alpha = 0.5f), lerp(pts[0], pts[3], f), lerp(pts[1], pts[2], f), 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.5f), lerp(pts[0], pts[1], f), lerp(pts[3], pts[2], f), 1.dp.toPx())
            }
        }
        pts.forEach { p ->
            drawCircle(Color.White, handleR, p)
            drawCircle(ActionBlue, handleR, p, style = Stroke(3.dp.toPx()))
        }
        if (page.cropMode == CropMode.FREE) {
            for (i in 0 until 4) {
                val m = (pts[i] + pts[(i + 1) % 4]) / 2f
                drawCircle(Color.White, handleR * 0.7f, m)
                drawCircle(ActionBlue, handleR * 0.7f, m, style = Stroke(2.dp.toPx()))
            }
        }
        val t = target
        if (page.cropMode == CropMode.CORNERS && t is DragTarget.Corner && finger != null) {
            val p = pts[t.index]
            val leftSide = p.x < this.size.width / 2
            val centre = Offset(
                if (leftSide) this.size.width - magR - magGap else magR + magGap,
                magR + magGap
            )
            drawMagnifier(image, page.quad.points[t.index], centre, magR, r)
        }
    }
}

private fun lerp(a: Offset, b: Offset, f: Float) = Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

/** Round zoomed view (2.5×) of the picture around the corner being dragged, with a cross on the exact spot. */
private fun DrawScope.drawMagnifier(image: ImageBitmap, point: Pt, centre: Offset, radius: Float, shown: Rect) {
    val zoom = 2.5f
    val pxPerScreen = image.width / shown.width // picture pixels per screen pixel
    val half = radius / zoom * pxPerScreen
    val bx = (point.x * image.width).toFloat(); val by = (point.y * image.height).toFloat()
    val sl = max(0f, bx - half); val st = max(0f, by - half)
    val sr = min(image.width.toFloat(), bx + half); val sb = min(image.height.toFloat(), by + half)
    val scale = radius / half // screen pixels per picture pixel inside the magnifier
    val circle = Path().apply { addOval(Rect(centre, radius)) }
    clipPath(circle) {
        drawRect(Color.White, topLeft = centre - Offset(radius, radius), size = Size(radius * 2, radius * 2))
        if (sr > sl && sb > st) {
            drawImage(
                image,
                srcOffset = IntOffset(sl.toInt(), st.toInt()),
                srcSize = IntSize(max(1, (sr - sl).toInt()), max(1, (sb - st).toInt())),
                dstOffset = IntOffset(
                    (centre.x - radius + (sl - (bx - half)) * scale).roundToInt(),
                    (centre.y - radius + (st - (by - half)) * scale).roundToInt()
                ),
                dstSize = IntSize(((sr - sl) * scale).roundToInt(), ((sb - st) * scale).roundToInt()),
                filterQuality = FilterQuality.Medium
            )
        }
    }
    drawCircle(Color.White, radius, centre, style = Stroke(4.dp.toPx()))
    drawCircle(ActionBlue, radius, centre, style = Stroke(2.dp.toPx()))
    val c = 10.dp.toPx()
    drawLine(AccentGreen, centre - Offset(c, 0f), centre + Offset(c, 0f), 2.dp.toPx())
    drawLine(AccentGreen, centre - Offset(0f, c), centre + Offset(0f, c), 2.dp.toPx())
}
