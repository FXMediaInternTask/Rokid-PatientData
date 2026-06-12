package com.fxMedia.androidAPITest.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.MessageType
import com.fxMedia.androidAPITest.R
import com.fxMedia.androidAPITest.api.RetrofitClient
import com.fxMedia.androidAPITest.api.model.LoginRequest
import com.fxMedia.androidAPITest.api.model.TestRequest
import com.fxMedia.androidAPITest.api.model.ValidateTokenRequest
import com.fxMedia.androidAPITest.data.TokenManager
import com.fxMedia.androidAPITest.data.SettingsRepository
import com.fxMedia.androidAPITest.service.BluetoothConnectionState
import com.fxMedia.androidAPITest.service.BluetoothSppManager
import com.fxMedia.androidAPITest.service.SpeechResult
import com.fxMedia.androidAPITest.service.stt.SttCredentialsRepository
import com.fxMedia.androidAPITest.service.stt.SttService
import com.fxMedia.androidAPITest.service.stt.SttServiceFactory
import com.fxMedia.androidAPITest.service.stt.SttValidationResult
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
    val isLoginLoading: Boolean = false,
    val isTranscribing: Boolean = false,
    val isAzureValid: Boolean = false,
    val isAzureChecking: Boolean = false,
    val isRemoteRecording: Boolean = false
)

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    private val btManager = BluetoothSppManager(application, viewModelScope)
    private val tokenManager = TokenManager(application)
    private val settingsRepository = SettingsRepository.getInstance(application)
    private val sttCredentialsRepository = SttCredentialsRepository.getInstance(application)
    
    private var currentSessionId: String? = null
    private var sttService: SttService? = null

    init {
        // Check if existing token is still valid
        validateExistingToken()
        
        // Load saved session ID from persistent storage
        currentSessionId = tokenManager.getSessionId()

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

        // Initialize STT service when settings change
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { _ ->
                updateSttService()
            }
        }
        
        viewModelScope.launch {
            sttCredentialsRepository.credentialsFlow.collect { _ ->
                updateSttService()
            }
        }

        // Handle incoming messages from glasses
        viewModelScope.launch {
            btManager.messageFlow.collect { message ->
                when (message.type) {
                    MessageType.USER_TRANSCRIPT -> {
                        message.payload?.let { text ->
                            sendTestAnnotation(text)
                        }
                    }
                    MessageType.VOICE_END -> {
                        message.binaryData?.let { audioData ->
                            _uiState.update { it.copy(isRemoteRecording = false) }
                            handleIncomingVoice(audioData)
                        }
                    }
                    MessageType.VOICE_START -> {
                        _uiState.update { it.copy(isRemoteRecording = true) }
                    }
                    else -> {
                        Log.d(TAG, "Received message type: ${message.type}")
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

    private fun updateSttService() {
        val settings = settingsRepository.getSettings()
        val sttCredentials = sttCredentialsRepository.getCredentials()
        sttService = SttServiceFactory.createService(sttCredentials, settings)
        Log.d(TAG, "STT Service updated: ${sttService?.provider}")
        
        // Auto check Azure connection if it's the current provider
        if (sttService?.provider == com.fxMedia.androidAPITest.service.stt.SttProvider.AZURE_SPEECH) {
            checkAzureStatus()
        }
    }

    private fun checkAzureStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAzureChecking = true) }
            val result = sttService?.validateCredentials()
            _uiState.update { 
                it.copy(
                    isAzureValid = result is SttValidationResult.Valid,
                    isAzureChecking = false
                )
            }
        }
    }

    fun handleIncomingVoice(audioData: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribing = true) }
            updateTranscripts("Processing voice...")
            
            val service = sttService
            if (service == null) {
                updateTranscripts("Error: STT service not configured")
                _uiState.update { it.copy(isTranscribing = false) }
                return@launch
            }

            try {
                // Get speech language from settings
                val language = settingsRepository.getSettings().speechLanguage.ifBlank { "en-US" }
                
                when (val result = service.transcribe(audioData, language)) {
                    is SpeechResult.Success -> {
                        Log.d(TAG, "Transcription success: ${result.text}")
                        
                        // Send transcript back to glasses
                        btManager.sendMessage(Message(
                            type = MessageType.USER_TRANSCRIPT,
                            payload = result.text
                        ))
                        
                        // This will eventually call sendTestAnnotation which replaces "Processing voice..."
                        sendTestAnnotation(result.text)
                    }
                    is SpeechResult.Error -> {
                        Log.e(TAG, "Transcription error: ${result.message}")
                        updateTranscripts("Transcription error: ${result.message}")
                        
                        btManager.sendMessage(Message(
                            type = MessageType.AI_ERROR,
                            payload = result.message
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "STT processing failed", e)
                updateTranscripts("STT failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isTranscribing = false) }
            }
        }
    }

    /**
     * Test Azure STT using a raw resource audio file.
     */
    fun testAzureSTT() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                // Open the raw resource
                val inputStream = context.resources.openRawResource(R.raw.afternoon)
                val audioData = inputStream.readBytes()
                inputStream.close()
                
                Log.d(TAG, "Testing Azure STT with raw resource, size: ${audioData.size} bytes")
                
                // Switch to main to update UI through handleIncomingVoice
                launch(Dispatchers.Main) {
                    handleIncomingVoice(audioData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read test audio resource", e)
                launch(Dispatchers.Main) {
                    updateTranscripts("Error: Failed to read test audio")
                }
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
                // 1. Show user's input in the conversation
                updateTranscripts("You: $text")
                
                // 2. Show "Sending..." status
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
                // PERSIST SESSION ID
                tokenManager.saveSessionId(currentSessionId)
                
                val reply = response.data?.reply ?: "Empty reply from AI"
                
                // 3. Update the conversation with the AI reply (replaces "Sending...")
                updateTranscripts("AI: $reply")
                
                // Send response back to glasses
                btManager.sendMessage(Message(
                    type = MessageType.AI_RESPONSE_TEXT,
                    payload = reply
                ))
                
            } catch (e: Exception) {
                Log.e(TAG, "API Error", e)
                updateTranscripts("Error: ${e.message}")
                
                btManager.sendMessage(Message(
                    type = MessageType.AI_ERROR,
                    payload = e.message
                ))
            }
        }
    }

    private fun updateTranscripts(text: String) {
        _uiState.update { state ->
            val currentList = state.transcripts
            
            val newList = when {
                // If we are adding a "loading" status, always prepend it
                text == "Processing voice..." || text == "Sending..." -> {
                    (listOf(text) + currentList).take(50)
                }
                // If the current top item is a "loading" status, replace it with the new message (actual result or "You:...")
                currentList.firstOrNull() == "Processing voice..." || currentList.firstOrNull() == "Sending..." -> {
                    listOf(text) + currentList.drop(1)
                }
                // Otherwise, just prepend the new message
                else -> {
                    (listOf(text) + currentList).take(50)
                }
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

    fun resetConversation() {
        currentSessionId = null
        // DELETE FROM STORAGE
        tokenManager.deleteSessionId()
        _uiState.update { it.copy(transcripts = emptyList()) }
        saveTranscripts()
    }
}
