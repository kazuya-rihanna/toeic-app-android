package com.example.myapplication.ui.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
    private var audioTrack: AudioTrack? = null

    suspend fun playText(text: String) {
        withContext(Dispatchers.IO) {
            try {
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

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

    suspend fun playTextStream(text: String) {
        withContext(Dispatchers.IO) {
            try {
                // Stop any previous playback
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                val response = repository.getTTSStream(text)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@withContext
                    val sampleRate = 24000
                    val minBufferSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    val bufferSize = maxOf(minBufferSize * 2, 4096)
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    audioTrack = track
                    track.play()

                    val tStart = System.currentTimeMillis()
                    var firstChunkLogged = false
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(2048)
                    var read: Int
                    try {
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            if (!firstChunkLogged) {
                                firstChunkLogged = true
                                val elapsed = System.currentTimeMillis() - tStart
                                android.util.Log.d("TtsManager", "⚡ AudioTrack started playback after ${elapsed}ms!")
                            }
                            track.write(buffer, 0, read)
                        }
                    } finally {
                        try {
                            track.stop()
                        } catch (_: Exception) {}
                        track.release()
                        if (audioTrack == track) audioTrack = null
                        try {
                            inputStream.close()
                        } catch (_: Exception) {}
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
        audioTrack?.release()
        audioTrack = null
    }
}
