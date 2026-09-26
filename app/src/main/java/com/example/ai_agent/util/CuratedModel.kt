package com.example.ai_agent.util

/**
 * Downloadable, reviewed GGUF files. Keep this list small and use immutable revision URLs when
 * publishing a production release so a repository update cannot silently replace a model.
 */
data class CuratedModel(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String,
    val estimatedSizeMb: Int,
)

object CuratedModels {
    val all = listOf(
        CuratedModel(
            id = "qwen-3.5-0.8b-q4-k-s",
            displayName = "Qwen 3.5 0.8B (Q4)",
            description = "Fastest option; about 418 MB. Good for lower-memory phones.",
            downloadUrl =
                "https://huggingface.co/TheStageAI/Qwen3.5-0.8B-GGUF/resolve/main/" +
                    "Qwen3.5-0.8B-S-TS-Q4_K_S.gguf",
            fileName = "Qwen3.5-0.8B-Q4_K_S.gguf",
            estimatedSizeMb = 418,
        ),
        CuratedModel(
            id = "qwen-3.5-2b-q4-k-m",
            displayName = "Qwen 3.5 2B (Q4)",
            description = "Balanced quality and memory; about 1.07 GB.",
            downloadUrl =
                "https://huggingface.co/TheStageAI/Qwen3.5-2B-GGUF/resolve/main/" +
                    "Qwen3.5-2B-M-TS-Q4_K_M.gguf",
            fileName = "Qwen3.5-2B-Q4_K_M.gguf",
            estimatedSizeMb = 1_070,
        ),
        CuratedModel(
            id = "gemma-4-e2b-q4-0",
            displayName = "Gemma 4 E2B Instruct (Q4_0)",
            description = "Google QAT GGUF; about 1.1 GB. Download is attempted without login.",
            downloadUrl =
                "https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf/resolve/main/" +
                    "gemma-4-E2B_q4_0-it.gguf",
            fileName = "gemma-4-E2B_q4_0-it.gguf",
            estimatedSizeMb = 1_100,
        ),
        CuratedModel(
            id = "gemma-4-e4b-q4-0",
            displayName = "Gemma 4 E4B Instruct (Q4_0)",
            description = "Google QAT GGUF; use only on high-memory phones. Download is attempted without login.",
            downloadUrl =
                "https://huggingface.co/google/gemma-4-E4B-it-qat-q4_0-gguf/resolve/main/" +
                    "gemma-4-E4B_q4_0-it.gguf",
            fileName = "gemma-4-E4B_q4_0-it.gguf",
            estimatedSizeMb = 2_200,
        ),
    )
}
