package com.fxMedia.vadPatientDataAssistantAndroid.service

import android.util.Log
import com.fxMedia.vadPatientDataAssistantAndroid.service.ai.NetworkClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * ElevenLabs TTS Client
 * Handles synthesis using ElevenLabs REST API
 */
class ElevenLabsClient {
    companion object {
        private const val TAG = "ElevenLabsClient"
        private const val BASE_URL = "https://api.elevenlabs.io/v1"
        
        // Default high-quality multilingual model
        private const val DEFAULT_MODEL = "eleven_multilingual_v2"
        
        // Default voice (Rachel) if none specified
        private const val DEFAULT_VOICE_ID = "ljEOxtzNoGEa58anWyea"
    }

    private val client = NetworkClientFactory.createClient()

    /**
     * Synthesize text to speech using ElevenLabs API
     */
    suspend fun synthesize(
        text: String,
        apiKey: String,
        voiceId: String = "",
        modelId: String = DEFAULT_MODEL,
        stability: Float = 0.5f,
        similarityBoost: Float = 0.75f,
        speed: Float = 1.0f
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("ElevenLabs API Key is missing"))
        }

        val targetVoiceId = if (voiceId.isBlank()) DEFAULT_VOICE_ID else voiceId

        try {
            val url = "$BASE_URL/text-to-speech/$targetVoiceId"
            
            val json = JSONObject().apply {
                put("text", text)
                put("model_id", modelId)
                put("voice_settings", JSONObject().apply {
                    put("stability", stability.toDouble())
                    put("similarity_boost", similarityBoost.toDouble())
                    put("speed", speed.toDouble())
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Sending TTS request to ElevenLabs: voiceId=$targetVoiceId, model=$modelId")
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.d(TAG, "ElevenLabs success: ${bytes.size} bytes")
                        Result.success(bytes)
                    } else {
                        Log.e(TAG, "ElevenLabs returned empty body")
                        Result.failure(Exception("ElevenLabs returned empty audio data"))
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "ElevenLabs error: ${response.code}, body=$errorBody")
                    Result.failure(Exception("ElevenLabs API failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs synthesis failed", e)
            Result.failure(e)
        }
    }
}
