package com.printgo.app.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Photos : Screen("photos")
    data object Documents : Screen("documents")
    data object WebPages : Screen("webpages")
    data object Printers : Screen("printers")
    data object Preview : Screen("preview")
    data object Settings : Screen("settings")
    data object Jobs : Screen("jobs")
    data object Help : Screen("help")
    data object About : Screen("about")
    data object Privacy : Screen("privacy")
    data object Onboarding : Screen("onboarding")
    data object JobDetails : Screen("job/{jobId}") {
        fun create(id: String) = "job/$id"
    }
}
