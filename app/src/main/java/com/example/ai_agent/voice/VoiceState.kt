package com.example.ai_agent.voice

sealed interface VoiceState {
    data object Idle : VoiceState
    data object DownloadingModels : VoiceState
    data class Downloading(
        val progress: Int,
        val label: String,
        val downloadedMb: Long = 0,
        val totalMb: Long = 0,
    ) : VoiceState
    data class Installing(val label: String) : VoiceState
    data object ListeningForWakeWord : VoiceState
    data object Recording : VoiceState
    data object Transcribing : VoiceState
    data object Speaking : VoiceState
    data class Error(val message: String) : VoiceState
}
