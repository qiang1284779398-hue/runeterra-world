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
import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    @Volatile private var appContext: Context? = null

    private fun recordStage(stage: String) {
        val ctx = appContext ?: return
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$stamp | $stage"
        Log.i("RuneterraAI", line)
        ctx.getSharedPreferences("runeterra_ai_diagnostics", Context.MODE_PRIVATE)
            .edit().putString("last_stage", line).putString("last_model", loadedPath ?: "").commit()
        try { File(ctx.filesDir, "ai_diagnostics.log").appendText(line + "\n") } catch (_: Throwable) {}
    }

    fun diagnosticReport(context: Context): String {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("runeterra_ai_diagnostics", Context.MODE_PRIVATE)
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val path = prefs.getString("last_model", "").orEmpty()
        val size = if (path.isNotBlank()) File(path).let { if (it.exists()) it.length() else -1L } else -1L
        val logFile = File(ctx.filesDir, "ai_diagnostics.log")
        val tail = if (logFile.exists()) logFile.readLines().takeLast(30).joinToString("\n") else "暂无日志"
        return "最近阶段：${prefs.getString("last_stage", "无记录")}\n模型路径：$path\n模型大小：${if (size >= 0) "${size / 1024 / 1024} MB" else "未知"}\n系统可用内存：${mi.availMem / 1024 / 1024} MB\n系统低内存状态：${mi.lowMemory}\n\n最近日志：\n$tail"
    }

    fun isLoaded(): Boolean = model?.isLoaded == true
    fun isLoading(): Boolean = loading
    fun modelPath(): String? = loadedPath

    // Conservative profile for small GGUFs on memory-constrained Android devices.
    private fun isSmallModel(path: String): Boolean {
        // Imported models use a generic filename, so classify by actual file size.
        val file = File(path)
        val bytes = if (file.exists()) file.length() else 0L
        return bytes in 1..(2_000L * 1024L * 1024L)
    }
    private fun inferenceContext(path: String): Int = if (isSmallModel(path)) 768 else 1536
    private fun inferenceThreads(path: String): Int = if (isSmallModel(path)) 2 else 3
    private fun promptForDevice(prompt: String, path: String): String {
        val limit = if (isSmallModel(path)) 2600 else 7000
        return if (prompt.length > limit) prompt.take(limit) + "\n（上下文已按设备内存限制截断）" else prompt
    }

    fun load(context: android.content.Context, path: String, callback: (String?) -> Unit) {
        appContext = context.applicationContext
        recordStage("开始加载模型；文件存在=${File(path).exists()}；大小=${File(path).length()} bytes")
        if (loading) { callback("模型正在加载，请稍候。"); return }
        if (loadedPath == path && isLoaded()) { callback(null); return }
        loading = true
        scope.launch {
            inferenceMutex.withLock {
              try {
                model?.let { Llama.releaseModel(it) }
                model = null
                loadedPath = null
                recordStage("调用 llama.cpp loadModel；context=${inferenceContext(path)}；threads=${inferenceThreads(path)}；small=${isSmallModel(path)}")
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
                recordStage("模型加载成功")
                withContext(Dispatchers.Main.immediate) { callback(null) }
              } catch (e: Throwable) {
                recordStage("模型加载异常：${e.message ?: e.toString()}")
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
                    recordStage("开始普通推理；small=${isSmallModel(loadedPath ?: "")}；promptChars=${prompt.length}")
                    val result = Llama.complete(
                        model = active,
                        prompt = promptForDevice(prompt, loadedPath ?: ""),
                        systemPrompt = "你是符文之地居民的个人认知引擎。严格依据提供的个人经历、知识和所处环境思考，不得假装知道未经历的事情。输出简洁、可执行的结果。/no_think",
                        maxTokens = if (isSmallModel(loadedPath ?: "")) 96 else 160
                    )
                    recordStage("普通推理完成；outputChars=${result.text.length}")
                    withContext(Dispatchers.Main.immediate) { callback(cleanStructuredOutput(result.text)) }
                } catch (e: Throwable) {
                    recordStage("普通推理异常：${e.message ?: e.toString()}")
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
                    recordStage("开始结构化推理；small=${isSmallModel(loadedPath ?: "")}；promptChars=${prompt.length}；maxTokens=${maxTokens}")
                    val result=Llama.complete(
                        model=active,
                        // Qwen3 otherwise may spend most of its token budget in a visible
                        // <think> block and never reach the JSON consumed by the simulator.
                        prompt=promptForDevice(if (prompt.contains("/no_think")) prompt else "$prompt\n/no_think", loadedPath ?: ""),
                        systemPrompt="$systemPrompt\n/no_think\n不要展示思维链，不要复述系统指令，不要解释任务；直接输出最终JSON。",
                        maxTokens=if (isSmallModel(loadedPath ?: "")) maxTokens.coerceAtMost(96) else maxTokens.coerceAtMost(160)
                    )
                    recordStage("结构化推理完成；outputChars=${result.text.length}")
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
