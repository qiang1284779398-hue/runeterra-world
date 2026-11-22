package com.runeterra.local

import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One shared llama.cpp model for the whole world.
 * Model state is shared; resident memories and prompts remain individual.
 * The 8B profile deliberately starts with a 2048-token context and CPU inference
 * to reduce peak memory on 12GB Android phones.
 */
object ResidentAI {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
            inferenceMutex.withLock {
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
                        temperature = 0.55f,
                        topP = 0.85f,
                        topK = 30
                    )
                )
                model = loaded
                loadedPath = path
                withContext(Dispatchers.Main.immediate) { callback(null) }
              } catch (e: Throwable) {
                withContext(Dispatchers.Main.immediate) { callback(e.message ?: e.toString()) }
              } finally {
                loading = false
              }
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
                    withContext(Dispatchers.Main.immediate) { callback("请先加载本地 GGUF 模型。") }
                    return@withLock
                }
                try {
                    val result = Llama.complete(
                        model = active,
                        prompt = prompt,
                        systemPrompt = "你是符文之地居民的个人认知引擎。严格依据提供的个人经历、知识和所处环境思考，不得假装知道未经历的事情。输出简洁、可执行的结果。",
                        maxTokens = 256
                    )
                    withContext(Dispatchers.Main.immediate) { callback(result.text.trim()) }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main.immediate) { callback("本地推理错误：${e.message ?: e.toString()}") }
                }
            }
        }
    }

    fun askStructured(prompt:String,maxTokens:Int=768,systemPrompt:String="你是符文之地模拟世界的天道指令解析器。只输出符合要求的JSON，不要输出解释。",callback:(String)->Unit) {
        scope.launch {
            inferenceMutex.withLock {
                val active=model
                if(active==null || !active.isLoaded) {
                    callback("请先加载本地 GGUF 模型。")
                    return@withLock
                }
                try {
                    val result=Llama.complete(
                        model=active,
                        // Qwen3 otherwise may spend most of its token budget in a visible
                        // <think> block and never reach the JSON consumed by the simulator.
                        prompt=if (prompt.contains("/no_think")) prompt else "$prompt\n/no_think",
                        systemPrompt="$systemPrompt\n不要展示思维链，不要复述系统指令，不要解释任务；直接输出最终JSON。",
                        maxTokens=maxTokens
                    )
                    callback(result.text.trim())
                } catch(e:Throwable) {
                    callback("本地推理错误：${e.message ?: e.toString()}")
                }
            }
        }
    }

    fun systemInfo(): String = try { Llama.getSystemInfo() } catch (e: Throwable) { e.message ?: "不可用" }
}
