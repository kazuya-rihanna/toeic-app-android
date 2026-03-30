package com.example.myapplication.ui.practice

import android.content.Context
import android.media.MediaPlayer
import com.example.myapplication.domain.ToeicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            start()
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
