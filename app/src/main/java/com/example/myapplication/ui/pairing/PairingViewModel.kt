package com.example.myapplication.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.myapplication.data.pen.BleScanner
import com.example.myapplication.data.pen.PenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val penManager: PenManager
) : ViewModel() {

    val foundPens = bleScanner.foundPens
    val isConnected = penManager.isConnected
    val message = penManager.message
    val scanStatus = bleScanner.status
    val totalDevicesFound = bleScanner.totalUniqueDevices
    
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

    private var isConnecting = false

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
        if (isConnecting) {
            logDiagnostic("Ignoring double tap.")
            return
        }
        isConnecting = true
        _logs.value = emptyList() // clear previous logs

        viewModelScope.launch {
            try {
                // Completely pure connection injection - Let the SDK (and Android native GATT) handle all bonding and pairing internally!
                logDiagnostic("1) Pure DOUBLE-MAC Injection: SPP=$sppAddress, LE=$address")
                penManager.connect(sppAddress, address)
            } catch (e: Exception) {
                logDiagnostic("Error: ${e.message}")
            } finally {
                isConnecting = false
            }
        }
    }

    fun disconnect() {
        penManager.disconnect()
    }
}
