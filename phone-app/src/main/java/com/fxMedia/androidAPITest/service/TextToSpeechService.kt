package com.fxMedia.androidAPITest.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fxMedia.androidAPITest.data.SettingsRepository
import com.fxMedia.androidAPITest.data.TtsProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*

class TextToSpeechService(private val context: Context) {
    private val TAG = "TextToSpeechService"
    private var tts: TextToSpeech? = null
    private var systemTtsReady = false
    private val edgeTtsClient = EdgeTtsClient()
    private val elevenLabsClient = ElevenLabsClient()
    private val settingsRepository = SettingsRepository.getInstance(context)
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
            val settings = settingsRepository.getSettings()
            val cleanedText = cleanMarkdown(text)
            
            Log.d(TAG, "TTS Flow Started. Provider: ${settings.ttsProvider}")
            Log.d(TAG, "Original text: $text")
            Log.d(TAG, "Cleaned text: $cleanedText")
            
            try {
                when (settings.ttsProvider) {
                    TtsProvider.ELEVENLABS -> {
                        Log.d(TAG, "ElevenLabs TTS: Synthesizing with VoiceID=${settings.elevenlabsVoiceId}")
                        if (settings.elevenlabsApiKey.isBlank()) {
                            Log.e(TAG, "ElevenLabs API Key is EMPTY. Falling back.")
                            speakWithEdgeTts(cleanedText, onAudioGenerated)
                            return@launch
                        }

                        val result = elevenLabsClient.synthesize(
                            text = cleanedText,
                            apiKey = settings.elevenlabsApiKey,
                            voiceId = settings.elevenlabsVoiceId,
                            stability = settings.elevenlabsStability,
                            similarityBoost = settings.elevenlabsSimilarityBoost,
                            modelId = settings.elevenlabsModelId,
                            speed = settings.elevenlabsSpeed
                        )
                        
                        result.onSuccess { audioData ->
                            Log.d(TAG, "ElevenLabs Success: Generated ${audioData.size} bytes. Triggering callback and local play.")
                            
                            // 1. Call the callback first so ViewModel can send to glasses
                            onAudioGenerated(audioData)
                            
                            // 2. Play locally on phone
//                            serviceScope.launch(Dispatchers.Main) {
//                                playAudioData(audioData)
//                            }
                        }.onFailure { err ->
                            Log.e(TAG, "ElevenLabs failed: ${err.message}. Falling back to Edge TTS")
                            speakWithEdgeTts(cleanedText, onAudioGenerated)
                        }
                    }
                    TtsProvider.EDGE_TTS -> {
                        Log.d(TAG, "Using Edge TTS (Microsoft)")
                        speakWithEdgeTts(cleanedText, onAudioGenerated)
                    }
                    TtsProvider.GOOGLE_TRANSLATE_TTS -> {
                        Log.d(TAG, "Using Google Translate TTS")
                        speakWithEdgeTts(cleanedText, onAudioGenerated)
                    }
                    TtsProvider.SYSTEM_TTS -> {
                        Log.d(TAG, "Using Android System TTS")
                        speakWithSystemTts(cleanedText, onAudioGenerated)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical TTS error", e)
                // Ensure callback is called so UI doesn't hang
                onAudioGenerated(ByteArray(0))
            }
        }
    }

    private suspend fun speakWithEdgeTts(text: String, onAudioGenerated: (ByteArray) -> Unit) {
        Log.d(TAG, "Edge TTS: Synthesizing...")
        val result = edgeTtsClient.synthesize(text)
        
        result.onSuccess { audioData ->
            if (audioData.isNotEmpty()) {
                Log.d(TAG, "Edge TTS Success: ${audioData.size} bytes. Triggering callback and local play.")
                onAudioGenerated(audioData)
                
                // Play locally on phone
//                serviceScope.launch(Dispatchers.Main) {
//                    playAudioData(audioData)
//                }
            } else {
                Log.w(TAG, "Edge TTS empty, falling back to System TTS")
                speakWithSystemTts(text, onAudioGenerated)
            }
        }.onFailure { err ->
            Log.w(TAG, "Edge TTS failed: ${err.message}, falling back to System TTS")
            speakWithSystemTts(text, onAudioGenerated)
        }
    }

    /**
     * Clean markdown characters from text to prevent TTS from reading them literally
     */
    private fun cleanMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*"), "") // Bold
            .replace(Regex("###"), "")    // Headers
            .replace(Regex("##"), "")
            .replace(Regex("#"), "")
            .replace(Regex("`"), "")      // Code
            .replace(Regex("__"), "")     // Italic/Bold
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Links
            .trim()
    }

    private fun speakWithSystemTts(text: String, onAudioGenerated: (ByteArray) -> Unit) {
        if (!systemTtsReady || tts == null) {
            Log.e(TAG, "System TTS not ready")
            onAudioGenerated(ByteArray(0)) // Call callback to unblock UI
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
                    Log.d(TAG, "System TTS Done: ${bytes.size} bytes. Triggering callback and local play.")
                    
                    // 1. Send to glasses via the callback
                    onAudioGenerated(bytes)

                    // 2. Play locally on phone so user hears it too
//                    serviceScope.launch(Dispatchers.Main) {
//                        playAudioData(bytes)
//                    }
                    tempFile.delete()
                }
            }
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "System TTS synthesis failed")
                onAudioGenerated(ByteArray(0)) // Unblock UI
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
