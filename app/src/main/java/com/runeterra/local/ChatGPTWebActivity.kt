package com.runeterra.local

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Visible WebView-based DOM reader for the user's own ChatGPT session.
 * The user signs in manually. No credentials, cookies, or challenge pages are bypassed.
 */
class ChatGPTWebActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prompt: EditText
    private lateinit var result: TextView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var baseline = ""
    private var lastText = ""
    private var stableCount = 0
    private var baselineAssistantCount = 0
    private var pollStartedAt = 0L
    private val maxPollDurationMs = 180_000L
    private var worldRequestCallback: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 8)
        }
        status = TextView(this).apply {
            text = "正在打开 ChatGPT。请在下方网页中手动登录；这是应用内独立网页会话。"
            textSize = 13f
            setPadding(4, 4, 4, 8)
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    status.text = "网页已加载。登录 ChatGPT 后，可将此网页会话作为符文之地世界的 AI 推理方案。"
                    ChatGPTWorldBridge.attach { message, callback ->
                        runOnUiThread {
                            if (polling) {
                                callback("【ChatGPT网页繁忙】当前网页仍在处理上一条任务，请稍后重试。")
                            } else {
                                worldRequestCallback = callback
                                prompt.setText(message)
                                sendPrompt()
                            }
                        }
                    }
                }
            }
        }
        prompt = EditText(this).apply {
            hint = "输入要发送给 ChatGPT 的内容"
            setText("请只回复：网页自动收发测试成功")
            minLines = 2
            maxLines = 4
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val send = Button(this).apply {
            text = "自动填写并发送"
            setOnClickListener { sendPrompt() }
        }
        val read = Button(this).apply {
            text = "直接提取回答"
            setOnClickListener {
                status.text = "已收到点击：正在从网页回答区域直接提取文本（不操作复制按钮、不读取剪贴板）……"
                readLatest(force = true)
            }
        }
        controls.addView(send, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(read, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        result = TextView(this).apply {
            text = "提取结果\\n点击“直接提取回答”后，答案会显示在这里"
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(4, 8, 4, 8)
        }
        root.addView(status)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(prompt)
        root.addView(controls)
        root.addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        web.loadUrl("https://chatgpt.com/")
    }


    private fun sendPrompt() {
        val message = prompt.text?.toString()?.trim().orEmpty()
        if (message.isEmpty()) {
            worldRequestCallback?.invoke("【ChatGPT网页桥接失败】提问内容为空。")
            worldRequestCallback = null
            Toast.makeText(this, "先输入要发送的内容", Toast.LENGTH_SHORT).show()
            return
        }
        val quoted = JSONObject.quote(message)
        web.evaluateJavascript("window.__runeterraLastPrompt = " + quoted + ";", null)
        val script = """
            (function() {
              const text = $quoted;
              const baselineRoots = [], baselineSeen = new Set();
              const addBaselineRoot = root => {
                if (!root || baselineSeen.has(root)) return;
                baselineSeen.add(root); baselineRoots.push(root);
                let els=[]; try { els=Array.from(root.querySelectorAll('*')); } catch (_) {}
                for (const el of els) { try { if (el.shadowRoot) addBaselineRoot(el.shadowRoot); } catch (_) {} }
                if (root === document) {
                  for (const frame of Array.from(document.querySelectorAll('iframe'))) {
                    try { if (frame.contentDocument) addBaselineRoot(frame.contentDocument); } catch (_) {}
                  }
                }
              };
              addBaselineRoot(document);
              const beforeNodes = [];
              for (const root of baselineRoots) {
                try { beforeNodes.push(...Array.from(root.querySelectorAll('[data-message-author-role="assistant"]'))); } catch (_) {}
              }
              const baselineCount = beforeNodes.length;
              const baselineText = baselineCount ? ((beforeNodes[baselineCount-1].innerText || beforeNodes[baselineCount-1].textContent || '').trim()) : '';
              const box =
                document.querySelector('[data-testid="prompt-textarea"]') ||
                document.querySelector('#prompt-textarea') ||
                document.querySelector('textarea') ||
                document.querySelector('[contenteditable="true"][role="textbox"]') ||
                document.querySelector('[contenteditable="true"]');
              if (!box) return JSON.stringify({ok:false, reason:'找不到输入框；请确认已登录且聊天页面已打开'});
              box.focus();
              if (box.tagName === 'TEXTAREA' || box.tagName === 'INPUT') {
                const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(box), 'value')?.set;
                if (setter) setter.call(box, text); else box.value = text;
              } else {
                box.textContent = text;
              }
              box.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'insertText', data:text}));
              box.dispatchEvent(new Event('change', {bubbles:true}));
              // ChatGPT mobile UI may update the send button asynchronously after input.
              // Wait for the real enabled send control instead of clicking after a fixed 250ms.
              window.__runeterraSendStatus = 'waiting';
              let attempts = 0;
              const sendTimer = setInterval(() => {
                attempts++;
                const buttons = Array.from(document.querySelectorAll('button'));
                const send = document.querySelector('[data-testid="send-button"]') ||
                  buttons.find(b => {
                    const label = [
                      b.getAttribute('aria-label') || '',
                      b.getAttribute('data-testid') || '',
                      b.getAttribute('title') || ''
                    ].join(' ');
                    return /(send|发送)/i.test(label);
                  });
                const visible = b => {
                  if (!b) return false;
                  const r = b.getBoundingClientRect();
                  const s = getComputedStyle(b);
                  return r.width > 0 && r.height > 0 && s.display !== 'none' && s.visibility !== 'hidden';
                };
                const enabled = send && !send.disabled && send.getAttribute('aria-disabled') !== 'true';
                if (visible(send) && enabled) {
                  clearInterval(sendTimer);
                  send.click();
                  window.__runeterraSendStatus = 'clicked';
                  return;
                }
                if (attempts >= 40) {
                  clearInterval(sendTimer);
                  // Last resort: submit with Enter only after the UI had time to process input.
                  box.focus();
                  box.dispatchEvent(new KeyboardEvent('keydown', {
                    key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true
                  }));
                  box.dispatchEvent(new KeyboardEvent('keyup', {
                    key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true
                  }));
                  window.__runeterraSendStatus = 'enter-fallback';
                }
              }, 200);
              return JSON.stringify({ok:true, baselineText:baselineText, baselineCount:baselineCount, reason:'请求已填入网页输入框，正在等待发送按钮就绪（最多8秒）'});
            })();
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val parsed = try { JSONTokener(raw ?: "null").nextValue() as? String } catch (_: Exception) { null }
            val obj = try { JSONObject(parsed ?: "{}") } catch (_: Exception) { JSONObject() }
            if (obj.optBoolean("ok")) {
                baseline = obj.optString("baselineText", lastText)
                baselineAssistantCount = obj.optInt("baselineCount", 0)
                lastText = ""
                stableCount = 0
                pollStartedAt = System.currentTimeMillis()
                result.text = "已触发发送，正在等待本轮新回答……"
                status.text = obj.optString("reason") + "；发送前已有回答数=" + baselineAssistantCount
                startPolling()
            } else {
                status.text = obj.optString("reason", "发送失败")
                worldRequestCallback?.invoke("【ChatGPT网页发送失败】" + status.text)
                worldRequestCallback = null
                Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPolling() {
        polling = true
        handler.removeCallbacks(pollTask)
        handler.post(pollTask)
    }

    private val pollTask = object : Runnable {
        override fun run() {
            if (!polling || isFinishing) return
            readLatest(force = false)
            handler.postDelayed(this, 1600)
        }
    }

    private fun extractLatestAnswerFromDiagnostic(raw: String, submittedPrompt: String): String {
        if (raw.isBlank() || submittedPrompt.isBlank()) return ""
        val normalized = raw.replace("\r", "").replace('\u00a0', ' ')
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        val prompt = submittedPrompt.replace("\r", "").trim()
        if (prompt.isEmpty()) return ""

        // Prefer an exact whole-line prompt match so earlier conversation history
        // cannot leak into the extracted answer.
        val lines = normalized.split('\n')
        var offset = 0
        var promptEnd = -1
        for (line in lines) {
            if (line.trim() == prompt) promptEnd = offset + line.length
            offset += line.length + 1
        }
        if (promptEnd < 0) {
            val at = normalized.lastIndexOf(prompt)
            if (at < 0) return ""
            promptEnd = at + prompt.length
        }

        var answer = normalized.substring(promptEnd).trim()
        val footerMarkers = listOf(
            "ChatGPT 是 AI，可能会犯错", "ChatGPT是AI，可能会犯错",
            "ChatGPT may make mistakes", "ChatGPT can make mistakes",
            "免责声明", "与 ChatGPT 聊天", "Message ChatGPT",
            "发送消息给 ChatGPT"
        )
        val footerAt = footerMarkers.map { answer.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }.minOrNull()
        if (footerAt != null) answer = answer.substring(0, footerAt)

        return answer.lineSequence()
            .map { it.trim() }
            .filterNot { line ->
                line.isBlank() || line.equals("与 ChatGPT 聊天", true) ||
                    line.equals("Message ChatGPT", true) ||
                    line.equals("发送消息给 ChatGPT", true) ||
                    line.equals("ChatGPT", true)
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** A stable, parseable character JSON is stronger completion evidence than a stale stop button. */
    private fun isCompleteCharacterJson(value: String): Boolean {
        val cleaned = value.trim()
            .replace(Regex("(?is)^\\u0060{3}\\s*json\\s*"), "")
            .replace(Regex("(?s)\\u0060{3}\\s*$"), "")
        val start = cleaned.indexOf('{')
        if (start < 0) return false
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until cleaned.length) {
            val ch = cleaned[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (ch == '\\\\') escaped = true
                else if (ch == '"') quoted = false
            } else when (ch) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val obj = runCatching { JSONObject(cleaned.substring(start, i + 1)) }.getOrNull()
                            ?: return false
                        return obj.optString("thought").isNotBlank()
                    }
                }
            }
        }
        return false
    }

    private fun readLatest(force: Boolean) {
        // Traverse the live document, open shadow roots and same-origin iframes.
        // Only accept assistant message containers; never treat the whole page or
        // the user's own prompt as an assistant answer.
        val script = """
            (function() {
              const roots = [], seen = new Set();
              function addRoot(root) {
                if (!root || seen.has(root)) return;
                seen.add(root); roots.push(root);
                let els = [];
                try { els = Array.from(root.querySelectorAll('*')); } catch (_) {}
                for (const el of els) { try { if (el.shadowRoot) addRoot(el.shadowRoot); } catch (_) {} }
                if (root === document) {
                  for (const frame of Array.from(document.querySelectorAll('iframe'))) {
                    try { if (frame.contentDocument) addRoot(frame.contentDocument); } catch (_) {}
                  }
                }
              }
              addRoot(document);
              const visible = el => {
                try {
                  const r = el.getBoundingClientRect(), st = getComputedStyle(el);
                  return r.width > 0 && r.height > 0 && st.display !== 'none' && st.visibility !== 'hidden';
                } catch (_) { return false; }
              };
              const clean = value => (value || '').replace(/\u00a0/g, ' ')
                .replace(/\r/g, '').replace(/[ \t]+\n/g, '\n')
                .replace(/\n{3,}/g, '\n\n').trim();
              const prompt = clean(String(window.__runeterraLastPrompt || ''));
              const roleSelector = '[data-message-author-role="assistant"], [data-author="assistant"], [data-role="assistant"]';
              const turns = [];
              for (const root of roots) {
                try {
                  for (const el of root.querySelectorAll('[data-testid^="conversation-turn-"], [data-testid*="conversation-turn"]')) {
                    if (visible(el) && !turns.includes(el)) turns.push(el);
                  }
                } catch (_) {}
              }
              // Scope extraction to the newest conversation turn first. This prevents
              // adjacent user messages and older answers from entering the result.
              let selected = null, method = 'no-assistant-turn';
              for (let i = turns.length - 1; i >= 0 && !selected; i--) {
                let assistant = null;
                try { assistant = turns[i].querySelector(roleSelector); } catch (_) {}
                if (assistant && visible(assistant)) {
                  selected = assistant;
                  method = 'latest-conversation-turn/assistant-role';
                }
              }
              // If the site's turn wrapper is absent, use the last assistant-role
              // element in DOM order, never a page-wide markdown fallback.
              if (!selected) {
                const assistants = [];
                for (const root of roots) {
                  try {
                    for (const el of root.querySelectorAll(roleSelector)) {
                      if (visible(el) && !assistants.includes(el)) assistants.push(el);
                    }
                  } catch (_) {}
                }
                if (assistants.length) {
                  selected = assistants[assistants.length - 1];
                  method = 'last-assistant-role';
                }
              }
              let answer = '';
              if (selected) {
                // Prefer the actual rendered markdown body inside the selected
                // assistant message. Ignore buttons, feedback controls and toolbars.
                let bodies = [];
                try {
                  bodies = Array.from(selected.querySelectorAll(
                    '[data-message-content], .markdown, [class~="prose"]'
                  )).filter(visible);
                } catch (_) {}
                const leafBodies = bodies.filter(node =>
                  !bodies.some(other => other !== node && node.contains(other))
                );
                const chosenBodies = leafBodies.length ? leafBodies : bodies;
                if (chosenBodies.length) {
                  answer = clean(chosenBodies.map(node => {
                    const clone = node.cloneNode(true);
                    clone.querySelectorAll('button, [role="toolbar"], nav, svg, [aria-hidden="true"]').forEach(n => n.remove());
                    return clone.innerText || clone.textContent || '';
                  }).filter(Boolean).join('\n\n'));
                  method += '/message-body';
                }
                if (!answer) {
                  const clone = selected.cloneNode(true);
                  clone.querySelectorAll('button, [role="toolbar"], nav, svg, [aria-hidden="true"]').forEach(n => n.remove());
                  answer = clean(clone.innerText || clone.textContent || '');
                  method += '/role-container';
                }
              }
              // Remove the exact submitted prompt if the DOM unexpectedly nests it
              // with the assistant content. Do not return any other conversation turn.
              if (prompt && answer.includes(prompt)) {
                answer = clean(answer.split(prompt).join(' '));
                method += '/prompt-removed';
              }
              // A hidden/stale stop control may remain mounted after generation.
              // Only treat a visible, enabled stop control as active streaming.
              const stop = roots.some(root => {
                try {
                  const candidates = Array.from(root.querySelectorAll(
                    '[data-testid*="stop-button"], button[aria-label*="Stop"], button[aria-label*="停止"]'
                  ));
                  return candidates.some(button => {
                    const rect = button.getBoundingClientRect();
                    const style = getComputedStyle(button);
                    return rect.width > 0 && rect.height > 0 &&
                      style.display !== 'none' && style.visibility !== 'hidden' &&
                      !button.disabled && button.getAttribute('aria-disabled') !== 'true';
                  });
                } catch (_) { return false; }
              });
              const previews = [];
              const diagnosticParts = [];
              for (const root of roots) {
                try {
                  const body = clean((root.body || root.documentElement).innerText || '');
                  previews.push(body.slice(-700));
                  diagnosticParts.push(body);
                } catch (_) {}
              }
              // Keep enough transcript to retain long world-task prompts and their answers.
              const diagnosticText = (diagnosticParts.filter(t => !prompt || t.includes(prompt)).sort((a,b) => b.length-a.length)[0] || diagnosticParts.sort((a,b) => b.length-a.length)[0] || '').slice(-50000);
              const assistantCount = roots.reduce((n, root) => {
                try { return n + root.querySelectorAll(roleSelector).length; } catch (_) { return n; }
              }, 0);
              return JSON.stringify({
                text: answer, submittedPrompt: prompt, streaming: stop, assistantCount: assistantCount,
                turnCount: turns.length, rootCount: roots.length,
                bodyLength: previews.reduce((n, p) => n + p.length, 0),
                bodyPreview: previews.join(' | ').slice(-1200),
                diagnosticText: diagnosticText,
                method: method, title: document.title, url: location.href,
                ready: document.readyState,
                selectedTag: selected ? selected.tagName : '',
                selectedTestId: selected ? (selected.getAttribute('data-testid') || '') : ''
              });
            })();
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val encoded = try { JSONTokener(raw ?: "null").nextValue() as? String } catch (_: Exception) { null }
            val data = try { JSONObject(encoded ?: "{}") } catch (_: Exception) { JSONObject() }
            // Parse the page diagnostic transcript, not the assistant DOM selector.
            // It contains the visible conversation in order: user prompt, then assistant answer.
            val submittedPrompt = data.optString("submittedPrompt").ifBlank {
                prompt.text?.toString().orEmpty()
            }.trim()
            val diagnosticText = data.optString("diagnosticText")
            // The diagnostic transcript is the sole source of truth. Find the latest
            // standalone occurrence of the exact submitted prompt, then keep only what
            // follows it; previous page content and the user's prompt are excluded.
            var text = extractLatestAnswerFromDiagnostic(diagnosticText, submittedPrompt)
            val streaming = data.optBoolean("streaming")
            val count = data.optInt("assistantCount", 0)
            val turns = data.optInt("turnCount", 0)
            val roots = data.optInt("rootCount", 0)
            val shadowHosts = data.optInt("shadowHostCount", 0)
            val frames = data.optInt("iframeCount", 0)
            val elements = data.optInt("elementCount", 0)
            val bodyLength = data.optInt("bodyLength", 0)
            val method = data.optString("method", "none")
            val isNewAnswer = count > baselineAssistantCount || (text.isNotBlank() && text != baseline)

            if (text.isNotBlank() && (force || isNewAnswer)) {
                result.text = text
                if (text == lastText) stableCount++ else stableCount = 0
                lastText = text
                if (force) {
                    status.text = "读取成功：" + text.length + " 字符；方式=" + method +
                        "；assistant节点=" + count + "；DOM根=" + roots + "；Shadow=" + shadowHosts
                } else {
                    // Normal path: stop control is gone and text has remained stable for 3 polls.
                    // Recovery path: if the page leaves a stale stop control mounted, accept only
                    // a complete, parseable character JSON that stays byte-for-byte stable longer.
                    val stableJsonComplete = streaming && stableCount >= 4 && isCompleteCharacterJson(text)
                    val completed = (!streaming && stableCount >= 2) || stableJsonComplete
                    if (completed) {
                        status.text = if (stableJsonComplete) {
                            "检测到停止按钮状态未更新，但人物JSON已完整且稳定：" + text.length + " 字符；队列继续"
                        } else {
                            "本轮回答读取完成：" + text.length + " 字符；方式=" + method
                        }
                        polling = false
                        handler.removeCallbacks(pollTask)
                        val callback = worldRequestCallback
                        worldRequestCallback = null
                        callback?.invoke(text)
                    } else {
                        status.text = if (streaming) {
                            "GPT 正在生成，本轮已读取 " + text.length + " 字符；稳定次数=" + stableCount + "……"
                        } else {
                            "检测到本轮新回答，等待文本稳定（" + (stableCount + 1) + "/3）……"
                        }
                    }
                }
            } else if (force) {
                val preview = data.optString("bodyPreview").replace("\n", " ").takeLast(180)
                status.text = "未找到助手回答：assistant节点=" + count +
                    "，turn=" + turns + "，DOM根=" + roots + "，Shadow=" + shadowHosts +
                    "，iframe=" + frames + "，页面字符=" + bodyLength +
                    "，元素=" + elements + "；方式=" + method +
                    "；页面=" + data.optString("url") + "；预览=" + preview +
                    "；框架=" + data.optJSONArray("iframeInfo")
            } else if (!force && System.currentTimeMillis() - pollStartedAt > maxPollDurationMs) {
                polling = false
                handler.removeCallbacks(pollTask)
                status.text = "等待超时（3分钟）：没有检测到本轮新回答。请检查网页是否仍在生成、网络是否正常，然后点击“读取最新回答”查看诊断。"
                worldRequestCallback?.invoke("【ChatGPT网页等待超时】3分钟内没有检测到本轮完整回答。")
                worldRequestCallback = null
            } else if (!force && text.isNotBlank() && !isNewAnswer) {
                status.text = "已读取到旧回答，继续等待本轮新回答……"
            } else if (!force && text.isBlank()) {
                status.text = "正在扫描 ChatGPT 回答节点…… assistant节点=" + count +
                    "；DOM根=" + roots + "；Shadow=" + shadowHosts + "；iframe=" + frames
            }
        }
    }

    override fun onDestroy() {
        polling = false
        ChatGPTWorldBridge.detach()
        worldRequestCallback = null
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
