package com.runeterra.local

/**
 * Serial bridge between the world cognition queue and the signed-in ChatGPT WebView.
 * It never stores credentials or bypasses the normal ChatGPT login/session.
 */
object ChatGPTWorldBridge {
    private data class Request(val prompt: String, val callback: (String) -> Unit)

    private val lock = Any()
    private val queue = ArrayDeque<Request>()
    @Volatile private var sender: ((String, (String) -> Unit) -> Unit)? = null
    private var activeRequest: Request? = null

    fun attach(handler: (String, (String) -> Unit) -> Unit) {
        synchronized(lock) { sender = handler }
        dispatchNext()
    }

    fun detach() {
        val failed = synchronized(lock) {
            sender = null
            val request = activeRequest
            activeRequest = null
            request
        }
        failed?.callback("【ChatGPT网页桥接中断】网页窗口已关闭，本轮人物思考未完成。请重新打开ChatGPT网页AI并重试。")
    }

    fun ask(prompt: String, callback: (String) -> Unit) {
        if (prompt.isBlank()) {
            callback("【ChatGPT网页桥接失败】人物任务内容为空。")
            return
        }
        val overflow = synchronized(lock) {
            if (queue.size >= 80) true else {
                queue.addLast(Request(prompt, callback))
                false
            }
        }
        if (overflow) {
            callback("【ChatGPT网页桥接繁忙】等待队列已满，本轮人物思考未提交。")
            return
        }
        dispatchNext()
    }

    fun queuedCount(): Int = synchronized(lock) { queue.size + if (activeRequest == null) 0 else 1 }

    /** Whether the signed-in ChatGPT WebView currently owns the request sender. */
    fun isAttached(): Boolean = synchronized(lock) { sender != null }

    private fun dispatchNext() {
        val pair = synchronized(lock) {
            val handler = sender ?: return
            if (activeRequest != null || queue.isEmpty()) return
            val request = queue.removeFirst()
            activeRequest = request
            handler to request
        }
        try {
            pair.first(pair.second.prompt) { answer -> finish(pair.second, answer) }
        } catch (error: Throwable) {
            finish(pair.second, "【ChatGPT网页桥接异常】" + (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun finish(request: Request, answer: String) {
        val callback = synchronized(lock) {
            if (activeRequest !== request) return
            activeRequest = null
            request.callback
        }
        try { callback(answer) } finally { dispatchNext() }
    }
}
