package com.runeterra.local

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class AIInferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    @Volatile private var model: LlamaModel? = null
    @Volatile private var modelPath = ""

    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true })
    }

    override fun onCreate() {
        super.onCreate()
        diagnostic("INFO", "AI_SERVICE_ON_CREATE pid=${android.os.Process.myPid()}")
    }

    // Persist service-side diagnostics to the same file shown by the in-app diagnostics screen.
    private fun diagnostic(level: String, message: String, error: Throwable? = null) {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val detail = buildString {
            append(stamp).append(" | [AI-SERVICE ").append(level).append("] ").append(message)
            if (error != null) {
                append("\n").append(android.util.Log.getStackTraceString(error))
            }
            append("\\n")
        }
        if (level == "ERROR") Log.e("RuneterraAI", message, error) else Log.i("RuneterraAI", message)
        try {
            File(filesDir, "ai_diagnostics.log").appendText(detail)
        } catch (ignored: Throwable) {
            Log.e("RuneterraAI", "写入诊断文件失败", ignored)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        diagnostic("INFO", "AI_SERVICE_ON_BIND")
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        diagnostic("INFO", "AI_SERVICE_ON_UNBIND")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        diagnostic("ERROR", "AI_SERVICE_ON_DESTROY")
        scope.cancel()
        super.onDestroy()
    }

    private fun reply(to: Messenger?, id: Long, ok: Boolean, text: String = "", load: Boolean = false) {
        try {
            val b = Bundle().apply {
                putLong("id", id)
                putBoolean("ok", ok)
                putString("result", text)
                if (load) putBoolean("loadFinished", true)
            }
            to?.send(Message.obtain(null, 100).apply { data = b })
        } catch (e: Throwable) {
            diagnostic("ERROR", "reply failed", e)
        }
    }

    // The 1.7B GGUF is below 2 GB. Keep its context conservative on mobile.
    private fun small(p: String) = File(p).length() <= 2_000L * 1024L * 1024L

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun handle(msg: Message) {
        val id = msg.data.getLong("id")
        val to = msg.replyTo

        when (msg.what) {
            1 -> {
                val p = msg.data.getString("path") ?: ""
                scope.launch {
                    mutex.withLock {
                        try {
                            require(File(p).isFile) { "模型文件不存在" }
                            model?.let { Llama.releaseModel(it) }
                            model = null
                            val isSmall = small(p)
                            val modelFile = File(p)
                            val bytesBefore = modelFile.length()
                            val modifiedBefore = modelFile.lastModified()
                            val hash = sha256(modelFile)
                            val expectedQwen17b = "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"
                            val officialMatch = hash.equals(expectedQwen17b, ignoreCase = true)
                            diagnostic("INFO", "模型SHA256=$hash；匹配ggml-org官方Qwen3-1.7B-Q4_K_M=$officialMatch")
                            val context = if (isSmall) 1024 else 1536
                            val threads = if (isSmall) 2 else 3
                            Log.i(
                                "RuneterraAI",
                                "加载模型 bytes=${File(p).length()} context=$context threads=$threads"
                            )
                            val loaded = Llama.loadModel(
                                modelPath = p,
                                config = LlamaConfig(
                                    contextSize = context,
                                    threads = threads,
                                    gpuLayers = 0,
                                    // First diagnostic pass for small GGUFs is deterministic: separate sampling instability from tokenizer/JNI/model-file faults.
                                    temperature = if (isSmall) 0.0f else 0.55f,
                                    topP = 0.85f,
                                    topK = 30
                                )
                            )
                            val hashAfterLoad = sha256(modelFile)
                            val bytesAfter = modelFile.length()
                            val modifiedAfter = modelFile.lastModified()
                            model = loaded
                            modelPath = p
                            val stable = hash == hashAfterLoad && bytesBefore == bytesAfter && modifiedBefore == modifiedAfter
                            diagnostic("INFO", "模型加载后文件校验；stable=$stable；before=$hash；after=$hashAfterLoad；bytesBefore=$bytesBefore；bytesAfter=$bytesAfter")
                            reply(to, id, true, "SHA256=$hash；加载后SHA256=$hashAfterLoad；加载期间文件稳定=$stable；匹配官方Qwen3-1.7B=$officialMatch", true)
                        } catch (e: Throwable) {
                            diagnostic("ERROR", "模型加载异常", e)
                            reply(to, id, false, e.message ?: e.toString(), true)
                        }
                    }
                }
            }

            2 -> scope.launch {
                mutex.withLock {
                    try {
                        model?.let { Llama.releaseModel(it) }
                        model = null
                        modelPath = ""
                        diagnostic("INFO", "模型已卸载并释放；requestId=$id")
                        reply(to, id, true, "模型已卸载")
                    } catch (e: Throwable) {
                        diagnostic("ERROR", "模型卸载异常", e)
                        reply(to, id, false, "模型卸载异常：" + (e.message ?: e.toString()))
                    }
                }
            }

            3, 4 -> {
                val prompt = msg.data.getString("prompt") ?: ""
                val structured = msg.what == 4
                val system = if (structured) {
                    (msg.data.getString("system") ?: "") + "\n/no_think\n直接输出最终JSON，不要解释。"
                } else {
                    "你是符文之地居民的个人认知引擎。依据个人经历、知识和环境思考。/no_think"
                }
                val requestedMax = msg.data.getInt("maxTokens", 160)

                scope.launch {
                    mutex.withLock {
                        val active = model
                        if (active == null || !active.isLoaded) {
                            reply(to, id, false, "模型尚未加载")
                            return@withLock
                        }
                        try {
                            val isSmall = small(modelPath)
                            // 768-token context combined with a 2600-character Chinese prompt can
                            // exceed the native context window and terminate the JNI process.
                            // Bound input well below the context limit and reserve room for output.
                            val cap = if (isSmall) 512 else 7000
                            val safePrompt = if (prompt.length > cap) prompt.take(cap) else prompt
                            val maxTokens = if (isSmall) requestedMax.coerceIn(1, 64)
                                            else requestedMax.coerceIn(1, 160)

                            val startedAt = android.os.SystemClock.elapsedRealtime()
                            val hashBeforeInference = try { sha256(File(modelPath)) } catch (_: Throwable) { "读取失败" }
                            diagnostic("INFO", "推理开始 id=$id structured=$structured inputChars=${safePrompt.length}/$cap maxTokens=$maxTokens small=$isSmall modelSHA=$hashBeforeInference")

                            val result = Llama.complete(
                                model = active,
                                prompt = if (structured && !safePrompt.contains("/no_think")) {
                                    safePrompt + "\n/no_think"
                                } else safePrompt,
                                systemPrompt = system,
                                maxTokens = maxTokens
                            )
                            val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
                            val hashAfterInference = try { sha256(File(modelPath)) } catch (_: Throwable) { "读取失败" }
                            val output = result.text.trim()
                            val replacementChars = output.count { it == '\uFFFD' }
                            diagnostic("INFO", "推理完成 id=$id elapsedMs=$elapsedMs outputChars=${output.length} " +
                                "tokens=${result.tokensGenerated} tps=${result.tokensPerSecond} " +
                                "promptEvalMs=${result.promptEvalTimeMs} generateMs=${result.generateTimeMs} " +
                                "replacementChars=$replacementChars outputPreview=${output.take(100)} " +
                                "modelSHA_before=$hashBeforeInference modelSHA_after=$hashAfterInference " +
                                "fileStable=${hashBeforeInference == hashAfterInference}")
                            reply(to, id, true, output)
                        } catch (e: Throwable) {
                            diagnostic("ERROR", "推理异常", e)
                            reply(to, id, false, "推理异常：" + (e.message ?: e.toString()))
                        }
                    }
                }
            }
        }
    }
}
