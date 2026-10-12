package com.printgo.app.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.components.PrintGoLogoIcon
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.PrimaryBlue
import com.printgo.app.theme.PrintGoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) { content() }
    }
}

private data class Faq(val q: String, val a: String)

private val faqs = listOf(
    Faq(
        "PrintGo cannot find my printer",
        "Make sure the phone and the printer are on the same Wi-Fi network (not a guest network) and the printer is switched on. " +
            "Open Printers and pull to rescan. If it still does not appear, tap \"Add printer manually\" and type the printer's IP address " +
            "(you can print a network configuration page from the printer's menu to find it)."
    ),
    Faq(
        "Which printers work?",
        "Most Wi-Fi printers made after about 2013 that support AirPrint, Mopria or IPP Everywhere. PrintGo sends the pages straight to the printer " +
            "as PDF, JPEG or PWG Raster, whichever the printer accepts. Bluetooth and USB support is meant for small thermal (ESC/POS) receipt and label printers."
    ),
    Faq(
        "My printer is found but printing fails",
        "Open Print jobs & history and tap the job to see the reason, such as out of paper, a jam or an open cover. Fix it on the printer and tap Retry. " +
            "If the message says the printer cannot accept the file type, use \"Print with system dialog\" on the job screen and choose your printer's own Android print service."
    ),
    Faq(
        "What does \"Save as PDF\" do?",
        "Choose the Save as PDF printer in the preview. PrintGo lays out the pages with your settings and saves a PDF in Downloads/PrintGo. You can open or share it from the job screen."
    ),
    Faq(
        "Can I print from another app?",
        "Yes. In a gallery, file manager, PDF viewer or browser, tap Share and choose PrintGo, or choose PrintGo when opening a PDF, image or text file."
    ),
    Faq(
        "Word, Excel and PowerPoint files look different",
        "PrintGo converts Office files on the phone, so complex layouts can differ from the original. For perfect results, open the file in its own app, export it as PDF, and share the PDF to PrintGo."
    ),
    Faq(
        "How do I print on both sides?",
        "Open the settings in the preview and choose Two-sided. If your printer cannot print on both sides itself, the option is turned off. You can also print odd and even pages separately."
    ),
    Faq(
        "Is my document sent to the internet?",
        "No. Documents are processed on your phone and sent only to the printer you choose. PrintGo has no account, no cloud and no ads. Web pages are loaded only when you ask PrintGo to print them."
    ),
    Faq(
        "How long is my history kept?",
        "30 days by default. Change it, export it as a CSV file or clear it under Settings."
    ),
    Faq(
        "A job says it was interrupted",
        "Android can close apps to save memory. Open the job and tap Retry. PrintGo keeps the prepared pages so you do not need to select the document again."
    )
)

@Composable
fun HelpScreen(onBackClick: () -> Unit) {
    InfoScaffold("Help & FAQ", onBackClick) {
        faqs.forEach { FaqItem(it) }
    }
}

@Composable
private fun FaqItem(faq: Faq) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = !open },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(faq.q, fontWeight = FontWeight.Bold, color = HeadingNavy, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = BodyGray)
            }
            AnimatedVisibility(visible = open) {
                Text(faq.a, color = BodyGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(onBackClick: () -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
    }
    InfoScaffold("About PrintGo", onBackClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            PrintGoLogoIcon(size = 84.dp)
            Spacer(Modifier.height(12.dp))
            Text("PrintGo", fontSize = 28.sp, fontWeight = FontWeight.Black, color = HeadingNavy)
            Text("Version $version", color = BodyGray)
        }
        Text(
            "PrintGo prints photos, documents and web pages from your phone to Wi-Fi printers, and saves them as PDF when no printer is around. " +
                "It talks to printers directly using the standard Internet Printing Protocol (IPP), so there is nothing to install on the printer and no account to create.",
            color = HeadingNavy, fontSize = 15.sp
        )
        Text(
            "No ads. No tracking. Your files stay on your phone and go only to the printer you pick.",
            color = BodyGray, fontSize = 14.sp
        )
        Text(
            "Privacy policy",
            color = PrimaryBlue, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onOpenPrivacy).padding(vertical = 8.dp)
        )
        Text("Free software", fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 17.sp)
        Text(
            "PrintGo is free software under the GNU General Public License, version 3 or later. You may use, " +
                "share and change it under that licence. It comes with no warranty. The printer drivers inside " +
                "(Epson ESC/P-R, Gutenprint, HPLIP, SpliX) keep their own free-software licences.",
            color = BodyGray, fontSize = 14.sp
        )
        Text(
            "Source code and licences",
            color = PrimaryBlue, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable {
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_CODE_URL))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) { }
                }
                .padding(vertical = 8.dp)
        )
        Text(SOURCE_CODE_URL, color = BodyGray, fontSize = 12.sp)
    }
}

const val SOURCE_CODE_URL = "https://github.com/justusok007-glitch/printgo"

@Composable
fun PrivacyScreen(onBackClick: () -> Unit) {
    InfoScaffold("Privacy policy", onBackClick) {
        PrivacyText.sections.forEach { (h, body) ->
            Text(h, fontWeight = FontWeight.Bold, color = HeadingNavy, fontSize = 17.sp)
            Text(body, color = BodyGray, fontSize = 14.sp)
        }
    }
}

object PrivacyText {
    val sections = listOf(
        "Summary" to "PrintGo does not collect, sell or share personal data. It has no accounts, no analytics, no advertising and no servers of its own.",
        "Your files" to "Photos and documents you choose are read on your phone, converted to pages, and sent only to the printer you select (over your local network, Bluetooth or USB) or saved as a PDF in your Downloads folder. A copy of each prepared job is kept inside PrintGo's private storage so you can retry or reprint it, and is deleted when the job leaves your history.",
        "History" to "PrintGo remembers the name, time, printer and settings of your print jobs on your phone for 30 days (you can change this in Settings). History never leaves your phone unless you export it yourself.",
        "Printers and network" to "To find printers PrintGo uses Wi-Fi discovery (mDNS) on your local network. Printer addresses and certificate fingerprints you trust are stored on your phone. Web pages are downloaded only when you ask PrintGo to print a web address.",
        "Permissions" to "Network and Wi-Fi state: find and talk to printers. Nearby devices / Bluetooth: connect to Bluetooth printers you pair. Notifications: tell you when a job finishes. Photos: show your pictures so you can pick what to print. Foreground service: keep sending a job while you switch apps.",
        "Children" to "PrintGo is a general-purpose tool and collects no information from anyone, including children.",
        "Changes and contact" to "If this policy changes, the new version will be shown here in the app. Questions: justusok007@gmail.com."
    )
}
