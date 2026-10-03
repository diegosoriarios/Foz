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
     * Loads a Gemma .task model file into memory. Blocking; call off the main thread.
     */
    suspend fun load(context: Context, modelFile: File)

    /**
     * Generates a completion for a fully formatted prompt, invoking [onPartial]
     * with the accumulated output as it grows. Blocking; call off the main thread.
     * Returns the final text (or the last partial if generation was cancelled
     * via [cancelGeneration]).
     */
    fun generateStreaming(prompt: String, onPartial: (String) -> Unit): String

    /**
     * Convenience for one-shot generation without streaming.
     */
    fun generate(prompt: String): String = generateStreaming(prompt) {}

    /**
     * Requests cancellation of an in-flight generation, if any.
     */
    fun cancelGeneration()

    /**
     * Releases model memory. Safe to call multiple times.
     */
    fun close()
}
