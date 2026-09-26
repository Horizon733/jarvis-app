package com.example.ai_agent.voice

object TextSpeechUtils {
    private val codeBlockRegex = Regex("```[\\s\\S]*?```")
    private val inlineCodeRegex = Regex("`[^`]+`")
    private val linkRegex = Regex("\\[([^\\]]+)]\\([^)]+\\)")
    private val markdownCharsRegex = Regex("[#*_~>|]")
    private val whitespaceRegex = Regex("\\s+")
    private val sentenceSplitRegex = Regex("(?<=[.!?])\\s+")

    fun prepareForSpeech(markdown: String): String {
        if (markdown.isBlank()) return ""
        return markdown
            .replace(codeBlockRegex, " ")
            .replace(inlineCodeRegex, " ")
            .replace(linkRegex, "$1")
            .replace(markdownCharsRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun chunkForSpeech(text: String, maxChars: Int = 350): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.isNotBlank()) {
            if (remaining.length <= maxChars) {
                chunks += remaining.trim()
                break
            }
            val window = remaining.take(maxChars)
            val splitAt = window.lastIndexOfAny(charArrayOf('.', '!', '?', ';'))
                .takeIf { it >= maxChars / 3 } ?: maxChars
            val chunk = remaining.substring(0, splitAt).trim()
            if (chunk.isNotEmpty()) {
                chunks += chunk
            }
            remaining = remaining.substring(splitAt).trim()
        }
        return chunks
    }
}
