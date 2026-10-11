package com.printgo.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Photos : Screen("photos")
    data object Documents : Screen("documents")
    data object WebPages : Screen("webpages")
    data object Printers : Screen("printers")
    data object Preview : Screen("preview")
    data object Settings : Screen("settings")
}
