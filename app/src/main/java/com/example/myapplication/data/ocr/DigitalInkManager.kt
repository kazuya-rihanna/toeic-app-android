package com.example.myapplication.data.ocr

import android.util.Log
import com.example.myapplication.ui.practice.PenStroke
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.common.RecognitionResult
import kr.neolab.sdk.ink.structure.Dot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class DigitalInkManager @Inject constructor() {

    private val tag = "DigitalInkManager"
    private var model: DigitalInkRecognitionModel? = null
    private var recognizer: DigitalInkRecognizer? = null
    private val remoteModelManager = RemoteModelManager.getInstance()

    init {
        initModel()
    }

    private fun initModel() {
        try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
            if (modelIdentifier == null) {
                Log.e(tag, "Language tag en-US not supported by Digital Ink")
                return
            }
            val targetModel = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            model = targetModel

            // Check and trigger download asynchronously in background
            remoteModelManager.isModelDownloaded(targetModel)
                .addOnSuccessListener { downloaded ->
                    if (downloaded) {
                        Log.d(tag, "ML Kit en-US model is already downloaded.")
                        recognizer = DigitalInkRecognition.getClient(
                            DigitalInkRecognizerOptions.builder(targetModel).build()
                        )
                    } else {
                        Log.d(tag, "ML Kit en-US model downloading...")
                        remoteModelManager.download(targetModel, DownloadConditions.Builder().build())
                            .addOnSuccessListener {
                                Log.d(tag, "ML Kit en-US model download complete.")
                                recognizer = DigitalInkRecognition.getClient(
                                    DigitalInkRecognizerOptions.builder(targetModel).build()
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e(tag, "ML Kit en-US model download failed: ${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "Error checking ML Kit model download: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Digital Ink Model: ${e.message}", e)
        }
    }

    private suspend fun getOrCreateRecognizer(): DigitalInkRecognizer = withContext(Dispatchers.IO) {
        recognizer?.let { return@withContext it }

        val targetModel = model ?: run {
            val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")
                ?: throw IllegalStateException("en-US language tag not available for Digital Ink")
            val m = DigitalInkRecognitionModel.builder(id).build()
            model = m
            m
        }

        // Suspend until model is downloaded if needed
        val isDownloaded = suspendCancellableCoroutine<Boolean> { cont ->
            remoteModelManager.isModelDownloaded(targetModel)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

        if (!isDownloaded) {
            Log.d(tag, "Awaiting ML Kit model download on demand...")
            suspendCancellableCoroutine<Unit> { cont ->
                remoteModelManager.download(targetModel, DownloadConditions.Builder().build())
                    .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                    .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
            }
        }

        val client = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(targetModel).build()
        )
        recognizer = client
        client
    }

    suspend fun recognizeStrokes(strokes: List<PenStroke>, orientation: Int): String {
        if (strokes.isEmpty()) return ""

        val client = getOrCreateRecognizer()

        // 1. Calculate transformed coordinates and bounding box
        fun getTransformedCoords(dot: Dot): Pair<Float, Float> {
            return when (orientation) {
                1 -> Pair(dot.y, -dot.x)
                2 -> Pair(-dot.x, -dot.y)
                3 -> Pair(-dot.y, dot.x)
                else -> Pair(dot.x, dot.y)
            }
        }

        val allDots = strokes.flatMap { it.dots }
        if (allDots.isEmpty()) return ""

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        allDots.forEach { dot ->
            val (tx, ty) = getTransformedCoords(dot)
            if (tx < minX) minX = tx
            if (ty < minY) minY = ty
        }

        // 2. Build ML Kit Ink
        val inkBuilder = Ink.builder()
        val margin = 10f // keep coordinates strictly non-negative
        for (stroke in strokes) {
            if (stroke.dots.isEmpty()) continue
            val strokeBuilder = Ink.Stroke.builder()
            for (dot in stroke.dots) {
                val (tx, ty) = getTransformedCoords(dot)
                val nx = (tx - minX) + margin
                val ny = (ty - minY) + margin
                if (dot.timestamp > 0) {
                    strokeBuilder.addPoint(Ink.Point.create(nx, ny, dot.timestamp))
                } else {
                    strokeBuilder.addPoint(Ink.Point.create(nx, ny))
                }
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }

        val ink = inkBuilder.build()

        // 3. Recognize
        return suspendCancellableCoroutine { continuation ->
            client.recognize(ink)
                .addOnSuccessListener { result: RecognitionResult ->
                    val topCandidate = result.candidates.firstOrNull()?.text ?: ""
                    Log.d(tag, "ML Kit recognized: '$topCandidate' (candidates=${result.candidates.size})")
                    if (continuation.isActive) continuation.resume(topCandidate)
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "ML Kit recognition error: ${e.message}", e)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
        }
    }

    fun close() {
        try {
            recognizer?.close()
            recognizer = null
        } catch (_: Exception) {}
    }
}
