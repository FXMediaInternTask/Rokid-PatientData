package com.fxMedia.patientDataAssistant.service.stt

import com.fxMedia.patientDataAssistant.service.SpeechResult

/**
 * Interface for Speech-to-Text services
 */
interface SttService {
    val provider: SttProvider
    
    /**
     * Transcribe audio data to text
     * @param audioData PCM 16-bit 16kHz mono audio data
     * @param languageCode BCP-47 language tag (e.g., "en-US", "zh-CN")
     */
    suspend fun transcribe(audioData: ByteArray, languageCode: String = "en-US"): SpeechResult
    
    /**
     * Validate the configured credentials
     */
    suspend fun validateCredentials(): SttValidationResult
}
