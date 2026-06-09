package com.example.myapplication.ui.pairing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val message by viewModel.message.collectAsState()
    val foundPens by viewModel.foundPens.collectAsState(initial = emptyList())
    val pendingConfirmPen by viewModel.pendingConfirmPen.collectAsState()
    val hasDeclined by viewModel.hasDeclinedCurrentSearch.collectAsState()

    // Discovery Monitor for Dialog Popup
    LaunchedEffect(foundPens.size, isAutoConnecting) {
        if (isAutoConnecting && foundPens.isNotEmpty() && pendingConfirmPen == null && !hasDeclined) {
            // Pick the first pen found and ask for confirmation
            val firstPen = foundPens.firstOrNull { 
                val n = it.name.uppercase()
                n.startsWith("NWP-") || n.contains("NEOSMARTPEN") || n.contains("SMARTPEN")
            }
            if (firstPen != null) {
                viewModel.setPendingConfirm(firstPen)
            }
        }
    }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startDiscovery()
        }
    }

    // 自動スキャン開始 (画面に入った瞬間に探し始める)
    LaunchedEffect(Unit) {
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.startDiscovery()
        } else {
            launcher.launch(permissions)
        }
    }

    var hasNavigatedBack by remember { mutableStateOf(false) }
    
    val safeBack = {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            onBack()
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected && !hasNavigatedBack) {
            val appContext = context.applicationContext
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(appContext, "NeoSmartpen Connected!", android.widget.Toast.LENGTH_SHORT).show()
                safeBack() // Auto-return instantly to prevent manual button race conditions
            }
        }
    }

    fun onPairClicked() {
        viewModel.startDiscovery()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Pen") },
                navigationIcon = {
                    IconButton(onClick = { safeBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Update Icon (Manual Discovery) - Now with same logic and guard as central button
                    IconButton(
                        onClick = { onPairClicked() },
                        enabled = !isAutoConnecting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isAutoConnecting) Color.Gray else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Hold power for 3s until LED flashes BLUE.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pen List (Manual selection fallback)
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                items(foundPens.size) { index ->
                    val pen = foundPens[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable(enabled = !isConnected) {
                                viewModel.connect(pen.address, pen.sppAddress)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (pen.isGenuine) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = pen.name, style = MaterialTheme.typography.titleSmall)
                                if (pen.scanRecordHex == "BONDED") {
                                    Text("PAIRED (System)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Low-key status messages
            val currentStatus = message ?: scanStatus ?: "Ready"
            Text(
                text = currentStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Connection Confirmation Dialog ---
        pendingConfirmPen?.let { pen ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.declineConfirmation() },
                title = { Text("Pen Found") },
                text = { Text("Connect to ${pen.name}?\n(Address: ${pen.address})") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { 
                            viewModel.connect(pen.address, pen.sppAddress)
                            viewModel.setPendingConfirm(null)
                        }
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.declineConfirmation() }
                    ) {
                        Text("Ignore")
                    }
                }
            )
        }
    }
}
