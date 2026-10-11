package com.printgo.app.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.data.local.JobStatus
import com.printgo.app.data.local.PrintJobEntity
import com.printgo.app.data.print.PdfSaver
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.theme.AccentGreenDark
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val ErrorRed = Color(0xFFD32F2F)
private val WarnAmber = Color(0xFFF59E0B)

fun statusColor(status: String): Color = when (status) {
    JobStatus.COMPLETED -> AccentGreenDark
    JobStatus.FAILED -> ErrorRed
    JobStatus.CANCELLED -> BodyGray
    else -> PrimaryBlue
}

fun statusIcon(status: String): ImageVector = when (status) {
    JobStatus.COMPLETED -> Icons.Filled.CheckCircle
    JobStatus.FAILED -> Icons.Filled.Error
    JobStatus.CANCELLED -> Icons.Filled.Cancel
    JobStatus.QUEUED -> Icons.Filled.Schedule
    else -> Icons.Filled.Print
}

fun statusLabel(status: String): String = when (status) {
    JobStatus.QUEUED -> "Waiting"
    JobStatus.PREPARING -> "Preparing"
    JobStatus.SENDING -> "Sending"
    JobStatus.PRINTING -> "Printing"
    JobStatus.COMPLETED -> "Done"
    JobStatus.FAILED -> "Failed"
    JobStatus.CANCELLED -> "Cancelled"
    else -> status
}

private enum class JobFilter(val label: String) { ALL("All"), ACTIVE("In progress"), DONE("Done"), FAILED("Failed") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onBackClick: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobs by PrintJobManager.jobs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(JobFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(jobs, query, filter) {
        jobs.filter { j ->
            val okFilter = when (filter) {
                JobFilter.ALL -> true
                JobFilter.ACTIVE -> JobStatus.isActive(j.status)
                JobFilter.DONE -> j.status == JobStatus.COMPLETED
                JobFilter.FAILED -> j.status == JobStatus.FAILED || j.status == JobStatus.CANCELLED
            }
            val q = query.trim()
            okFilter && (q.isEmpty() || j.title.contains(q, true) || j.printerName.contains(q, true))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Print jobs & history", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val f = PrintJobManager.exportCsv()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, PdfSaver.shareUri(context, f))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Export history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Icon(Icons.Filled.FileDownload, contentDescription = "Export CSV", tint = Color.White) }
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear finished", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("Search document or printer") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JobFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Print, contentDescription = null, tint = BodyGray, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (jobs.isEmpty()) "No print jobs yet" else "Nothing matches your search",
                            fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 18.sp
                        )
                        Text(
                            if (jobs.isEmpty()) "Jobs you print will show up here for 30 days." else "Try another word or filter.",
                            color = BodyGray, fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { job ->
                        JobRow(
                            job = job,
                            onClick = { onOpenJob(job.id) },
                            onCancel = { PrintJobManager.cancel(job.id) },
                            onRetry = { PrintJobManager.retry(job.id) },
                            onReprint = { scope.launch { PrintJobManager.reprint(job.id) } }
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear finished jobs?") },
            text = { Text("Completed, failed and cancelled jobs will be removed from this list. Jobs in progress stay.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; scope.launch { PrintJobManager.clearFinished() } }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun JobRow(
    job: PrintJobEntity,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onReprint: () -> Unit
) {
    val color = statusColor(job.status)
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PrintGoTheme.colors.cardBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(statusIcon(job.status), contentDescription = null, tint = color) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title, fontWeight = FontWeight.Bold, color = HeadingNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${job.printerName} • ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(job.createdAt))}",
                        fontSize = 12.sp, color = BodyGray, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(statusLabel(job.status), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            if (JobStatus.isActive(job.status)) {
                Spacer(Modifier.height(10.dp))
                if (job.progress > 0f) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
            }
            if (job.statusMessage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(job.statusMessage, fontSize = 13.sp, color = if (job.status == JobStatus.FAILED) ErrorRed else BodyGray)
            }
            if (job.settingsSummary.isNotBlank()) {
                Text(job.settingsSummary, fontSize = 12.sp, color = BodyGray)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (JobStatus.isActive(job.status)) TextButton(onClick = onCancel) { Text("Cancel", color = ErrorRed) }
                if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) TextButton(onClick = onRetry) { Text("Retry") }
                if (job.status == JobStatus.COMPLETED) TextButton(onClick = onReprint) { Text("Print again") }
            }
        }
    }
}
