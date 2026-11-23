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
import org.json.JSONArray
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
                    status.text = "网页已加载。登录 ChatGPT 后，在下方输入测试消息并点击发送。"
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
              setTimeout(() => {
                const buttons = Array.from(document.querySelectorAll('button'));
                const send = document.querySelector('[data-testid="send-button"]') ||
                  buttons.find(b => /send|发送/i.test((b.getAttribute('aria-label') || '') + ' ' + (b.getAttribute('data-testid') || '')));
                if (send && !send.disabled) {
                  send.click();
                } else {
                  box.dispatchEvent(new KeyboardEvent('keydown', {key:'Enter', code:'Enter', bubbles:true}));
                }
              }, 250);
              return JSON.stringify({ok:true, baselineText:baselineText, baselineCount:baselineCount, reason:'已填入输入框，正在触发发送'});
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

    private fun extractLatestAnswerFromMessages(messages: JSONArray?): String {
        if (messages == null || messages.length() == 0) return ""
        var lastUserIndex = -1
        var lastAssistantIndex = -1
        var lastAssistantText = ""
        for (i in 0 until messages.length()) {
            val item = messages.optJSONObject(i) ?: continue
            val role = item.optString("role").lowercase()
            val body = item.optString("text").trim()
            if (role == "user") {
                lastUserIndex = i
            } else if (role == "assistant" && body.isNotBlank()) {
                lastAssistantIndex = i
                lastAssistantText = body
            }
        }
        if (lastAssistantIndex <= lastUserIndex) return ""
        var answer = lastAssistantText
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
            .map { it.trimEnd() }
            .filterNot { line ->
                line.trim().equals("与 ChatGPT 聊天", true) ||
                    line.trim().equals("Message ChatGPT", true) ||
                    line.trim().equals("发送消息给 ChatGPT", true)
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
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
              const messageSelector = '[data-message-author-role="user"], [data-message-author-role="assistant"], [data-author="user"], [data-author="assistant"], [data-role="user"], [data-role="assistant"]';
              const diagnosticMessages = [];
              const seenMessages = new Set();
              for (const root of roots) {
                try {
                  for (const node of root.querySelectorAll(messageSelector)) {
                    if (!visible(node) || seenMessages.has(node)) continue;
                    seenMessages.add(node);
                    const role = (node.getAttribute('data-message-author-role') ||
                      node.getAttribute('data-author') || node.getAttribute('data-role') || '').toLowerCase();
                    const clone = node.cloneNode(true);
                    clone.querySelectorAll('button, [role="toolbar"], nav, svg, [aria-hidden="true"], [data-testid*="feedback"], [data-testid*="toolbar"]').forEach(n => n.remove());
                    const body = clean(clone.innerText || clone.textContent || '');
                    if ((role === 'user' || role === 'assistant') && body) {
                      diagnosticMessages.push({role: role, text: body});
                    }
                  }
                } catch (_) {}
              }

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
              const stop = roots.some(root => {
                try { return !!root.querySelector('[data-testid*="stop-button"], button[aria-label*="Stop"], button[aria-label*="停止"]'); }
                catch (_) { return false; }
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
              const diagnosticText = (diagnosticParts.filter(t => !prompt || t.includes(prompt)).sort((a,b) => b.length-a.length)[0] || diagnosticParts.sort((a,b) => b.length-a.length)[0] || '').slice(-12000);
              const assistantCount = roots.reduce((n, root) => {
                try { return n + root.querySelectorAll(roleSelector).length; } catch (_) { return n; }
              }, 0);
              return JSON.stringify({
                text: answer, submittedPrompt: prompt, diagnosticMessages: diagnosticMessages,
                streaming: stop, assistantCount: assistantCount,
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
            // Use message-role metadata. Works for app-sent and page-manually-sent prompts.
            val diagnosticMessages = data.optJSONArray("diagnosticMessages")
            var text = extractLatestAnswerFromMessages(diagnosticMessages)
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
                } else if (!streaming && stableCount >= 2) {
                    status.text = "本轮回答读取完成：" + text.length + " 字符；方式=" + method
                    polling = false
                    handler.removeCallbacks(pollTask)
                } else {
                    status.text = if (streaming) {
                        "GPT 正在生成，本轮已读取 " + text.length + " 字符……"
                    } else {
                        "检测到本轮新回答，等待文本稳定（" + (stableCount + 1) + "/3）……"
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
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
