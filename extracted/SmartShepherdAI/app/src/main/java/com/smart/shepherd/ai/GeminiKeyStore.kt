package com.smart.shepherd.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class GeminiKeyStore(context: Context) {
    private val prefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_ai_settings", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("ai_settings_fallback", Context.MODE_PRIVATE)
    }

    fun saveKey(key: String) { prefs.edit().putString("gemini_api_key", key).apply() }
    fun getKey(): String? = prefs.getString("gemini_api_key", null)
}
