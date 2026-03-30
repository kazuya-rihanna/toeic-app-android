package com.example.myapplication.data.pen

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredPen(
    val name: String,
    val address: String,
    val sppAddress: String,
    val rssi: Int,
    val scanRecordHex: String = "",
    val isGenuine: Boolean = false
)

@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _foundPens = MutableStateFlow<List<DiscoveredPen>>(emptyList())
    val foundPens = _foundPens.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    
    private val _uniqueAddresses = mutableSetOf<String>()
    private val _totalUniqueDevices = MutableStateFlow(0)
    val totalUniqueDevices = _totalUniqueDevices.asStateFlow()
    
    private val _status = MutableStateFlow<String?>("Ready to scan")
    val status = _status.asStateFlow()

    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val bleScanner: android.bluetooth.le.BluetoothLeScanner? 
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address
            
            if (_uniqueAddresses.add(address)) {
                _totalUniqueDevices.value = _uniqueAddresses.size
                _status.value = "Scanning... (Found ${_totalUniqueDevices.value} unique)"
            }
            
            val scanRecord = result.scanRecord?.bytes
            val deviceName = device.name ?: result.scanRecord?.deviceName ?: "Unknown"
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString().lowercase() } ?: emptyList()
            
            // Log every device for developer, but don't update status every time to save UI thread
            android.util.Log.d("BleScanner", "Discovered device: $deviceName ($address) RSSI: ${result.rssi}")

            // Efficient fallback identification for NeoSmartpen models
            // Avoids redundant PenCtrl singleton access which can disrupt connection state
            val nameUpper = deviceName.uppercase()
            val hasMatchName = nameUpper.startsWith("NWP-") || 
                              nameUpper.contains("NEOSMARTPEN") || 
                              nameUpper.contains("SMARTPEN") ||
                              nameUpper.contains("NEO") ||
                              nameUpper.startsWith("PEN")
            
            val hasMatchUuid = serviceUuids.any { 
                it.contains("1901") || it.contains("6e400001") || it.contains("180a")
            }

            val isNeoPen = hasMatchName || hasMatchUuid

            // NO FILTER: Show everything to find the real pen
            val addrUpper = address.uppercase()
            val bytes = result.scanRecord?.bytes ?: byteArrayOf()
            
            // NeoLAB Manufacturer ID is 0x0113. In BLE it might appear as 13 01
            var hasNeoLabId = false
            for (i in 0 until bytes.size - 1) {
                if (bytes[i].toInt() == 0x13 && bytes[i+1].toInt() == 0x01) {
                    hasNeoLabId = true
                    break
                }
            }

            val isGenuine = addrUpper.startsWith("9C:7B:D2") || 
                           addrUpper.startsWith("00:07:80") ||
                           addrUpper.startsWith("9C:7B:D1") ||
                           hasNeoLabId
            
            // RELAXED FILTER (V36):
            // We now know that R1 pens regenerate Static Random MAC Addresses, so strict OUI/UUID filters blocked the real pen.
            // We will show ANY device that matches "Neosmartpen" or "NWP".
            val shouldShow = isNeoPen || hasNeoLabId

            if (shouldShow) {
                val currentList = _foundPens.value
                if (currentList.none { it.address == address }) {
                    val sppAddress = try {
                        kr.neolab.sdk.util.UuidUtil.changeAddressFromLeToSpp(bytes) ?: address
                    } catch (e: Exception) {
                        address
                    }
                    val hex = bytes.take(16).joinToString("") { "%02X ".format(it) }
                    _foundPens.value = currentList + DiscoveredPen(deviceName, address, sppAddress, result.rssi, hex, isGenuine)
                }
            }
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                device?.let { 
                    val address = it.address
                    val name = it.name ?: "Unknown Classic"
                    val currentList = _foundPens.value
                    if (currentList.none { p -> p.address == address }) {
                        val isGenuine = address.uppercase().startsWith("9C:7B:D2") || 
                                       address.uppercase().startsWith("00:07:80")
                        _foundPens.value = currentList + DiscoveredPen(name, address, address, -1, "CLASSIC", isGenuine)
                    }
                }
            }
        }
    }

    fun checkBondedDevices() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        val bondedDevices = adapter?.bondedDevices ?: emptySet()
        
        val currentList = _foundPens.value.toMutableList()
        bondedDevices.forEach { device ->
            if (currentList.none { it.address == device.address }) {
                val name = device.name ?: "Bonded Device"
                val isGenuine = device.address.uppercase().startsWith("9C:7B:D2") || 
                               device.address.uppercase().startsWith("00:07:80")
                if (name.contains("Neo", ignoreCase = true) || isGenuine) {
                    currentList.add(DiscoveredPen(name, device.address, device.address, -1, "BONDED", isGenuine))
                }
            }
        }
        _foundPens.value = currentList
    }

    fun startClassicDiscovery() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        
        // Register receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(classicReceiver, filter)
        
        if (adapter?.isDiscovering == true) {
            adapter.cancelDiscovery()
        }
        adapter?.startDiscovery()
        _status.value = "Scanning (Classic)..."
    }

    suspend fun bondDevice(address: String): Boolean = suspendCancellableCoroutine { continuation ->
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        val device = adapter.getRemoteDevice(address)
        
        // If already bonded, succeed immediately
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val d = if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (d?.address == address) {
                        when (d.bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                try { context.unregisterReceiver(this) } catch (e: Exception) {}
                                if (continuation.isActive) continuation.resume(true)
                            }
                            BluetoothDevice.BOND_NONE -> {
                                try { context.unregisterReceiver(this) } catch (e: Exception) {}
                                if (continuation.isActive) continuation.resume(false)
                            }
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        
        if (device.bondState != BluetoothDevice.BOND_BONDING) {
            val initiated = try {
                device.createBond()
            } catch (e: SecurityException) {
                false
            }
            
            if (!initiated) {
                try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
                if (continuation.isActive) continuation.resume(false)
            }
        }
        
        continuation.invokeOnCancellation {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }

    private var preGatt: BluetoothGatt? = null

    suspend fun preloadLeConnection(address: String): Boolean = suspendCancellableCoroutine { continuation ->
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        val device = adapter.getRemoteDevice(address)
        
        try {
            preGatt?.close()
        } catch (e: Exception) {}
        preGatt = null

        var resumed = false
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    android.util.Log.i("BleScanner", "Pre-GATT connected via TRANSPORT_LE!")
                    if (!resumed) { resumed = true; if (continuation.isActive) continuation.resume(true) }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    android.util.Log.e("BleScanner", "Pre-GATT disconnected with status $status")
                    if (!resumed) { resumed = true; if (continuation.isActive) continuation.resume(false) }
                } else if (status != BluetoothGatt.GATT_SUCCESS) {
                    android.util.Log.e("BleScanner", "Pre-GATT failed with status $status")
                    if (!resumed) { resumed = true; if (continuation.isActive) continuation.resume(false) }
                }
            }
        }
        
        android.util.Log.i("BleScanner", "Initiating Pre-GATT with TRANSPORT_LE...")
        try {
            preGatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        } catch (e: SecurityException) {
            if (!resumed) { resumed = true; if (continuation.isActive) continuation.resume(false) }
        }
        
        continuation.invokeOnCancellation {
            try { preGatt?.close() } catch (e: Exception) {}
            preGatt = null
        }
    }

    fun startScan() {
        android.util.Log.i("BleScanner", "startScan() called")
        val scanner = bleScanner
        if (scanner == null) {
            val errorMsg = if (bluetoothAdapter == null) "Bluetooth Hardware not available" else "Bluetooth is turned off"
            android.util.Log.e("BleScanner", errorMsg)
            _status.value = errorMsg
            return
        }

        // Diagnostic: Check if Location is enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        val isLocationEnabled = isGpsEnabled || isNetworkEnabled
        
        if (!isLocationEnabled) {
            android.util.Log.w("BleScanner", "Location Services are disabled. BLE scanning will fail on most Android devices.")
            _status.value = "Error: System Location Toggle is OFF"
        } else {
            _status.value = "Scanning (Unique found: ${_totalUniqueDevices.value})..."
        }

        if (!_isScanning.value) {
            _isScanning.value = true
            _foundPens.value = emptyList()
            _uniqueAddresses.clear()
            _totalUniqueDevices.value = 0
            
            try {
                // Low latency for better responsiveness during discovery
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                // Universal scan (no filters) to troubleshoot discovery
                scanner.startScan(null, settings, scanCallback)
                android.util.Log.i("BleScanner", "Universal scan started successfully")
            } catch (e: SecurityException) {
                android.util.Log.e("BleScanner", "Permission denied", e)
                _status.value = "Permission Denied"
                _isScanning.value = false
            } catch (e: Exception) {
                android.util.Log.e("BleScanner", "Failed to start scan", e)
                _status.value = "Scan Error: ${e.message}"
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            // Log or handle
        }
    }
}
