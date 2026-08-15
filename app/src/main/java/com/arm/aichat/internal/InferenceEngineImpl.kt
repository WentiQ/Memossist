package com.arm.aichat.internal

import android.content.Context

/**
 * Native facade over llama.cpp.
 * Each instance of [InferenceEngineImpl] maintains its own native execution context
 * while sharing the underlying cached model weights in memory.
 * This allows multiple LLM operations (e.g. Chat inference and background Memory Parameter Evaluation)
 * to run in parallel without blocking or disturbing each other.
 */
class InferenceEngineImpl(context: Context) {
    private var nativeHandle: Long = 0L
    private var modelLoaded = false
    private var currentModelPath: String? = null
    private var currentContextSize: Int = 0

    companion object {
        private var backendInitialized = false
        val GLOBAL_INFERENCE_LOCK = Any()

        @Synchronized
        fun ensureBackendInit(context: Context) {
            if (!backendInitialized) {
                System.loadLibrary("ai-chat")
                initBackend(context.applicationInfo.nativeLibraryDir)
                backendInitialized = true
            }
        }

        @JvmStatic
        private external fun initBackend(nativeLibDir: String)
    }

    init {
        ensureBackendInit(context)
        nativeHandle = createHandle()
    }

    fun generate(
        modelPath: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = 768,
        contextSize: Int = 2048,
        stopPattern: Regex? = null,
        stopCondition: ((String) -> Boolean)? = null,
        onTokenGenerated: ((String) -> Unit)? = null
    ): String = synchronized(GLOBAL_INFERENCE_LOCK) {
        if (nativeHandle == 0L) {
            nativeHandle = createHandle()
        }
        val needsFullInit = !modelLoaded || currentModelPath != modelPath || currentContextSize != contextSize

        if (needsFullInit) {
            if (modelLoaded) {
                unload(nativeHandle)
                modelLoaded = false
            }
            check(load(nativeHandle, modelPath) == 0) { "Unable to load GGUF model file ($modelPath)." }
            check(prepare(nativeHandle, contextSize) == 0) { "Unable to prepare llama context for GGUF model." }
            modelLoaded = true
            currentModelPath = modelPath
            currentContextSize = contextSize
            reset(nativeHandle)
        } else {
            reset(nativeHandle)
        }

        check(processSystemPrompt(nativeHandle, systemPrompt) == 0) { "Unable to process system prompt in llama context." }
        check(processUserPrompt(nativeHandle, userMessage, maxTokens) == 0) { "Unable to process user prompt in llama context." }

        try {
            buildString {
                while (true) {
                    val token = generateNextToken(nativeHandle) ?: break
                    if (token.isNotEmpty()) {
                        append(token)
                        onTokenGenerated?.invoke(token)
                        if (stopPattern != null && stopPattern.containsMatchIn(this)) {
                            break
                        }
                        if (stopCondition != null && stopCondition.invoke(this.toString())) {
                            break
                        }
                    }
                }
            }
        } finally {
            // Clean context for next turn immediately
            reset(nativeHandle)
        }
    }

    fun release() = synchronized(GLOBAL_INFERENCE_LOCK) {
        if (nativeHandle != 0L) {
            if (modelLoaded) {
                unload(nativeHandle)
                modelLoaded = false
                currentModelPath = null
                currentContextSize = 0
            }
            destroyHandle(nativeHandle)
            nativeHandle = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun createHandle(): Long
    private external fun destroyHandle(handle: Long)
    private external fun load(handle: Long, modelPath: String): Int
    private external fun prepare(handle: Long, contextSize: Int): Int
    private external fun reset(handle: Long): Int
    private external fun processSystemPrompt(handle: Long, systemPrompt: String): Int
    private external fun processUserPrompt(handle: Long, userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(handle: Long): String?
    private external fun unload(handle: Long)
}
