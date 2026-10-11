package com.printgo.app.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.printgo.app.theme.AccentGreen
import com.printgo.app.theme.BackgroundWhite
import com.printgo.app.theme.BodyGray
import com.printgo.app.theme.HeadingNavy
import com.printgo.app.theme.LightBlue
import com.printgo.app.theme.PrimaryBlue
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val text: String)

private val pages = listOf(
    Page(Icons.Filled.Print, "Print from your phone", "Photos, PDFs, Office files, text and web pages — straight to your printer, no cables and no extra apps on the printer."),
    Page(Icons.Filled.Wifi, "Connect in seconds", "Switch your printer on and join the same Wi-Fi. PrintGo finds it automatically. You can also add one by its address."),
    Page(Icons.Filled.Share, "Print from anywhere", "In any app, tap Share and choose PrintGo. Pick your layout, copies, paper and colour, then print."),
    Page(Icons.Filled.PictureAsPdf, "No printer? Save a PDF", "Choose Save as PDF to turn anything into a PDF in your Downloads. We will also tell you when a print finishes.")
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinished() }

    fun finish() {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinished()
    }

    Column(
        Modifier.fillMaxSize().background(BackgroundWhite).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < pages.lastIndex) TextButton(onClick = { finish() }) { Text("Skip", color = BodyGray) }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(Modifier.size(150.dp).clip(CircleShape).background(LightBlue), contentAlignment = Alignment.Center) {
                    Icon(p.icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(76.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(p.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = HeadingNavy, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.text, fontSize = 16.sp, color = BodyGray, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 16.dp)) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (i == pager.currentPage) PrimaryBlue else Color(0xFFCBD5E1))
                )
            }
        }
        Button(
            onClick = {
                if (pager.currentPage < pages.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                else finish()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text(if (pager.currentPage < pages.lastIndex) "Next" else "Get started", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
