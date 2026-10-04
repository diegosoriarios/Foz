package com.example.foz.model

/**
 * Downloadable LLM models verified to work with the MediaPipe LLM Inference
 * API (.task bundles, Android exports). Sizes/gates are declared so the UI
 * can warn before a multi-hundred-MB download on a small device.
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
    val needsToken: Boolean
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

    val ALL: List<ModelSpec> = listOf(GEMMA_1B, GEMMA_1B_CLASSIC, QWEN_05B)

    val DEFAULT: ModelSpec = GEMMA_1B

    fun byId(id: String?): ModelSpec =
        ALL.firstOrNull { it.id == id } ?: DEFAULT

    /** Maps a model file on disk (incl. legacy sideload names) to its spec. */
    fun byFileName(fileName: String?): ModelSpec? {
        if (fileName == null) return null
        return when {
            fileName.equals(GEMMA_1B.fileName, ignoreCase = true) -> GEMMA_1B
            fileName.equals(GEMMA_1B_CLASSIC.fileName, ignoreCase = true) -> GEMMA_1B_CLASSIC
            fileName.equals(QWEN_05B.fileName, ignoreCase = true) -> QWEN_05B
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
