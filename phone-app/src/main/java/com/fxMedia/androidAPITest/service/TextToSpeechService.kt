package com.fxMedia.androidAPITest.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

class TextToSpeechService(private val context: Context) {
    private val TAG = "TextToSpeechService"
    private var tts: TextToSpeech? = null
    private var systemTtsReady = false
    private val edgeTtsClient = EdgeTtsClient()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val defaultLocale = Locale.getDefault()
                val langResult = tts?.setLanguage(defaultLocale)
                systemTtsReady = langResult != TextToSpeech.LANG_MISSING_DATA
                        && langResult != TextToSpeech.LANG_NOT_SUPPORTED
                Log.d(TAG, "System TTS Ready: $systemTtsReady")
            } else {
                Log.e(TAG, "System TTS initialization failed")
            }
        }
    }

    fun speak(text: String, onAudioGenerated: (ByteArray) -> Unit) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Edge TTS: Synthesizing...")
                val result = edgeTtsClient.synthesize(text)
                
                result.onSuccess { audioData ->
                    if (audioData.isNotEmpty()) {
                        Log.d(TAG, "Edge TTS Success: ${audioData.size} bytes")
                        onAudioGenerated(audioData)
                        //play on android
//                        withContext(Dispatchers.Main) {
//                            playAudioData(audioData)
//                        }
                    } else {
                        Log.w(TAG, "Edge TTS empty, falling back to System TTS")
                        speakWithSystemTts(text, onAudioGenerated)
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Edge TTS failed: ${err.message}, falling back to System TTS")
                    speakWithSystemTts(text, onAudioGenerated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS error", e)
                speakWithSystemTts(text, onAudioGenerated)
            }
        }
    }

    private fun speakWithSystemTts(text: String, onAudioGenerated: (ByteArray) -> Unit) {
        if (!systemTtsReady || tts == null) {
            Log.e(TAG, "System TTS not ready")
            return
        }
        val locale = detectLocaleForText(text)
        tts?.setLanguage(locale)

        // Create a temporary file to capture the system speech
        val tempFile = File(context.cacheDir, "sys_tts_${System.currentTimeMillis()}.wav")

        // Set a listener to know when the audio file is ready
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "sync_to_glasses") {
                    val bytes = tempFile.readBytes()
                    if (bytes.isNotEmpty()) {
                        // 1. Send to glasses via the callback
                        onAudioGenerated(bytes)

                        // 2. Play locally on phone so user hears it too
//                        serviceScope.launch(Dispatchers.Main) {
//                            playAudioData(bytes)
//                        }
                    }
                    tempFile.delete()
                }
            }
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "System TTS synthesis failed")
                tempFile.delete()
            }
        })

        // This captures the speech to the file instead of just playing to speaker
        tts?.synthesizeToFile(text, null, tempFile, "sync_to_glasses")
    }

    private fun playAudioData(audioData: ByteArray) {
        try {
            val tempFile = File.createTempFile("tts_", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(audioData) }
            
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setOnCompletionListener { mp -> mp.release(); tempFile.delete() }
                setOnErrorListener { mp, _, _ -> mp.release(); tempFile.delete(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
        }
    }

    private fun detectLocaleForText(text: String): Locale {
        return when {
            text.any { it in '\u4E00'..'\u9FFF' } -> Locale.CHINESE
            text.any { it in '\uAC00'..'\uD7AF' } -> Locale.KOREAN
            text.any { it in '\u3040'..'\u30FF' } -> Locale.JAPANESE
            else -> Locale.ENGLISH
        }
    }

    fun shutdown() {
        serviceScope.cancel()
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
    }
}
