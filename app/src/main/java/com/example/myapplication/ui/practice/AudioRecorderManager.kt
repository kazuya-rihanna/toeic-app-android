package com.example.myapplication.ui.practice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var currentOutputFile: File? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel = _volumeLevel.asStateFlow()

    private var recordingJob: Job? = null
    
    @Volatile
    private var isRecordingFlag = false

    private val defaultMic = MicrophoneInfo(id = -1, name = "Default (Automatic)", type = -1)

    private val _availableMics = MutableStateFlow<List<MicrophoneInfo>>(listOf(defaultMic))
    val availableMics = _availableMics.asStateFlow()

    private val _selectedMic = MutableStateFlow<MicrophoneInfo>(defaultMic)
    val selectedMic = _selectedMic.asStateFlow()

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateAvailableMics()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateAvailableMics()
            }
        }
    } else null

    init {
        updateAvailableMics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
    }

    fun selectMicrophone(mic: MicrophoneInfo) {
        _selectedMic.value = mic
    }

    fun updateAvailableMics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val micList = devices.filter { device ->
                device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }.map { device ->
                val typeStr = when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Device"
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
                    else -> "Audio Input"
                }
                val productName = device.productName?.toString()?.takeIf { it.isNotEmpty() } ?: "Microphone"
                MicrophoneInfo(
                    id = device.id,
                    name = "$productName ($typeStr)",
                    type = device.type
                )
            }
            
            _availableMics.value = listOf(defaultMic) + micList
            
            // If selected mic is no longer available, fall back to default
            val currentSelected = _selectedMic.value
            if (currentSelected.id != -1 && micList.none { it.id == currentSelected.id }) {
                _selectedMic.value = defaultMic
            }
        } else {
            _availableMics.value = listOf(defaultMic)
        }
    }



    private var audioFocusRequest: Any? = null

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { }
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? android.media.AudioFocusRequest)?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }

    fun startRecording(): File? {
        if (isRecordingFlag) return null

        val outputFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.wav")
        currentOutputFile = outputFile

        val currentSelected = _selectedMic.value
        
        // Determine whether a Bluetooth microphone is selected
        val isBluetooth = if (currentSelected.id != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val targetDevice = devices.find { it.id == currentSelected.id }
            targetDevice?.let {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: false
        } else {
            false
        }

        // FORCE Android Audio Policy Engine to process these requests on the Main Thread.
        // Android 12+ silently drops routing requests made on background threads.
        runOnMainThread {
            requestAudioFocus()
            // CRITICAL FIX: Keep MODE_NORMAL instead of MODE_IN_COMMUNICATION!
            // Web browsers capture audio in MODE_NORMAL, which avoids disrupting A2DP links
            // and does not drop quality or break high-fidelity DACs (like FIIO BTR5/7).
            // We do NOT manually trigger SCO or setCommunicationDevice.
            // Android automatically connects/routes via setPreferredDevice on AudioRecord below.
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        // Setup strictly raw PCM AudioRecord parameters optimized for Whisper Speech-To-Text
        val sampleRate = if (isBluetooth) 16000 else 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION
    
    val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    if (minBufSize == AudioRecord.ERROR || minBufSize == AudioRecord.ERROR_BAD_VALUE) {
        cleanupBluetoothMic()
        abandonAudioFocus()
        return null
    }
    // WebRTC Fix: Massive buffer (10x minimum) to prevent dropped frames causing choppy audio
    val bufferSize = minBufSize * 10

    try {
        audioRecord = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        // WebRTC Fix: Apply Hardware Noise Suppression and Gain Control
        if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
            try {
                android.media.audiofx.AutomaticGainControl.create(audioRecord?.audioSessionId ?: 0)?.enabled = true
            } catch (e: Exception) {}
        }
        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
            try {
                android.media.audiofx.NoiseSuppressor.create(audioRecord?.audioSessionId ?: 0)?.enabled = true
            } catch (e: Exception) {}
        }

        // CRITICAL FIX: DO NOT call audioRecord?.preferredDevice = device automatically!
        // WebRTC avoids this because setting preferredDevice directly on the AudioRecord 
        // completely bypasses the AudioManager's global VOICE_COMMUNICATION routing policy.
        // HOWEVER, if the user explicitly selected a microphone, we apply it here.
        val currentSelected = _selectedMic.value
        if (currentSelected.id != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val targetDevice = devices.find { it.id == currentSelected.id }
            if (targetDevice != null) {
                val success = audioRecord?.setPreferredDevice(targetDevice)
                android.util.Log.d("AudioRecorderManager", "Set preferred device: ${targetDevice.productName}, success=$success")
            }
        }

        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            cleanupBluetoothMic()
            abandonAudioFocus()
            return null
        }
        
        isRecordingFlag = true
        startPcmReadingLoop(outputFile, sampleRate, bufferSize)

    } catch (e: SecurityException) {
        cleanupBluetoothMic()
        abandonAudioFocus()
        return null
    }

    return outputFile
}

