package com.example.foz.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File
import java.util.concurrent.CancellationException
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

    @Volatile
    private var activeSession: LlmInferenceSession? = null

    override val isLoaded: Boolean
        get() = llmInference != null

    override suspend fun load(context: Context, modelFile: File, maxTokens: Int) {
        mutex.withLock {
            if (llmInference != null) return
            withContext(Dispatchers.Default) {
                try {
                    llmInference = try {
                        createInference(context, modelFile, preferGpu = true, maxTokens = maxTokens)
                    } catch (t: Throwable) {
                        Log.w(TAG, "GPU backend unavailable, falling back to CPU", t)
                        createInference(context, modelFile, preferGpu = false, maxTokens = maxTokens)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "maxTokens=$maxTokens rejected, retrying with 1280", t)
                    try {
                        createInference(context, modelFile, preferGpu = false, maxTokens = 1280)
                    } catch (t2: Throwable) {
                        Log.e(TAG, "Failed to load model", t2)
                        throw t2
                    }
                }
            }
        }
    }

    private fun createInference(
        context: Context,
        modelFile: File,
        preferGpu: Boolean,
        maxTokens: Int
    ): LlmInference {
        val builder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(maxTokens)
        if (preferGpu) {
            builder.setPreferredBackend(LlmInference.Backend.GPU)
        }
        return LlmInference.createFromOptions(
            context.applicationContext,
            builder.build()
        )
    }

    override fun generateStreaming(prompt: String, onPartial: (String) -> Unit): String {
        val engine = llmInference ?: error("Model is not loaded")
        val session = LlmInferenceSession.createFromOptions(
            engine,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.7f)
                .setTopK(40)
                .setTopP(0.95f)
                .build()
        )
        var lastPartial = ""
        try {
            activeSession = session
            session.addQueryChunk(prompt)
            val future = session.generateResponseAsync { partial, _ ->
                if (!partial.isNullOrEmpty()) {
                    lastPartial = partial
                    onPartial(partial)
                }
            }
            return try {
                future.get() ?: lastPartial
            } catch (e: CancellationException) {
                lastPartial
            } catch (e: java.util.concurrent.ExecutionException) {
                // cancelGenerateResponseAsync() surfaces as a failed future;
                // keep whatever the user already saw.
                val cause = e.cause
                if (cause is CancellationException || lastPartial.isNotEmpty()) {
                    lastPartial
                } else {
                    throw cause ?: e
                }
            }
        } finally {
            if (activeSession === session) {
                activeSession = null
            }
            session.close()
        }
    }

    override fun cancelGeneration() {
        try {
            activeSession?.cancelGenerateResponseAsync()
        } catch (t: Throwable) {
            Log.w(TAG, "cancelGeneration failed", t)
        }
    }

    override fun close() {
        cancelGeneration()
        llmInference?.close()
        llmInference = null
    }

    companion object {
        private const val TAG = "MediaPipeLlmEngine"
    }
}
