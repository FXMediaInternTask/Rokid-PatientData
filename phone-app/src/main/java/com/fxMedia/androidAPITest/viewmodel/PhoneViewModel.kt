package com.fxMedia.androidAPITest.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fxMedia.androidAPITest.R
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.Message
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
import com.fxMedia.androidAPITest.data.log.LogManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "PhoneViewModel"

enum class MicSource {
    PHONE,
    GLASSES
}

enum class AudioOutput {
    PHONE,
    GLASSES
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
    val audioOutput: AudioOutput = AudioOutput.PHONE,
    val isElevenLabsLiveActive: Boolean = false,
    val statusMessage: String? = null,
    val logs: List<com.fxMedia.androidAPITest.data.log.LogEntry> = emptyList(),
    val appVersion: String = "1.0.0"
)

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhoneUiState())
    val uiState: StateFlow<PhoneUiState> = _uiState.asStateFlow()

    private val logManager = LogManager.getInstance(application)
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

        // Get app version
        try {
            val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            val version = pInfo.versionName
            _uiState.update { it.copy(appVersion = version ?: "1.0.0") }
        } catch (e: Exception) {
            _uiState.update { it.copy(appVersion = "1.0.0") }
        }

        // Collect logs
        viewModelScope.launch {
            logManager.logs.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }

        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                val prevConnectionState = _uiState.value.connectionState
                val connectionState = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> ConnectionState.DISCONNECTED
                    BluetoothConnectionState.LISTENING -> ConnectionState.CONNECTING // Wait for incoming
                    BluetoothConnectionState.CONNECTING -> ConnectionState.CONNECTING
                    BluetoothConnectionState.CONNECTED -> ConnectionState.CONNECTED
                }
                
                _uiState.update { it.copy(bluetoothState = state, connectionState = connectionState) }

                if (state == BluetoothConnectionState.CONNECTED) {
                    logManager.d(TAG, "Glasses SPP Connected - Initializing CXR Bluetooth")
                    
                    // Keep Mic and Audio on PHONE by default for TWS-like behavior
                    // The user can manually switch to GLASSES if they want custom SPP streaming
                    _uiState.update { it.copy(
                        micSource = MicSource.PHONE,
                        audioOutput = AudioOutput.PHONE
                    )}
                    
                    // Initialize CXR Bluetooth with the connected device
                    btManager.connectedDevice?.let { device ->
                        cxrManager.initBluetooth(device)
                    }
                } else if (state == BluetoothConnectionState.DISCONNECTED) {
                    // Only log and revert if we were previously connected/connecting
                    if (prevConnectionState != ConnectionState.DISCONNECTED) {
                        logManager.d(TAG, "Glasses Disconnected - Reverting to Phone Mic & Audio")
                        _uiState.update { it.copy(
                            micSource = MicSource.PHONE,
                            audioOutput = AudioOutput.PHONE
                        )}
                    }
                }
            }
        }

        viewModelScope.launch {
            cxrManager.bluetoothState.collect { state ->
                when (state) {
                    is com.fxMedia.androidAPITest.service.cxr.CxrMobileManager.BluetoothState.Connected -> {
                        logManager.d(TAG, "CXR Bluetooth Connected - Setting AI Event Listener")
                        
                        // Set AI Event Listener to handle glasses AI button (long press)
                        cxrManager.setAiEventListener(
                            onKeyDown = {
                                logManager.d(TAG, "AI Button on glasses pressed - signaling SPP start")
                                toggleElevenLabsLive()
                            }
                        )
                    }
                    is com.fxMedia.androidAPITest.service.cxr.CxrMobileManager.BluetoothState.Failed -> {
                        logManager.e(TAG, "CXR Error: ${state.error}")
                        updateTranscripts("CXR Error: ${state.error}")
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            vadLiveService.isUserSpeaking.collect { isSpeaking ->
                if (_uiState.value.isElevenLabsLiveActive) {
                    logManager.d(TAG, "VAD speaking status changed: $isSpeaking - sending message to glasses")
                    btManager.sendMessage(Message(
                        type = if (isSpeaking) MessageType.VOICE_START else MessageType.VOICE_END
                    ))
                }
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
                    MessageType.VOICE_DATA -> {
                        message.binaryData?.let { audioData ->
                            // Feed the SPP audio into VAD if Live Session is active
                            if (_uiState.value.isElevenLabsLiveActive && _uiState.value.micSource == MicSource.GLASSES) {
                                vadLiveService.feedAudio(audioData)
                            }
                        }
                    }
                    MessageType.LIVE_SESSION_START -> {
                        logManager.i(TAG, "VAD Session START triggered from glasses")
                        if (!_uiState.value.isElevenLabsLiveActive) {
                            toggleElevenLabsLive()
                        }
                    }
                    MessageType.LIVE_SESSION_END -> {
                        logManager.i(TAG, "VAD Session STOP triggered from glasses")
                        if (_uiState.value.isElevenLabsLiveActive) {
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
                logManager.e(TAG, "Failed to load transcripts", e)
            }
        }
        
        /* Auto-start listening disabled as per user request
        viewModelScope.launch {
            delay(1000)
            if (btManager.isBluetoothEnabled()) {
                logManager.d(TAG, "Auto-starting Bluetooth listener")
                btManager.startListening()
            }
        }
        */
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
            logManager.d(TAG, "Handling incoming voice data (${audioData.size} bytes)")
            // Pause VAD while processing and AI is talking
            if (_uiState.value.isElevenLabsLiveActive) {
                vadLiveService.pause()
            }
            
            _uiState.update { it.copy(isTranscribing = true, statusMessage = "Processing voice...") }
            
            val service = sttService ?: run {
                logManager.e(TAG, "STT service not configured")
                updateTranscripts("Error: STT service not configured")
                _uiState.update { it.copy(isTranscribing = false, statusMessage = null) }
                return@launch
            }

            try {
                val language = settingsRepository.getSettings().speechLanguage.ifBlank { "en-US" }
                logManager.d(TAG, "Starting transcription with language: $language")
                when (val result = service.transcribe(audioData, language)) {
                    is SpeechResult.Success -> {
                        val cleanedText = result.text.replace("**", "")
                        logManager.i(TAG, "Transcription Success: $cleanedText")
                        logManager.d(TAG, "Sending transcript to glasses...")
                        viewModelScope.launch {
                            val sent = btManager.sendMessage(Message(
                                type = MessageType.USER_TRANSCRIPT,
                                payload = cleanedText
                            ))
                            if (sent) logManager.d(TAG, "Transcript sent to glasses successfully")
                            else logManager.e(TAG, "Failed to send transcript to glasses")
                        }
                        sendTestAnnotation(cleanedText)
                    }
                    is SpeechResult.Error -> {
                        logManager.e(TAG, "Transcription Failed: ${result.message}")
                        updateTranscripts("Transcription error: ${result.message}")
                        btManager.sendMessage(Message(type = MessageType.AI_ERROR, payload = result.message))
                        if (_uiState.value.isElevenLabsLiveActive) {
                            vadLiveService.resume()
                        }
                    }
                }
            } catch (e: Exception) {
                logManager.e(TAG, "STT failed", e)
                updateTranscripts("STT failed: ${e.message}")
                if (_uiState.value.isElevenLabsLiveActive) {
                    vadLiveService.resume()
                }
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
                    if (_uiState.value.isElevenLabsLiveActive) {
                        logManager.d(TAG, "Auth Error - Stopping Live Session")
                        vadLiveService.stop()
                        _uiState.update { it.copy(isElevenLabsLiveActive = false) }
                        btManager.sendMessage(Message(type = MessageType.LIVE_SESSION_END))
                    }
                    return@launch
                }

                logManager.i(TAG, "Sending message to AI...")
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

                val rawReply = response.data?.reply ?: "Empty reply from AI"
                val reply = rawReply.replace("**", "")
                logManager.i(TAG, "AI Response Received: $reply")
                
                // 1. Trigger TTS in background IMMEDIATELY
                logManager.d(TAG, "Requesting TTS for AI response...")
                ttsService.speak(reply) { audioData ->
                    viewModelScope.launch {
                        if (audioData.isNotEmpty()) {
                            logManager.i(TAG, "TTS Audio Generated (${audioData.size} bytes)")
                        } else {
                            logManager.e(TAG, "TTS Audio Generation Failed or Empty")
                        }

                        // 2. Send Audio to glasses as soon as it's ready
                        if (audioData.isNotEmpty() && _uiState.value.audioOutput == AudioOutput.GLASSES) {
                            logManager.d(TAG, "Sending TTS Audio to glasses...")
                            val audioSent = btManager.sendMessage(Message(
                                type = MessageType.AI_RESPONSE_TTS,
                                binaryData = audioData
                            ))
                            if (audioSent) logManager.d(TAG, "TTS Audio sent to glasses successfully")
                            else logManager.e(TAG, "Failed to send TTS Audio to glasses")
                        }

                        // 3. NOW send the Text so the glasses displays it and plays the buffered audio
                        logManager.d(TAG, "Sending Text response to glasses...")
                        val textSent = btManager.sendMessage(Message(
                            type = MessageType.AI_RESPONSE_TEXT, 
                            payload = reply
                        ))
                        if (textSent) logManager.d(TAG, "Text response sent to glasses successfully")
                        else logManager.e(TAG, "Failed to send Text response to glasses")
                        
                        // Update Phone UI
                        updateTranscripts("AI: $reply")
                        _uiState.update { it.copy(statusMessage = null) }
                        
                        // 4. Play Locally on Phone if output is PHONE
                        if (audioData.isNotEmpty() && _uiState.value.audioOutput == AudioOutput.PHONE) {
                            if (_uiState.value.isElevenLabsLiveActive) {
                                vadLiveService.pause()
                            }
                            
                            // Do NOT force speakerphone so it can route to Bluetooth (TWS behavior)
                            // val am = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                            // am?.isSpeakerphoneOn = true

                            ttsService.playAudioData(audioData) {
                                if (_uiState.value.isElevenLabsLiveActive) {
                                    vadLiveService.resume()
                                }
                            }
                        } else {
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
                if (_uiState.value.isElevenLabsLiveActive) {
                    vadLiveService.resume()
                }
            }
        }
    }

    private fun validateExistingToken() {
        val existingToken = tokenManager.getToken() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                val response = RetrofitClient.authService.validateToken(existingToken)
                if (response.status) {
                    _uiState.update { it.copy(isLoggedIn = true) }
                } else {
                    logManager.d(TAG, "Token invalid: ${response.token}")
                    tokenManager.deleteToken()
                    _uiState.update { it.copy(isLoggedIn = false) }
                }
            } catch (e: Exception) {
                logManager.e(TAG, "Token validation error: ${e.message}")
                tokenManager.deleteToken()
                _uiState.update { it.copy(isLoggedIn = false) }
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    fun performLogin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoginLoading = true) }
            try {
                val response = RetrofitClient.authService.login("unity", "Unity@123")
                if (response.status && response.token != null) {
                    tokenManager.saveToken(response.token)
                    _uiState.update { it.copy(isLoggedIn = true) }
                    logManager.i(TAG, "Login successful")
                } else {
                    logManager.e(TAG, "Login failed: Status false")
                    updateTranscripts("Login failed: Status false")
                }
            } catch (e: Exception) {
                logManager.e(TAG, "Login error", e)
                updateTranscripts("Login error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isLoginLoading = false) }
            }
        }
    }

    private fun updateTranscripts(text: String) {
        _uiState.update { state ->
            val currentList = state.transcripts
            val cleanedText = text.replace("**", "")
            val newList = (listOf(cleanedText) + currentList).take(50)
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

    /* 
    fun toggleMicSource() {
        val newSource = if (_uiState.value.micSource == MicSource.PHONE) {
            MicSource.GLASSES
        } else {
            MicSource.PHONE
        }
        
        _uiState.update { it.copy(micSource = newSource) }
        
        // If VAD is active, we need to restart it to switch between internal/external mic
        if (_uiState.value.isElevenLabsLiveActive) {
            vadLiveService.stop()
            vadLiveService.start(useInternalMic = (newSource == MicSource.PHONE))
        }
        
        if (newSource == MicSource.GLASSES) {
            Log.d(TAG, "Switching to Glasses Mic")
            cxrManager.startRemoteMicStreaming { audioData ->
                // Feed the glasses audio into VAD if Live Session is active
                if (_uiState.value.isElevenLabsLiveActive && _uiState.value.micSource == MicSource.GLASSES) {
                    vadLiveService.feedAudio(audioData)
                }
            }
        } else {
            Log.d(TAG, "Switching to Phone Mic")
        }
    }

    fun toggleAudioOutput() {
        val current = _uiState.value.audioOutput
        val next = when (current) {
            AudioOutput.PHONE -> AudioOutput.GLASSES
            AudioOutput.GLASSES -> AudioOutput.PHONE
        }
        _uiState.update { it.copy(audioOutput = next) }
        updateTranscripts("Audio Output set to: ${next.name}")
    }
    */

    fun toggleElevenLabsLive() {
        val isActive = _uiState.value.isElevenLabsLiveActive
        if (isActive) {
            logManager.d(TAG, "Stopping ElevenLabs Live Session")
            vadLiveService.stop()
            _uiState.update { it.copy(isElevenLabsLiveActive = false) }
            updateTranscripts("VAD Session Ended")
            
            // Notify glasses
            viewModelScope.launch {
                btManager.sendMessage(Message(type = MessageType.LIVE_SESSION_END))
                cxrManager.sendTtsContent("Session stopped")
            }
        } else {
            logManager.d(TAG, "Starting ElevenLabs Live Session")
            val useInternal = _uiState.value.micSource == MicSource.PHONE
            vadLiveService.start(useInternalMic = useInternal)
            _uiState.update { it.copy(isElevenLabsLiveActive = true) }
            updateTranscripts("VAD Session Started - Listening...")
            
            // Notify glasses
            viewModelScope.launch {
                btManager.sendMessage(Message(type = MessageType.LIVE_SESSION_START))
                cxrManager.sendTtsContent("I'm listening")
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
