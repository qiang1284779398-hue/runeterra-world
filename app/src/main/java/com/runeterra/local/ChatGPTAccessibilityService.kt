package com.runeterra.local

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatGPTAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 500
        }
        log("辅助服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: "unknown"
        if (pkg == "com.android.chrome") {
            val texts = mutableListOf<String>()
            collect(rootInActiveWindow, texts, 0)
            log("Chrome 前台事件=" + event.eventType + "；可见文本节点=" + texts.size)
            if (texts.isNotEmpty()) log("网页可见文本：\n" + texts.distinct().joinToString("\n").take(5000))
        } else {
            log("前台应用=" + pkg + "；Chrome 在后台时无法读取其隐藏网页内容")
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 30 || out.size > 100) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth + 1)
    }

    override fun onInterrupt() { log("辅助服务中断") }

    private fun log(message: String) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            File(filesDir, "bridge-test.log").appendText("[" + time + "] " + message + "\n", Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val component = context.packageName + "/" + ChatGPTAccessibilityService::class.java.name
            return enabled.split(":").any { it.equals(component, ignoreCase = true) }
        }
        fun readLog(context: Context): String {
            val file = File(context.filesDir, "bridge-test.log")
            return if (file.exists()) file.readText(Charsets.UTF_8).takeLast(16000) else "暂无日志。请先启用辅助服务并打开 ChatGPT 网页测试。"
        }
    }
}