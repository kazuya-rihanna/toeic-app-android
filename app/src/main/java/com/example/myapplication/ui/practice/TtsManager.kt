package com.example.myapplication.ui.practice

import android.content.Context
import android.media.MediaPlayer
import com.example.myapplication.domain.ToeicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToeicRepository
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preloadJob: Job? = null

    @Volatile
    private var cachedText: String? = null
    private val cacheFile by lazy { File(context.cacheDir, "current_page_tts.mp3") }

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Preloads TTS audio for the current page in the background.
     * Overwrites a single cache file so storage does not grow.
     */
    fun preloadText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (cachedText == trimmed && cacheFile.exists() && cacheFile.length() > 0L) {
            android.util.Log.d("TtsManager", "TTS already cached for: '$trimmed'")
            return
        }

        preloadJob?.cancel()
        preloadJob = coroutineScope.launch {
            fetchAndCache(trimmed)
        }
    }

    private suspend fun fetchAndCache(text: String): Boolean {
        return try {
            android.util.Log.d("TtsManager", "Fetching TTS audio in background for: '$text'")
            val response = repository.getTTS(text)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val tempFile = File(context.cacheDir, "current_page_tts.tmp")
                    FileOutputStream(tempFile).use { it.write(body.bytes()) }
                    if (cacheFile.exists()) cacheFile.delete()
                    if (!tempFile.renameTo(cacheFile)) {
                        tempFile.copyTo(cacheFile, overwrite = true)
                        tempFile.delete()
                    }
                    cachedText = text
                    android.util.Log.d("TtsManager", "✅ TTS preloaded successfully: '$text' (${cacheFile.length()} bytes)")
                    return true
                }
            }
            false
        } catch (e: CancellationException) {
            android.util.Log.d("TtsManager", "TTS preload cancelled for: '$text'")
            false
        } catch (e: Exception) {
            android.util.Log.w("TtsManager", "TTS preload failed: ${e.message}")
            false
        }
    }

    suspend fun playText(text: String) {
        val trimmed = text.trim()
        withContext(Dispatchers.IO) {
            try {
                // If preloading is currently active, wait for it
                if (preloadJob?.isActive == true) {
                    preloadJob?.join()
                }

                // If not cached yet or text differs, fetch now
                if (cachedText != trimmed || !cacheFile.exists() || cacheFile.length() == 0L) {
                    val success = fetchAndCache(trimmed)
                    if (!success) {
                        android.util.Log.e("TtsManager", "Failed to fetch audio for: '$trimmed'")
                        return@withContext
                    }
                }

                // Play from local cache file with 0-latency
                withContext(Dispatchers.Main) {
                    stop()
                    suspendCancellableCoroutine<Unit> { continuation ->
                        val player = MediaPlayer()
                        mediaPlayer = player

                        player.apply {
                            setDataSource(cacheFile.absolutePath)
                            setOnCompletionListener {
                                release()
                                if (mediaPlayer == it) mediaPlayer = null
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            setOnErrorListener { it, _, _ ->
                                release()
                                if (mediaPlayer == it) mediaPlayer = null
                                if (continuation.isActive) continuation.resumeWithException(Exception("MediaPlayer error"))
                                true
                            }
                            try {
                                prepare()
                                start()
                                android.util.Log.d("TtsManager", "▶️ Started playing cached TTS immediately")
                            } catch (e: Exception) {
                                release()
                                if (mediaPlayer == this) mediaPlayer = null
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }

                        continuation.invokeOnCancellation {
                            player.release()
                            if (mediaPlayer == player) mediaPlayer = null
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            mediaPlayer = null
        }
    }

    fun release() {
        preloadJob?.cancel()
        stop()
    }
}

