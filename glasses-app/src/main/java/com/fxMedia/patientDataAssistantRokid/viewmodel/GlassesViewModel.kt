package com.fxMedia.patientDataAssistantRokid.viewmodel

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fxMedia.patientDataAssistantRokid.sdk.CxrServiceManager
import com.fxMedia.patientDataAssistantRokid.service.BluetoothClientState
import com.fxMedia.patientDataAssistantRokid.service.BluetoothSppClient
import com.fxMedia.rokidcommon.Constants
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.rokidcommon.protocol.Message
import com.fxMedia.rokidcommon.protocol.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

sealed class AppScreen {
    object Main : AppScreen()
    object Recording : AppScreen()
    object Response : AppScreen()
}

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
    val aiResponse: String = "",
    val userTranscript: String = "",
    val suggestions: List<String> = emptyList()
)

class GlassesViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "GlassesViewModel"
    }

    private val _uiState = MutableStateFlow(GlassesUIState())
    val uiState: StateFlow<GlassesUIState> = _uiState.asStateFlow()

    private val _appScreen = MutableStateFlow<AppScreen>(AppScreen.Main)
    val appScreen: StateFlow<AppScreen> = _appScreen.asStateFlow()

    private val _scrollEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollEvent = _scrollEvent.asSharedFlow()

    private val bluetoothClient = BluetoothSppClient(context, viewModelScope)
    private var cxrServiceManager: CxrServiceManager? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val audioBuffer = ByteArrayOutputStream()
    
    private var mediaPlayer: MediaPlayer? = null
    private var pendingTtsAudio: ByteArray? = null

    init {
        initializeBluetooth()
        initializeCxrService()

    }

    /**
     * Handles the temple tap / ENTER key logic:
     * 1. If not connected -> Show device list
     * 2. If connected & idle -> Start recording
     * 3. If recording -> Stop and send
     */
    fun onPrimaryTap() {
        val currentState = _uiState.value
        
        // Stop any ongoing TTS playback when user interacts
        stopPlayback()
        
        if (!currentState.isConnected) {
            refreshPairedDevices()
            _uiState.update { it.copy(showDeviceSelector = true) }
            return
        }

        if (currentState.isListening) {
            stopRecording()
        } else {
            // Always start recording when connected and idle (Response or Main)
            startRecording()
        }
    }

    fun onNavigateUp() {
        viewModelScope.launch { _scrollEvent.emit(-1) }
    }

    fun onNavigateDown() {
        viewModelScope.launch { _scrollEvent.emit(1) }
    }

    fun requestPatientData() {
        if (!_uiState.value.isConnected) return
        if (_uiState.value.isProcessing) return

        _uiState.update {
            it.copy(
                isProcessing = true,
                displayText = "Fetching data...",
                hintText = "AI is looking up records",
                aiResponse = "",
                suggestions = emptyList()
            )
        }

        viewModelScope.launch {
            bluetoothClient.sendMessage(
                Message(
                    type = MessageType.USER_TRANSCRIPT,
                    payload = "Can you get me this patient data?"
                )
            )
        }
    }

    fun startRecording() {
        if (_uiState.value.bluetoothState != BluetoothClientState.CONNECTED) {
            _uiState.update {
                it.copy(
                    displayText = "Please connect phone",
                    hintText = "Select paired device"
                )
            }
            return
        }

        if (_uiState.value.isListening) return

        // Explicit permission check
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            _uiState.update {
                it.copy(
                    displayText = "Microphone permission required",
                    isListening = false
                )
            }
            return
        }

        _appScreen.value = AppScreen.Recording
        audioBuffer.reset()
        pendingTtsAudio = null // Clear any old audio buffer when starting new record

        _uiState.update {
            it.copy(
                isListening = true,
                displayText = "Listening...",
                hintText = "Tap to stop",
                userTranscript = "",
                aiResponse = "",
                suggestions = emptyList()
            )
        }

        viewModelScope.launch {
            bluetoothClient.sendVoiceStart()
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                @Suppress("MissingPermission")
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    Constants.AUDIO_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                audioRecord = recorder

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                displayText = "Tap to start",
                                isListening = false
                            )
                        }
                        _appScreen.value = AppScreen.Main
                    }
                    recorder.release()
                    audioRecord = null
                    return@launch
                }

                recorder.startRecording()
                Log.d(TAG, "AudioRecord started")

                val buffer = ByteArray(Constants.AUDIO_BUFFER_SIZE)
                while (isActive && _uiState.value.isListening) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(audioBuffer) { audioBuffer.write(buffer, 0, read) }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
            } finally {
                cleanupAudioRecord()
            }
        }
    }

    private fun cleanupAudioRecord() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord cleanup error", e)
        }
        audioRecord = null
    }

    fun stopRecording() {
        _uiState.update {
            it.copy(
                isListening = false,
                isProcessing = true,
                displayText = "Sending...",
                hintText = "Please wait"
            )
        }

        recordingJob?.cancel()
        recordingJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val audioData: ByteArray
                synchronized(audioBuffer) { audioData = audioBuffer.toByteArray() }

                if (audioData.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                displayText = "Tap to start",
                                hintText = "Tap to record"
                            )
                        }
                        _appScreen.value = AppScreen.Main
                    }
                    return@launch
                }

                val success = bluetoothClient.sendVoiceEnd(audioData)

                if (!success) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                displayText = "Tap to start",
                                hintText = "Tap to record"
                            )
                        }
                        _appScreen.value = AppScreen.Main
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            displayText = "Processing...",
                            hintText = "AI is thinking"
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping/sending", e)
            }
        }
    }

    fun dismissDeviceSelector() {
        _uiState.update { it.copy(showDeviceSelector = false) }
    }

    private fun handlePhoneMessage(message: Message) {
        when (message.type) {
            MessageType.USER_TRANSCRIPT -> {
                _uiState.update {
                    it.copy(
                        userTranscript = message.payload ?: "",
                        displayText = "You: ${message.payload ?: ""}"
                    )
                }
            }
            MessageType.AI_RESPONSE_TEXT -> {
                // Response back from API via Phone
                val text = message.payload ?: ""
                Log.d(TAG, "Received AI Response Text. Triggering UI and Pending TTS.")
                
                _appScreen.value = AppScreen.Response
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        aiResponse = text,
                        displayText = text,
                        hintText = "Tap to record again"
                    )
                }

                // Synchronization: Play audio only after text UI is ready
                pendingTtsAudio?.let { audioData ->
                    Log.d(TAG, "Playing buffered TTS audio (${audioData.size} bytes)")
                    playTtsAudio(audioData)
                    pendingTtsAudio = null
                }
            }
            MessageType.AI_RESPONSE_TTS -> {
                message.binaryData?.let { audioData ->
                    Log.d(TAG, "Received TTS audio data: ${audioData.size} bytes. Buffering until Text arrives.")
                    pendingTtsAudio = audioData
                }
            }
            MessageType.AI_SUGGESTIONS -> {
                val suggestionsText = message.payload ?: ""
                if (suggestionsText.isNotEmpty()) {
                    val suggestionsList = suggestionsText.split("|")
                    _uiState.update { it.copy(suggestions = suggestionsList) }
                }
            }
            MessageType.AI_ERROR -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        displayText = "Tap to start",
                        hintText = "Tap to record"
                    )
                }
                _appScreen.value = AppScreen.Main
            }
            MessageType.HEARTBEAT -> {
                viewModelScope.launch {
                    bluetoothClient.sendMessage(Message(type = MessageType.HEARTBEAT_ACK))
                }
            }
            else -> {}
        }
    }
    
    private fun playTtsAudio(audioData: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a temporary file to play the audio
                val tempFile = File(context.cacheDir, "tts_response.mp3")
                FileOutputStream(tempFile).use { it.write(audioData) }
                
                withContext(Dispatchers.Main) {
                    stopPlayback()
                    
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(tempFile.absolutePath)
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .build()
                        )
                        setOnPreparedListener { start() }
                        setOnCompletionListener {
                            it.release()
                            if (mediaPlayer == it) mediaPlayer = null
                            tempFile.delete()
                        }
                        setOnErrorListener { mp, _, _ ->
                            mp.release()
                            if (mediaPlayer == mp) mediaPlayer = null
                            tempFile.delete()
                            true
                        }
                        prepareAsync()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing TTS audio", e)
            }
        }
    }
    
    private fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
        mediaPlayer = null
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
                
                // On disconnect, force back to Main screen
                if (!isConnected) {
                    _appScreen.value = AppScreen.Main
                }

                _uiState.update {
                    it.copy(
                        bluetoothState = state,
                        isConnected = isConnected,
                        displayText = when (state) {
                            BluetoothClientState.DISCONNECTED -> "Not connected"
                            BluetoothClientState.CONNECTING -> "Waiting for device..." // "Waiting for device" as you suggested
                            BluetoothClientState.CONNECTED -> "Connected"
                        },
                        // FIX: Check for CONNECTING state here
                        hintText = when (state) {
                            BluetoothClientState.CONNECTED -> "Tap to record"
                            BluetoothClientState.CONNECTING -> "Connecting..."
                            else -> "Tap to connect"
                        }
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
        recordingJob?.cancel()
        cleanupAudioRecord()
        stopPlayback()
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
