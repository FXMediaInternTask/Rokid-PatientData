package com.fxMedia.patientDataAssistant.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.patientDataAssistant.R
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.MessageType
import com.fxMedia.patientDataAssistant.api.RetrofitClient
import com.fxMedia.patientDataAssistant.api.model.LoginRequest
import com.fxMedia.patientDataAssistant.api.model.TestRequest
import com.fxMedia.patientDataAssistant.api.model.ValidateTokenRequest
import com.fxMedia.patientDataAssistant.data.TokenManager
import com.fxMedia.patientDataAssistant.data.SettingsRepository
import com.fxMedia.patientDataAssistant.service.BluetoothConnectionState
import com.fxMedia.patientDataAssistant.service.BluetoothSppManager
import com.fxMedia.patientDataAssistant.service.SpeechResult
import com.fxMedia.patientDataAssistant.service.TextToSpeechService
import com.fxMedia.patientDataAssistant.service.stt.SttCredentialsRepository
import com.fxMedia.patientDataAssistant.service.stt.SttService
import com.fxMedia.patientDataAssistant.service.stt.SttServiceFactory
import com.fxMedia.patientDataAssistant.service.stt.SttValidationResult
import com.fxMedia.rokidcommon.protocol.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    // Use the new TextToSpeechService
    private val ttsService = TextToSpeechService(application)
    
    private var currentSessionId: String? = null
    private var sttService: SttService? = null

    init {
        validateExistingToken()
        currentSessionId = tokenManager.getSessionId()

        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                val connectionState = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.LISTENING -> ConnectionState.CONNECTING // Wait for incoming
                    BluetoothConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothConnectionState.CONNECTED -> ConnectionState.CONNECTED
                }
                _uiState.update { it.copy(bluetoothState = state, connectionState = connectionState) }
            }
        }

        viewModelScope.launch {
            btManager.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(connectedGlassesName = name) }
            }
        }

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
                    else -> {}
                }
            }
        }

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
        
        // Auto-start listening on init so glasses can connect immediately
        // Added a 1s delay to let the BT stack settle after app launch
