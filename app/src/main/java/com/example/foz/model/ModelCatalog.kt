package com.example.foz.model

/** Which on-device runtime a spec runs on. */
enum class ModelRuntime {
    /** Legacy MediaPipe LLM Inference API (.task bundles). */
    MEDIAPIPE_TASK,

    /** LiteRT-LM (.litertlm): native chat templates, modern model zoo. */
    LITERT_LM
}

/**
 * Downloadable LLM models verified to work with their declared runtime.
 * Sizes/gates are declared so the UI can warn before a multi-hundred-MB
 * download on a small device.
 */
data class ModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val approxBytes: Long,
    val minTotalRamBytes: Long,
    val maxTokens: Int,
    /** Gemma repos are license-gated on HuggingFace; Qwen is not. */
    val needsToken: Boolean,
    val runtime: ModelRuntime = ModelRuntime.MEDIAPIPE_TASK
)

object ModelCatalog {

    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024

    /** Current default: same size as the classic export but 2048-token KV cache. */
    val GEMMA_1B = ModelSpec(
        id = "gemma-1b-2048",
        displayName = "Gemma 3 1B (2048 ctx)",
        fileName = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
        approxBytes = 555L * MB,
        minTotalRamBytes = 4L * GB,
        maxTokens = 2048,
        needsToken = true
    )

    /** Classic export many users sideloaded; fixed 1280-token KV cache. */
    val GEMMA_1B_CLASSIC = ModelSpec(
        id = "gemma-1b",
        displayName = "Gemma 3 1B (classic)",
        fileName = "gemma3-1b-it-int4.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
        approxBytes = 555L * MB,
        minTotalRamBytes = 3L * GB,
        maxTokens = 1280,
        needsToken = true
    )

    /** Mid-tier no-token option: ~2.6x larger than the 0.5B, clearly better answers. */
    val QWEN_15B = ModelSpec(
        id = "qwen-15b",
        displayName = "Qwen 2.5 1.5B",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        approxBytes = 1524L * MB,
        minTotalRamBytes = 4L * GB,
        maxTokens = 1280,
        needsToken = false
    )

    /** Low-RAM option; Apache-2.0 so it needs no HuggingFace token. */
    val QWEN_05B = ModelSpec(
        id = "qwen-05b",
        displayName = "Qwen 2.5 0.5B (low RAM)",
        fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        approxBytes = 547L * MB,
        minTotalRamBytes = 2L * GB,
        maxTokens = 1280,
        needsToken = false
    )

    /**
     * LiteRT-LM starters (all ungated, CPU backend, native chat templates).
     * Qwen3-0.6B: 4096 ctx, dynamic INT8. Thinking chains disabled in-engine.
     */
    val QWEN3_06B = ModelSpec(
        id = "qwen3-06b",
        displayName = "Qwen 3 0.6B (new runtime)",
        fileName = "Qwen3-0.6B.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        approxBytes = 610L * MB,
        minTotalRamBytes = 3L * GB,
        maxTokens = 4096,
        needsToken = false,
        runtime = ModelRuntime.LITERT_LM
    )

    /** LFM 2.5 1.2B: strong multilingual (pt-BR candidate), 4096 ctx, int4. */
    val LFM25_12B = ModelSpec(
        id = "lfm25-12b",
        displayName = "LFM 2.5 1.2B (new runtime)",
        fileName = "LFM2.5-1.2B-Instruct_int4.litertlm",
        url = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
        approxBytes = 740L * MB,
        minTotalRamBytes = 4L * GB,
        maxTokens = 4096,
        needsToken = false,
        runtime = ModelRuntime.LITERT_LM
    )

    /** Flagship of the new runtime: biggest quality jump, big-RAM devices. */
    val GEMMA4_E2B = ModelSpec(
        id = "gemma4-e2b",
        displayName = "Gemma 4 E2B (flagship)",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approxBytes = 2590L * MB,
        minTotalRamBytes = 6L * GB,
        maxTokens = 2048,
        needsToken = false,
        runtime = ModelRuntime.LITERT_LM
    )

    val ALL: List<ModelSpec> = listOf(
        GEMMA_1B, GEMMA_1B_CLASSIC, QWEN_15B, QWEN_05B,
        QWEN3_06B, LFM25_12B, GEMMA4_E2B
    )

    val DEFAULT: ModelSpec = GEMMA_1B

    fun byId(id: String?): ModelSpec =
        ALL.firstOrNull { it.id == id } ?: DEFAULT

    /** Maps a model file on disk (incl. legacy sideload names) to its spec. */
    fun byFileName(fileName: String?): ModelSpec? {
        if (fileName == null) return null
        return when {
            fileName.equals(GEMMA_1B.fileName, ignoreCase = true) -> GEMMA_1B
            fileName.equals(GEMMA_1B_CLASSIC.fileName, ignoreCase = true) -> GEMMA_1B_CLASSIC
            fileName.equals(QWEN_15B.fileName, ignoreCase = true) -> QWEN_15B
            fileName.equals(QWEN_05B.fileName, ignoreCase = true) -> QWEN_05B
            fileName.equals(QWEN3_06B.fileName, ignoreCase = true) -> QWEN3_06B
            fileName.equals(LFM25_12B.fileName, ignoreCase = true) -> LFM25_12B
            fileName.equals(GEMMA4_E2B.fileName, ignoreCase = true) -> GEMMA4_E2B
            // Legacy name used by our own setup instructions before the catalog.
            fileName.equals("gemma-3-1b-it-int4.task", ignoreCase = true) -> GEMMA_1B_CLASSIC
            else -> null
        }
    }

    /** Non-null warning when the device probably cannot run [spec]. */
    fun ramWarning(spec: ModelSpec, totalRamBytes: Long): String? {
        return if (totalRamBytes < spec.minTotalRamBytes) {
            "This model wants a device with at least ${spec.minTotalRamBytes / GB} GB of RAM"
        } else {
            null
        }
    }
}
