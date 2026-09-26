package com.example.ai_agent.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class HuggingFaceTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _token = MutableStateFlow(preferences.getString(KEY_TOKEN, null))
    val token: StateFlow<String?> = _token

    fun save(token: String) {
        preferences.edit().putString(KEY_TOKEN, token).apply()
        _token.value = token
    }

    fun clear() {
        preferences.edit().remove(KEY_TOKEN).apply()
        _token.value = null
    }

    companion object {
        private const val FILE_NAME = "secure_credentials"
        private const val KEY_TOKEN = "hugging_face_token"
    }
}
