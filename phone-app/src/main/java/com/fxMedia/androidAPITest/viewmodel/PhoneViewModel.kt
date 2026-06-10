package com.fxMedia.androidAPITest.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.MessageType
import com.fxMedia.androidAPITest.api.RetrofitClient
import com.fxMedia.androidAPITest.api.model.LoginRequest
import com.fxMedia.androidAPITest.api.model.TestRequest
import com.fxMedia.androidAPITest.api.model.ValidateTokenRequest
import com.fxMedia.androidAPITest.data.TokenManager
import com.fxMedia.androidAPITest.service.BluetoothConnectionState
import com.fxMedia.androidAPITest.service.BluetoothSppManager
import com.fxMedia.rokidcommon.protocol.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PhoneViewModel"

data class PhoneUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val bluetoothState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val connectedGlassesName: String? = null,
    val transcripts: List<String> = emptyList(),
    val isLoggedIn: Boolean = false,
    val isLoginLoading: Boolean = false
)

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    private val btManager = BluetoothSppManager(application, viewModelScope)
    private val tokenManager = TokenManager(application)
    private var currentSessionId: String? = null

    init {
        // Check if existing token is still valid
        validateExistingToken()

        // Observe Bluetooth connection status
        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                val connectionState = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.LISTENING -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothConnectionState.CONNECTED -> ConnectionState.CONNECTED
                }
                _uiState.update { it.copy(bluetoothState = state, connectionState = connectionState) }
            }
        }

        // Observe connected device name
        viewModelScope.launch {
            btManager.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedGlassesName = name) }
            }
        }

        // Handle incoming messages from glasses
        viewModelScope.launch {
            btManager.messageFlow.collect { message ->
                if (message.type == MessageType.USER_TRANSCRIPT) {
                    message.payload?.let { text ->
                        sendTestAnnotation(text)
                    }
                }
            }
        }

        // Load saved transcripts from disk
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "transcripts.json")
                if (file.exists()) {
                    val json = file.readText()
                    val type = object : TypeToken<List<String>>() {}.type
                    val savedList: List<String> = Gson().fromJson(json, type)
                    _uiState.update { it.copy(transcripts = savedList) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transcripts", e)
            }
        }
    }

    private fun validateExistingToken() {
        val existingToken = tokenManager.getToken()
        if (existingToken.isNullOrEmpty()) {
            _uiState.update { it.copy(isLoggedIn = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                val response = RetrofitClient.authService.validateToken(
                    ValidateTokenRequest(token = existingToken)
                )
                if (response.status) {
                    _uiState.update { it.copy(isLoggedIn = true) }
                    Log.d(TAG, "Token validated successfully")
                } else {
                    // Token expired or invalid
                    tokenManager.deleteToken()
                    _uiState.update { it.copy(isLoggedIn = false) }
                    updateTranscripts("Session expired. Please login again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token validation failed", e)
                // On error, we stay "logged in" for UI purposes but API calls might still fail
                _uiState.update { it.copy(isLoggedIn = true) }
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    /**
     * Performs login with hardcoded credentials for testing.
     */
    fun performLogin(
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                // Hardcoded credentials for testing as requested
                val response = RetrofitClient.authService.login(
                    LoginRequest(username = "unity", password = "Unity@123")
                )
                
                if (response.status && response.token != null) {
                    tokenManager.saveToken(response.token)
                    _uiState.update { it.copy(isLoggedIn = true) }
                    updateTranscripts("Login successful")
                } else {
                    updateTranscripts("Login failed: ${if (!response.status) "Invalid credentials" else "No token received"}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                updateTranscripts("Login error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    fun sendTestAnnotation(text: String) {
        viewModelScope.launch {
            try {
                updateTranscripts("Sending...")
                val savedToken = tokenManager.getToken()
                
                if (savedToken == null) {
                    updateTranscripts("Error: Not logged in")
                    return@launch
                }

                val response = RetrofitClient.testInstance.sendTest(
                    token = "Bearer $savedToken",
                    request = TestRequest(
                        message = text,
                        sessionId = currentSessionId
                    )
                )
                
                currentSessionId = response.data?.sessionId
                val reply = response.data?.reply ?: "Empty reply from AI"
                updateTranscripts("AI: $reply")
                
            } catch (e: Exception) {
                Log.e(TAG, "API Error", e)
                updateTranscripts("Error: ${e.message}")
            }
        }
    }

    private fun updateTranscripts(text: String) {
        _uiState.update { state ->
            val currentList = state.transcripts
            val newList = if (currentList.firstOrNull() == "Sending...") {
                listOf(text) + currentList.drop(1)
            } else {
                (listOf(text) + currentList).take(10)
            }
            state.copy(transcripts = newList)
        }
        saveTranscripts()
    }

    private fun saveTranscripts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val file = File(context.filesDir, "transcripts.json")
                val json = Gson().toJson(_uiState.value.transcripts)
                file.writeText(json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transcripts", e)
            }
        }
    }

    fun startScanning() {
        btManager.startListening()
    }

    fun disconnect() {
        btManager.disconnect(restartListening = false)
    }
}
