package com.example.ai_agent.voice

enum class VoiceLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi"),
    ;

    companion object {
        /** Set to true to re-enable Hindi in the UI and voice pipeline. */
        const val HINDI_ENABLED = false

        fun normalize(language: VoiceLanguage): VoiceLanguage =
            if (language == HINDI && !HINDI_ENABLED) ENGLISH else language
    }
}
