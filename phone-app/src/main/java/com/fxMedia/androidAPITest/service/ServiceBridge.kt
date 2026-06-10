package com.fxMedia.androidAPITest.service

import android.util.Log
import com.fxMedia.rokidcommon.protocol.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ServiceBridge"

/**
 * Bridge between Service and UI
 */
object ServiceBridge {
    
    private val _conversationFlow = MutableSharedFlow<Message>(replay = 0)
    val conversationFlow: SharedFlow<Message> = _conversationFlow.asSharedFlow()
    
    // Bluetooth connection state
    private val _bluetoothStateFlow = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val bluetoothStateFlow: StateFlow<BluetoothConnectionState> = _bluetoothStateFlow.asStateFlow()
    
    // Connected device name
    private val _connectedDeviceNameFlow = MutableStateFlow<String?>(null)
    val connectedDeviceNameFlow: StateFlow<String?> = _connectedDeviceNameFlow.asStateFlow()
    
    // Send message to glasses
    private val _sendToGlassesFlow = MutableSharedFlow<Message>(replay = 0)
    val sendToGlassesFlow: SharedFlow<Message> = _sendToGlassesFlow.asSharedFlow()

    /**
     * Send a message to glasses (called by ViewModel)
     */
    suspend fun sendToGlasses(message: Message) {
        Log.d(TAG, "Sending message to glasses: type=${message.type}")
        _sendToGlassesFlow.emit(message)
    }
    
    /**
     * Emit conversation message (called by Service)
     */
    suspend fun emitConversation(message: Message) {
        _conversationFlow.emit(message)
    }
    
    /**
     * Update Bluetooth connection state (called by Service)
     */
    fun updateBluetoothState(state: BluetoothConnectionState) {
        _bluetoothStateFlow.value = state
    }
    
    /**
     * Update connected device name (called by Service)
     */
    fun updateConnectedDeviceName(name: String?) {
        _connectedDeviceNameFlow.value = name
    }

    // Connection control requests from UI
    private val _startListeningFlow = MutableSharedFlow<Unit>(replay = 0)
    val startListeningFlow: SharedFlow<Unit> = _startListeningFlow.asSharedFlow()
    
    private val _disconnectFlow = MutableSharedFlow<Unit>(replay = 0)
    val disconnectFlow: SharedFlow<Unit> = _disconnectFlow.asSharedFlow()
    
    suspend fun requestStartListening() {
        _startListeningFlow.emit(Unit)
    }
    
    suspend fun requestDisconnect() {
        _disconnectFlow.emit(Unit)
    }
}
