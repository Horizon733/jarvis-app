package com.example.ai_agent.ui.common

data class UserMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)
