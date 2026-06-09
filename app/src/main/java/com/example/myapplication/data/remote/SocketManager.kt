package com.example.myapplication.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketManager @Inject constructor() {

    private val _ocrText = MutableStateFlow<String?>(null)
    val ocrText = _ocrText.asStateFlow()

    private val _ocrStatus = MutableStateFlow("idle")
    val ocrStatus = _ocrStatus.asStateFlow()

    private val _buttonPress = MutableStateFlow<String?>(null)
    val buttonPress = _buttonPress.asStateFlow()

    private val _isOcrConnected = MutableStateFlow(false)
    val isOcrConnected = _isOcrConnected.asStateFlow()

    private var ocrSocket: Socket? = null
    private var dropboxSocket: Socket? = null
    private var buttonSocket: Socket? = null

    fun connect() {
        if (ocrSocket != null && ocrSocket?.connected() == true) return

        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket")
                reconnection = true
            }

            ocrSocket = IO.socket("https://excalidraw-ocr-591803230494.us-central1.run.app", options)
            dropboxSocket = IO.socket("https://dropbox-livescribe-quicksend-591803230494.us-central1.run.app", options)
            buttonSocket = IO.socket("https://button-mapper-591803230494.us-central1.run.app", options)

            ocrSocket?.on("ocr_result") { args ->
                try {
                    val firstArg = args?.getOrNull(0)
                    val data = if (firstArg is JSONObject) {
                        firstArg
                    } else {
                        JSONObject(firstArg?.toString() ?: "{}")
                    }
                    _ocrText.value = data.optString("text")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            dropboxSocket?.on("status_update") { args ->
                try {
                    val firstArg = args?.getOrNull(0)
                    val data = if (firstArg is JSONObject) {
                        firstArg
                    } else {
                        JSONObject(firstArg?.toString() ?: "{}")
                    }
                    _ocrStatus.value = data.optString("status")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            buttonSocket?.on("flic_event") { args ->
                try {
                    val firstArg = args?.getOrNull(0)
                    val data = if (firstArg is JSONObject) {
                        firstArg
                    } else {
                        JSONObject(firstArg?.toString() ?: "{}")
                    }
                    _buttonPress.value = data.optString("action")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            ocrSocket?.on(Socket.EVENT_CONNECT) { _isOcrConnected.value = true }
            ocrSocket?.on(Socket.EVENT_DISCONNECT) { _isOcrConnected.value = false }
            ocrSocket?.on(Socket.EVENT_CONNECT_ERROR) { _isOcrConnected.value = false }

            ocrSocket?.connect()
            dropboxSocket?.connect()
            buttonSocket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            _isOcrConnected.value = false
        }
    }

    fun disconnect() {
        ocrSocket?.disconnect()
        dropboxSocket?.disconnect()
        buttonSocket?.disconnect()
    }

    fun resetOcrText() {
        _ocrText.value = null
    }

    fun resetButtonPress() {
        _buttonPress.value = null
    }
}
