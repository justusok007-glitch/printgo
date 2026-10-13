package com.printgo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.printgo.app.data.prefs.AppPreferences
import com.printgo.app.screens.AboutScreen
import com.printgo.app.screens.HelpScreen
import com.printgo.app.screens.OnboardingScreen
import com.printgo.app.screens.PrivacyScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.printgo.app.screens.JobDetailsScreen
import com.printgo.app.screens.JobsScreen
import com.printgo.app.screens.DocumentsScreen
import com.printgo.app.screens.HomeScreen
import com.printgo.app.screens.PhotosScreen
import com.printgo.app.screens.PreviewScreen
import com.printgo.app.screens.PrintersScreen
import com.printgo.app.screens.PrinterDetailsScreen
import com.printgo.app.screens.DriverPickerScreen
import com.printgo.app.screens.DriverPacksScreen
import com.printgo.app.screens.SettingsScreen
import com.printgo.app.screens.WebPagesScreen
import com.printgo.app.screens.ScanDocumentScreen
import com.printgo.app.screens.ScanEditScreen
import com.printgo.app.viewmodel.ScanPurpose
import com.printgo.app.viewmodel.ScanViewModel
import com.printgo.app.viewmodel.PrintSettingsViewModel
import com.printgo.app.viewmodel.PrinterViewModel
import com.printgo.app.viewmodel.SelectionViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PrintGoNavHost(
    modifier: Modifier = Modifier,
    selectionViewModel: SelectionViewModel = viewModel(),
    printerViewModel: PrinterViewModel = viewModel(),
    printSettingsViewModel: PrintSettingsViewModel = viewModel(),
    scanViewModel: ScanViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    openJobsTick: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var firstRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        firstRoute = if (prefs.current().onboardingDone) startDestination else Screen.Onboarding.route
    }

    // Navigation is only possible once the NavHost has installed its graph.
    suspend fun awaitGraph() {
        var tries = 0
        while (tries < 100 && (firstRoute == null || runCatching { navController.graph }.isFailure)) {
            delay(30); tries++
        }
    }

    LaunchedEffect(openJobsTick) {
        if (openJobsTick > 0) {
            awaitGraph()
            navController.navigate(Screen.Jobs.route) { launchSingleTop = true }
        }
    }

    // Whenever an item or multiple items trigger navigateToPreview (e.g. from intent share or Print button)
    LaunchedEffect(selectionViewModel, navController) {
        selectionViewModel.navigateToPreview.collectLatest {
            awaitGraph()
            navController.navigate(Screen.Preview.route) {
                launchSingleTop = true
            }
        }
    }

    val start = firstRoute ?: return

    fun openScanDocument() {
        scanViewModel.startDocument()
        navController.navigate(Screen.ScanDocument.create(camera = true))
    }

    fun openScanBeforePrint(items: List<com.printgo.app.model.PrintItem>) {
        scanViewModel.startPhotos(items)
        if (scanViewModel.state.value.pages.isEmpty()) return
        scanViewModel.beginEdit()
        navController.navigate(Screen.ScanEdit.route) { launchSingleTop = true }
    }
    androidx.compose.runtime.DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == Screen.Home.route) {
                selectionViewModel.clearSelection()
                com.printgo.app.data.layout.SheetLayoutEngine.clearCache()
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = start,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(onFinished = {
                scope.launch { prefs.setOnboardingDone(true) }
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                } else {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            })
        }

        composable(Screen.Help.route) { HelpScreen(onBackClick = { navController.popBackStack() }) }
        composable(Screen.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() },
                onOpenPrivacy = { navController.navigate(Screen.Privacy.route) }
            )
        }
        composable(Screen.Privacy.route) { PrivacyScreen(onBackClick = { navController.popBackStack() }) }

        composable(Screen.Home.route) {
            HomeScreen(
                printerViewModel = printerViewModel,
                onNavigateToPhotos = { navController.navigate(Screen.Photos.route) },
                onNavigateToDocuments = { navController.navigate(Screen.Documents.route) },
                onNavigateToWebPages = { navController.navigate(Screen.WebPages.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPrinters = { navController.navigate(Screen.Printers.route) },
                onNavigateToJobs = { navController.navigate(Screen.Jobs.route) },
                onNavigateToScan = { openScanDocument() }
            )
        }

        composable(Screen.Photos.route) {
            PhotosScreen(
                selectionViewModel = selectionViewModel,
                onBackClick = { navController.popBackStack() },
                onScanBeforePrint = { openScanBeforePrint(selectionViewModel.selectedItems.value) },
                onScanWithCamera = { openScanDocument() }
            )
        }

        composable(Screen.ScanEdit.route) {
            ScanEditScreen(
                scanViewModel = scanViewModel,
                onCancel = {
                    scanViewModel.cancelEdit()
                    navController.popBackStack()
                },
                onDone = {
                    if (scanViewModel.state.value.purpose == ScanPurpose.PHOTOS) {
                        // Edited copies replace the photos in the selection; printing then works as before.
                        selectionViewModel.replaceItems(scanViewModel.finishPhotos())
                        navController.popBackStack()
                        selectionViewModel.onPrintClicked()
                    } else {
                        scanViewModel.endEdit()
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            Screen.ScanDocument.route,
            arguments = listOf(navArgument("camera") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            ScanDocumentScreen(
                scanViewModel = scanViewModel,
                startWithCamera = entry.arguments?.getBoolean("camera") ?: false,
                onBack = { navController.popBackStack() },
                onEditPage = { index ->
                    scanViewModel.select(index)
                    scanViewModel.beginEdit()
                    navController.navigate(Screen.ScanEdit.route) { launchSingleTop = true }
                },
                onPrint = { item ->
                    selectionViewModel.setItems(listOf(item))
                    selectionViewModel.onPrintClicked()
                }
            )
        }

        composable(Screen.Documents.route) {
            DocumentsScreen(
                selectionViewModel = selectionViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.WebPages.route) {
            WebPagesScreen(
                selectionViewModel = selectionViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Printers.route) {
            PrintersScreen(
                printerViewModel = printerViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToDetails = { id -> navController.navigate(Screen.PrinterDetails.create(id)) }
            )
        }

        composable(
            Screen.PrinterDetails.route,
            arguments = listOf(navArgument("printerId") { type = NavType.StringType })
        ) { entry ->
            PrinterDetailsScreen(
                printerId = entry.arguments?.getString("printerId").orEmpty(),
                printerViewModel = printerViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenDriver = { id -> navController.navigate(Screen.PrinterDriver.create(id)) }
            )
        }

        composable(
            Screen.PrinterDriver.route,
            arguments = listOf(navArgument("printerId") { type = NavType.StringType })
        ) { entry ->
            DriverPickerScreen(
                printerId = entry.arguments?.getString("printerId").orEmpty(),
                printerViewModel = printerViewModel,
                onBackClick = { navController.popBackStack() },
                onOpenPacks = { navController.navigate(Screen.DriverPacks.route) }
            )
        }

        composable(Screen.DriverPacks.route) {
            DriverPacksScreen(onBackClick = { navController.popBackStack() })
        }

        composable(Screen.Preview.route) {
            PreviewScreen(
                selectionViewModel = selectionViewModel,
                printerViewModel = printerViewModel,
                printSettingsViewModel = printSettingsViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToPrinters = { navController.navigate(Screen.Printers.route) },
                onScanPhotos = { openScanBeforePrint(selectionViewModel.selectedItems.value) },
                onJobQueued = { id ->
                    selectionViewModel.clearSelection()
                    navController.navigate(Screen.JobDetails.create(id)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Jobs.route) {
            JobsScreen(
                onBackClick = { navController.popBackStack() },
                onOpenJob = { navController.navigate(Screen.JobDetails.create(it)) }
            )
        }

        composable(
            Screen.JobDetails.route,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { entry ->
            JobDetailsScreen(
                jobId = entry.arguments?.getString("jobId").orEmpty(),
                onBackClick = { navController.popBackStack() },
                onOpenAllJobs = { navController.navigate(Screen.Jobs.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToPrinters = { navController.navigate(Screen.Printers.route) },
                onNavigateToJobs = { navController.navigate(Screen.Jobs.route) },
                onNavigateToDrivers = { navController.navigate(Screen.DriverPacks.route) },
                onNavigateToHelp = { navController.navigate(Screen.Help.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onShowOnboarding = { navController.navigate(Screen.Onboarding.route) }
            )
        }
    }
}
