package com.example.myapplication.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.myapplication.data.pen.BleScanner
import com.example.myapplication.data.pen.PenManager
import com.example.myapplication.data.pen.DiscoveredPen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val penManager: PenManager,
    private val bleScanner: BleScanner
) : ViewModel() {

    val foundPens = bleScanner.foundPens
    val isConnected = penManager.isConnected
    val message = penManager.message
    val scanStatus = bleScanner.status
    val totalDevicesFound = bleScanner.totalUniqueDevices
    val penEvents = penManager.penEvents
    
    private val _isAutoConnecting = MutableStateFlow(false)
    val isAutoConnecting = _isAutoConnecting.asStateFlow()
    
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()
    
    init {
        android.util.Log.d("PairingViewModel", "Initializing PairingViewModel")
    }

    fun startScan() {
        bleScanner.checkBondedDevices()
        bleScanner.startScan()
    }

    fun startClassicScan() {
        bleScanner.startClassicDiscovery()
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    private val _pendingConfirmPen = MutableStateFlow<DiscoveredPen?>(null)
    val pendingConfirmPen: StateFlow<DiscoveredPen?> = _pendingConfirmPen.asStateFlow()

    private val _hasDeclinedCurrentSearch = MutableStateFlow(false)
    val hasDeclinedCurrentSearch: StateFlow<Boolean> = _hasDeclinedCurrentSearch.asStateFlow()

    fun startDiscovery() {
        if (isConnected.value || _isConnecting.value || _isAutoConnecting.value) return
        
        _pendingConfirmPen.value = null
        _hasDeclinedCurrentSearch.value = false
        _isAutoConnecting.value = true
        logDiagnostic("Search: Starting Hybrid Scan...")
        
        autoConnectJob?.cancel()
        
        autoConnectJob = viewModelScope.launch {
            try {
                // Zero-Reset and Start Hybrid Discovery (Classic + BLE)
                startClassicScan()
                startScan() 
                
                logDiagnostic("Searching for Pens... (Manual Select)")
            } catch (e: Exception) {
                logDiagnostic("Search Error: ${e.message}")
            } finally {
                _isAutoConnecting.value = false
                autoConnectJob = null
            }
        }
    }

    fun setPendingConfirm(pen: DiscoveredPen?) {
        if (_hasDeclinedCurrentSearch.value && pen != null) return
        _pendingConfirmPen.value = pen
    }

    fun declineConfirmation() {
        _hasDeclinedCurrentSearch.value = true
        _pendingConfirmPen.value = null
    }

    private var autoConnectJob: kotlinx.coroutines.Job? = null

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private fun logDiagnostic(msg: String) {
        val current = _logs.value.toMutableList()
        current.add(msg)
        if (current.size > 8) current.removeAt(0)
        _logs.value = current
        android.util.Log.i("PairingViewModel", msg)
    }

    fun connect(address: String, sppAddress: String) {
        // Only cancel background logic if manually clicking from list
        if (_isAutoConnecting.value == false) {
            autoConnectJob?.cancel()
            autoConnectJob = null
        }
        
        _isConnecting.value = true
        _logs.value = emptyList() // clear previous logs

        viewModelScope.launch {
            try {
                logDiagnostic("1) Stable Connection Injection: SPP=$sppAddress, LE=$address")
                // Stabilization delay
                kotlinx.coroutines.delay(500)
                penManager.connect(sppAddress, address)
            } catch (e: Exception) {
                logDiagnostic("Error: ${e.message}")
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun disconnect() {
        penManager.disconnect()
    }
}
