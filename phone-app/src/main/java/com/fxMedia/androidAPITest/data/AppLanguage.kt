package com.fxMedia.androidAPITest.data

import androidx.annotation.StringRes
import com.fxMedia.androidAPITest.R
import java.util.Locale

/**
 * Supported App Languages
 */
enum class AppLanguage(
    val code: String,
    @get:StringRes val displayNameResId: Int,
    val flagEmoji: String
) {
    ENGLISH("en", R.string.lang_en, "🇺🇸"),
    CHINESE_SIMPLIFIED("zh-CN", R.string.lang_zh_cn, "🇨🇳"),
    CHINESE_TRADITIONAL("zh-TW", R.string.lang_zh_tw, "🇭🇰"),
    KOREAN("ko", R.string.lang_ko, "🇰🇷"),
    JAPANESE("ja", R.string.lang_ja, "🇯🇵"),
    FRENCH("fr", R.string.lang_fr, "🇫🇷"),
    GERMAN("de", R.string.lang_de, "🇩🇪"),
    SPANISH("es", R.string.lang_es, "🇪🇸"),
    ITALIAN("it", R.string.lang_it, "🇮🇹"),
    RUSSIAN("ru", R.string.lang_ru, "🇷🇺"),
    THAI("th", R.string.lang_th, "🇹🇭"),
    VIETNAMESE("vi", R.string.lang_vi, "🇻🇳");

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code || code.startsWith(it.code) } ?: ENGLISH
        }

        fun fromLocale(locale: Locale): AppLanguage {
            return fromCode(locale.toLanguageTag())
        }
    }
}
