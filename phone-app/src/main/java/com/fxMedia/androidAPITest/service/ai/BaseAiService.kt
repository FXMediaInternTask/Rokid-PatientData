package com.fxMedia.androidAPITest.service.ai

import android.util.Log
import com.fxMedia.androidAPITest.service.stt.BaseSttService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.concurrent.TimeUnit

/**
 * Base class for AI Service implementations
 */
abstract class BaseAiService(
    protected val apiKey: String,
    protected val modelId: String,
    protected val systemPrompt: String = "",
    protected val temperature: Float = 0.7f,
    protected val maxTokens: Int = 2048,
    protected val topP: Float = 1.0f,
    protected val frequencyPenalty: Float = 0.0f,
    protected val presencePenalty: Float = 0.0f
) : AiServiceProvider {
    
    protected val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    protected val conversationHistory = mutableListOf<Pair<String, String>>()
    
    protected fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    protected fun logError(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
    
    protected fun getFullSystemPrompt(): String {
        return systemPrompt
    }
    
    protected fun addToHistory(role: String, content: String) {
        conversationHistory.add(role to content)
        if (conversationHistory.size > 20) {
            repeat(2) { conversationHistory.removeAt(0) }
        }
    }
    
    override fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * Convert PCM audio data to WAV format
     */
    protected fun pcmToWav(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        
        val output = ByteArrayOutputStream()
        val dos = DataOutputStream(output)
        
        dos.writeBytes("RIFF")
        dos.writeInt(Integer.reverseBytes(36 + pcmData.size))
        dos.writeBytes("WAVE")
        dos.writeBytes("fmt ")
        dos.writeInt(Integer.reverseBytes(16))
        dos.writeShort(java.lang.Short.reverseBytes(1).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
        dos.writeInt(Integer.reverseBytes(sampleRate))
        dos.writeInt(Integer.reverseBytes(byteRate))
        dos.writeShort(java.lang.Short.reverseBytes(blockAlign.toShort()).toInt())
        dos.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
        dos.writeBytes("data")
        dos.writeInt(Integer.reverseBytes(pcmData.size))
        dos.write(pcmData)
        
        dos.flush()
        return output.toByteArray()
    }

    protected suspend fun <T> executeWithRetry(
        tag: String,
        maxRetries: Int = 3,
        block: suspend (attempt: Int) -> T?
    ): T? {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            repeat(maxRetries) { attempt ->
                try {
                    val result = block(attempt + 1)
                    if (result != null) return@withContext result
                } catch (e: Exception) {
                    Log.w(tag, "Attempt ${attempt + 1} failed: ${e.message}")
                    lastException = e
                    if (attempt < maxRetries - 1) kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
            if (lastException != null) Log.e(tag, "All $maxRetries attempts failed", lastException)
            null
        }
    }
}
