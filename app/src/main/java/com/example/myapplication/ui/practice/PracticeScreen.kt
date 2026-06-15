package com.example.myapplication.ui.practice

import android.os.Build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.*
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
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSuccess by viewModel.isSuccess.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isPenConnected by viewModel.isPenConnected.collectAsState()
    val strokes by viewModel.strokes.collectAsState()
    val currentStroke by viewModel.currentStroke.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val penBattery by viewModel.penBattery.collectAsState()
    val currentProgress by viewModel.currentProgress.collectAsState()
    val canvasOrientation by viewModel.canvasOrientation.collectAsState()

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
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val bluetoothGranted = if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        } else true
        
        if (recordGranted && bluetoothGranted) {
            viewModel.handleWhisperToggle()
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
                        Spacer(modifier = Modifier.width(6.dp))
                        val batteryText = if (isPenConnected && penBattery != null) " (${penBattery}%)" else ""
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Page ${state.currentPage} / ${state.totalPages}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (state.sentence.hasOcrDataset == true) {
                                    Row(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "OCR Dataset",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
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

                        // Display API Error if any
                        errorMessage?.let { error ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        val pageKey = "page_${state.currentPage}"
                        val clearedTyping = currentProgress?.correct?.get("typing")?.get(pageKey) == true
                        val clearedStt = currentProgress?.correct?.get("stt")?.get(pageKey) == true
                        val clearedLive = currentProgress?.correct?.get("livescribe")?.get(pageKey) == true

                        // Sentence Card Centered
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp).fillMaxWidth()) {
                                
                                Row(
                                    modifier = Modifier.align(Alignment.TopStart).offset(y = (-16).dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (clearedTyping) Icon(Icons.Default.Keyboard, contentDescription = "Cleared Typing", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    if (clearedStt) Icon(Icons.Default.Mic, contentDescription = "Cleared Dictation", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                                    if (clearedLive) Icon(Icons.Default.EditNote, contentDescription = "Cleared Sync", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary)
                                }

                                val sentenceText = if (isBlurred) "••••••••••••" else (state.sentence.example ?: "")
                                val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                                    append(sentenceText)
                                    if (!isBlurred) {
                                        val wordRegex = "[a-zA-Z0-9_'-]+".toRegex()
                                        wordRegex.findAll(sentenceText).forEach { matchResult ->
                                            addStringAnnotation(
                                                tag = "WORD",
                                                annotation = matchResult.value,
                                                start = matchResult.range.first,
                                                end = matchResult.range.last + 1
                                            )
                                        }
                                    }
                                }

                                androidx.compose.foundation.text.ClickableText(
                                    text = annotatedString,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color = androidx.compose.material3.LocalContentColor.current
                                    ),
                                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                                    onClick = { offset ->
                                        if (!isBlurred) {
                                            annotatedString.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                                                .firstOrNull()?.let { annotation ->
                                                    val clickedWord = annotation.item
                                                    val copyText = """
                                                        Act as a Lexicographer. From the provided URL, extract ONLY the definition and the specific example sentence that matches the context of the input sentence. Output the result in a clean Markdown blockquote. Do not provide any introductory text or conversational filler.
                                                        
                                                        URL: https://www.ldoceonline.com/dictionary/${clickedWord.lowercase()}
                                                        Input: ${state.sentence.example}
                                                    """.trimIndent()
                                                    clipboardManager.setText(AnnotatedString(copyText))
                                                    android.widget.Toast.makeText(context, "Copied: $clickedWord", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    }
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
                        val hasDrawing = strokes.isNotEmpty() || currentStroke.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasDrawing) Modifier.wrapContentHeight()
                                    else Modifier.height(80.dp)
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                .padding(4.dp)
                        ) {
                            if (hasDrawing) {
                                NativePenCanvas(
                                    strokes = strokes,
                                    currentStroke = currentStroke,
                                    orientation = canvasOrientation,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // プレースホルダー
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Draw with your pen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
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
                            IconButton(
                                onClick = { viewModel.toggleOrientation() }
                            ) {
                                Icon(
                                    Icons.Default.RotateRight,
                                    contentDescription = "Rotate Canvas",
                                    tint = MaterialTheme.colorScheme.primary
                                )
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

                        Spacer(modifier = Modifier.height(16.dp))

                        val micVolume by viewModel.micVolume.collectAsState()

                        // Sensitivity Gauge (Always visible and centered, not hidden inside scrollable Row)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AudioVolumeVisualizer(
                                volume = micVolume,
                                isListening = isListening,
                                modifier = Modifier.width(180.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = { viewModel.evaluateInput(inputText, "typing") }) {
                                Text("Check")
                            }
                            
                            Surface(
                                color = if (isListening) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                IconButton(onClick = {
                                    val recordPermission = Manifest.permission.RECORD_AUDIO
                                    val btPermission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
                                    
                                    val permissionsToRequest = mutableListOf<String>()
                                    
                                    if (ContextCompat.checkSelfPermission(context, recordPermission) != PackageManager.PERMISSION_GRANTED) {
                                        permissionsToRequest.add(recordPermission)
                                    }
                                    
                                    if (btPermission != null && ContextCompat.checkSelfPermission(context, btPermission) != PackageManager.PERMISSION_GRANTED) {
                                        permissionsToRequest.add(btPermission)
                                    }

                                    if (permissionsToRequest.isEmpty()) {
                                        viewModel.handleWhisperToggle()
                                    } else {
                                        launcher.launch(permissionsToRequest.toTypedArray())
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Mic, 
                                        contentDescription = "Mic",
                                        tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

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

@Composable
fun AudioVolumeVisualizer(
    volume: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 12
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until barCount) {
                // Calculate individual bar weights for standard VU shape (higher in center)
                val baseFactor = when (i) {
                    0, 11 -> 0.25f
                    1, 10 -> 0.45f
                    2, 9 -> 0.65f
                    3, 8 -> 0.8f
                    4, 7 -> 0.95f
                    else -> 1.0f
                }
                
                val targetFraction = if (isListening) {
                    val sineOffset = 0.8f + 0.2f * kotlin.math.sin(i * 1.5 + System.currentTimeMillis() * 0.05).toFloat()
                    (volume * baseFactor * sineOffset).coerceIn(0.1f, 1.0f)
                } else {
                    0.1f // Flat/resting level when idle
                }

                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "bar_height_$i"
                )

                val barColor = if (isListening) {
                    // Premium VU gradient colored based on level
                    when {
                        animatedFraction < 0.35f -> MaterialTheme.colorScheme.primary
                        animatedFraction < 0.7f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(animatedFraction)
                        .background(
                            color = barColor,
                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = if (isListening) "Recording..." else "Mic Idle",
            style = MaterialTheme.typography.labelSmall,
            color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
