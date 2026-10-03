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
     * Generates a completion for a fully formatted prompt. Blocking; call off the main thread.
     */
    fun generate(prompt: String): String

    /**
     * Releases model memory. Safe to call multiple times.
     */
    fun close()
}
