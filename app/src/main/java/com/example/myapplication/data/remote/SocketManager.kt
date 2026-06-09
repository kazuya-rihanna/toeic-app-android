package com.example.myapplication.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val socketScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var ocrSocket: Socket? = null
    private var dropboxSocket: Socket? = null
    private var buttonSocket: Socket? = null

    fun connect() {
        socketScope.launch {
            if (ocrSocket == null) {
                android.util.Log.i("SocketManager", "Initializing sockets and registering listeners...")
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
                            val text = when (firstArg) {
                                is JSONObject -> firstArg.optString("text")
                                is String -> firstArg
                                else -> {
                                    val str = firstArg?.toString()
                                    if (str != null && str.startsWith("{")) {
                                        try {
                                            JSONObject(str).optString("text")
                                        } catch (e: Exception) {
                                            str
                                        }
                                    } else {
                                        str
                                    }
                                }
                            }
                            _ocrText.value = text
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    dropboxSocket?.on("status_update") { args ->
                        try {
                            val firstArg = args?.getOrNull(0)
                            val status = when (firstArg) {
                                is JSONObject -> firstArg.optString("status")
                                is String -> firstArg
                                else -> {
                                    val str = firstArg?.toString()
                                    if (str != null && str.startsWith("{")) {
                                        try {
                                            JSONObject(str).optString("status")
                                        } catch (e: Exception) {
                                            str
                                        }
                                    } else {
                                        str ?: "idle"
                                    }
                                }
                            }
                            _ocrStatus.value = status
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    buttonSocket?.on("flic_event") { args ->
                        try {
                            val firstArg = args?.getOrNull(0)
                            val action = when (firstArg) {
                                is JSONObject -> firstArg.optString("action")
                                is String -> firstArg
                                else -> {
                                    val str = firstArg?.toString()
                                    if (str != null && str.startsWith("{")) {
                                        try {
                                            JSONObject(str).optString("action")
                                        } catch (e: Exception) {
                                            str
                                        }
                                    } else {
                                        str
                                    }
                                }
                            }
                            _buttonPress.value = action
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    ocrSocket?.on(Socket.EVENT_CONNECT) { _isOcrConnected.value = true }
                    ocrSocket?.on(Socket.EVENT_DISCONNECT) { _isOcrConnected.value = false }
                    ocrSocket?.on(Socket.EVENT_CONNECT_ERROR) { _isOcrConnected.value = false }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _isOcrConnected.value = false
                }
            }

            try {
                android.util.Log.d("SocketManager", "Connecting sockets if not connected...")
                if (ocrSocket?.connected() == false) {
                    ocrSocket?.connect()
                }
                if (dropboxSocket?.connected() == false) {
                    dropboxSocket?.connect()
                }
                if (buttonSocket?.connected() == false) {
                    buttonSocket?.connect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        socketScope.launch {
            android.util.Log.i("SocketManager", "Disconnecting sockets...")
            ocrSocket?.disconnect()
            dropboxSocket?.disconnect()
            buttonSocket?.disconnect()
        }
    }

    fun resetOcrText() {
        _ocrText.value = null
    }

    fun resetButtonPress() {
        _buttonPress.value = null
    }
}
