package com.example.myapplication.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    collectionId: String,
    onBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val checkResult by viewModel.checkResult.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isPenConnected by viewModel.isPenConnected.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val currentStroke by viewModel.currentStroke.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val penBattery by viewModel.penBattery.collectAsState()

    var isBlurred by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val penEvents by viewModel.penEvents.collectAsState()

    val context = LocalContext.current
    
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            try {
                val mediaPlayer = android.media.MediaPlayer.create(context, com.example.myapplication.R.raw.apple_pay)
                mediaPlayer?.setOnCompletionListener { it.release() }
                mediaPlayer?.start()
            } catch (e: Exception) {
                // Ignore audio errors
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    LaunchedEffect(collectionId) {
        // Using the user's Firebase UID for synchronization with web
        viewModel.loadSentences(collectionId, "VnocHGzzyhNUbkx2YVw9qi1GtFe2") 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice: $collectionId") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val statusColor = if (isPenConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Pen Status",
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val batteryText = if (isPenConnected && penBattery != null) " ($penBattery%)" else ""
                        Text(
                            text = if (isPenConnected) "Pen Linked$batteryText" else "Pen Offline",
                            color = statusColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                is PracticeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
                is PracticeUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                is PracticeUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Top Navigation Row: Page indicator (Left) and Copy (Right)
                        val clipboardManager = LocalClipboardManager.current
                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${state.currentPage} / ${state.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(state.sentence.example))
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Sentence",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sentence Card Centered
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp).fillMaxWidth()) {
                                Text(
                                    text = if (isBlurred) "••••••••••••" else state.sentence.example,
                                    style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { isBlurred = !isBlurred },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Icon(
                                        if (isBlurred) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Draw your answer", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Strokes: ${strokes.size}, Dots: ${currentStroke.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                .padding(4.dp)
                        ) {
                            NativePenCanvas(
                                strokes = strokes,
                                currentStroke = currentStroke,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        // Button Row 1: Primary actions
                        Row(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.handleSubmitDrawing() },
                                enabled = strokes.isNotEmpty() && !isSubmitting
                            ) {
                                Text(if (isSubmitting) "Submitting..." else "Submit Drawing")
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearDrawing() },
                                enabled = strokes.isNotEmpty() || currentStroke.isNotEmpty()
                            ) {
                                Text("Clear")
                            }
                        }

                        // Removed Debug Row 2
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        TextField(
                            value = inputText,
                            onValueChange = { viewModel.onInputChanged(it) },
                            label = { Text("Your Input") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Button(onClick = { viewModel.evaluateInput(inputText, "typing") }) {
                                Text("Check")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(onClick = {
                                val permission = Manifest.permission.RECORD_AUDIO
                                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                    if (isListening) viewModel.stopListening() else viewModel.startListening()
                                } else {
                                    launcher.launch(permission)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Mic, 
                                    contentDescription = "Mic",
                                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.playTTS() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "TTS")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = { viewModel.prevSentence() },
                                enabled = state.currentPage > 1
                            ) {
                                Text("Previous")
                            }
                            Button(
                                onClick = { viewModel.nextSentence() },
                                enabled = state.currentPage < state.totalPages
                            ) {
                                Text("Next")
                            }
                        }

                        if (isSuccess) {
                            Text("Correct!", color = MaterialTheme.colorScheme.primary)
                        }
                        checkResult?.let { result ->
                            Spacer(modifier = Modifier.height(16.dp))
                            CheckResultDisplay(result = result)
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
