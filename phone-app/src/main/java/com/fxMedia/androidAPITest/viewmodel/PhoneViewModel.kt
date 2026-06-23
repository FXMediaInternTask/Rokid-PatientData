package com.fxMedia.androidAPITest.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.androidAPITest.R
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.MessageType
import com.fxMedia.androidAPITest.api.RetrofitClient
import com.fxMedia.androidAPITest.api.model.LoginRequest
import com.fxMedia.androidAPITest.api.model.TestRequest
import com.fxMedia.androidAPITest.api.model.ValidateTokenRequest
import com.fxMedia.androidAPITest.data.TokenManager
import com.fxMedia.androidAPITest.data.SettingsRepository
import com.fxMedia.androidAPITest.service.BluetoothConnectionState
import com.fxMedia.androidAPITest.service.BluetoothSppManager
import com.fxMedia.androidAPITest.service.SpeechResult
import com.fxMedia.androidAPITest.service.TextToSpeechService
import com.fxMedia.androidAPITest.service.stt.SttCredentialsRepository
import com.fxMedia.androidAPITest.service.stt.SttService
import com.fxMedia.androidAPITest.service.stt.SttServiceFactory
import com.fxMedia.androidAPITest.service.stt.SttValidationResult
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

enum class MicSource {
    PHONE,
    GLASSES
}

