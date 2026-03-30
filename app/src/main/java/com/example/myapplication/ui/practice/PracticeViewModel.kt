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
import javax.inject.Inject

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val repository: ToeicRepository,
    private val socketManager: SocketManager,
    private val sttManager: SpeechToTextManager,
    private val ttsManager: TtsManager,
    private val penManager: PenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private var currentUserId: String = "anonymous"

    val isListening = sttManager.isListening

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
                                if (sttManager.isListening.value) {
                                    stopListening()
                                } else {
                                    startRecognitionWithPermissionCheck()
                                }
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

    private fun isPuiSubmitArea(dot: Dot): Boolean {
        val x = dot.x
        val y = dot.y
        val book = dot.noteId
        val owner = dot.ownerId
        val page = dot.pageId

        return when {
            // Book 462 (Owner 27) - Narrowed to x in 47~53, y in 0~12 based on user sample (48.77, 7.4)
            // (Previous range 44~59 triggered too easily in the middle of the paper)
            owner == 27 && book == 462 -> (x in 47.0f..53.0f && y in 0.0f..12.0f)
            
            // Book 368 (Owner 27) - Checked via custom_368.nproj
            // Calculated NU: x: 78.2~88.3, y: 0.0~21.8
            owner == 27 && book == 368 -> (x in 78.0f..89.0f && y in 0.0f..22.0f)
            
            // Book 3138 (Owner 1012) - Keeping current range as placeholders for now
            owner == 1012 && book == 3138 -> (x in 20.0f..45.0f && y in 0.0f..10.0f)

            // Book 100 (Owner 50) - Expanded 1cm top-left based on user feedback
            // (Targeting sample dot 89.5, 107 with 1cm buffer to top and left)
            owner == 50 && book == 100 -> (x in 84.0f..93.0f && y in 101.0f..112.0f)

            else -> false
        }
    }

    private fun processNativeDot(dot: Dot) {
        // Intercept PUI tap (Collision Detection)
        if (isPuiSubmitArea(dot)) {
            if (dot.dotType == 17) { // Trigger only once on DOWN
                android.util.Log.d("PracticeViewModel", "PUI tap detected on Book ${dot.noteId} at (${dot.x}, ${dot.y}). Submitting...")
                handleSubmitDrawing()
            }
            // Do not render this dot as a stroke
            return
        }

        android.util.Log.d("PracticeViewModel", "Processing dot: x=${dot.x}, y=${dot.y}, type=${dot.dotType}")
        // Dot types: 17: DOWN, 18: MOVE, 20: UP
        when (dot.dotType) {
            17 -> { // DOWN
                android.util.Log.d("PracticeViewModel", "Stroke DOWN")
                _currentStroke.value = listOf(dot)
            }
            18 -> { // MOVE
                _currentStroke.value = _currentStroke.value + dot
            }
            20 -> { // UP
                android.util.Log.d("PracticeViewModel", "Stroke UP - Total dots: ${_currentStroke.value.size + 1}")
                val finalDots = _currentStroke.value + dot
                if (finalDots.isNotEmpty()) {
                    _strokes.value = _strokes.value + PenStroke(finalDots)
                }
                _currentStroke.value = emptyList()
            }
            else -> {
                android.util.Log.w("PracticeViewModel", "Unknown dot type: ${dot.dotType}")
            }
        }
    }

    fun handleSubmitDrawing() {
        val currentStrokes = _strokes.value
        if (currentStrokes.isEmpty() || _isSubmitting.value) return

        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val bitmap = createBitmapFromStrokes(currentStrokes)
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                
                val body = byteArray.toRequestBody("image/png".toMediaType())
                
                val response = repository.uploadDrawing(body)
                if (response.isSuccessful && response.body() != null) {
                    val resultText = response.body()!!.text
                    _inputText.value = resultText
                    evaluateInput(resultText, "livescribe")
                    clearDrawing()
                } else {
                    android.util.Log.e("PracticeViewModel", "OCR failed: ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Error in handleSubmitDrawing", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun clearDrawing() {
        _strokes.value = emptyList()
        _currentStroke.value = emptyList()
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

    fun playTTS() {
        val currentState = _uiState.value
        if (currentState is PracticeUiState.Success) {
            viewModelScope.launch {
                ttsManager.playText(currentState.sentence.example)
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
                val response = repository.getFirstVocabulary(collectionId, userId)
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
                val response = repository.checkText(
                    userId = currentUserId,
                    correctText = currentState.sentence.example,
                    inputText = text
                )
                if (response.isSuccessful) {
                    val result = response.body()
                    _checkResult.value = result
                    val correct = result?.exactMatch?.normalizedMatch == true || result?.isCorrect == true
                    if (correct) {
                        _isSuccess.value = true
                        repository.updateProgress(
                            userId = currentUserId,
                            collectionId = currentState.collectionId,
                            page = currentState.sentence.page ?: 1,
                            inputMethod = method
                        )
                        // Auto-advance to next sentence after a short delay to see result
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(500)
                            nextSentence()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
