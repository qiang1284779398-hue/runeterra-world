package com.runeterra.local

import android.content.Context
import com.tensai.llamakt.AiKitEngine
import com.tensai.llamakt.AiKitChat

/**
 * Shared local llama.cpp engine. The GGUF model is selected/imported by the user and remains on device.
 * Residents will call this shared engine with resident-specific context; never load one model per resident.
 */
object ResidentAI {
    @Volatile private var engine: AiKitEngine? = null
    @Volatile private var chat: AiKitChat? = null
    @Volatile private var loadedPath: String? = null

    fun isLoaded(): Boolean = engine?.isModelLoaded() == true
    fun modelPath(): String? = loadedPath

    fun load(context: Context, path: String, callback: (String?) -> Unit) {
        val e = engine ?: AiKitEngine.create(context.applicationContext).also { engine = it }
        e.loadModel(path, object : AiKitEngine.ModelStateListener {
            override fun onModelLoading() { }
            override fun onModelLoaded() {
                loadedPath = path
                chat = e.chat()
                callback(null)
            }
            override fun onModelError(error: Throwable) { callback(error.message ?: error.toString()) }
            override fun onModelUnloaded() { chat = null; loadedPath = null }
        })
    }

    fun unload() {
        engine?.unloadModel()
        chat = null
        loadedPath = null
    }

    fun ask(prompt: String, callback: (String) -> Unit) {
        val c = chat ?: run { callback("请先加载本地 GGUF 模型。"); return }
        c.sendMessage(prompt, listener = object : AiKitChat.ChatListener {
            override fun onGenerationStarted() { }
            override fun onToken(token: String) { }
            override fun onGenerationComplete(fullResponse: String) { callback(fullResponse) }
            override fun onGenerationError(error: Throwable) { callback("本地推理错误：${error.message}") }
            override fun onCancelled() { callback("推理已取消") }
        })
    }
}
