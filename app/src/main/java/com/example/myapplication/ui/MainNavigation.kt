package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.practice.PracticeScreen
import com.example.myapplication.ui.score.ScoreScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.pairing.PairingScreen

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCategoryClick = { categoryId ->
                    navController.navigate(Screen.Practice.createRoute(categoryId))
                },
                onScoreClick = {
                    navController.navigate("score")
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onPairingClick = {
                    navController.navigate(Screen.Pairing.route)
                }
            )
        }
        composable(
            route = Screen.Practice.route,
            arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
            PracticeScreen(
                collectionId = collectionId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Score.route) {
            ScoreScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Pairing.route) {
            PairingScreen(onBack = { navController.popBackStack() })
        }
    }
}
