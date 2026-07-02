package com.fxMedia.vadPatientDataAssistantAndroid.service.stt

import android.util.Log
import com.fxMedia.vadPatientDataAssistantAndroid.service.SpeechErrorCode
import com.fxMedia.vadPatientDataAssistantAndroid.service.SpeechResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Microsoft Azure AI Speech Service (Speech-to-Text)
 * 
 * API Docs: https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-speech-to-text
 * 
 * This service uses the REST API (v1.0) for short audio recognition (up to 60 seconds).
 */
class AzureSpeechSttService(
    private val subscriptionKey: String,
    private val region: String,
    internal val baseEndpoint: String? = null,
    internal val baseTokenEndpoint: String? = null
) : BaseSttService() {
    
    companion object {
        private const val TAG = "AzureSpeechSttService"
    }
    
    override val provider = SttProvider.AZURE_SPEECH
    
    // REST API Endpoint for short audio
    private val endpoint: String
        get() = baseEndpoint ?: "https://$region.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"

    override suspend fun transcribe(audioData: ByteArray, languageCode: String): SpeechResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting Azure Speech transcription, audio size: ${audioData.size} bytes")
            
            if (isAudioTooShort(audioData)) {
                return@withContext SpeechResult.Error(
                    message = "Audio too short",
                    errorCode = SpeechErrorCode.AUDIO_TOO_SHORT
                )
            }
            
            // 1. Determine if we need to wrap in WAV
            val isAlreadyWav = isWav(audioData)
            val wavData = if (isAlreadyWav) {
                Log.d(TAG, "Audio is already WAV format, skipping conversion")
                audioData
            } else {
                Log.d(TAG, "Audio is raw PCM, converting to WAV header")
                pcmToWav(audioData)
            }
            
            // Map language code to Azure format
            val azureLanguage = mapLanguageCode(languageCode)
            
            val url = "$endpoint?language=$azureLanguage&format=detailed"
            
            val result = executeWithRetry(TAG) { attempt ->
                Log.d(TAG, "Sending Azure Speech request (attempt $attempt)")
                
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .addHeader("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                    .addHeader("Accept", "application/json")
                    .post(wavData.toRequestBody("audio/wav".toMediaType()))
                    .build()
                
                try {
                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Azure Response Code: ${response.code}")
                        Log.d(TAG, "Full Azure Response: $responseBody")
                        
                        if (response.isSuccessful) {
                            parseTranscript(responseBody)
                        } else {
                            Log.e(TAG, "API error: ${response.code}, body: $responseBody")
                            null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Azure Request Exception: ${e.message}")
                    null
                }
            }
            
            if (result != null) {
                SpeechResult.Success(result)
            } else {
                SpeechResult.Error(
                    message = "Please try again",
                    errorCode = SpeechErrorCode.UNABLE_TO_RECOGNIZE
                )
            }
        }
    }
    
    private fun isWav(data: ByteArray): Boolean {
        return data.size > 12 && 
               data[0] == 'R'.toByte() && data[1] == 'I'.toByte() && 
               data[2] == 'F'.toByte() && data[3] == 'F'.toByte()
    }
    
    private fun mapLanguageCode(languageCode: String): String {
        // Azure uses BCP-47 language tags
        return when {
            languageCode.startsWith("zh-CN") || languageCode == "zh" -> "zh-CN"
            languageCode.startsWith("zh-TW") -> "zh-TW"
            languageCode.startsWith("zh-HK") -> "zh-HK"
            languageCode.startsWith("en") -> "en-US"
            languageCode.startsWith("ja") -> "ja-JP"
            languageCode.startsWith("ko") -> "ko-KR"
            languageCode.startsWith("fr") -> "fr-FR"
            languageCode.startsWith("de") -> "de-DE"
            languageCode.startsWith("es") -> "es-ES"
            languageCode.startsWith("it") -> "it-IT"
            languageCode.startsWith("ru") -> "ru-RU"
            languageCode.startsWith("th") -> "th-TH"
            languageCode.startsWith("vi") -> "vi-VN"
            languageCode.startsWith("ar") -> "ar-SA"
            else -> languageCode
        }
    }
    
    private fun parseTranscript(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            val status = json.optString("RecognitionStatus")
            
            if (status == "Success") {
                // For detailed format, get the best result
                val nBest = json.optJSONArray("NBest")
                if (nBest != null && nBest.length() > 0) {
                    val best = nBest.getJSONObject(0)
                    val display = best.optString("Display", "").trim()
                    if (display.isNotEmpty()) {
                        Log.d(TAG, "Transcription: $display")
                        return display
                    }
                }
                
                // Fallback to DisplayText
                val displayText = json.optString("DisplayText", "").trim()
                if (displayText.isNotEmpty()) {
                    Log.d(TAG, "Transcription: $displayText")
                    displayText
                } else {
                    null
                }
            } else {
                Log.w(TAG, "Recognition status: $status")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
            null
        }
    }
    
    override suspend fun validateCredentials(): SttValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                // Validation via token issue endpoint (fastest way to check key)
                val tokenUrl = baseTokenEndpoint ?: "https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken"
                val request = Request.Builder()
                    .url(tokenUrl)
                    .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                    .post("".toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Azure credentials validated successfully")
                        SttValidationResult.Valid
                    } else {
                        Log.w(TAG, "Azure credential validation failed: ${response.code}")
                        SttValidationResult.Invalid(mapHttpStatusToError(response.code))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Azure connection error: ${e.message}")
                SttValidationResult.Invalid(SttValidationError.NETWORK_ERROR)
            }
        }
    }
}
