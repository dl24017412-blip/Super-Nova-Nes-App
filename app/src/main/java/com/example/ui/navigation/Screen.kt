package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Emulator : Screen("emulator/{romId}") {
        fun createRoute(romId: Long) = "emulator/$romId"
    }
    object Settings : Screen("settings")
}
