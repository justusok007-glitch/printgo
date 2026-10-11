package com.printgo.app.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.printgo.app.data.local.JobStatus
import com.printgo.app.data.print.PdfPrintAdapter
import com.printgo.app.data.print.PdfSaver
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsScreen(
    jobId: String,
    onBackClick: () -> Unit,
    onOpenAllJobs: () -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by PrintJobManager.jobs.collectAsStateWithLifecycle()
    val job = all.firstOrNull { it.id == jobId }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Job details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = PrintGoTheme.colors.screenBackground
    ) { padding ->
        if (job == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This job is no longer in your history.", color = BodyGray)
                TextButton(onClick = onOpenAllJobs) { Text("See all jobs") }
            }
            return@Scaffold
        }
        val color = statusColor(job.status)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PrintGoTheme.colors.cardBorder)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon(job.status), contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(statusLabel(job.status), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                            Text(job.title, color = HeadingNavy, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (JobStatus.isActive(job.status)) {
                        if (job.progress > 0f) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PrimaryBlue)
                    }
                    if (job.statusMessage.isNotBlank()) {
                        Text(job.statusMessage, color = if (job.status == JobStatus.FAILED) Color(0xFFD32F2F) else BodyGray)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PrintGoTheme.colors.cardBorder)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Printer", job.printerName)
                    DetailLine("Connection", job.method.ifBlank { job.connectionType.lowercase().replaceFirstChar { it.uppercase() } })
                    DetailLine("Started", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(job.createdAt)))
                    DetailLine("Sheets", "${job.sheets} × ${job.copies} ${if (job.copies > 1) "copies" else "copy"}")
                    DetailLine("Paper", job.paperName)
                    DetailLine("Settings", job.settingsSummary)
                    if (job.attempts > 1) DetailLine("Attempts", job.attempts.toString())
                }
            }

            note?.let { Text(it, color = BodyGray, fontSize = 13.sp) }

            val pdf = job.pdfPath?.let { File(it) }?.takeIf { it.exists() }
            if (JobStatus.isActive(job.status)) {
                OutlinedButton(onClick = { PrintJobManager.cancel(job.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel job", color = Color(0xFFD32F2F))
                }
            }
            if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
                Button(
                    onClick = { PrintJobManager.retry(job.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Retry") }
            }
            if (job.status == JobStatus.COMPLETED) {
                Button(
                    onClick = { scope.launch { PrintJobManager.reprint(job.id) ; note = "Sent to the queue again." } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Print again") }
            }
            if (pdf != null && !JobStatus.isActive(job.status)) {
                OutlinedButton(
                    onClick = {
                        val ok = PdfPrintAdapter.print(context, pdf, job.title)
                        if (!ok) note = "The system print dialog is not available on this phone."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Print with system dialog") }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, PdfSaver.shareUri(context, pdf))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share as PDF") }
            }
            job.outputUri?.let { uri ->
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "application/pdf")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: ActivityNotFoundException) {
                            note = "No app on this phone can open PDF files."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open saved PDF") }
            }
            if (!JobStatus.isActive(job.status)) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete from history", color = Color(0xFFD32F2F))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this job?") },
            text = { Text("It will be removed from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { PrintJobManager.delete(jobId); onBackClick() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = BodyGray, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(value, color = HeadingNavy, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
