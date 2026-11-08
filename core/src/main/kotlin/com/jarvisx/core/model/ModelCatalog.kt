package com.jarvisx.core.model

/**
 * One downloadable local model.
 *
 * [sha256] is pinned when known (CI workflow `model-catalog.yml` reports the values from
 * Hugging Face). When null, the downloader takes the SHA-256 that Hugging Face publishes for the
 * file (X-Linked-Etag) before downloading, and refuses the file if the hashes differ.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val family: String,
    val parameters: String,
    val quantization: String,
    val fileName: String,
    val url: String,
    val approxSizeMb: Int,
    val sha256: String?,
    /** Estimated peak RAM while answering at the default context, model weights included. */
    val ramRequiredMb: Int,
    /** Smallest total phone RAM this model is offered for by default. */
    val minTotalRamMb: Int,
    val licence: String,
    val summary: String,
    val experimental: Boolean = false,
)

object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "qwen3.5-0.8b-q8_0",
            displayName = "Qwen3.5 0.8B",
            family = "Qwen3.5",
            parameters = "0.8B",
            quantization = "Q8_0",
            fileName = "Qwen3.5-0.8B-Q8_0.gguf",
            url = "$HF/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q8_0.gguf",
            approxSizeMb = 870,
            sha256 = null,
            ramRequiredMb = 1300,
            minTotalRamMb = 3000,
            licence = "Apache-2.0",
            summary = "Fastest. Short answers and simple questions; weak at reasoning.",
        ),
        ModelSpec(
            id = "qwen3.5-2b-q4_k_m",
            displayName = "Qwen3.5 2B",
            family = "Qwen3.5",
            parameters = "2B",
            quantization = "Q4_K_M",
            fileName = "Qwen3.5-2B-Q4_K_M.gguf",
            url = "$HF/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            approxSizeMb = 1300,
            sha256 = null,
            ramRequiredMb = 2300,
            minTotalRamMb = 5500,
            licence = "Apache-2.0",
            summary = "Balanced. Good explanations and drafting on 6 GB phones.",
        ),
        ModelSpec(
            id = "qwen3.5-4b-q4_k_m",
            displayName = "Qwen3.5 4B",
            family = "Qwen3.5",
            parameters = "4B",
            quantization = "Q4_K_M",
            fileName = "Qwen3.5-4B-Q4_K_M.gguf",
            url = "$HF/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            approxSizeMb = 2700,
            sha256 = null,
            ramRequiredMb = 3600,
            minTotalRamMb = 7500,
            licence = "Apache-2.0",
            summary = "Strongest default. Multi-step answers and basic code on 8 GB+ phones.",
        ),
        ModelSpec(
            id = "qwen3.5-9b-q4_k_m",
            displayName = "Qwen3.5 9B",
            family = "Qwen3.5",
            parameters = "9B",
            quantization = "Q4_K_M",
            fileName = "Qwen3.5-9B-Q4_K_M.gguf",
            url = "$HF/unsloth/Qwen3.5-9B-GGUF/resolve/main/Qwen3.5-9B-Q4_K_M.gguf",
            approxSizeMb = 5700,
            sha256 = null,
            ramRequiredMb = 6800,
            minTotalRamMb = 11000,
            licence = "Apache-2.0",
            summary = "Experimental. Best quality, but slow and hot on most phones.",
            experimental = true,
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }
}
