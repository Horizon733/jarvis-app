package com.example.ai_agent.util

object GenerationDiagnostics {
    fun emptyResponseMessage(detail: String?, contextTokens: Int = 2048): String = buildString {
        appendLine("**Generation failed — no response from the model**")
        appendLine()
        appendLine(detail ?: "No diagnostic was returned from the inference engine.")
        appendLine()
        appendLine("**Common causes:**")
        appendLine("- Prompt too long for the loaded context window ($contextTokens tokens)")
        appendLine("- Chat template mismatch with this GGUF model")
        appendLine("- Model ran out of memory during decode")
        appendLine("- Sampling ended immediately (end-of-generation token)")
        appendLine()
        appendLine("**Try:** start a new chat, send a shorter message, or use a chat/instruct-tuned model.")
    }
}
