package com.example.ai_agent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_agent.util.InferenceSettings
import com.example.ai_agent.voice.VoiceLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val voiceLanguageKey = stringPreferencesKey("voice_language")
    private val wakeWordEnabledKey = booleanPreferencesKey("wake_word_enabled")
    private val ttsEnabledKey = booleanPreferencesKey("tts_enabled")
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")
    private val voiceDownloadConsentedKey = booleanPreferencesKey("voice_download_consented")
    private val temperatureKey = floatPreferencesKey("temperature")
    private val topKKey = intPreferencesKey("top_k")
    private val maxTokensKey = intPreferencesKey("max_tokens")
    private val jarvisHudEnabledKey = booleanPreferencesKey("jarvis_hud_enabled")

    val voiceLanguage: Flow<VoiceLanguage> = context.dataStore.data.map { preferences ->
        val stored = when (preferences[voiceLanguageKey]) {
            VoiceLanguage.HINDI.code -> VoiceLanguage.HINDI
            else -> VoiceLanguage.ENGLISH
        }
        VoiceLanguage.normalize(stored)
    }

    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[wakeWordEnabledKey] ?: false
    }

    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ttsEnabledKey] ?: true
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[onboardingCompleteKey] ?: false
    }

    val voiceDownloadConsented: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[voiceDownloadConsentedKey] ?: false
    }

    val jarvisHudEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[jarvisHudEnabledKey] ?: false
    }

    val inferenceSettings: Flow<InferenceSettings> = context.dataStore.data.map { preferences ->
        InferenceSettings(
            temperature = preferences[temperatureKey] ?: 0.8f,
            topK = preferences[topKKey] ?: 40,
            maxTokens = preferences[maxTokensKey] ?: 512,
        )
    }

    suspend fun setVoiceLanguage(language: VoiceLanguage) {
        context.dataStore.edit { preferences ->
            preferences[voiceLanguageKey] = VoiceLanguage.normalize(language).code
        }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[wakeWordEnabledKey] = enabled
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ttsEnabledKey] = enabled
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[onboardingCompleteKey] = complete
        }
    }

    suspend fun setVoiceDownloadConsented(consented: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[voiceDownloadConsentedKey] = consented
        }
    }

    suspend fun setJarvisHudEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[jarvisHudEnabledKey] = enabled
        }
    }

    suspend fun setInferenceSettings(settings: InferenceSettings) {
        context.dataStore.edit { preferences ->
            preferences[temperatureKey] = settings.temperature
            preferences[topKKey] = settings.topK
            preferences[maxTokensKey] = settings.maxTokens
        }
    }
}
