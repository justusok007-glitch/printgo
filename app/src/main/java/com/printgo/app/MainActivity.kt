package com.printgo.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.printgo.app.navigation.PrintGoNavHost
import com.printgo.app.theme.PrintGoTheme
import com.printgo.app.viewmodel.SelectionViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val selectionViewModel: SelectionViewModel by viewModels()
    private val printerViewModel: com.printgo.app.viewmodel.PrinterViewModel by viewModels()
    private var openJobsTick by androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle shared files if launched via Intent
        // Only on a fresh start: after a screen rotation the same shared file must not be added again.
        if (savedInstanceState == null) {
            handleUsbIntent(intent)
            selectionViewModel.handleIncomingIntent(this, intent)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_JOBS, false) == true) openJobsTick++

        setContent {
            PrintGoTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                var usbNotice by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<com.printgo.app.viewmodel.UsbEvent.NeedsPrintService?>(null)
                }
                LaunchedEffect(printerViewModel) {
                    printerViewModel.usbEvents.collectLatest { ev ->
                        when (ev) {
                            is com.printgo.app.viewmodel.UsbEvent.Message -> snackbarHostState.showSnackbar(ev.text)
                            is com.printgo.app.viewmodel.UsbEvent.NeedsPrintService -> usbNotice = ev
                        }
                    }
                }
                usbNotice?.let { n ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { usbNotice = null },
                        title = { androidx.compose.material3.Text("One more step for ${n.printerName}") },
                        text = {
                            androidx.compose.material3.Text(
                                "PrintGo saved this printer, but over USB it only understands its own special language. " +
                                    "Android printers use a small free print-service app as their driver. Install the one for " +
                                    "${n.maker ?: "your printer"} once, then use \"Print with system dialog\" on any job. " +
                                    "You will not need to do this again."
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                openPrintServiceStore(n.maker)
                                usbNotice = null
                            }) { androidx.compose.material3.Text("Open Play Store") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { usbNotice = null }) {
                                androidx.compose.material3.Text("Not now")
                            }
                        }
                    )
                }

                LaunchedEffect(selectionViewModel) {
                    selectionViewModel.snackbarMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        PrintGoNavHost(
                            selectionViewModel = selectionViewModel,
                            printerViewModel = printerViewModel,
                            openJobsTick = openJobsTick
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_JOBS, false)) openJobsTick++
        else selectionViewModel.handleIncomingIntent(this, intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action != android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE, android.hardware.usb.UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE)
        }
        if (device != null) printerViewModel.handleUsbDevice(device)
    }

    private fun openPrintServiceStore(maker: String?) {
        val q = Uri.encode("${maker ?: ""} print service plugin".trim())
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps")))
            } catch (_: Exception) { }
        }
    }

    companion object {
        const val EXTRA_OPEN_JOBS = "com.printgo.app.OPEN_JOBS"
    }
}
