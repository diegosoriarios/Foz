package com.example.foz.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device LLM engine backed by Google AI Edge (MediaPipe LLM Inference API).
 * Runs a Gemma .task model fully offline; no network access is used.
 */
class MediaPipeLlmEngine : LlmEngine {

    private var llmInference: LlmInference? = null
    private val mutex = Mutex()

    override val isLoaded: Boolean
        get() = llmInference != null

    override suspend fun load(context: Context, modelFile: File) {
        mutex.withLock {
            if (llmInference != null) return
            withContext(Dispatchers.Default) {
                try {
                    llmInference = try {
                        createInference(context, modelFile, preferGpu = true)
                    } catch (t: Throwable) {
                        Log.w(TAG, "GPU backend unavailable, falling back to CPU", t)
                        createInference(context, modelFile, preferGpu = false)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to load model", t)
                    throw t
                }
            }
        }
    }

    private fun createInference(
        context: Context,
        modelFile: File,
        preferGpu: Boolean
    ): LlmInference {
        val builder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOTAL_TOKENS)
        if (preferGpu) {
            builder.setPreferredBackend(LlmInference.Backend.GPU)
        }
        return LlmInference.createFromOptions(
            context.applicationContext,
            builder.build()
        )
    }

    override fun generate(prompt: String): String {
        val engine = llmInference ?: error("Model is not loaded")
        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.7f)
                .setTopK(40)
                .setTopP(0.95f)
                .build()
        )
        try {
            session.addQueryChunk(prompt)
            return session.generateResponse() ?: ""
        } finally {
            session.close()
        }
    }

    override fun close() {
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private const val TAG = "MediaPipeLlmEngine"
        private const val MAX_TOTAL_TOKENS = 1280
    }
}
