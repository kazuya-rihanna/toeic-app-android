package com.example.myapplication.ui.practice

import android.os.Build
import android.content.Intent
import android.net.Uri

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Close
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    collectionId: String,
    onBack: () -> Unit,
    onPairingClick: () -> Unit = {},
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
    val categoryIndex by viewModel.categoryIndex.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    val ocrMode by viewModel.ocrMode.collectAsState()
    val lastOcrDurationMs by viewModel.lastOcrDurationMs.collectAsState()
    val lastOcrMethod by viewModel.lastOcrMethod.collectAsState()
    val translationText by viewModel.translationText.collectAsState()
    val isTranslating by viewModel.isTranslating.collectAsState()
    val isTranslationVisible by viewModel.isTranslationVisible.collectAsState()
    val spokenTranslationText by viewModel.spokenTranslationText.collectAsState()
    val isSpokenTranslating by viewModel.isSpokenTranslating.collectAsState()
    val isSpokenTranslationVisible by viewModel.isSpokenTranslationVisible.collectAsState()
    val micVolume by viewModel.micVolume.collectAsState()
    val isPenScanning by viewModel.isPenScanning.collectAsState()
    val penConnectionStatus by viewModel.penConnectionStatus.collectAsState()

    var isBlurred by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
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

    LaunchedEffect(penConnectionStatus) {
        penConnectionStatus?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
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

    val triggerMic: () -> Unit = {
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
    }

    val penPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val penPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            viewModel.autoScanAndConnectPen()
        } else {
            android.widget.Toast.makeText(context, "Bluetooth & Location permissions required for pen scanning", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val triggerAutoConnect: () -> Unit = {
        val missing = penPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.autoScanAndConnectPen()
        } else {
            penPermissionLauncher.launch(missing.toTypedArray())
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

                    if (!isPenConnected) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPenScanning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable {
                                    if (isPenScanning) viewModel.cancelPenScan() else triggerAutoConnect()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isPenScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Scanning...",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = "Auto Connect Pen",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Auto Link",
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPenConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { onPairingClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                        val catLabel = when (selectedCategory) {
                            "word" -> "Word"
                            "phrasal_verb" -> "Phrasal Verb"
                            "collocation_idiom" -> "Collocation & Idiom"
                            else -> selectedCategory?.replace("_", " ")?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        }
                        val activeCategoryPages = if (selectedCategory != null) {
                            categoryIndex?.pages?.get(selectedCategory) ?: emptyList()
                        } else null
                        val activeCategoryIndex = if (activeCategoryPages != null) {
                            val idx = activeCategoryPages.indexOf(state.currentPage)
                            if (idx >= 0) idx + 1 else null
                        } else null
                        val activeCategoryTotal = activeCategoryPages?.size ?: 0

                        val pageIndicatorText = if (catLabel != null && activeCategoryTotal > 0) {
                            "$catLabel ${activeCategoryIndex ?: "-"}/$activeCategoryTotal (Page ${state.currentPage})"
                        } else {
                            "Page ${state.currentPage} of ${state.totalPages}"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(), 
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Page indicator & OCR Dataset badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { showJumpDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = pageIndicatorText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Jump Page",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
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

                            // Right: Play TTS & Record Mic buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.playTTS() },
                                    enabled = !isTtsPlaying,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play TTS",
                                        tint = if (isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = triggerMic,
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = if (isListening) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Record Speech",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Category Filter Chips
                        val counts = categoryIndex?.counts ?: emptyMap()
                        if (counts.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { viewModel.selectCategory(null) },
                                    label = { Text("All (${state.totalPages})", style = MaterialTheme.typography.labelSmall) }
                                )
                                counts.forEach { (catKey, count) ->
                                    if (count > 0) {
                                        val labelText = when (catKey) {
                                            "word" -> "Words"
                                            "phrasal_verb" -> "Phrasal Verbs"
                                            "collocation_idiom" -> "Collocations & Idioms"
                                            else -> catKey.replace("_", " ").split(" ")
                                                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                                        }
                                        FilterChip(
                                            selected = selectedCategory == catKey,
                                            onClick = { viewModel.selectCategory(if (selectedCategory == catKey) null else catKey) },
                                            label = { Text("$labelText ($count)", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
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
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 1. Top Control Bar (Cleared indicators on left, Action buttons on right)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Cleared status icons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (clearedTyping) Icon(Icons.Default.Keyboard, contentDescription = "Cleared Typing", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        if (clearedStt) Icon(Icons.Default.Mic, contentDescription = "Cleared Dictation", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                        if (clearedLive) Icon(Icons.Default.EditNote, contentDescription = "Cleared Sync", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    }

                                    // Right: Action buttons (Copy + Translate + Visibility)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // 1. Copy Sentence
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.sentence.displayExample))
                                                android.widget.Toast.makeText(context, "Copied sentence", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy Sentence",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 4. Translate Sentence
                                        FilledTonalIconButton(
                                            onClick = { viewModel.toggleTranslation() },
                                            modifier = Modifier.size(36.dp),
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = if (isTranslationVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                contentColor = if (isTranslationVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = "Toggle Translation",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // 5. Visibility Toggle
                                        IconButton(
                                            onClick = { isBlurred = !isBlurred },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                if (isBlurred) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Visibility",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // 2. Sentence Text Area (Full width, zero overlap with any buttons)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val sentenceText = if (isBlurred) "••••••••••••" else state.sentence.displayExample
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
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { offset ->
                                            if (!isBlurred) {
                                                annotatedString.getStringAnnotations(tag = "WORD", start = offset, end = offset)
                                                    .firstOrNull()?.let { annotation ->
                                                        val clickedWord = annotation.item
                                                        val copyText = """
                                                            Act as a Lexicographer. From the provided URL, extract ONLY the definition and the specific example sentence that matches the context of the input sentence. Output the result in a clean Markdown blockquote. Do not provide any introductory text or conversational filler.
                                                            
                                                            URL: https://www.ldoceonline.com/dictionary/${clickedWord.lowercase()}
                                                            Input: ${state.sentence.displayExample}
                                                        """.trimIndent()
                                                        clipboardManager.setText(AnnotatedString(copyText))
                                                        android.widget.Toast.makeText(context, "Copied: $clickedWord", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        }
                                    )
                                }

                                if (isListening) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AudioVolumeVisualizer(
                                            volume = micVolume,
                                            isListening = true,
                                            modifier = Modifier.width(180.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isTranslationVisible,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            if (isTranslating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Gemini 翻訳中...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else if (!translationText.isNullOrBlank()) {
                                                Text(
                                                    text = translationText!!,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                val sentence = state.sentence

                                // 3. Selected Definition Area (英英定義)
                                val def = sentence.selectedDefinition
                                if (!def.isNullOrBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MenuBook,
                                                contentDescription = "Definition",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "DEFINITION",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = def,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. Spoken Corpus Quote & Video Player (街頭インタビュー生セリフ & 動画再生)
                                val spoken = sentence.spokenCorpusExample
                                if (spoken != null && (!spoken.speakerLine.isNullOrBlank() || !spoken.videoUrl.isNullOrBlank())) {
                                    Surface(
                                        color = Color(0xFFFFEBEE).copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.8.dp, Color(0xFFE57373).copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.FormatQuote,
                                                        contentDescription = "Spoken Line",
                                                        tint = Color(0xFFC62828),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = spoken.sourceChannel ?: "Spoken Corpus",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFC62828)
                                                    )
                                                }

                                                // Right: Action buttons (Copy + Translate + Watch on YouTube)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    if (!spoken.speakerLine.isNullOrBlank()) {
                                                        IconButton(
                                                            onClick = {
                                                                clipboardManager.setText(AnnotatedString(spoken.speakerLine))
                                                                android.widget.Toast.makeText(context, "Copied spoken line", android.widget.Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.ContentCopy,
                                                                contentDescription = "Copy Spoken Line",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        IconButton(
                                                            onClick = { viewModel.toggleSpokenTranslation() },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Translate,
                                                                contentDescription = "Translate Spoken Line",
                                                                tint = if (isSpokenTranslationVisible) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    if (!spoken.videoUrl.isNullOrBlank()) {
                                                        IconButton(
                                                            onClick = {
                                                                try {
                                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spoken.videoUrl)).apply {
                                                                        setPackage("com.google.android.youtube")
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    try {
                                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spoken.videoUrl)))
                                                                    } catch (e2: Exception) {
                                                                        android.widget.Toast.makeText(context, "動画リンクを開けませんでした", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.OpenInNew,
                                                                contentDescription = "Watch on YouTube",
                                                                tint = Color(0xFFC62828),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (!spoken.speakerLine.isNullOrBlank()) {
                                                Text(
                                                    text = "“${spoken.speakerLine}”",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )

                                                AnimatedVisibility(
                                                    visible = isSpokenTranslationVisible,
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut()
                                                ) {
                                                    Surface(
                                                        color = Color(0xFFFFEBEE).copy(alpha = 0.95f),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 2.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Translate,
                                                                contentDescription = null,
                                                                tint = Color(0xFFC62828),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            if (isSpokenTranslating) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(14.dp),
                                                                    strokeWidth = 2.dp,
                                                                    color = Color(0xFFC62828)
                                                                )
                                                                Text(
                                                                    text = "Gemini 翻訳中...",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = Color(0xFFC62828)
                                                                )
                                                            } else if (!spokenTranslationText.isNullOrBlank()) {
                                                                Text(
                                                                    text = spokenTranslationText!!,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = Color(0xFFB71C1C)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (!spoken.videoTitle.isNullOrBlank()) {
                                                Text(
                                                    text = spoken.videoTitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                } else if (collectionId == "justin_waller_vocabulary") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "このページには動画例がありません（Page 2 など次のページをお試しください）",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // --- Corpus Metadata Badges ---
                                val cat = sentence.category?.lowercase() ?: ""
                                val catJp = sentence.categoryJp ?: ""
                                val isPhrasal = cat == "phrasal_verb" || catJp.contains("句動詞")
                                val isCollocIdiom = cat == "collocation_idiom" || catJp.contains("イディオム") || catJp.contains("コロケーション")
                                val isWord = cat == "word" || catJp.contains("単語") || (!isPhrasal && !isCollocIdiom && (sentence.expression != null || cat.isNotBlank() || catJp.isNotBlank()))
                                val hasCategory = isPhrasal || isCollocIdiom || isWord

                                val posLabel = sentence.pos ?: sentence.partLabel
                                val cefr = sentence.cefrLevel
                                val formality = sentence.formality
                                val channelOcc = sentence.channelOccurrences
                                val coca = sentence.cocaRank
                                val ipa = sentence.ipa

                                val hasMetadata = !posLabel.isNullOrBlank() ||
                                        sentence.importanceRank != null ||
                                        sentence.testCount != null ||
                                        sentence.totalCount != null ||
                                        !sentence.appearedTests.isNullOrBlank() ||
                                        !cefr.isNullOrBlank() ||
                                        !formality.isNullOrBlank() ||
                                        channelOcc != null ||
                                        coca != null ||
                                        !ipa.isNullOrBlank() ||
                                        hasCategory

                                if (hasMetadata) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // 1. 言語区分バッジ (単語 / 句動詞 / イディオム・成句)
                                        if (hasCategory) {
                                            val (typeText, typeBg, typeColor) = when {
                                                isPhrasal -> Triple("Phrasal Verb", Color(0xFFE8F5E9), Color(0xFF1B5E20))
                                                isCollocIdiom -> Triple("Collocation & Idiom", Color(0xFFFFF8E1), Color(0xFFE65100))
                                                else -> Triple("Word", Color(0xFFEDE7F6), Color(0xFF4A148C))
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = typeBg,
                                                border = BorderStroke(1.dp, typeColor.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = typeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = typeColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 2. Target expression / headword
                                        val expr = sentence.expression
                                        if (!expr.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = expr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 3. IPA (Pronunciation)
                                        if (!ipa.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "/$ipa/",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 4. POS / Part Label
                                        if (!posLabel.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = posLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 5. CEFR Level (e.g. B1, C1)
                                        if (!cefr.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFEDE7F6),
                                                border = BorderStroke(0.8.dp, Color(0xFF7E57C2).copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "CEFR $cefr",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4527A0),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 6. Formality (e.g. Casual-Neutral)
                                        if (!formality.isNullOrBlank()) {
                                            val formattedFormality = formality.split("-")
                                                .joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE0F2F1),
                                                border = BorderStroke(0.8.dp, Color(0xFF26A69A).copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = formattedFormality,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF004D40),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 7. Importance Rank (TOEIC)
                                        val rankRaw = sentence.importanceRank
                                        if (!rankRaw.isNullOrBlank()) {
                                            val isRankS = rankRaw.startsWith("S", ignoreCase = true)
                                            val isRankA = rankRaw.startsWith("A", ignoreCase = true)
                                            val isRankB = rankRaw.startsWith("B", ignoreCase = true)
                                            val (rankBg, rankColor) = when {
                                                isRankS -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                                                isRankA -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                                                isRankB -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
                                                else -> Color(0xFFF5F5F5) to Color(0xFF616161)
                                            }
                                            val rankLabel = if (rankRaw.contains(" ")) "Rank " + rankRaw.substringBefore(" ") else "Rank $rankRaw"
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = rankBg,
                                                border = BorderStroke(0.8.dp, rankColor.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = rankLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = rankColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 8. Corpus frequency (Channel occurrences & COCA Rank)
                                        val corpusFreqParts = mutableListOf<String>()
                                        if (channelOcc != null) corpusFreqParts.add("${channelOcc}x channel")
                                        if (coca != null) corpusFreqParts.add("COCA #$coca")
                                        if (corpusFreqParts.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = corpusFreqParts.joinToString(" • "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 9. Test count & Total occurrences (TOEIC)
                                        val countParts = mutableListOf<String>()
                                        val totalCount = sentence.totalCount
                                        val testCount = sentence.testCount
                                        if (totalCount != null) countParts.add("${totalCount}x total")
                                        if (testCount != null) countParts.add("${testCount} tests")
                                        if (countParts.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = countParts.joinToString(" • "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        // 10. Appeared tests list (e.g. test1, test2)
                                        val appeared = sentence.appearedTests
                                        if (!appeared.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Text(
                                                    text = "Tests: $appeared",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.handleSubmitDrawing() },
                                enabled = strokes.isNotEmpty() && !isSubmitting
                            ) {
                                Text(if (isSubmitting) "Recognizing..." else "Submit Drawing")
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

                        // OCR Engine Mode & Latency Benchmark Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = ocrMode == OcrMode.DIGITAL_INK,
                                onClick = { viewModel.setOcrMode(OcrMode.DIGITAL_INK) },
                                label = { Text("⚡ Digital Ink + AI") },
                                leadingIcon = if (ocrMode == OcrMode.DIGITAL_INK) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = ocrMode == OcrMode.GEMINI,
                                onClick = { viewModel.setOcrMode(OcrMode.GEMINI) },
                                label = { Text("☁️ Gemini (クラウド)") },
                                leadingIcon = if (ocrMode == OcrMode.GEMINI) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )

                            if (lastOcrDurationMs != null) {
                                val seconds = lastOcrDurationMs!! / 1000.0
                                val isFast = lastOcrMethod == OcrMode.DIGITAL_INK
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isFast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    border = BorderStroke(1.dp, if (isFast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = if (isFast) {
                                            String.format(java.util.Locale.US, "⚡ %.2fs (%dms)", seconds, lastOcrDurationMs)
                                        } else {
                                            String.format(java.util.Locale.US, "☁️ %.2fs (%dms)", seconds, lastOcrDurationMs)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextField(
                            value = inputText,
                            onValueChange = { viewModel.onInputChanged(it) },
                            label = { Text("Your Input") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

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
                                IconButton(onClick = triggerMic) {
                                    Icon(
                                        Icons.Default.Mic, 
                                        contentDescription = "Mic",
                                        tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.playTTS() },
                                enabled = !isTtsPlaying
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "TTS",
                                    tint = if (isTtsPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val canPrev = if (activeCategoryPages != null && activeCategoryPages.isNotEmpty()) {
                            val idx = activeCategoryPages.indexOf(state.currentPage)
                            idx > 0 || (idx < 0 && activeCategoryPages.any { it < state.currentPage })
                        } else {
                            state.currentPage > 1
                        }

                        val canNext = if (activeCategoryPages != null && activeCategoryPages.isNotEmpty()) {
                            val idx = activeCategoryPages.indexOf(state.currentPage)
                            (idx >= 0 && idx < activeCategoryPages.size - 1) || (idx < 0 && activeCategoryPages.any { it > state.currentPage })
                        } else {
                            state.currentPage < state.totalPages
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.prevSentence() },
                                enabled = canPrev,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Previous", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { showJumpDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (selectedCategory != null && activeCategoryTotal > 0) {
                                        "${activeCategoryIndex ?: "-"}/$activeCategoryTotal (P.${state.currentPage})"
                                    } else {
                                        "${state.currentPage} / ${state.totalPages}"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Button(
                                onClick = { viewModel.nextSentence() },
                                enabled = canNext,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Next", maxLines = 1)
                            }
                        }

                        if (showJumpDialog) {
                            PageJumpDialog(
                                currentPage = state.currentPage,
                                totalPages = state.totalPages,
                                categoryName = catLabel,
                                categoryPages = activeCategoryPages,
                                onDismiss = { showJumpDialog = false },
                                onJump = { targetPage ->
                                    showJumpDialog = false
                                    viewModel.jumpToPage(targetPage)
                                }
                            )
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

@Composable
fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    categoryName: String? = null,
    categoryPages: List<Int>? = null,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    val isCategoryMode = categoryPages != null && categoryPages.isNotEmpty()
    val catTotal = categoryPages?.size ?: totalPages
    val currentCatIndex = if (isCategoryMode) {
        val idx = categoryPages!!.indexOf(currentPage)
        if (idx >= 0) idx + 1 else 1
    } else {
        currentPage
    }

    var inputIndex by remember { mutableStateOf(currentCatIndex.toString()) }
    var sliderValue by remember { mutableStateOf(currentCatIndex.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCategoryMode) "Jump to $categoryName" else "Jump to Page",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val promptText = if (isCategoryMode) {
                    "Select item number (1 to $catTotal):"
                } else {
                    "Enter page number (1 to $totalPages):"
                }
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = inputIndex,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }
                        inputIndex = filtered
                        filtered.toIntOrNull()?.let { p ->
                            sliderValue = p.coerceIn(1, catTotal).toFloat()
                        }
                    },
                    label = { Text(if (isCategoryMode) "Item Number (1 - $catTotal)" else "Page Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (isCategoryMode) {
                        val currentTypedIdx = inputIndex.toIntOrNull()?.coerceIn(1, catTotal) ?: currentCatIndex
                        val originalPage = categoryPages!![currentTypedIdx - 1]
                        { Text("→ Jumps to Page $originalPage") }
                    } else null
                )

                if (catTotal > 1) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (isCategoryMode) "1 (P.${categoryPages!!.first()})" else "Page 1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                if (isCategoryMode) "$catTotal (P.${categoryPages!!.last()})" else "Page $totalPages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                inputIndex = it.toInt().toString()
                            },
                            valueRange = 1f..catTotal.toFloat(),
                            steps = if (catTotal in 2..50) catTotal - 2 else 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Quick jump buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            inputIndex = "1"
                            sliderValue = 1f
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("First (1)")
                    }
                    OutlinedButton(
                        onClick = {
                            inputIndex = catTotal.toString()
                            sliderValue = catTotal.toFloat()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isCategoryMode) "Last ($catTotal)" else "Last ($totalPages)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val typed = inputIndex.toIntOrNull() ?: currentCatIndex
                    val clamped = typed.coerceIn(1, catTotal)
                    val targetPage = if (isCategoryMode) {
                        categoryPages!![clamped - 1]
                    } else {
                        clamped
                    }
                    onJump(targetPage)
                }
            ) {
                Text("Jump")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