//        viewModelScope.launch {
//            delay(1000)
//            if (btManager.isBluetoothEnabled()) {
//                Log.d(TAG, "Auto-starting Bluetooth listener")
//                btManager.startListening()
//            }
//        }
    }

    private fun updateSttService() {
        val settings = settingsRepository.getSettings()
        val sttCredentials = sttCredentialsRepository.getCredentials()
        sttService = SttServiceFactory.createService(sttCredentials, settings)

        if (sttService?.provider == com.fxMedia.patientDataAssistant.service.stt.SttProvider.AZURE_SPEECH) {
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
            
            val service = sttService ?: run {
                updateTranscripts("Error: STT service not configured")
                btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = "STT not configured"))
                _uiState.update { it.copy(isTranscribing = false) }
                return@launch
            }

            try {
                val language = settingsRepository.getSettings().speechLanguage.ifBlank { "en-US" }
                when (val result = service.transcribe(audioData, language)) {
                    is SpeechResult.Success -> {
                        btManager.sendMessage(Message(
                            type = MessageType.USER_TRANSCRIPT,
                            payload = result.text
                        ))
                        sendTestAnnotation(result.text)
                    }
                    is SpeechResult.Error -> {
                        updateTranscripts("Transcription error: ${result.message}")
                        btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = result.message))
                    }
                }
            } catch (e: Exception) {
                updateTranscripts("STT failed: ${e.message}")
                btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = e.message))
            } finally {
                _uiState.update { it.copy(isTranscribing = false) }
            }
        }
    }

    fun sendTestAnnotation(text: String) {
        viewModelScope.launch {
            try {
                updateTranscripts("You: $text")
                updateTranscripts("Sending...")
                
                val savedToken = tokenManager.getToken() ?: run {
                    updateTranscripts("Error: Not logged in")
                    btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = "Not logged in"))
                    return@launch
                }

                val response = RetrofitClient.testInstance.sendTest(
                    token = "Bearer $savedToken",
                    request = TestRequest(
                        message = text,
                        sessionId = currentSessionId,
                        patientId = "6a41e92c008637ec66d5354a"
                    )
                )
                
                currentSessionId = response.data?.sessionId
                tokenManager.saveSessionId(currentSessionId)

                val baseReply: String = response.data?.reply ?: "Empty reply from AI"
                val allSuggestions: List<String>? = response.data?.suggestions
                val suggestions: List<String>? = allSuggestions?.take(2)
                
                // Clean the base reply for TTS and glasses display
                val cleanBaseReply = baseReply.replace("**", "").replace("###", "")
                
                // For Phone UI: combined text
                val suggestionText = if (suggestions != null && !suggestions.isEmpty()) {
                    "\n\nSuggestions:\n" + suggestions.joinToString("\n") { s: String -> "• $s" }
                } else ""
                val phoneDisplayReply = (cleanBaseReply + suggestionText)
                
                // Trigger TTS with the base reply
                ttsService.speak(cleanBaseReply) { audioData ->
                    // This block runs when audio data is successfully synthesized (from ElevenLabs or Fallback)
                    viewModelScope.launch {
                        // 1. Send Audio to glasses first (largest payload)
                        if (audioData.isNotEmpty()) {
                            btManager.sendMessage(Message(
                                type = MessageType.AI_RESPONSE_TTS,
                                binaryData = audioData
                            ))
                        }
                        
                        // 2. Send Text to glasses (Main body only)
                        btManager.sendMessage(Message(
                            type = MessageType.AI_RESPONSE_TEXT, 
                            payload = cleanBaseReply
                        ))

                        // 3. Send Suggestions to glasses (For the new boxes)
                        if (suggestions != null && !suggestions.isEmpty()) {
                            btManager.sendMessage(Message(
                                type = MessageType.AI_SUGGESTIONS,
                                payload = suggestions.joinToString("|")
                            ))
                        }
                        
                        // 4. Update Phone UI with full text
                        updateTranscripts("AI: $phoneDisplayReply")
                    }
                }

            } catch (e: Exception) {
                updateTranscripts("Error: ${e.message}")
                btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = e.message))
            }
        }
    }

    private fun validateExistingToken() {
        val existingToken = tokenManager.getToken() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                val response = RetrofitClient.authService.validateToken(ValidateTokenRequest(token = existingToken))
                if (response.status) {
                    _uiState.update { it.copy(isLoggedIn = true) }
                } else {
                    tokenManager.deleteToken()
                    _uiState.update { it.copy(isLoggedIn = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggedIn = true) }
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    fun performLogin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                val response = RetrofitClient.authService.login(LoginRequest(username = "unity", password = "Unity@123"))
                if (response.status && response.token != null) {
                    tokenManager.saveToken(response.token)
                    _uiState.update { it.copy(isLoggedIn = true) }
                }
            } catch (e: Exception) {
                updateTranscripts("Login error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    private fun updateTranscripts(text: String) {
        _uiState.update { state ->
            val currentList = state.transcripts
            val newList = when {
                text == "Processing voice..." || text == "Sending..." -> (currentList + text).takeLast(50)
                currentList.lastOrNull() == "Processing voice..." || currentList.lastOrNull() == "Sending..." -> currentList.dropLast(1) + text
                else -> (currentList + text).takeLast(50)
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
                file.writeText(Gson().toJson(_uiState.value.transcripts))
            } catch (e: Exception) {}
        }
    }

    fun startScanning() = btManager.startListening()
    fun disconnect() = btManager.disconnect(restartListening = false)
    fun resetConversation() {
        currentSessionId = null
        tokenManager.deleteSessionId()
        _uiState.update { it.copy(transcripts = emptyList()) }
        saveTranscripts()
    }

    override fun onCleared() {
        super.onCleared()
        ttsService.shutdown()
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
}
