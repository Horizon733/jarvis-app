package com.example.ai_agent.domain.model

sealed class LlmState {
    object Idle : LlmState()
    object Loading : LlmState()
    object Ready : LlmState()
    data class Error(val message: String) : LlmState()
    object ModelMissing : LlmState()
}
