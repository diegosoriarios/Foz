package com.example.foz.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlin.text.Regex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM engine (.litertlm). Delegates conversation handling to the
 * runtime so each model's native chat template applies (Qwen3, LFM2.5,
 * Gemma 4, ...). Thinking chains (Qwen3 et al.) are disabled via
 * [ThinkingConfig] and stripped defensively from partials and finals.
 *
 * All entry points are serialized behind [mutex]: the runtime does not
 * tolerate concurrent use of one native engine (e.g. a user query racing
 * the background memory-extraction pass) and crashes in liblitertlm_jni
 * when that happens.
 */
class LiteRtLmEngine : LlmEngine {

    private val mutex = Mutex()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var activeConversation: Conversation? = null

    override val isLoaded: Boolean
        get() = engine?.isInitialized() == true

    override val supportsChat: Boolean = true

    override suspend fun load(context: Context, modelFile: File, maxTokens: Int) {
        mutex.withLock {
            closeLocked()
            withContext(Dispatchers.IO) {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = maxTokens,
                    cacheDir = context.cacheDir.absolutePath
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
            }
        }
    }

    /** Raw prompts are not supported: LiteRT-LM always applies a chat template. */
    override fun generateStreaming(prompt: String, onPartial: (String) -> Unit): String =
        throw UnsupportedOperationException("Use chat() with a LiteRT-LM engine")

    /** One-shot generation (memory extraction) via a throwaway conversation. */
    override suspend fun generate(prompt: String): String {
        return chat(
            systemPrompt = "You follow instructions exactly and output only what was asked.",
            history = emptyList(),
            input = prompt,
            onPartial = {}
        )
    }

    override suspend fun chat(
        systemPrompt: String,
        history: List<AssistantMessage>,
        input: String,
        onPartial: (String) -> Unit
    ): String {
        return mutex.withLock {
            val engine = engine ?: throw IllegalStateException("Model not loaded")
            withContext(Dispatchers.IO) {
                val initialMessages = history.map { message ->
                    if (message.isFromUser) Message.user(message.text) else Message.model(message.text)
                }
                val config = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    initialMessages = initialMessages,
                    thinkingConfig = ThinkingConfig(enableThinking = false)
                )
                val conversation = engine.createConversation(config)
                activeConversation = conversation
                try {
                    val raw = StringBuilder()
                    conversation.sendMessageAsync(input).collect { message ->
                        val delta = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        if (delta.isNotEmpty()) {
                            raw.append(delta)
                            onPartial(visibleText(raw.toString()))
                        }
                    }
                    visibleText(raw.toString())
                } finally {
                    activeConversation = null
                    try {
                        conversation.close()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    override fun cancelGeneration() {
        try {
            activeConversation?.cancelProcess()
        } catch (_: Throwable) {
        }
    }

    override fun close() {
        // Abort any in-flight generation first so chat()'s finally block
        // releases the native conversation before we tear down the engine.
        cancelGeneration()
        closeLocked()
    }

    /** Caller must either hold [mutex] or have cancelled generation. */
    private fun closeLocked() {
        try {
            activeConversation?.close()
        } catch (_: Throwable) {
        }
        activeConversation = null
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
    }

    private fun visibleText(raw: String): String {
        var text = raw.replace(THINK_BLOCK, "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.trimStart()
    }

    companion object {
        private val THINK_BLOCK = Regex("(?s)<think>.*?</think>")
    }
}
