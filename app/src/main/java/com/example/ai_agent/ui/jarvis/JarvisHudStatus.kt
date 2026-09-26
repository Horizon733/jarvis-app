package com.example.ai_agent.ui.jarvis

import com.example.ai_agent.domain.model.LlmState
import com.example.ai_agent.voice.VoiceState

fun jarvisHudStatusText(
    voiceState: VoiceState,
    isGenerating: Boolean,
    llmState: LlmState,
): String {
    if (llmState is LlmState.Loading) return "Loading model…"
    if (llmState is LlmState.Error) return "Model error"
    if (isGenerating) return "Thinking…"
    val raw = when (voiceState) {
        VoiceState.Idle -> "Ready"
        VoiceState.DownloadingModels -> "Downloading voice…"
        is VoiceState.Downloading -> "Downloading voice…"
        is VoiceState.Installing -> "Installing voice…"
        VoiceState.ListeningForWakeWord -> "Hey Jarvis"
        VoiceState.Recording -> "Listening…"
        VoiceState.Transcribing -> "Transcribing…"
        VoiceState.Speaking -> "Speaking"
        is VoiceState.Error -> voiceState.message
    }
    return sanitizeHudStatus(raw)
}

fun sanitizeHudMessage(text: String?): String? {
    if (text.isNullOrBlank()) return null
    if (text.contains("Coroutine", ignoreCase = true) && text.contains("cancel", ignoreCase = true)) {
        return null
    }
    return text.trim()
}

private fun sanitizeHudStatus(text: String): String {
    if (text.contains("Coroutine", ignoreCase = true) ||
        text.contains("cancelled", ignoreCase = true)
    ) {
        return "Ready"
    }
    if (text.length > 48) return text.take(45) + "…"
    return text
}
