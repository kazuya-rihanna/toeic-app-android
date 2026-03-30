package com.example.myapplication.ui.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.pen.DiscoveredPen
import androidx.hilt.navigation.compose.hiltViewModel
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val foundPens by viewModel.foundPens.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val message by viewModel.message.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()
    val totalDevicesFound by viewModel.totalDevicesFound.collectAsState()
    val logs by viewModel.logs.collectAsState()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.startScan()
        }
    }

    LaunchedEffect(Unit) {
        android.util.Log.i("PairingScreen", "DIAGNOSTIC VERSION 5 RUNNING")
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.startScan()
        } else {
            launcher.launch(permissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Neo Smartpen (v29)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        android.util.Log.i("PairingScreen", "Classic Scan Button Clicked")
                        viewModel.startClassicScan() 
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Classic Scan")
                    }
                    IconButton(onClick = { 
                        android.util.Log.i("PairingScreen", "Manual Scan Button Clicked")
                        launcher.launch(permissions)
                        viewModel.startScan() 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isConnected) "Status: Connected" else "Status: Disconnected (BUILD V41)",
                style = MaterialTheme.typography.headlineSmall,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(
                text = "SDK API Introspection (v41)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "MANDATORY: Holding power 3s UNTIL LED flashes BLUE!",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1. Tap your pen in the list below.\n2. The system will prompt you to Pair. ACCEPT IT FAST!\n3. Wait 5-10s while the app silently piggybacks the connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            message?.let {
                Text(text = "Pen Result: $it", style = MaterialTheme.typography.bodyMedium)
            }
            
            val statusText = scanStatus ?: "(No Status)"
            Text(text = "Scan: $statusText (Total: $totalDevicesFound)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

            if (logs.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Connection Logs (v40):", style = MaterialTheme.typography.labelMedium)
                        logs.forEach { log ->
                            Text(log, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Discovered Devices:", style = MaterialTheme.typography.titleMedium)
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortedPens = foundPens.sortedWith(compareByDescending<DiscoveredPen> { it.isGenuine }.thenByDescending { it.rssi })
                
                if (sortedPens.isEmpty()) {
                    item {
                        Text(
                            "No pens found. Try moving the pen closer, ensuring it flashes blue, and tapping Refresh.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                items(sortedPens) { pen ->
                    val addrUpper = pen.address.uppercase()
                    // All ghosts are strictly filtered now, so anything here is visually trusted
                    val isBonded = pen.scanRecordHex == "BONDED"
                    
                    val displayName = pen.name
                    val cardAlpha = 1.0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.connect(pen.address, pen.sppAddress) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (pen.isGenuine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp).alpha(cardAlpha)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(text = displayName, style = MaterialTheme.typography.titleLarge)
                                if (pen.isGenuine) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ) {
                                        Text(
                                            if (isBonded) "BONDED & GENUINE" else "GENUINE NEOLAB", 
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                } else if (isBonded) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ) {
                                        Text(
                                            "BONDED (OS)", 
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Text(text = "Address: ${pen.address}", style = MaterialTheme.typography.bodySmall)
                            if (pen.scanRecordHex.isNotEmpty()) {
                                Text(
                                    text = "Data: ${pen.scanRecordHex}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.ui.graphics.Color.Gray
                                )
                            }
                            val rssiColor = if (pen.rssi < -80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            Text(
                                text = "RSSI: ${pen.rssi}${if (pen.rssi < -80) " (WEAK SIGNAL)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = rssiColor
                            )
                        }
                    }
                }
            }
        }
    }
}