enum class AudioOutput {
    PHONE,
    GLASSES,
    BOTH
}

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
    val isRemoteRecording: Boolean = false,
    val micSource: MicSource = MicSource.PHONE,
    val audioOutput: AudioOutput = AudioOutput.BOTH,
    val isElevenLabsLiveActive: Boolean = false,
    val statusMessage: String? = null
)

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    private val btManager = BluetoothSppManager(application, viewModelScope)
    private val cxrManager = com.fxMedia.androidAPITest.service.cxr.CxrMobileManager(application)
    private val elevenLabsLiveService = com.fxMedia.androidAPITest.service.ai.ElevenLabsLiveService(application)
    private val vadLiveService = com.fxMedia.androidAPITest.service.ai.VadLiveService(application) { audioData ->
        handleIncomingVoice(audioData)
    }
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
                    MessageType.KEY_EVENT -> {
                        if (message.payload == "66") {
                            Log.d(TAG, "Enter key (66) from glasses detected")
                            toggleElevenLabsLive()
                        }
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
        viewModelScope.launch {
            delay(1000)
            if (btManager.isBluetoothEnabled()) {
                Log.d(TAG, "Auto-starting Bluetooth listener")
                btManager.startListening()
            }
        }
    }

    private fun updateSttService() {
        val settings = settingsRepository.getSettings()
        val sttCredentials = sttCredentialsRepository.getCredentials()
        sttService = SttServiceFactory.createService(sttCredentials, settings)

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
            // Pause VAD while processing and AI is talking
            if (_uiState.value.isElevenLabsLiveActive) {
                vadLiveService.pause()
            }
            
            _uiState.update { it.copy(isTranscribing = true, statusMessage = "Processing voice...") }
            
            val service = sttService ?: run {
                updateTranscripts("Error: STT service not configured")
                _uiState.update { it.copy(isTranscribing = false, statusMessage = null) }
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
            } finally {
                _uiState.update { it.copy(isTranscribing = false) }
            }
        }
    }

    fun sendTestAnnotation(text: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(statusMessage = "Sending to AI...") }
                updateTranscripts("You: $text")
                
                val savedToken = tokenManager.getToken() ?: run {
                    updateTranscripts("Error: Not logged in")
                    _uiState.update { it.copy(statusMessage = null) }
                    return@launch
                }

                val response = RetrofitClient.testInstance.sendTest(
                    token = "Bearer $savedToken",
                    request = TestRequest(message = text, sessionId = currentSessionId)
                )
                
                currentSessionId = response.data?.sessionId
                tokenManager.saveSessionId(currentSessionId)

                val reply = response.data?.reply ?: "Empty reply from AI"
                
                // Trigger TTS: This prepares audio for glasses and phone
                // We wait for the audio to be generated before showing text/playing
                ttsService.speak(reply) { audioData ->
                    // This block runs when audio data is successfully synthesized (from ElevenLabs or Fallback)
                    viewModelScope.launch {
                        // 1. Send Audio to glasses if output is GLASSES or BOTH
                        if (audioData.isNotEmpty() && (_uiState.value.audioOutput == AudioOutput.GLASSES || _uiState.value.audioOutput == AudioOutput.BOTH)) {
                            btManager.sendMessage(Message(
                                type = MessageType.AI_RESPONSE_TTS,
                                binaryData = audioData
                            ))
                        }
                        
                        // 2. Send Text to glasses
                        btManager.sendMessage(Message(
                            type = MessageType.AI_RESPONSE_TEXT, 
                            payload = reply
                        ))
                        
                        // 3. Update Phone UI
                        updateTranscripts("AI: $reply")
                        _uiState.update { it.copy(statusMessage = null) }
                        
                        // 4. Play Locally on Phone if output is PHONE or BOTH
                        if (audioData.isNotEmpty() && (_uiState.value.audioOutput == AudioOutput.PHONE || _uiState.value.audioOutput == AudioOutput.BOTH)) {
                            // If VAD is active, we must ensure it doesn't listen to the AI
                            if (_uiState.value.isElevenLabsLiveActive) {
                                vadLiveService.pause()
                            }
                            
                            // Ensure speakerphone is active
                            val am = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                            am?.isSpeakerphoneOn = true

                            // Play via TTS service
                            ttsService.playAudioData(audioData) {
                                // This callback runs when playback finishes
                                if (_uiState.value.isElevenLabsLiveActive) {
                                    vadLiveService.resume()
                                }
                            }
                        } else {
                            // If not playing locally, resume VAD immediately
                            if (_uiState.value.isElevenLabsLiveActive) {
                                vadLiveService.resume()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                updateTranscripts("Error: ${e.message}")
                btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = e.message))
                _uiState.update { it.copy(statusMessage = null) }
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
            val newList = (listOf(text) + currentList).take(50)
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

    fun toggleMicSource() {
        val newSource = if (_uiState.value.micSource == MicSource.PHONE) {
            MicSource.GLASSES
        } else {
            MicSource.PHONE
        }
        
        _uiState.update { it.copy(micSource = newSource) }
        
        if (newSource == MicSource.GLASSES) {
            Log.d(TAG, "Switching to Glasses Mic")
            cxrManager.startRemoteMicStreaming { audioData ->
                // Feed the glasses audio into VAD if Live Session is active
                if (_uiState.value.isElevenLabsLiveActive && _uiState.value.micSource == MicSource.GLASSES) {
                    vadLiveService.feedAudio(audioData)
                }
                Log.v(TAG, "Received ${audioData.size} bytes from Glasses mic")
            }
        } else {
            Log.d(TAG, "Switching to Phone Mic")
            cxrManager.stopRemoteMicStreaming()
        }
    }

    fun toggleAudioOutput() {
        val current = _uiState.value.audioOutput
        val next = when (current) {
            AudioOutput.PHONE -> AudioOutput.GLASSES
            AudioOutput.GLASSES -> AudioOutput.BOTH
            AudioOutput.BOTH -> AudioOutput.PHONE
        }
        _uiState.update { it.copy(audioOutput = next) }
        updateTranscripts("Audio Output set to: ${next.name}")
    }

    fun toggleElevenLabsLive() {
        val isActive = _uiState.value.isElevenLabsLiveActive
        if (isActive) {
            vadLiveService.stop()
            _uiState.update { it.copy(isElevenLabsLiveActive = false) }
            updateTranscripts("VAD Session Ended")
            
            // Notify glasses
            viewModelScope.launch {
                btManager.sendMessage(Message(type = MessageType.LIVE_SESSION_END))
            }
        } else {
            vadLiveService.start()
            _uiState.update { it.copy(isElevenLabsLiveActive = true) }
            updateTranscripts("VAD Session Started - Listening...")
            
            // Notify glasses
            viewModelScope.launch {
                btManager.sendMessage(Message(type = MessageType.LIVE_SESSION_START))
            }
        }
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
