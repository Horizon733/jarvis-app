package com.example.llama

data class SamplingParams(
    val temp: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val repeatPenalty: Float = 1.1f,
    val nPredict: Int = -1
)
