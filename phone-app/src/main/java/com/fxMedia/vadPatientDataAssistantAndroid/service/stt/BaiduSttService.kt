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
import java.util.*

/**
 * Baidu Cloud ASR (Speech Recognition)
 * 
 * API Docs: https://cloud.baidu.com/doc/SPEECH/s/ek6z02p9z
 * Auth: API Key + Secret Key (OAuth 2.0 to get Access Token)
 */
class BaiduSttService(
    private val apiKey: String,
    private val secretKey: String
) : BaseSttService() {
    
    companion object {
        private const val TAG = "BaiduSttService"
        private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        private const val ASR_URL = "https://vop.baidu.com/server_api"
    }
    
    override val provider = SttProvider.BAIDU_ASR
    
    private var cachedToken: String? = null
    private var tokenExpireTime: Long = 0
    
    private suspend fun getAccessToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime - 60000) {
            return cachedToken
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = "$TOKEN_URL?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey"
                val request = Request.Builder().url(url).build()
                
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        cachedToken = json.getString("access_token")
                        tokenExpireTime = System.currentTimeMillis() + (json.getLong("expires_in") * 1000)
                        cachedToken
                    } else {
                        Log.e(TAG, "Failed to get token: $body")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting token", e)
                null
            }
        }
    }
    
    override suspend fun transcribe(audioData: ByteArray, languageCode: String): SpeechResult {
        return withContext(Dispatchers.IO) {
            val token = getAccessToken() ?: return@withContext SpeechResult.Error(
                message = "Auth failed",
                errorCode = SpeechErrorCode.RECOGNITION_FAILED
            )
            
            if (isAudioTooShort(audioData)) {
                return@withContext SpeechResult.Error(
                    message = "Audio too short",
                    errorCode = SpeechErrorCode.AUDIO_TOO_SHORT
                )
            }
            
            val devPid = mapLanguageToDevPid(languageCode)
            val cuid = UUID.randomUUID().toString()
            
            val requestBody = JSONObject().apply {
                put("format", "pcm")
                put("rate", SAMPLE_RATE)
                put("channel", CHANNELS)
                put("token", token)
                put("cuid", cuid)
                put("len", audioData.size)
                put("speech", android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP))
                put("dev_pid", devPid)
            }
            
            val request = Request.Builder()
                .url(ASR_URL)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        val errNo = json.optInt("err_no", -1)
                        if (errNo == 0) {
                            val results = json.optJSONArray("result")
                            if (results != null && results.length() > 0) {
                                SpeechResult.Success(results.getString(0))
                            } else {
                                SpeechResult.Error("No results", errorCode = SpeechErrorCode.UNABLE_TO_RECOGNIZE)
                            }
                        } else {
                            Log.e(TAG, "ASR Error $errNo: ${json.optString("err_msg")}")
                            SpeechResult.Error("API Error $errNo", errorCode = SpeechErrorCode.RECOGNITION_FAILED)
                        }
                    } else {
                        SpeechResult.Error("Network error", errorCode = SpeechErrorCode.NETWORK_ERROR)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                SpeechResult.Error(e.message ?: "Unknown error", errorCode = SpeechErrorCode.TRANSCRIPTION_ERROR)
            }
        }
    }
    
    private fun mapLanguageToDevPid(languageCode: String): Int {
        return when {
            languageCode.startsWith("zh") -> 1537 // Mandarin (with punctuation)
            languageCode.startsWith("en") -> 1737 // English (with punctuation)
            else -> 1537
        }
    }
    
    override suspend fun validateCredentials(): SttValidationResult {
        return if (getAccessToken() != null) SttValidationResult.Valid 
        else SttValidationResult.Invalid(SttValidationError.INVALID_CREDENTIALS)
    }
}
