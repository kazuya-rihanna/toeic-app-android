package com.example.myapplication.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Practice : Screen("practice/{collectionId}") {
        fun createRoute(collectionId: String) = "practice/$collectionId"
    }
    object Score : Screen("score")
    object Settings : Screen("settings")
    object Pairing : Screen("pairing")
}
