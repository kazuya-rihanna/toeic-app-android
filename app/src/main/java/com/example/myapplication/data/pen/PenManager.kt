package com.example.myapplication.data.pen

import android.content.Context
import android.content.ContextWrapper
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import kr.neolab.sdk.pen.PenCtrl
import kr.neolab.sdk.pen.MultiPenCtrl
import kr.neolab.sdk.pen.penmsg.IPenDotListener
import kr.neolab.sdk.pen.penmsg.IPenMsgListener
import kr.neolab.sdk.pen.penmsg.PenMsg
import kr.neolab.sdk.pen.penmsg.PenMsgType
import kr.neolab.sdk.ink.structure.Dot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class PenManager @Inject constructor(
    @ApplicationContext private val context: Context
) : IPenMsgListener, IPenDotListener {

    private val prefs by lazy { context.getSharedPreferences("pen_prefs", Context.MODE_PRIVATE) }

    private val receiverContext by lazy {
        if (Build.VERSION.SDK_INT >= 34) {
            object : android.content.ContextWrapper(context) {
                override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
                    return super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                }
                override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
                    return super.registerReceiver(receiver, filter, flags)
                }
                override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, broadcastPermission: String?, scheduler: Handler?): Intent? {
                    return super.registerReceiver(receiver, filter, broadcastPermission, scheduler, Context.RECEIVER_NOT_EXPORTED)
                }
                override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, broadcastPermission: String?, scheduler: Handler?, flags: Int): Intent? {
                    return super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
                }
            }
        } else context
    }

    private val penCtrl: PenCtrl? by lazy {
        try {
            android.util.Log.d("PenManager", "Getting PenCtrl instance via lazy")
            val ctrl = PenCtrl.getInstance()
            ctrl.setContext(receiverContext)
            ctrl.setLeMode(true)
            ctrl.setListener(this)
            ctrl.setDotListener(this)
            ctrl
        } catch (e: Exception) {
            android.util.Log.e("PenManager", "Failed to initialize PenCtrl", e)
            null
        }
    }

    private val multiPenCtrl: MultiPenCtrl? by lazy {
        try {
            val ctrl = MultiPenCtrl.getInstance()
            ctrl.setContext(receiverContext)
            ctrl.setListener(this)
            ctrl.setDotListener(this)
            ctrl
        } catch (e: Exception) {
            null
        }
    }

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _lastDot = MutableStateFlow<Dot?>(null)
    val lastDot = _lastDot.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _penEvents = MutableStateFlow<List<String>>(emptyList())
    val penEvents = _penEvents.asStateFlow()

    private fun logEvent(msg: String) {
        val current = _penEvents.value.toMutableList()
        current.add("${System.currentTimeMillis() % 100000}: $msg")
        if (current.size > 50) current.removeAt(0)
        _penEvents.value = current
        android.util.Log.i("PenManager", "Event: $msg")
    }

    init {
        android.util.Log.d("PenManager", "PenManager init called")
        refreshListeners()
    }

    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting = _isAutoConnecting.asStateFlow()

    fun refreshListeners() {
        val h = this.hashCode()
        android.util.Log.i("PenManager", "Refreshing all SDK listeners... [$h]")
        logEvent("Refresh Triggered [Hash: $h]")
        try {
            val ctrl = PenCtrl.getInstance()
            ctrl.setContext(receiverContext)
            ctrl.setLeMode(true)
            ctrl.setListener(this)
            ctrl.setDotListener(this)
            ctrl.registerBroadcastBTDuplicate()
            
            multiPenCtrl?.let {
                it.setContext(receiverContext)
                it.setListener(this)
                it.setDotListener(this)
                it.registerBroadcastBTDuplicate()
            }
            
            // Proactive status requests
            ctrl.reqPenStatus()
            try { ctrl.reqSystemInfo() } catch(e: Exception) {}
            
            // Force data stream start commands
            try {
                ctrl.setAllowOfflineData(true)
                ctrl.reqOfflineDataList()
                ctrl.reqAddUsingNoteAll() // Tell SDK to allow all notes
                ctrl.reqPenStatus()
            } catch (e: Exception) {
                logEvent("Data start fail: ${e.message}")
            }
            
            logEvent("Refresh: Start commands sent.")
            _message.value = "Listeners set"
            dumpSdkStatus()
        } catch (e: Exception) {
            android.util.Log.e("PenManager", "Listener refresh failed", e)
            logEvent("Refresh error: ${e.message}")
        }
    }

    private fun dumpSdkStatus() {
        logEvent("Dumping SDK status...")
        try {
            val ctrl = PenCtrl.getInstance()
            if (ctrl == null) {
                logEvent("Error: PenCtrl.getInstance() is null")
                return
            }

            // Find the active Adt (Active Adapter)
            val btAdtField = try { 
                ctrl.javaClass.getDeclaredField("btAdt") 
            } catch (e: Exception) {
                ctrl.javaClass.getDeclaredFields().find { it.type.name.contains("IPenAdt") || it.name.contains("Adt") }
            }
            
            if (btAdtField == null) {
                logEvent("Error: Could not find Adt field in PenCtrl")
                return
            }
            
            btAdtField.isAccessible = true
            val adt = btAdtField.get(ctrl)
            logEvent("Active Adt: ${adt?.javaClass?.simpleName}")

            if (adt != null) {
                // Inspect BTLEAdt fields
                val adtClass = adt.javaClass
                val dotLField = adtClass.getDeclaredFields().find { it.name == "dotListener" }
                dotLField?.let {
                    it.isAccessible = true
                    val l = it.get(adt)
                    logEvent("Adt.dotListener: ${l?.javaClass?.name}")
                }

                // BTLEAdt -> mConnectionThread
                val connThreadField = try { adtClass.getDeclaredField("mConnectionThread") } catch(e: Exception) { null }
                connThreadField?.let {
                    it.isAccessible = true
                    val connThread = it.get(adt)
                    logEvent("ConnThread: ${connThread?.javaClass?.simpleName}")

                    // ConnectedThread -> getPacketProcessor()
                    val getProcMethod = connThread?.javaClass?.methods?.find { it.name == "getPacketProcessor" }
                    val processor = getProcMethod?.invoke(connThread)
                    logEvent("Processor: ${processor?.javaClass?.simpleName}")

                    if (processor != null) {
                        val pClass = processor.javaClass
                        val sb = StringBuilder("Proc: ")
                        val pFields = listOf(
                            "isReceivedPageIdChange", "sectionId", "ownerId", "noteId", "pageId",
                            "isHoverMode", "isDotHover", "isPenAuthenticated", "receiveProtocolVer", "FW_VER"
                        )

                        pFields.forEach { name ->
                            try {
                                val f = pClass.getDeclaredField(name)
                                f.isAccessible = true
                                val v = f.get(processor)
                                sb.append("$name=$v; ")
                                if (name == "isReceivedPageIdChange" && v == false) {
                                    f.set(processor, true)
                                }
                            } catch (e: Exception) {
                                try {
                                    val f = pClass.superclass?.getDeclaredField(name)
                                    f?.isAccessible = true
                                    val v = f?.get(processor)
                                    sb.append("$name(sup)=$v; ")
                                } catch (e2: Exception) {}
                            }
                        }
                        logEvent(sb.toString())

                        // Inspect FilterForPaper
                        try {
                            val filterField = pClass.getDeclaredField("dotFilterPaper")
                            filterField.isAccessible = true
                            val filter = filterField.get(processor)
                            if (filter != null) {
                                val fClass = filter.javaClass
                                val fNoteId = fClass.getDeclaredField("mNoteId").apply { isAccessible = true }.get(filter)
                                val fPageId = fClass.getDeclaredField("mPageId").apply { isAccessible = true }.get(filter)
                                logEvent("Filter: note=$fNoteId, page=$fPageId")
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        } catch (e: Throwable) {
            logEvent("Dump Crash: ${e.message}")
        }
    }

    override fun onReceiveMessage(penAddress: String?, penMsg: PenMsg?) {
        if (penMsg == null) return
        val type = penMsg.penMsgType
        
        // Protocol and FW version are often in the JSON content for certain messages
        val content = try { penMsg.contentByJSONObject } catch(e: Exception) { null }
        val raw = content?.toString() ?: "n/a"
        
        // Extract battery if present (check multiple potential keys from JsonTag)
        val battery = content?.optInt("battery", -1).let { 
            if (it == -1) content?.optInt("Battery", -1) else it
        }.let {
            if (it == -1) content?.optInt("battery_status", -1) else it
        }.let {
            if (it == -1) content?.optInt("batt", -1) else it
        }.let {
            if (it == -1) content?.optInt("battery_per", -1) else it
        } ?: -1

        if (battery != -1) {
            // SDK quirk: 128 sometimes means 100% charging
            val displayBattery = if (battery == 128) 100 else battery
            _batteryLevel.value = displayBattery
        }
        
        logEvent("Msg: t=$type, raw=$raw")
        
        // Diagnostic: Show raw code and type
        val typeStr = when(type) {
            PenMsgType.PEN_CONNECTION_SUCCESS -> "SUCCESS (2)"
            PenMsgType.PEN_CONNECTION_FAILURE -> "FAILURE (3)"
            PenMsgType.PEN_DISCONNECTED -> "DISCONNECTED (4)"
            PenMsgType.PEN_AUTHORIZED -> "AUTHORIZED (5)"
            PenMsgType.PASSWORD_REQUEST -> "PASSWORD_REQ (81)"
            PenMsgType.PEN_STATUS -> "STATUS (17)"
            118 -> "ERR_PAGE_MISSING (118)"
            else -> "OTHER ($type)"
        }
        _message.value = "Type: $typeStr"

        when (type) {
            PenMsgType.PEN_CONNECTION_SUCCESS -> {
                _isConnected.value = true
                _message.value = "Connected (Success)"
                logEvent("Connection Success - Waiting for Auth...")
            }
            PenMsgType.PEN_CONNECTION_FAILURE -> {
                _isConnected.value = false
                _message.value = "Failed (Code: $typeStr)"
            }
            PenMsgType.PEN_DISCONNECTED -> {
                logEvent("PEN DISCONNECTED (RawMsg=$raw)")
                _isConnected.value = false
                _batteryLevel.value = null
                _message.value = "Disconnected"
            }
            PenMsgType.PEN_AUTHORIZED -> {
                logEvent("PEN AUTHORIZED (Type=$type)")
                _isConnected.value = true
                managerScope.launch {
                    try {
                        penCtrl?.inputPassword("")
                    } catch (e: Exception) {}
                    
                    // Delay slightly to ensure ConnectedThread is fully initialized
                    delay(500)
                    refreshListeners()
                }
            }
            16 -> { // Authentication/Bonding/Protocol Info
                logEvent("AUTH/SECURITY MSG (RawMsg=$raw)")
            }
            PenMsgType.PASSWORD_REQUEST -> {
                logEvent("PASSWORD REQUESTED! Pen is locked.")
            }
            130 -> { // SYSTEM_INFO_VALUE
                logEvent("System Info (130) received")
            }
            else -> {
                logEvent("PenMsg: type=$type, raw=$raw")
            }
        }
    }

    override fun onReceiveDot(macAddress: String?, dot: Dot?) {
        if (dot == null) return
        val copy = Dot(dot)
        // Full ID dump for debugging area detection
        android.util.Log.d("PenManager", "DOT: section=${copy.sectionId}, owner=${copy.ownerId}, book=${copy.noteId}, page=${copy.pageId}, x=${copy.x}, y=${copy.y}, type=${copy.dotType}")
        logEvent("DOT sec=${copy.sectionId} own=${copy.ownerId} bk=${copy.noteId} x=${copy.x.toInt()} y=${copy.y.toInt()} t=${copy.dotType}")
        _lastDot.value = copy
    }

    fun saveLastConnected(sppAddress: String, leAddress: String) {
        prefs.edit().apply {
            putString("last_spp", sppAddress)
            putString("last_le", leAddress)
            apply()
        }
        logEvent("Saved last pen: $leAddress")
    }

    fun getLastConnected(): Pair<String, String>? {
        val spp = prefs.getString("last_spp", null)
        val le = prefs.getString("last_le", null)
        return if (spp != null && le != null) spp to le else null
    }

    suspend fun connect(sppAddress: String, leAddress: String) {
        saveLastConnected(sppAddress, leAddress)
        try {
            android.util.Log.i("PenManager", "Attempting to connect to SPP: $sppAddress LE: $leAddress")
            _message.value = "Connecting via BLE..."
            
            if (penCtrl == null) {
                _message.value = "SDK not initialized"
                return
            }
            
            val ctrl = penCtrl!!
            
            val upperSpp = sppAddress.uppercase()
            val upperLe = leAddress.uppercase()
            
            try {
                android.util.Log.i("PenManager", "Executing Reflection-based Double-MAC: SPP $upperSpp / LE $upperLe")
                _message.value = "Connecting (VER_5)..."
                
                // Ensure LE mode is active
                ctrl.setLeMode(true)
                
                // Micro-stabilization for SDK state machine (300ms)
                Thread.sleep(300)
                
                // Reflected call for NASDK 2.12 Double-MAC
                val enumClass = Class.forName("kr.neolab.sdk.pen.bluetooth.BTLEAdt\$UUID_VER")
                val ver5 = java.lang.Enum.valueOf(enumClass as Class<out Enum<*>>, "VER_5")
                
                val method = ctrl.javaClass.getMethod(
                    "connect",
                    String::class.java,
                    String::class.java,
                    enumClass,
                    Short::class.javaPrimitiveType,
                    String::class.java
                )
                method.invoke(ctrl, upperSpp, upperLe, ver5, 4353.toShort(), "2.12")
                
            } catch (e: Throwable) {
                android.util.Log.e("PenManager", "Reflection connect failed, trying fallback", e)
                _message.value = "Connecting (Fallback)..."
                ctrl.connect(upperSpp, upperLe)
            }
        } catch (e: Throwable) {
            android.util.Log.e("PenManager", "Critical error during connect", e)
            _message.value = "Connect Error: ${e.message}"
        }
    }

    fun disconnect() {
        penCtrl?.disconnect()
    }

    fun clear() {
        penCtrl?.clear()
    }

    fun getConnectMethods(): List<String> {
        val ctrl = penCtrl ?: return listOf("PenCtrl is NULL!")
        val methods = ctrl.javaClass.methods
        val connectMethods = methods.filter { it.name.contains("connect", ignoreCase = true) }
        return if (connectMethods.isEmpty()) {
            listOf("No connect methods found?!")
        } else {
            connectMethods.map { m ->
                val params = m.parameterTypes.joinToString(",") { it.simpleName }
                "- ${m.name}($params)"
            }
        }
    }
    private fun refreshDeviceCache(address: String) {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val adapter = bluetoothManager?.adapter
            val device = adapter?.getRemoteDevice(address)
            
            // Connect dummy GATT to call hidden refresh() method
            val gatt = device?.connectGatt(context, false, object : android.bluetooth.BluetoothGattCallback() {})
            val method = gatt?.javaClass?.getMethod("refresh")
            if (method != null && gatt != null) {
                val success = method.invoke(gatt) as Boolean
                android.util.Log.i("PenManager", "GATT Cache Refresh initiated: $success")
            }
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            android.util.Log.w("PenManager", "GATT Cache Refresh failed: ${e.message}")
        }
    }
}

/**
 * Intercepts registerReceiver calls from the SDK to inject the RECEIVER_EXPORTED flag
 * required by Android 14 (API 34) when running on newer devices.
 */
class ExportedReceiverContext(base: Context) : ContextWrapper(base) {
    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            super.registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            super.registerReceiver(receiver, filter, flags or RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter, flags)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter?,
        broadcastPermission: String?,
        scheduler: Handler?,
        flags: Int
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags or RECEIVER_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
        }
    }
}
