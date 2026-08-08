package com.arm.aichat.internal

import android.content.Context

/**
 * Small synchronous facade over llama.cpp's official Android JNI sample. Calls
 * are made from ChatRepository's worker thread, never from the UI thread.
 */
class InferenceEngineImpl(context: Context) {
    private var modelLoaded = false

    init {
        System.loadLibrary("ai-chat")
        init(context.applicationInfo.nativeLibraryDir)
    }

    @Synchronized
    fun generate(
        modelPath: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 768,
        onTokenGenerated: ((String) -> Unit)? = null
    ): String {
        if (modelLoaded) {
            unload()
            modelLoaded = false
        }
        check(load(modelPath) == 0) { "Unable to load GGUF model file ($modelPath)." }
        check(prepare() == 0) { "Unable to prepare llama context for GGUF model." }
        modelLoaded = true
        check(processSystemPrompt(systemPrompt) == 0) { "Unable to process system prompt in llama context." }
        check(processUserPrompt(userMessage, maxTokens) == 0) { "Unable to process user prompt in llama context." }

        return buildString {
            while (true) {
                val token = generateNextToken() ?: break
                if (token.isNotEmpty()) {
                    append(token)
                    onTokenGenerated?.invoke(token)
                }
            }
        }
    }

    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()
}
