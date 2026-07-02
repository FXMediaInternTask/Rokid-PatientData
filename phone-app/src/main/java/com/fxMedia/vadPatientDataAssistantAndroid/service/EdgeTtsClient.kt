package com.fxMedia.vadPatientDataAssistantAndroid.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Microsoft Edge TTS Client
 * 
 * Uses Edge browser's TTS WebSocket API
 */
class EdgeTtsClient {
    
    companion object {
        private const val TAG = "EdgeTtsClient"
        
        // Edge TTS WebSocket endpoint
        private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        
        // User Agent - Updated to a newer version
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
        
        // Fixed Trusted Token
        private const val TRUSTED_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        
        // Timeout duration
        private const val TIMEOUT_SECONDS = 20L
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Synthesize speech
     */
    suspend fun synthesize(
        text: String,
        voice: String = "en-US-JennyNeural",
        rate: String = "+0%",
        pitch: String = "+0Hz"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting synthesis: voice=$voice, text=${text.take(30)}...")
            
            val audioData = ByteArrayOutputStream()
            val latch = CountDownLatch(1)
            var error: Exception? = null
            
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val wsUrl = "$WSS_URL?TrustedClientToken=$TRUSTED_TOKEN&ConnectionId=$requestId"
            
            val request = Request.Builder()
                .url(wsUrl)
                .header("User-Agent", USER_AGENT)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .build()
            
            val webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    webSocket.send(buildConfigMessage())
                    webSocket.send(buildSsmlMessage(requestId, text, voice, rate, pitch))
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val data = bytes.toByteArray()
                    val headerEnd = findHeaderEnd(data)
                    if (headerEnd > 0 && headerEnd < data.size) {
                        audioData.write(data, headerEnd, data.size - headerEnd)
                    }
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "Done")
                        latch.countDown()
                    }
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    if (response?.code == 403) {
                        error = Exception("Edge TTS blocked request (403 Forbidden).")
                    } else {
                        error = Exception(t.message)
                    }
                    latch.countDown()
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (latch.count > 0) latch.countDown()
                }
            })
            
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                webSocket.cancel()
                return@withContext Result.failure(Exception("Synthesis timeout"))
            }
            
            error?.let { return@withContext Result.failure(it) }
            
            val result = audioData.toByteArray()
            if (result.isEmpty()) return@withContext Result.failure(Exception("Empty result"))
            
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            Result.failure(e)
        }
    }
    
    private fun buildConfigMessage(): String {
        return "X-Timestamp:${getTimestamp()}\r\n" +
               "Content-Type:application/json; charset=utf-8\r\n" +
               "Path:speech.config\r\n\r\n" +
               "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
    }
    
    private fun buildSsmlMessage(requestId: String, text: String, voice: String, rate: String, pitch: String): String {
        val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                   "<voice name='$voice'><prosody pitch='$pitch' rate='$rate'>$escapedText</prosody></voice></speak>"
        
        return "X-RequestId:$requestId\r\n" +
               "Content-Type:application/ssml+xml\r\n" +
               "X-Timestamp:${getTimestamp()}\r\n" +
               "Path:ssml\r\n\r\n" +
               ssml
    }
    
    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.US)
        return sdf.format(Date())
    }
    
    private fun findHeaderEnd(data: ByteArray): Int {
        val marker = "Path:audio".toByteArray()
        for (i in 0 until data.size - marker.size) {
            if (data.sliceArray(i until i + marker.size).contentEquals(marker)) {
                for (k in (i + marker.size) until data.size - 1) {
                    if (data[k] == 0x00.toByte() && data[k + 1] == 0x82.toByte()) {
                        return k + 2
                    }
                }
                return i + marker.size + 2
            }
        }
        return -1
    }
}