private fun startPcmReadingLoop(outputFile: File, sampleRate: Int, bufferSize: Int) {
    recordingJob?.cancel()
    recordingJob = CoroutineScope(Dispatchers.IO).launch {
        var totalAudioLen: Long = 0
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(outputFile)
            writeWavHeader(fileOutputStream, totalAudioLen, sampleRate.toLong(), channels, byteRate.toLong())

            val data = ByteArray(bufferSize)
            while (isActive && isRecordingFlag) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    // Compute max amplitude manually and apply Software Gain for Whisper
                    var maxAmp = 0
                    for (i in 0 until read step 2) {
                        if (i + 1 < read) {
                            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                            
                            // WebRTC Fix: Software amplification (3.0x) because raw Android SCO is notoriously quiet
                            var amplified = (sample.toShort() * 3.0).toInt()
                            
                            // Hard limiter to prevent audio distortion/clipping
                            if (amplified > 32767) amplified = 32767
                            if (amplified < -32768) amplified = -32768
                            
                            // Write amplified bytes back to the raw buffer
                            data[i] = (amplified and 0xFF).toByte()
                            data[i + 1] = ((amplified shr 8) and 0xFF).toByte()

                            val absSample = kotlin.math.abs(amplified)
                            if (absSample > maxAmp) maxAmp = absSample
                        }
                    }
                    
                    // Write the amplified array to the output file!
                    fileOutputStream.write(data, 0, read)
                    totalAudioLen += read

                    // Normalize 16-bit PCM (max 32767) to 0.0f..1.0f
                    _volumeLevel.value = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
                } else {
                    delay(10)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
                fileOutputStream?.close()
                // Safely update WAV header lengths after closing stream
                updateWavHeaderLengths(outputFile, totalAudioLen)
            }
            _volumeLevel.value = 0f
        }
    }

    private fun writeWavHeader(out: java.io.OutputStream, totalAudioLen: Long, sampleRate: Long, channels: Int, byteRate: Long) {
        val totalDataLen = totalAudioLen + 36
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        out.write(header, 0, 44)
    }

    private fun updateWavHeaderLengths(file: File, totalAudioLen: Long) {
        try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            val totalDataLen = totalAudioLen + 36
            
            randomAccessFile.seek(4)
            randomAccessFile.write(byteArrayOf(
                (totalDataLen and 0xff).toByte(),
                ((totalDataLen shr 8) and 0xff).toByte(),
                ((totalDataLen shr 16) and 0xff).toByte(),
                ((totalDataLen shr 24) and 0xff).toByte()
            ))
            
            randomAccessFile.seek(40)
            randomAccessFile.write(byteArrayOf(
                (totalAudioLen and 0xff).toByte(),
                ((totalAudioLen shr 8) and 0xff).toByte(),
                ((totalAudioLen shr 16) and 0xff).toByte(),
                ((totalAudioLen shr 24) and 0xff).toByte()
            ))
            randomAccessFile.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            runBlocking(Dispatchers.Main) {
                block()
            }
        }
    }

    private fun cleanupBluetoothMic() {
        runOnMainThread {
            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            }
        }
    }

    fun stopRecording(): File? {
        isRecordingFlag = false
        _volumeLevel.value = 0f

        try {
            audioRecord?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        
        cleanupBluetoothMic()
        abandonAudioFocus()
        
        val result = currentOutputFile
        currentOutputFile = null
        
        // Brief sleep to allow the coroutine IO thread to finalize the .wav header
        try { Thread.sleep(150) } catch (e: Exception) {}
        
        return result
    }

    fun isRecording(): Boolean = isRecordingFlag
}

data class MicrophoneInfo(
    val id: Int,
    val name: String,
    val type: Int
)


