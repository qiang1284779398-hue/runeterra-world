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

    // Conservative profile for small GGUFs on memory-constrained Android devices.
    private fun isSmallModel(path: String): Boolean {
        val p = path.lowercase()
        return listOf("0.6b", "1.7b", "2b", "3b", "qwen3-1.7").any { p.contains(it) }
    }
    private fun inferenceContext(path: String): Int = if (isSmallModel(path)) 768 else 1536
    private fun inferenceThreads(path: String): Int = if (isSmallModel(path)) 2 else 3
    private fun promptForDevice(prompt: String, path: String): String {
        val limit = if (isSmallModel(path)) 2600 else 7000
        return if (prompt.length > limit) prompt.take(limit) + "\n（上下文已按设备内存限制截断）" else prompt
    }

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
                        contextSize = inferenceContext(path),
                        threads = inferenceThreads(path),
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
                        prompt = promptForDevice(prompt, loadedPath ?: ""),
                        systemPrompt = "你是符文之地居民的个人认知引擎。严格依据提供的个人经历、知识和所处环境思考，不得假装知道未经历的事情。输出简洁、可执行的结果。/no_think",
                        maxTokens = if (isSmallModel(loadedPath ?: "")) 96 else 160
                    )
                    withContext(Dispatchers.Main.immediate) { callback(cleanStructuredOutput(result.text)) }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main.immediate) { callback("本地推理错误：${e.message ?: e.toString()}") }
                }
            }
        }
    }

    fun askStructured(prompt:String,maxTokens:Int=192,systemPrompt:String="你是符文之地模拟世界的天道指令解析器。只输出符合要求的JSON，不要输出解释。",callback:(String)->Unit) {
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
                        prompt=promptForDevice(if (prompt.contains("/no_think")) prompt else "$prompt\n/no_think", loadedPath ?: ""),
                        systemPrompt="$systemPrompt\n/no_think\n不要展示思维链，不要复述系统指令，不要解释任务；直接输出最终JSON。",
                        maxTokens=if (isSmallModel(loadedPath ?: "")) maxTokens.coerceAtMost(96) else maxTokens.coerceAtMost(160)
                    )
                    callback(result.text.trim())
                } catch(e:Throwable) {
                    callback("本地推理错误：${e.message ?: e.toString()}")
                }
            }
        }
    }

    /** Strip Qwen analysis markers and extract only final JSON for simulation parsing. */
    private fun cleanStructuredOutput(raw: String): String {
        val text = raw.replace(Regex("(?s)<think>.*?</think>"), "")
            .replace("<think>", "").replace("</think>", "")
            .replace("```json", "").replace("```", "").trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1).trim()
        if (listOf("用户让我", "系统要求", "按照要求输出", "扮演", "JSON格式").any { text.contains(it) })
            return "格式错误：模型复述了任务指令，没有生成角色JSON。"
        return text.take(500)
    }

    fun systemInfo(): String = try { Llama.getSystemInfo() } catch (e: Throwable) { e.message ?: "不可用" }
}
