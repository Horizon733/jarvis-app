package com.example.ai_agent.util

import com.example.llama.SamplingParams

data class InferenceSettings(
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val maxTokens: Int = 512,
) {
    fun toSamplingParams(): SamplingParams = SamplingParams(
        temp = temperature,
        topK = topK,
        nPredict = maxTokens,
    )
}
