package com.fxMedia.rokidaiassistant.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method
import java.util.Locale

/**
 * Unit tests for TextToSpeechService (app module).
 * Tests the voice-selection and locale-detection helpers via reflection
 * since both are private methods.
 */
@RunWith(RobolectricTestRunner::class)
class TextToSpeechServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createService(preferredLocale: Locale? = null): TextToSpeechService =
        TextToSpeechService(context, preferredLocale)

    /**
     * Invoke selectEdgeVoiceForText(text, voiceOverride, preferredLocale).
     * The method is now public, so we call it directly.
     */
    private fun selectVoice(
        service: TextToSpeechService,
        text: String,
        preferredLocale: Locale? = null,
        voiceOverride: String = ""
    ): String {
        return service.selectEdgeVoiceForText(text, voiceOverride, preferredLocale)
    }

    /** Invoke detectLocaleForText(text) via reflection. */
    private fun detectLocale(service: TextToSpeechService, text: String): Locale {
        val method: Method = TextToSpeechService::class.java.getDeclaredMethod(
            "detectLocaleForText", String::class.java
        )
        method.isAccessible = true
        return method.invoke(service, text) as Locale
    }

    // ==================== selectEdgeVoiceForText ====================

    @Test
    fun `selectEdgeVoiceForText - Korean Hangul selects Korean voice`() {
        val service = createService()
        val voice = selectVoice(service, "안녕하세요")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_SUNHI)
    }

    @Test
    fun `selectEdgeVoiceForText - CJK Unified selects Chinese voice`() {
        val service = createService()
        val voice = selectVoice(service, "你好世界")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_XIAOXIAO)
    }

    @Test
    fun `selectEdgeVoiceForText - Hiragana selects Japanese voice`() {
        val service = createService()
        val voice = selectVoice(service, "こんにちは")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_NANAMI)
    }

    @Test
    fun `selectEdgeVoiceForText - Katakana selects Japanese voice`() {
        val service = createService()
        val voice = selectVoice(service, "コンニチハ")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_NANAMI)
    }

    @Test
    fun `selectEdgeVoiceForText - Latin text selects English voice`() {
        val service = createService()
        val voice = selectVoice(service, "Hello world")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_EN)
    }

    @Test
    fun `selectEdgeVoiceForText - Korean takes priority over Chinese in mixed text`() {
        val service = createService()
        // Korean Hangul checked first
        val voice = selectVoice(service, "안녕 你好")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_SUNHI)
    }

    @Test
    fun `selectEdgeVoiceForText - unknown script with ko locale selects Korean voice`() {
        val service = createService()
        // No CJK/Hangul/Kana/Latin — falls through to preferredLocale branch
        val voice = selectVoice(service, "١٢٣", Locale.KOREAN)
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_SUNHI)
    }

    @Test
    fun `selectEdgeVoiceForText - unknown script with ja locale selects Japanese voice`() {
        val service = createService()
        val voice = selectVoice(service, "١٢٣", Locale.JAPANESE)
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_NANAMI)
    }

    @Test
    fun `selectEdgeVoiceForText - unknown script with zh locale selects Chinese voice`() {
        val service = createService()
        val voice = selectVoice(service, "١٢٣", Locale.CHINESE)
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_XIAOXIAO)
    }

    @Test
    fun `selectEdgeVoiceForText - unknown script with no locale defaults to English voice`() {
        val service = createService()
        val voice = selectVoice(service, "١٢٣", null)
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_EN)
    }

    @Test
    fun `selectEdgeVoiceForText - unknown script with unrelated locale defaults to English voice`() {
        val service = createService()
        val voice = selectVoice(service, "١٢٣", Locale.FRENCH)
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_EN)
    }

    // ==================== detectLocaleForText ====================

    @Test
    fun `detectLocaleForText - Korean text returns KOREAN locale`() {
        val service = createService()
        val locale = detectLocale(service, "안녕하세요")
        assertThat(locale.language).isEqualTo(Locale.KOREAN.language)
    }

    @Test
    fun `detectLocaleForText - Chinese CJK returns TRADITIONAL_CHINESE locale`() {
        val service = createService()
        val locale = detectLocale(service, "你好世界")
        assertThat(locale.language).isEqualTo(Locale.TRADITIONAL_CHINESE.language)
    }

    @Test
    fun `detectLocaleForText - Japanese kana returns JAPANESE locale`() {
        val service = createService()
        val locale = detectLocale(service, "こんにちは")
        assertThat(locale.language).isEqualTo(Locale.JAPANESE.language)
    }

    @Test
    fun `detectLocaleForText - Latin text returns system default locale`() {
        val service = createService()
        val locale = detectLocale(service, "Hello world")
        assertThat(locale).isEqualTo(Locale.getDefault())
    }

    @Test
    fun `detectLocaleForText - empty text returns system default locale`() {
        val service = createService()
        val locale = detectLocale(service, "")
        assertThat(locale).isEqualTo(Locale.getDefault())
    }

    // ==================== Voice constants ====================

    @Test
    fun `DEFAULT_VOICE is the English voice constant`() {
        assertThat(TextToSpeechService.DEFAULT_VOICE).isEqualTo(TextToSpeechService.EDGE_VOICE_EN)
    }

    @Test
    fun `EDGE_VOICE_SUNHI is the Korean voice`() {
        assertThat(TextToSpeechService.EDGE_VOICE_SUNHI).contains("ko-KR")
    }

    @Test
    fun `EDGE_VOICE_NANAMI is the Japanese voice`() {
        assertThat(TextToSpeechService.EDGE_VOICE_NANAMI).contains("ja-JP")
    }

    // ==================== voiceOverride ====================

    @Test
    fun `selectEdgeVoiceForText - voiceOverride bypasses script detection`() {
        // 測試：明確的語音覆寫應跳過腳本偵測
        val service = createService()
        val overrideVoice = "en-GB-SoniaNeural"
        // Korean text would normally select SunHi, but override wins
        val voice = selectVoice(service, "안녕하세요", voiceOverride = overrideVoice)
        assertThat(voice).isEqualTo(overrideVoice)
    }

    @Test
    fun `selectEdgeVoiceForText - blank voiceOverride uses normal detection`() {
        // 測試：空白覆寫應回退到正常偵測
        val service = createService()
        val voice = selectVoice(service, "안녕하세요", voiceOverride = "")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_SUNHI)
    }

    @Test
    fun `selectEdgeVoiceForText - whitespace-only voiceOverride uses normal detection`() {
        // 測試：僅空白字元的覆寫應回退到正常偵測
        val service = createService()
        val voice = selectVoice(service, "こんにちは", voiceOverride = "   ")
        assertThat(voice).isEqualTo(TextToSpeechService.EDGE_VOICE_NANAMI)
    }

    @Test
    fun `selectEdgeVoiceForText - voiceOverride works for arbitrary voice names`() {
        // 測試：覆寫可使用任意語音名稱
        val service = createService()
        val customVoice = "zh-TW-HsiaoChenNeural"
        val voice = selectVoice(service, "Hello world", voiceOverride = customVoice)
        assertThat(voice).isEqualTo(customVoice)
    }

    @Test
    fun `selectEdgeVoiceForText - voiceOverride takes priority over preferredLocale`() {
        // 測試：覆寫的優先級高於偏好地區設定
        val service = createService()
        val voice = selectVoice(
            service,
            "١٢٣",    // no detectable script
            preferredLocale = Locale.KOREAN,
            voiceOverride = "ja-JP-KeitaNeural"
        )
        assertThat(voice).isEqualTo("ja-JP-KeitaNeural")
    }
}
