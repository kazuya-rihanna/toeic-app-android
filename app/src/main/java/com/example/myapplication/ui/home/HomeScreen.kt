package com.example.myapplication.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCategoryClick: (String) -> Unit,
    onScoreClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPairingClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val categories = listOf(
        "nakamura_vocabulary_1_part_1" to "Nakamura Vocab 1 (Part 1)",
        "nakamura_vocabulary_1_part_2_3" to "Nakamura Vocab 1 (Part 2-3)",
        "nakamura_vocabulary_1_part_4" to "Nakamura Vocab 1 (Part 4)",
        "nakamura_vocabulary_1_part_5_6" to "Nakamura Vocab 1 (Part 5-6)",
        "nakamura_vocabulary_1_part_7" to "Nakamura Vocab 1 (Part 7)",
        "nakamura_vocabulary_2" to "Nakamura Vocab 2",
        "gold_phrases" to "Gold Phrases",
        "gold_sentences" to "Gold Sentences",
        "gold_collocations" to "Gold Collocations",
        "toeic_official_vocabulary" to "TOEIC Official Vocabulary",
        "black_phrases" to "Black Phrases"
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(12.dp))
                NavigationDrawerItem(
                    label = { Text("Progress") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onScoreClick()
                    },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSettingsClick()
                    },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Pair Pen") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onPairingClick()
                    },
                    icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TOEIC Dictation") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onScoreClick) {
                            Icon(Icons.Default.Star, contentDescription = "Score")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (id, title) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(id) }
                    ) {
                        ListItem(
                            headlineContent = { Text(title) },
                            leadingContent = { Icon(Icons.Default.Home, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}
