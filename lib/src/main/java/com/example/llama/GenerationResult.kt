package com.example.llama

data class GenerationResult(
    val text: String,
    val error: String? = null,
    val warning: String? = null,
)
