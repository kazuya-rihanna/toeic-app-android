package com.example.myapplication.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.CheckResult
import com.example.myapplication.data.model.Sentence
import com.example.myapplication.data.remote.SocketManager
import com.example.myapplication.domain.ToeicRepository
import com.example.myapplication.data.pen.PenManager
import kr.neolab.sdk.ink.structure.Dot
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.example.myapplication.data.model.Progress
import javax.inject.Inject
import android.media.AudioDeviceInfo

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val repository: ToeicRepository,
    private val socketManager: SocketManager,
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TtsManager,
    private val penManager: PenManager,
    private val recorderManager: AudioRecorderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private var currentUserId: String = "anonymous"

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _currentProgress = MutableStateFlow<Progress?>(null)
    val currentProgress = _currentProgress.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    val micVolume = recorderManager.volumeLevel

    val availableMics = recorderManager.availableMics
    val selectedMic = recorderManager.selectedMic

    fun selectMicrophone(mic: MicrophoneInfo) {
        recorderManager.selectMicrophone(mic)
    }

    val isListening = combine(_isRecording, sttManager.isListening) { rec, list ->
        rec || list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _checkResult = MutableStateFlow<CheckResult?>(null)
    val checkResult = _checkResult.asStateFlow()

    private val _isSuccess = MutableStateFlow(false)
    val isSuccess = _isSuccess.asStateFlow()

    val isPenConnected = combine(
        socketManager.isOcrConnected,
        penManager.isConnected
    ) { socketConnected, nativeConnected ->
        socketConnected || nativeConnected
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _strokes = MutableStateFlow<List<PenStroke>>(emptyList())
    val strokes = _strokes.asStateFlow()

    private val _currentStroke = MutableStateFlow<List<Dot>>(emptyList())
    val currentStroke = _currentStroke.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()
    val penEvents = penManager.penEvents
    val message = penManager.message
    val penBattery = penManager.batteryLevel
    
    private val _canvasOrientation = MutableStateFlow(1) // 0, 1, 2, 3
    val canvasOrientation = _canvasOrientation.asStateFlow()

    private val _isTtsPlaying = MutableStateFlow(false)
    val isTtsPlaying = _isTtsPlaying.asStateFlow()

    private var lastSubmittedDrawingBytes: ByteArray? = null
    private var lastOcrRawResponse: String? = null

    private var lastDrawnBookId: Int = -1
    private var lastDrawnPageId: Int = -1

    private var lastClearTime = 0L
    private val CLEAR_DEBOUNCE_MS = 1000L
    
    private var lastWriteTime = 0L
    private val COMMAND_COOLDOWN_MS = 1000L
    private var potentialCommand: String? = null
    private var commandStartX = 0f
    private var commandStartY = 0f
    private val COMMAND_MAX_DISTANCE = 3.0f
    init {
        android.util.Log.d("PracticeViewModel", "Initializing PracticeViewModel")
        try {
            socketManager.connect()
            viewModelScope.launch {
                socketManager.ocrText.collect { text ->
                    if (text != null) {
                        _inputText.value = text
                        evaluateInput(text, "livescribe")
                        socketManager.resetOcrText()
                    }
                }
            }
            viewModelScope.launch {
                sttManager.recognizedText.collect { text ->
                    if (text != null) {
                        _inputText.value = text
                        evaluateInput(text, "stt")
                        sttManager.resetText()
                    }
                }
            }
            viewModelScope.launch {
                penManager.lastDot.collect { dot ->
                    if (dot != null) {
                        processNativeDot(dot)
                    }
                }
            }
            viewModelScope.launch {
                socketManager.buttonPress.collect { action ->
                    if (action != null) {
                        android.util.Log.d("PracticeViewModel", "Flic button received: $action")
                        when (action) {
                            "A" -> playTTS()
                            "B" -> {
                                handleWhisperToggle()
                            }
                        }
                        socketManager.resetButtonPress()
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("PracticeViewModel", "Crash in PracticeViewModel init", e)
        }
    }

    private fun getCurrentQuestionPage(): Int? {
        val state = _uiState.value
        return if (state is PracticeUiState.Success) {
            state.sentence.page ?: state.currentPage
        } else {
            null
        }
    }

    private fun isPuiSubmitArea(dot: Dot): Boolean {
        val x = dot.x
        val y = dot.y
        val book = dot.noteId
        val owner = dot.ownerId
        val page = dot.pageId

        // Ensure app is in Success state (an active question is loaded)
        if (_uiState.value !is PracticeUiState.Success) return false

        return when {
            // Book 462 (Owner 27) - Narrowed to x in 47~53, y in 0~12 based on user sample (48.77, 7.4)
            // (Previous range 44~59 triggered too easily in the middle of the paper)
            owner == 27 && book == 462 -> (x in 47.0f..53.0f && y in 0.0f..12.0f)
            
            // Book 368 (Owner 27) - Checked via custom_368.nproj
            // Calculated NU: x: 78.2~88.3, y: 0.0~21.8
            owner == 27 && book == 368 -> (x in 78.0f..89.0f && y in 0.0f..22.0f)
            
            // Book 3138 (Owner 1012) - Keeping current range as placeholders for now
            owner == 1012 && book == 3138 -> (x in 20.0f..45.0f && y in 0.0f..10.0f)

            // Book 100 (Owner 50) - PUI Submit Areas
            // 1. Existing bottom-right area (expanded 1cm top-left)
            owner == 50 && book == 100 && (x in 84.0f..93.0f && y in 101.0f..112.0f) -> true
            // 2. New top-right area based on user log (82.94, 7.45)
            owner == 50 && book == 100 && (x in 75.0f..95.0f && y in 0.0f..20.0f) -> true

            // Book 551 (Section 3, Owner 27) - PUI Submit Areas, page-specific
            // page:2 Submit 左 (X≈5.94, Y≈65.85)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 2 && (x in 0.0f..12.0f && y in 60.0f..72.0f) -> true
            // page:2 Submit 右 (X≈74.01, Y≈65.71)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 2 && (x in 68.0f..80.0f && y in 60.0f..72.0f) -> true
            // page:3 Submit 右 (X≈74.76, Y≈111.06)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 3 && (x in 68.0f..81.0f && y in 105.0f..117.0f) -> true
            // page:3 Submit 左 (X≈6.41, Y≈110.61)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 3 && (x in 0.0f..13.0f && y in 105.0f..117.0f) -> true

            else -> false
        }
    }

    private fun isAudioTriggerArea(dot: Dot): Boolean {
        val x = dot.x
        val y = dot.y
        val book = dot.noteId
        val owner = dot.ownerId
        val page = dot.pageId

        // Ensure app is in Success state (an active question is loaded)
        if (_uiState.value !is PracticeUiState.Success) return false

        return when {
            // Book 100 (Owner 50) - Audio Trigger
            // MOVED TO TOP-LEFT based on trade request (prev Clear area)
            owner == 50 && book == 100 && (x in 0.0f..15.0f && y in 0.0f..15.0f) -> true

            // Book 462 (Owner 27) - Audio Trigger
            // MOVED TO TOP-LEFT based on trade request (prev Clear area)
            owner == 27 && book == 462 && (x in 0.0f..12.0f && y in 0.0f..12.0f) -> true

            // Book 551 (Section 3, Owner 27) - Audio Trigger, page-specific
            // page:2 音声再生 左 (X≈5.52, Y≈112.45)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 2 && (x in 0.0f..12.0f && y in 107.0f..119.0f) -> true
            // page:3 音声再生 右 (X≈74.82, Y≈59.44)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 3 && (x in 68.0f..81.0f && y in 53.0f..66.0f) -> true

            else -> false
        }
    }

    private fun isClearTriggerArea(dot: Dot): Boolean {
        val x = dot.x
        val y = dot.y
        val book = dot.noteId
        val owner = dot.ownerId
        val page = dot.pageId

        // Ensure app is in Success state (an active question is loaded)
        if (_uiState.value !is PracticeUiState.Success) return false

        return when {
            // Book 100 (Owner 50) - Clear Trigger
            // MOVED TO BOTTOM-LEFT based on trade request (prev Audio area)
            owner == 50 && book == 100 && (x in 0.0f..15.0f && y in 105.0f..125.0f) -> true

            // Book 462 (Owner 27) - Clear Trigger
            // MOVED TO MID-LEFT based on trade request (prev Audio area)
            owner == 27 && book == 462 && (x in 0.0f..10.0f && y in 70.0f..80.0f) -> true

            // Book 551 (Section 3, Owner 27) - Clear Trigger, page-specific
            // page:2 Clear 右 (X≈74.88, Y≈112.4)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 2 && (x in 68.0f..81.0f && y in 107.0f..119.0f) -> true
            // page:3 Clear 左 (X≈6.11, Y≈59.29)
            dot.sectionId == 3 && owner == 27 && book == 551 && page == 3 && (x in 0.0f..13.0f && y in 53.0f..66.0f) -> true

            else -> false
        }
    }

    private fun processNativeDot(dot: Dot) {
        val now = System.currentTimeMillis()

        // Clear existing drawing if the new dot is on a different page or book
        if (lastDrawnBookId != -1 && lastDrawnPageId != -1 && (lastDrawnBookId != dot.noteId || lastDrawnPageId != dot.pageId)) {
            clearDrawing()
        }
        lastDrawnBookId = dot.noteId
        lastDrawnPageId = dot.pageId

        // Treat 17 (DOWN) as start. If 17 is missed, first 18 (MOVE) with empty stroke is start.
        if (dot.dotType == 17 || (dot.dotType == 18 && _currentStroke.value.isEmpty())) {
            _currentStroke.value = listOf(dot)
            
            val isSubmit = isPuiSubmitArea(dot)
            val isAudio = isAudioTriggerArea(dot)
            val isClear = isClearTriggerArea(dot)
            
            if ((isSubmit || isAudio || isClear) && (now - lastWriteTime >= COMMAND_COOLDOWN_MS)) {
                potentialCommand = when {
                    isSubmit -> "SUBMIT"
                    isAudio -> "AUDIO"
                    isClear -> "CLEAR"
                    else -> null
                }
                commandStartX = dot.x
                commandStartY = dot.y
                android.util.Log.d("PracticeViewModel", "Potential command started: $potentialCommand at (${dot.x}, ${dot.y})")
            } else {
                potentialCommand = null
                lastWriteTime = now
            }
            return
        }

        if (dot.dotType == 18) {
            _currentStroke.value = _currentStroke.value + dot
            
            if (potentialCommand != null) {
                val dx = dot.x - commandStartX
                val dy = dot.y - commandStartY
                if (dx * dx + dy * dy > COMMAND_MAX_DISTANCE * COMMAND_MAX_DISTANCE) {
                    android.util.Log.d("PracticeViewModel", "Command canceled: stroke moved too far")
                    potentialCommand = null
                    lastWriteTime = now
                }
            } else {
                lastWriteTime = now
            }
            return
        }

        if (dot.dotType == 20) {
            val finalDots = _currentStroke.value + dot
            _currentStroke.value = emptyList()

            // Handle edge case where ONLY dot type 20 was received
            if (finalDots.size == 1) {
                val isSubmit = isPuiSubmitArea(dot)
                val isAudio = isAudioTriggerArea(dot)
                val isClear = isClearTriggerArea(dot)
                if ((isSubmit || isAudio || isClear) && (now - lastWriteTime >= COMMAND_COOLDOWN_MS)) {
                    potentialCommand = when {
                        isSubmit -> "SUBMIT"
                        isAudio -> "AUDIO"
                        isClear -> "CLEAR"
                        else -> null
                    }
                    commandStartX = dot.x
                    commandStartY = dot.y
                }
            }

            var executedCommand = false
            if (potentialCommand != null) {
                val dx = dot.x - commandStartX
                val dy = dot.y - commandStartY
                if (dx * dx + dy * dy <= COMMAND_MAX_DISTANCE * COMMAND_MAX_DISTANCE) {
                    android.util.Log.d("PracticeViewModel", "Executing command: $potentialCommand")
                    when (potentialCommand) {
                        "SUBMIT" -> handleSubmitDrawing()
                        "AUDIO" -> playTTS()
                        "CLEAR" -> clearDrawingWithGuard()
                    }
                    executedCommand = true
                    lastWriteTime = now // コマンド実行後もクールダウンをリセット
                } else {
                    lastWriteTime = now
                }
                potentialCommand = null
            } else {
                lastWriteTime = now
            }

            // If not a command tap, it's a regular drawing stroke
            if (!executedCommand && finalDots.isNotEmpty()) {
                _strokes.value = _strokes.value + PenStroke(finalDots)
            }
            return
        }
    }

    fun handleSubmitDrawing() {
        val currentStrokes = _strokes.value
        val orientation = _canvasOrientation.value
        if (currentStrokes.isEmpty() || _isSubmitting.value) return

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val bitmap = createBitmapFromStrokes(currentStrokes, orientation = orientation)
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                
                val body = byteArray.toRequestBody("image/png".toMediaType())
                
                val response = repository.uploadDrawing(body)
                if (response.isSuccessful && response.body() != null) {
                    val resultText = response.body()!!.text
                    lastSubmittedDrawingBytes = byteArray
                    val rawText = response.body()!!.text
                    val rawStatus = response.body()!!.status ?: ""
                    lastOcrRawResponse = "{\"text\":\"${rawText.replace("\"", "\\\"")}\",\"status\":\"${rawStatus.replace("\"", "\\\"")}\"}"
                    _inputText.value = resultText
                    evaluateInput(resultText, "livescribe")
                    clearDrawing()
                } else {
                    val errorMsg = response.errorBody()?.string() ?: response.message()
                    android.util.Log.e("PracticeViewModel", "OCR failed: ${response.code()} - $errorMsg")
                    _errorMessage.value = "OCR Failed (${response.code()}): Please try again"
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Error in handleSubmitDrawing", e)
                _errorMessage.value = "Submit Error: ${e.localizedMessage}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun clearDrawing() {
        _strokes.value = emptyList()
        _currentStroke.value = emptyList()
        lastDrawnBookId = -1
        lastDrawnPageId = -1
    }

    fun toggleOrientation() {
        _canvasOrientation.value = (_canvasOrientation.value + 1) % 4
        android.util.Log.d("PracticeViewModel", "Orientation toggled to: ${_canvasOrientation.value}")
    }

    fun clearDrawingWithGuard() {
        val now = System.currentTimeMillis()
        if (now - lastClearTime < CLEAR_DEBOUNCE_MS) return
        lastClearTime = now
        clearDrawing()
    }

    // Removed simulateDot and refreshListeners

    fun startListening() = sttManager.startListening()
    fun stopListening() = sttManager.stopListening()

    private fun startRecognitionWithPermissionCheck() {
        // ViewModel doesn't have Context for permission check directly, 
        // but it can call startListening() which handles its own state.
        // In this app, startListening() is called from UI with permission check.
        // If we trigger it via Flic, we assume permissions are already granted 
        // or we just trigger the command.
        sttManager.startListening()
    }

    fun handleWhisperToggle() {
        if (_isRecording.value) {
            stopWhisperRecording()
        } else {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                startWhisperRecording()
            }
        }
    }

    private suspend fun startWhisperRecording() {
        if (_isRecording.value) return
        val file = recorderManager.startRecording()
        if (file != null) {
            _isRecording.value = true
        }
    }

    private fun stopWhisperRecording() {
        if (!_isRecording.value) return
        val file = recorderManager.stopRecording()
        _isRecording.value = false

        if (file != null && file.exists()) {
            viewModelScope.launch {
                try {
                    val response = repository.transcribeAudio(file)
                    if (response.isSuccessful) {
                        val text = response.body()?.transcribedText
                        if (text != null) {
                            android.util.Log.d("PracticeViewModel", "Whisper result: $text")
                            _inputText.value = text
                            evaluateInput(text, "stt")
                        }
                    } else {
                        android.util.Log.e("PracticeViewModel", "Transcription failed: ${response.message()}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PracticeViewModel", "Whisper transcription error", e)
                } finally {
                    file.delete()
                }
            }
        }
    }

    fun playTTS() {
        if (_isTtsPlaying.value) return

        val currentState = _uiState.value
        if (currentState is PracticeUiState.Success) {
            viewModelScope.launch {
                _isTtsPlaying.value = true
                try {
                    ttsManager.playText(currentState.sentence.example)
                } finally {
                    _isTtsPlaying.value = false
                }
            }
        }
    }

/*
    fun connectPen() {
        val lastPen = penManager.getLastConnected()
        if (lastPen != null) {
            viewModelScope.launch {
                android.util.Log.d("PracticeViewModel", "Reconnect to last pen: ${lastPen.second}")
                penManager.connect(lastPen.first, lastPen.second)
            }
        } else {
            android.util.Log.w("PracticeViewModel", "No last pen saved. Please pair in Pairing Screen first.")
        }
    }
*/
    fun loadSentences(collectionId: String, userId: String) {
        this.currentUserId = userId
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Loading
            try {
                android.util.Log.d("PracticeViewModel", "Loading sentences for $collectionId, user=$userId")
                
                val progressDeferred = async { 
                    try { repository.getProgress(userId) }
                    catch (e: Exception) { null }
                }
                val response = repository.getFirstVocabulary(collectionId, userId)
                
                val progressRes = progressDeferred.await()
                if (progressRes != null && progressRes.isSuccessful) {
                    _currentProgress.value = progressRes.body()?.progress?.get(collectionId)
                }

                if (response.isSuccessful) {
                    val body = response.body()
                    android.util.Log.d("PracticeViewModel", "API Response: docs=${body?.docs?.size}, currentPage=${body?.currentPage}, totalPages=${body?.totalPages}")
                    val docs = body?.docs ?: emptyList()
                    if (docs.isNotEmpty()) {
                        val totalPages = body?.totalPages ?: 1
                        val currentPage = body?.currentPage ?: 1
                        _uiState.value = PracticeUiState.Success(docs[0], collectionId, currentPage, totalPages)
                    } else {
                        _uiState.value = PracticeUiState.Error("No sentences found")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("PracticeViewModel", "API Error: ${response.code()} - $errorBody")
                    _uiState.value = PracticeUiState.Error("Failed to fetch: ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Crash in loadSentences", e)
                _uiState.value = PracticeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun evaluateInput(text: String, method: String) {
        val currentState = _uiState.value
        if (currentState !is PracticeUiState.Success) return

        viewModelScope.launch {
            try {
                val options = mapOf(
                    "ignore_case" to true,
                    "ignore_punctuation" to true,
                    "normalize_whitespace" to true
                )
                val correctText = currentState.sentence.example ?: ""
                
                android.util.Log.d("PracticeViewModel", "Evaluating input: method=$method, text=$text")
                android.util.Log.d("PracticeViewModel", "Correct text: $correctText")

                val response = repository.checkText(
                    userId = currentUserId,
                    correctText = correctText,
                    inputText = text,
                    options = options
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    _checkResult.value = result
                    _errorMessage.value = null
                    android.util.Log.d("PracticeViewModel", "CheckResult received: $result")
                    
                    val correct = result?.exactMatch?.normalizedMatch == true || result?.isCorrect == true
                    if (correct) {
                        android.util.Log.d("PracticeViewModel", "Match confirmed! Updating progress...")
                        _isSuccess.value = true

                        // If it's correct and we have the last submitted drawing, save it to GCS
                        val drawingBytes = lastSubmittedDrawingBytes
                        val ocrRaw = lastOcrRawResponse
                        if (drawingBytes != null && ocrRaw != null) {
                            viewModelScope.launch {
                                try {
                                    val res = repository.saveOcrData(
                                        imageBytes = drawingBytes,
                                        rawOutput = ocrRaw,
                                        correctText = correctText,
                                        userId = currentUserId,
                                        collectionId = currentState.collectionId,
                                        page = currentState.sentence.page ?: 1
                                    )
                                    if (res.isSuccessful) {
                                        val updatedSentence = currentState.sentence.copy(hasOcrDataset = true)
                                        _uiState.value = currentState.copy(sentence = updatedSentence)
                                        android.util.Log.d("PracticeViewModel", "Successfully saved OCR to GCS and updated UI state.")
                                    } else {
                                        android.util.Log.e("PracticeViewModel", "Failed to save OCR to GCS: ${res.code()}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PracticeViewModel", "Error saving OCR data to GCS", e)
                                }
                            }
                        }
                        
                        // Optimistically update locally for immediate icon display
                        val pageKey = "page_${currentState.sentence.page ?: 1}"
                        val currentProg = _currentProgress.value ?: Progress()
                        val updatedCorrect = currentProg.correct.toMutableMap()
                        val methodMap = updatedCorrect[method]?.toMutableMap() ?: mutableMapOf()
                        methodMap[pageKey] = true
                        updatedCorrect[method] = methodMap
                        _currentProgress.value = currentProg.copy(correct = updatedCorrect)

                        viewModelScope.launch {
                            try {
                                val progResponse = repository.updateProgress(
                                    userId = currentUserId,
                                    collectionId = currentState.collectionId,
                                    page = currentState.sentence.page ?: 1,
                                    inputMethod = method
                                )
                                if (progResponse.isSuccessful) {
                                    val updatedProgress = progResponse.body()?.progress
                                    if (updatedProgress != null) {
                                        _currentProgress.value = updatedProgress
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("PracticeViewModel", "Error updating progress", e)
                            }
                        }

                        // Auto-advance to next sentence after a delay so user can see result
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(3500)
                            if (_isSuccess.value) { // Check if we are still on the same success state
                                nextSentence()
                            }
                        }
                    } else {
                        android.util.Log.d("PracticeViewModel", "No match. Result: $result")
                    }
                    // Always clear cached drawing and raw response after evaluation
                    lastSubmittedDrawingBytes = null
                    lastOcrRawResponse = null
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    _errorMessage.value = "Match API failed: ${response.code()} - $errorBody"
                    android.util.Log.e("PracticeViewModel", "API Error: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Request failed: ${e.localizedMessage}"
                android.util.Log.e("PracticeViewModel", "Exception in evaluateInput", e)
                lastSubmittedDrawingBytes = null
                lastOcrRawResponse = null
            }
        }
    }

    fun nextSentence() {
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Success) {
            if (currentState.currentPage >= currentState.totalPages) return
            
            viewModelScope.launch {
                _uiState.value = PracticeUiState.Loading
                try {
                    val nextRawPage = currentState.currentPage + 1
                    val response = repository.getVocabularyPage(currentState.collectionId, nextRawPage)
                    if (response.isSuccessful) {
                        val body = response.body()
                        val docs = body?.docs ?: emptyList()
                        if (docs.isNotEmpty()) {
                            _uiState.value = PracticeUiState.Success(
                                docs[0], 
                                currentState.collectionId, 
                                nextRawPage, 
                                body?.totalPages ?: currentState.totalPages
                            )
                            _inputText.value = ""
                            _checkResult.value = null
                            _isSuccess.value = false
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = PracticeUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun prevSentence() {
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Success) {
            if (currentState.currentPage <= 1) return

            viewModelScope.launch {
                _uiState.value = PracticeUiState.Loading
                try {
                    val prevRawPage = currentState.currentPage - 1
                    val response = repository.getVocabularyPage(currentState.collectionId, prevRawPage)
                    if (response.isSuccessful) {
                        val body = response.body()
                        val docs = body?.docs ?: emptyList()
                        if (docs.isNotEmpty()) {
                            _uiState.value = PracticeUiState.Success(
                                docs[0], 
                                currentState.collectionId, 
                                prevRawPage, 
                                body?.totalPages ?: currentState.totalPages
                            )
                            _inputText.value = ""
                            _checkResult.value = null
                            _isSuccess.value = false
                        }
                    }
                } catch (e: Exception) {
                    _uiState.value = PracticeUiState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketManager.disconnect()
    }
}

sealed class PracticeUiState {
    object Loading : PracticeUiState()
    data class Success(
        val sentence: com.example.myapplication.data.model.Sentence, 
        val collectionId: String,
        val currentPage: Int,
        val totalPages: Int
    ) : PracticeUiState()
    data class Error(val message: String) : PracticeUiState()
}
