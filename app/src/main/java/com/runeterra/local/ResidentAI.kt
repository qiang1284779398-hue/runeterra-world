package com.runeterra.local

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One shared llama.cpp model for the whole world.
 * Model state is shared; resident memories and prompts remain individual.
 * The 8B profile deliberately starts with a 2048-token context and CPU inference
 * to reduce peak memory on 12GB Android phones.
 */
object ResidentAI {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inferenceMutex = Mutex()
    @Volatile private var model: LlamaModel? = null
    @Volatile private var loadedPath: String? = null
    @Volatile private var loading: Boolean = false

    fun isLoaded(): Boolean = model?.isLoaded == true
    fun isLoading(): Boolean = loading
    fun modelPath(): String? = loadedPath

    fun load(context: android.content.Context, path: String, callback: (String?) -> Unit) {
        if (loading) { callback("模型正在加载，请稍候。"); return }
        if (loadedPath == path && isLoaded()) { callback(null); return }
        loading = true
        scope.launch {
            try {
                model?.let { Llama.releaseModel(it) }
                model = null
                loadedPath = null
                val loaded = Llama.loadModel(
                    modelPath = path,
                    config = LlamaConfig(
                        contextSize = 2048,
                        threads = 4,
                        gpuLayers = 0,
                        temperature = 0.75f,
                        topP = 0.9f,
                        topK = 40
                    )
                )
                model = loaded
                loadedPath = path
                callback(null)
            } catch (e: Throwable) {
                callback(e.message ?: e.toString())
            } finally {
                loading = false
            }
        }
    }

    fun unload() {
        scope.launch {
            inferenceMutex.withLock {
                model?.let { Llama.releaseModel(it) }
                model = null
                loadedPath = null
            }
        }
    }

    fun ask(prompt: String, callback: (String) -> Unit) {
        scope.launch {
            inferenceMutex.withLock {
                val active = model
                if (active == null || !active.isLoaded) {
                    callback("请先加载本地 GGUF 模型。")
                    return@withLock
                }
                try {
                    val result = Llama.complete(
                        model = active,
                        prompt = prompt,
                        systemPrompt = "你是符文之地居民的个人认知引擎。严格依据提供的个人经历、知识和所处环境思考，不得假装知道未经历的事情。输出简洁、可执行的结果。",
                        maxTokens = 256
                    )
                    callback(result.text.trim())
                } catch (e: Throwable) {
                    callback("本地推理错误：${e.message ?: e.toString()}")
                }
            }
        }
    }

    fun systemInfo(): String = try { Llama.getSystemInfo() } catch (e: Throwable) { e.message ?: "不可用" }
}
