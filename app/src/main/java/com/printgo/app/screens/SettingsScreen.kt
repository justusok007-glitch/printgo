package com.printgo.app.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.R
import com.printgo.app.data.prefs.AppPreferences
import com.printgo.app.data.prefs.AppSettings
import com.printgo.app.data.print.PrintJobManager
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToPrinters: () -> Unit,
    onNavigateToJobs: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onShowOnboarding: () -> Unit = {}
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context.applicationContext) }
    val settings by prefs.settings.collectAsState(initial = AppSettings())
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showClearCache by remember { mutableStateOf(false) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                },
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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Printing")
            SettingsRow(Icons.Filled.Print, "Printers", "Wi-Fi, Bluetooth and USB printers", onNavigateToPrinters)
            SettingsRow(Icons.Filled.History, "Print jobs & history", "See, retry and export past jobs", onNavigateToJobs)
            SettingsRow(
                Icons.Filled.History, "Keep history for",
                if (settings.keepHistoryDays == 0) "Do not keep history" else "${settings.keepHistoryDays} days"
            ) { showHistoryDialog = true }

            SectionLabel("Notifications")
            SettingsSwitchRow(
                Icons.Filled.Notifications, "Job notifications", "Tell me when a print finishes or fails",
                settings.notificationsEnabled
            ) { scope.launch { prefs.setNotifications(it) } }
            SettingsRow(Icons.Filled.Notifications, "Phone notification settings", "Allow or block PrintGo in Android") {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            }

            SectionLabel("Storage")
            SettingsSwitchRow(
                Icons.Filled.CleaningServices, "Tidy temporary files", "Remove converted files older than a day",
                settings.autoCleanCache
            ) { scope.launch { prefs.setAutoCleanCache(it) } }
            SettingsRow(Icons.Filled.CleaningServices, "Clear temporary files now", cacheMessage ?: "Frees space used by previews") {
                showClearCache = true
            }

            SectionLabel("Help")
            SettingsRow(Icons.Filled.School, "Quick tour", "Replay the welcome guide", onShowOnboarding)
            SettingsRow(Icons.Filled.HelpOutline, "Help & FAQ", "Fix connection and printing problems", onNavigateToHelp)
            SettingsRow(Icons.Filled.PrivacyTip, "Privacy policy", "What PrintGo does with your files", onNavigateToPrivacy)
            SettingsRow(Icons.Filled.Info, "About PrintGo", "Version and credits", onNavigateToAbout)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Keep history for") },
            text = {
                Column {
                    listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 0 to "Do not keep history").forEach { (days, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    prefs.setHistoryDays(days)
                                    PrintJobManager.cleanupOlderThan(if (days == 0) 1 else days)
                                }
                                showHistoryDialog = false
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.keepHistoryDays == days, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Close") } }
        )
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { showClearCache = false },
            title = { Text("Clear temporary files?") },
            text = { Text("Previews will be rebuilt next time you open a document. Your print history is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCache = false
                    scope.launch {
                        val freed = clearTempFiles(context.cacheDir)
                        cacheMessage = "Cleared ${freed / 1024} KB"
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearCache = false }) { Text("Cancel") } }
        )
    }
}

fun clearTempFiles(cacheDir: File, olderThanMs: Long = 0L): Long {
    var freed = 0L
    val cutoff = System.currentTimeMillis() - olderThanMs
    cacheDir.listFiles()?.forEach { f ->
        if (f.name == "printgo_history.csv" && olderThanMs == 0L) { freed += f.length(); f.delete(); return@forEach }
        if (f.isDirectory && f.name.startsWith("printgo")) {
            f.walkBottomUp().forEach { x ->
                if (x != f && x.lastModified() <= cutoff) { freed += if (x.isFile) x.length() else 0; x.delete() }
            }
        } else if (f.isFile && (f.name.startsWith("job_") || f.name.endsWith(".pwg")) && f.lastModified() <= cutoff) {
            freed += f.length(); f.delete()
        }
    }
    return freed
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BodyGray, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HeadingNavy)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    androidx.compose.material3.Surface(
        modifier = Modifier.size(44.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = LightBlue
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
        }
    }
}
