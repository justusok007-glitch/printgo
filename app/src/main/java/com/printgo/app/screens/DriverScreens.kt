package com.printgo.app.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.printgo.app.data.drivers.DriverEntry
import com.printgo.app.theme.ActionBlue
import com.printgo.app.theme.ActionGreen
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.LightMint
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.DriverViewModel
import com.printgo.app.viewmodel.PrinterViewModel

private val CardBorder = Color(0xFFE2E8F0)

/** Choose how a printer is driven: Automatic, or one of the built-in drivers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPickerScreen(
    printerId: String,
    printerViewModel: PrinterViewModel,
    onBackClick: () -> Unit,
    onOpenPacks: () -> Unit,
    driverViewModel: DriverViewModel = viewModel()
) {
    BackHandler(onBack = onBackClick)
    val state by driverViewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(printerId) {
        val printer = printerViewModel.getPrinterById(printerId)
        if (printer != null) {
            val model = runCatching { printerViewModel.loadCapabilitiesFor(printer).makeAndModel }.getOrNull()
            driverViewModel.load(printer, model)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            driverViewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("driver_picker_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { DriverTopBar("Printer driver", onBackClick) },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(state.printerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                state.printerModel?.let { Text(it, fontSize = 13.sp, color = BodyGray) }
            }
            if (!state.driversInApp) {
                item {
                    NoticeCard(
                        "This copy of PrintGo was built without the printer drivers. Install the latest PrintGo " +
                            "from the download page to use them."
                    )
                }
            }
            item {
                ChoiceRow(
                    title = "Automatic (recommended)",
                    subtitle = "PrintGo talks to the printer directly and uses a built-in driver only when the printer needs one.",
                    selected = state.choiceId == null,
                    badge = null,
                    onClick = { driverViewModel.chooseAutomatic() }
                )
            }

            val chosen = state.choice
            if (chosen != null && state.suggestions.none { it.entry.id == chosen.id }) {
                item { SectionTitle("Chosen driver") }
                item { DriverRow(chosen, true, false, state, driverViewModel) }
            }

            if (state.suggestions.isNotEmpty()) {
                item { SectionTitle("Suggested for this printer") }
                items(state.suggestions, key = { "s_" + it.entry.id }) { s ->
                    DriverRow(s.entry, state.choiceId == s.entry.id, s.exact, state, driverViewModel)
                }
            } else if (state.driversInApp) {
                item {
                    NoticeCard(
                        "No built-in driver matches this printer's name. Most printers do not need one. If prints fail, " +
                            "search for the printer's model below."
                    )
                }
            }

            item { SectionTitle("Find another driver") }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { driverViewModel.setQuery(it) },
                    modifier = Modifier.fillMaxWidth().testTag("driver_search"),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BodyGray) },
                    placeholder = { Text("Model, for example L3150 or LaserJet 1320") },
                    shape = RoundedCornerShape(14.dp)
                )
                Text(
                    "${state.totalModels} printer models: Epson ESC/P-R, Gutenprint, HPLIP and SpliX drivers.",
                    fontSize = 12.sp, color = BodyGray, modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            items(state.results, key = { "r_" + it.id }) { e ->
                DriverRow(e, state.choiceId == e.id, false, state, driverViewModel)
            }
            if (state.moreResults) {
                item { Text("More models match. Type more of the name to narrow the list.", fontSize = 12.sp, color = BodyGray) }
            } else if (state.query.isNotBlank() && state.results.isEmpty()) {
                item { Text("No driver found for \"${state.query}\".", fontSize = 13.sp, color = BodyGray) }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                OutlinedButton(onClick = onOpenPacks, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage driver packs")
                }
            }
            item {
                Text(
                    "Some printers (for example Canon LBP2900 or HP LaserJet P1102) only work with their maker's own " +
                        "computer driver. For those, use \"Print with system dialog\" with the maker's print app.",
                    fontSize = 12.sp, color = BodyGray, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    state.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { driverViewModel.dismissPending() },
            title = { Text("Download driver pack?") },
            text = {
                Text(
                    (p.entry?.let { "${it.displayName} needs the ${p.pack.label} pack. " } ?: "") +
                        "It is downloaded once (${p.pack.sizeText}) and then works without internet."
                )
            },
            confirmButton = { TextButton(onClick = { driverViewModel.confirmDownload() }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { driverViewModel.dismissPending() }) { Text("Cancel") } }
        )
    }
}

/** Settings → Printer drivers: the data packs on this phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPacksScreen(
    onBackClick: () -> Unit,
    driverViewModel: DriverViewModel = viewModel()
) {
    BackHandler(onBack = onBackClick)
    val state by driverViewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) driverViewModel.installFromFile(uri)
    }
    LaunchedEffect(Unit) { driverViewModel.loadPacks() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            driverViewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("driver_packs_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { DriverTopBar("Printer drivers", onBackClick) },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "PrintGo has drivers for ${state.totalModels} printer models. The Epson driver is built in. The others " +
                        "need a pack of printer descriptions, downloaded once when a printer needs it.",
                    fontSize = 13.sp, color = BodyGray
                )
            }
            if (!state.loading && !state.driversInApp) {
                item { NoticeCard("This copy of PrintGo was built without the printer drivers.") }
            }
            items(state.packs, key = { it.pack.name }) { p -> PackRow(p, driverViewModel) }
            item {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install a pack from a file")
                }
                Text(
                    "Useful without internet: download the pack's .zip file on another device from the PrintGo " +
                        "\"driver-packs\" release page, copy it to this phone and pick it here.",
                    fontSize = 12.sp, color = BodyGray, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    state.pending?.let { p ->
        AlertDialog(
            onDismissRequest = { driverViewModel.dismissPending() },
            title = { Text("Download driver pack?") },
            text = { Text("${p.pack.label}: ${p.pack.sizeText}, downloaded once.") },
            confirmButton = { TextButton(onClick = { driverViewModel.confirmDownload() }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { driverViewModel.dismissPending() }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverTopBar(title: String, onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrimaryBlue)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BodyGray,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
private fun NoticeCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = LightBlue,
        border = BorderStroke(1.dp, Color(0xFFBBDEFB))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, color = HeadingNavy)
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, badge: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) LightMint else Color.White),
        border = BorderStroke(1.dp, if (selected) ActionGreen.copy(alpha = 0.5f) else CardBorder)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = ActionGreen))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                Text(subtitle, fontSize = 12.sp, color = BodyGray)
            }
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = LightBlue) {
                    Text(badge, fontSize = 11.sp, color = ActionBlue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun DriverRow(
    entry: DriverEntry,
    selected: Boolean,
    exact: Boolean,
    state: DriverViewModel.UiState,
    vm: DriverViewModel
) {
    val pack = entry.kind.pack?.let { n -> state.packs.firstOrNull { it.pack.name == n } }
    val needsDownload = pack != null && !pack.installed
    val details = buildList {
        add(entry.kind.label)
        add(if (entry.color) "colour" else "black and white")
        if (entry.duplex) add("two-sided")
        if (needsDownload) add("pack ${pack!!.pack.sizeText} to download")
    }.joinToString(" • ")
    Column {
        ChoiceRow(
            title = entry.displayName,
            subtitle = details,
            selected = selected,
            badge = if (exact) "Exact match" else null,
            onClick = { vm.choose(entry) }
        )
        pack?.progress?.let { p ->
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun PackRow(p: DriverViewModel.PackUi, vm: DriverViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.pack.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HeadingNavy)
                    val status = when {
                        p.progress != null -> "Downloading…"
                        p.installed -> "Installed (%.1f MB on this phone)".format(java.util.Locale.US, p.installedBytes / 1_048_576.0)
                        p.outdated -> "An older version is installed. Download the new one (${p.pack.sizeText})."
                        else -> "Not downloaded (${p.pack.sizeText})"
                    }
                    Text(status, fontSize = 12.sp, color = if (p.installed) ActionGreen else BodyGray)
                }
                when {
                    p.progress != null -> TextButton(onClick = { vm.cancelDownload() }) { Text("Stop") }
                    p.installed -> IconButton(onClick = { vm.deletePack(p.pack.name) }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove", tint = BodyGray)
                    }
                    else -> IconButton(onClick = { vm.askDownload(p.pack) }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Download", tint = PrimaryBlue)
                    }
                }
                if (p.installed && p.progress == null) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ActionGreen, modifier = Modifier.size(20.dp))
                }
            }
            p.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
        }
    }
}
