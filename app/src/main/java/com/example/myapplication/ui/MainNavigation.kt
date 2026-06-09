package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.practice.PracticeScreen
import com.example.myapplication.ui.score.ScoreScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.pairing.PairingScreen
import kotlinx.coroutines.launch

/**
 * ライフサイクルが RESUMED のときだけ navigate を許可する。
 * 遷移アニメーション中は STARTED に落ちるため、二重 navigate を防げる。
 */
private fun NavBackStackEntry.lifecycleIsResumed() =
    this.lifecycle.currentState == Lifecycle.State.RESUMED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 現在のルートを監視
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // ドロワーを閉じてからナビゲーション
    fun navigateFromDrawer(route: String) {
        scope.launch {
            drawerState.close()
            navController.navigate(route)
        }
    }

    // ModalNavigationDrawer を NavHost の外側に配置することで、
    // NavHost のアニメーションとドロワーのアニメーションが衝突しなくなる
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Home 画面以外ではドロワーを無効化
        gesturesEnabled = (currentRoute == Screen.Home.route),
        drawerContent = {
            // ModalDrawerSheet は常にレンダリング（条件分岐すると初回表示時に一瞬フラッシュが起きるため）
            // アイテムの表示は Home 画面のときのみ
            ModalDrawerSheet {
                if (currentRoute == Screen.Home.route) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationDrawerItem(
                        label = { Text("Progress") },
                        selected = false,
                        onClick = { navigateFromDrawer(Screen.Score.route) },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = false,
                        onClick = { navigateFromDrawer(Screen.Settings.route) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        label = { Text("Pair Pen") },
                        selected = false,
                        onClick = { navigateFromDrawer(Screen.Pairing.route) },
                        icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {

            composable(Screen.Home.route) { homeEntry ->
                HomeScreen(
                    onMenuClick = {
                        if (homeEntry.lifecycleIsResumed()) {
                            scope.launch { drawerState.open() }
                        }
                    },
                    onCategoryClick = { categoryId ->
                        if (homeEntry.lifecycleIsResumed()) {
                            navController.navigate(Screen.Practice.createRoute(categoryId))
                        }
                    },
                    onScoreClick = {
                        if (homeEntry.lifecycleIsResumed()) {
                            navController.navigate(Screen.Score.route)
                        }
                    },
                    onSettingsClick = {
                        if (homeEntry.lifecycleIsResumed()) {
                            navController.navigate(Screen.Settings.route)
                        }
                    },
                    onPairingClick = {
                        if (homeEntry.lifecycleIsResumed()) {
                            navController.navigate(Screen.Pairing.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.Practice.route,
                arguments = listOf(navArgument("collectionId") { type = NavType.StringType })
            ) { practiceEntry ->
                val collectionId = practiceEntry.arguments?.getString("collectionId") ?: ""
                PracticeScreen(
                    collectionId = collectionId,
                    onBack = {
                        if (practiceEntry.lifecycleIsResumed()) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Screen.Score.route) { scoreEntry ->
                ScoreScreen(
                    onBack = {
                        if (scoreEntry.lifecycleIsResumed()) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Screen.Settings.route) { settingsEntry ->
                SettingsScreen(
                    onBack = {
                        if (settingsEntry.lifecycleIsResumed()) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Screen.Pairing.route) { pairingEntry ->
                PairingScreen(
                    onBack = {
                        if (pairingEntry.lifecycleIsResumed()) {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}
