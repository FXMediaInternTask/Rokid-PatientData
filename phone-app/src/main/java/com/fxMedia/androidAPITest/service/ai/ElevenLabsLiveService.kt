package com.fxMedia.androidAPITest.service.ai

import android.content.Context
import android.util.Log
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import io.elevenlabs.models.ConversationStatus
import io.elevenlabs.models.ConversationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ElevenLabs Conversational AI Service (SDK-based)
 */
class ElevenLabsLiveService(private val context: Context) {
    companion object {
        private const val TAG = "ElevenLabsLiveService"
    }

    private var session: ConversationSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _status = MutableStateFlow(ConversationStatus.DISCONNECTED)
    val status = _status.asStateFlow()
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    fun startSession(agentId: String) {
        if (session != null) {
            Log.w(TAG, "Session already active")
            return
        }

        val config = ConversationConfig(
            agentId = agentId,
            onStatusChange = { newStatus ->
                Log.d(TAG, "Status: $newStatus")
                _status.value = newStatus
            },
            onModeChange = { mode ->
                Log.d(TAG, "Mode: $mode")
                _isSpeaking.value = (mode == ConversationMode.SPEAKING)
            }
        )

        serviceScope.launch {
            try {
                session = ConversationClient.startSession(config, context)
                Log.d(TAG, "ElevenLabs Session Started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ElevenLabs session", e)
            }
        }
    }

    fun endSession() {
        val currentSession = session
        session = null
        serviceScope.launch {
            try {
                currentSession?.endSession()
                Log.d(TAG, "ElevenLabs Session Ended")
            } catch (e: Exception) {
                Log.e(TAG, "Error ending ElevenLabs session", e)
            } finally {
                _status.value = ConversationStatus.DISCONNECTED
                _isSpeaking.value = false
            }
        }
    }
}
