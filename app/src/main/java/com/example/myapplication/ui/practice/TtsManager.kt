package com.example.myapplication.ui.practice

import android.content.Context
import android.media.MediaPlayer
import com.example.myapplication.domain.ToeicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToeicRepository
) {
    private var mediaPlayer: MediaPlayer? = null

    suspend fun playText(text: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = repository.getTTS(text)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@withContext
                    val tempFile = File(context.cacheDir, "tts_audio.mp3")
                    FileOutputStream(tempFile).use { it.write(body.bytes()) }
                    
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            mediaPlayer?.release()
                            val player = MediaPlayer()
                            mediaPlayer = player
                            
                            player.apply {
                                setDataSource(tempFile.absolutePath)
                                setOnCompletionListener { 
                                    it.release()
                                    if (mediaPlayer == it) mediaPlayer = null
                                    if (continuation.isActive) continuation.resume(Unit) 
                                }
                                setOnErrorListener { it, _, _ ->
                                    it.release()
                                    if (mediaPlayer == it) mediaPlayer = null
                                    if (continuation.isActive) continuation.resumeWithException(Exception("MediaPlayer error"))
                                    true
                                }
                                try {
                                    prepare()
                                    start()
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
