package com.fxMedia.androidAPITest.service.ai

import android.content.Context
import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * Custom Live Service using WebRTC VAD
 * Flow: Detect Speech -> Record -> Trigger STT
 */
class VadLiveService(
    private val context: Context,
    private val onSpeechCaptured: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "VadLiveService"
        private const val SILENCE_THRESHOLD_MS = 1000L // Wait for 1s of silence before finishing
        private const val FRAME_SIZE_BYTES = 640 // 20ms at 16kHz 16-bit mono (320 samples * 2 bytes)
    }

    private val liveAudioManager = LiveAudioManager(context)
    private var vad: VadWebRTC? = null
    
    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()
    
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking = _isUserSpeaking.asStateFlow()

    private var recordingBuffer = ByteArrayOutputStream()
    private var lastSpeechTime = 0L
    private var isCurrentlyRecording = false
    private var isPaused = false

    fun start() {
        if (_isActive.value) return
        
        Log.d(TAG, "Starting VadLiveService")
        isPaused = false
        
        // Initialize VAD
        vad = VadWebRTC(
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_320, // 20ms
            mode = Mode.VERY_AGGRESSIVE,
            silenceDurationMs = 300,
            speechDurationMs = 50
        )

        liveAudioManager.onAudioChunk = { chunk ->
            if (!isPaused) {
                processAudioChunk(chunk)
            }
        }

        if (liveAudioManager.startRecording()) {
            _isActive.value = true
        } else {
            Log.e(TAG, "Failed to start LiveAudioManager")
        }
    }

    /**
     * Feed external audio data (e.g. from glasses) into VAD
     */
    fun feedAudio(chunk: ByteArray) {
        if (!_isActive.value || isPaused) return
        processAudioChunk(chunk)
    }

    fun stop() {
        Log.d(TAG, "Stopping VadLiveService")
        _isActive.value = false
        _isUserSpeaking.value = false
        liveAudioManager.stopRecording()
        vad?.close()
        vad = null
        isCurrentlyRecording = false
    }

    fun pause() {
        Log.d(TAG, "Pausing VAD detection")
        isPaused = true
        _isUserSpeaking.value = false
    }

    fun resume() {
        Log.d(TAG, "Resuming VAD detection")
        isPaused = false
    }

    /**
     * Play PCM audio via LiveAudioManager (handles echo cancellation)
     */
    fun playAudio(pcmData: ByteArray) {
        liveAudioManager.playAudio(pcmData)
        
        // Also finish playback after queue is empty
        liveAudioManager.onPlaybackComplete = {
            resume()
        }
        
        pause()
    }

    private fun processAudioChunk(chunk: ByteArray) {
        // LiveAudioManager sends 100ms chunks, we need to process them in 20ms frames
        var offset = 0
        while (offset + FRAME_SIZE_BYTES <= chunk.size) {
            val frame = chunk.copyOfRange(offset, offset + FRAME_SIZE_BYTES)
            val isSpeech = vad?.isSpeech(frame) ?: false
            
            handleFrame(frame, isSpeech)
            offset += FRAME_SIZE_BYTES
        }
    }

    private fun handleFrame(frame: ByteArray, isSpeech: Boolean) {
        if (isSpeech) {
            lastSpeechTime = System.currentTimeMillis()
            if (!isCurrentlyRecording) {
                Log.d(TAG, "Speech detected, starting capture")
                isCurrentlyRecording = true
                _isUserSpeaking.value = true
                recordingBuffer.reset()
            }
            recordingBuffer.write(frame)
        } else {
            if (isCurrentlyRecording) {
                recordingBuffer.write(frame) // Keep recording during short silence
                
                val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                if (silenceDuration > SILENCE_THRESHOLD_MS) {
                    Log.d(TAG, "Silence detected, finishing capture. Duration: ${recordingBuffer.size()} bytes")
                    finishCapture()
                }
            }
        }
    }

    private fun finishCapture() {
        isCurrentlyRecording = false
        _isUserSpeaking.value = false
        val audioData = recordingBuffer.toByteArray()
        recordingBuffer.reset()
        
        if (audioData.size > 8000) { // Minimum ~0.25s of audio to avoid triggers by noise spikes
            onSpeechCaptured(audioData)
        }
    }
}
