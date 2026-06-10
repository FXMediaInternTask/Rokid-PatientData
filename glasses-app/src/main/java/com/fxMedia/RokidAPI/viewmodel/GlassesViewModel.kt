package com.fxMedia.RokidAPI.viewmodel

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fxMedia.RokidAPI.sdk.CxrServiceManager
import com.fxMedia.RokidAPI.service.BluetoothClientState
import com.fxMedia.RokidAPI.service.BluetoothSppClient
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.Message
import com.fxMedia.rokidcommon.protocol.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GlassesUIState(
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val showDeviceSelector: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val bluetoothState: BluetoothClientState = BluetoothClientState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val cxrConnectedPhoneName: String? = null,
    val availableDevices: List<BluetoothDevice> = emptyList(),
    val displayText: String = "Tap to start",
    val hintText: String = "Please connect phone",
    val aiResponse: String = ""
)

class GlassesViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "GlassesViewModel"
    }

    private val _uiState = MutableStateFlow(GlassesUIState())
    val uiState: StateFlow<GlassesUIState> = _uiState.asStateFlow()

    private val bluetoothClient = BluetoothSppClient(context, viewModelScope)
    private var cxrServiceManager: CxrServiceManager? = null

    init {
        initializeBluetooth()
        initializeCxrService()
    }

    /**
     * Handles the temple tap / ENTER key logic:
     * 1. If not connected -> Show device list
     * 2. If connected & idle -> Show "Tap to send API"
     * 3. If "Tap to send API" is showing -> Send "FX API Test" to phone
     */
    fun onPrimaryTap() {
        val currentState = _uiState.value
        
        if (!currentState.isConnected) {
            refreshPairedDevices()
            _uiState.update { it.copy(showDeviceSelector = true) }
            return
        }

        if (currentState.displayText == "Tap to send API") {
            // Action: Send the API Test text
            sendApiTest()
        } else {
            // Action: Show instruction
            _uiState.update {
                it.copy(
                    displayText = "Tap to send API",
                    hintText = "Confirm to send test",
                    isProcessing = false
                )
            }
        }
    }

    fun dismissDeviceSelector() {
        _uiState.update { it.copy(showDeviceSelector = false) }
    }

    private fun sendApiTest() {
        _uiState.update { 
            it.copy(
                displayText = "Sending...",
                hintText = "Communicating with phone",
                isProcessing = true
            )
        }

        viewModelScope.launch {
            val success = bluetoothClient.sendMessage(
                Message(
                    type = MessageType.USER_TRANSCRIPT,
                    payload = "FX API Test"
                )
            )
            
            if (!success) {
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        displayText = "Send Failed",
                        hintText = "Check connection"
                    )
                }
            }
        }
    }

    private fun handlePhoneMessage(message: Message) {
        when (message.type) {
            MessageType.AI_RESPONSE_TEXT -> {
                // Response back from API via Phone
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        displayText = message.payload ?: "No response",
                        hintText = "Tap to send again"
                    )
                }
            }
            MessageType.AI_ERROR -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        displayText = message.payload ?: "Error",
                        hintText = "Tap to try again"
                    )
                }
            }
            MessageType.HEARTBEAT -> {
                viewModelScope.launch {
                    bluetoothClient.sendMessage(Message(type = MessageType.HEARTBEAT_ACK))
                }
            }
            else -> {}
        }
    }

    private fun initializeCxrService() {
        if (!CxrServiceManager.isSdkAvailable()) {
            Log.w(TAG, "CXR-S SDK not available")
            return
        }
        cxrServiceManager = CxrServiceManager.getInstance()
        if (cxrServiceManager?.initialize() != true) return

        viewModelScope.launch {
            cxrServiceManager?.connectionState?.collect { state ->
                when (state) {
                    is CxrServiceManager.ConnectionState.Connected ->
                        _uiState.update { it.copy(cxrConnectedPhoneName = state.deviceName) }
                    is CxrServiceManager.ConnectionState.Disconnected ->
                        _uiState.update { it.copy(cxrConnectedPhoneName = null) }
                }
            }
        }
    }

    fun refreshPairedDevices() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val devices = bluetoothClient.getPairedDevices()
            _uiState.update { it.copy(availableDevices = devices) }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        bluetoothClient.connect(device)
        dismissDeviceSelector()
    }

    fun disconnectDevice() {
        bluetoothClient.disconnect()
    }

    private fun initializeBluetooth() {
        viewModelScope.launch {
            bluetoothClient.connectionState.collect { state ->
                val isConnected = state == BluetoothClientState.CONNECTED
                _uiState.update {
                    it.copy(
                        bluetoothState = state,
                        isConnected = isConnected,
                        displayText = when (state) {
                            BluetoothClientState.DISCONNECTED -> "Not connected"
                            BluetoothClientState.CONNECTING -> "Connecting..."
                            BluetoothClientState.CONNECTED -> "Connected"
                        },
                        hintText = if (isConnected) "Tap to record" else "Tap to connect"
                    )
                }
            }
        }

        viewModelScope.launch {
            bluetoothClient.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedDeviceName = name) }
            }
        }

        viewModelScope.launch {
            bluetoothClient.messageFlow.collect { message ->
                handlePhoneMessage(message)
            }
        }
        refreshPairedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothClient.disconnect()
        cxrServiceManager?.release()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GlassesViewModel(context.applicationContext) as T
        }
    }
}
