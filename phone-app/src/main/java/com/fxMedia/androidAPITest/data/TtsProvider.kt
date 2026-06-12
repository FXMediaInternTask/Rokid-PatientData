package com.fxMedia.androidAPITest.data

import com.fxMedia.androidAPITest.R

/**
 * Text-to-Speech provider options.
 *
 * EDGE_TTS uses Microsoft's high-quality neural voices via WebSocket (free).
 * SYSTEM_TTS uses the Android platform TTS engine (offline-capable).
 * GOOGLE_TRANSLATE_TTS uses Google Translate's TTS endpoint (free, average quality).
 */
enum class TtsProvider(
    val displayNameResId: Int,
    val descriptionResId: Int
) {
    EDGE_TTS(
        displayNameResId = R.string.tts_provider_edge,
        descriptionResId = R.string.tts_provider_edge_desc
    ),
    SYSTEM_TTS(
        displayNameResId = R.string.tts_provider_system,
        descriptionResId = R.string.tts_provider_system_desc
    ),
    GOOGLE_TRANSLATE_TTS(
        displayNameResId = R.string.tts_provider_google_translate,
        descriptionResId = R.string.tts_provider_google_translate_desc
    );

    companion object {
        /**
         * Parse a [TtsProvider] from its [name] string.
         * Returns [EDGE_TTS] when [name] is null, blank, or unrecognised.
         */
        fun fromName(name: String?): TtsProvider {
            if (name.isNullOrBlank()) return EDGE_TTS
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: EDGE_TTS
        }
    }
}
