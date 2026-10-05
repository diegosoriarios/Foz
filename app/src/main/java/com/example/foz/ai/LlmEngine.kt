package com.example.foz.ai

import android.content.Context
import java.io.File

data class AssistantMessage(
    val isFromUser: Boolean,
    val text: String,
    val isToolActivity: Boolean = false
)

interface LlmEngine {
    val isLoaded: Boolean

    /**
     * True when the engine can run [chat] with per-model chat templates
     * (LiteRT-LM). False means callers must fall back to fully formatted
     * prompts via [generateStreaming].
     */
    val supportsChat: Boolean get() = false

    /**
     * Loads a Gemma .task model file into memory with [maxTokens] as the
     * context budget. Blocking; call off the main thread.
     */
    suspend fun load(context: Context, modelFile: File, maxTokens: Int)

    /**
     * Generates a completion for a fully formatted prompt, invoking [onPartial]
     * with the accumulated output as it grows. Blocking; call off the main thread.
     * Returns the final text (or the last partial if generation was cancelled
     * via [cancelGeneration]).
     */
    fun generateStreaming(prompt: String, onPartial: (String) -> Unit): String

    /**
     * Multi-turn chat using the engine's own conversation handling and the
     * model's chat template. [history] holds prior turns (without the current
     * input); [systemPrompt] is applied as the system instruction. Only
     * meaningful when [supportsChat] is true. Returns the final text (or the
     * last partial if cancelled via [cancelGeneration]).
     */
    suspend fun chat(
        systemPrompt: String,
        history: List<AssistantMessage>,
        input: String,
        onPartial: (String) -> Unit
    ): String = throw UnsupportedOperationException("chat requires a chat-capable engine")

    /**
     * Convenience for one-shot generation without streaming.
     */
    suspend fun generate(prompt: String): String = generateStreaming(prompt) {}

    /**
     * Requests cancellation of an in-flight generation, if any.
     */
    fun cancelGeneration()

    /**
     * Releases model memory. Safe to call multiple times.
     */
    fun close()
}
