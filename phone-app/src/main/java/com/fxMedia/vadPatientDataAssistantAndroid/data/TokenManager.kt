package com.fxMedia.vadPatientDataAssistantAndroid.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_token_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_SESSION_ID = "current_session_id"
    }

    fun saveToken(token: String) {
        sharedPreferences.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString(KEY_TOKEN, null)
    }

    fun deleteToken() {
        sharedPreferences.edit().remove(KEY_TOKEN).apply()
    }

    fun saveSessionId(sessionId: String?) {
        sharedPreferences.edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    fun getSessionId(): String? {
        return sharedPreferences.getString(KEY_SESSION_ID, null)
    }

    fun deleteSessionId() {
        sharedPreferences.edit().remove(KEY_SESSION_ID).apply()
    }
}
