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
    private val maxResendAttempts = 1
    private var activePromptText = ""
    private var requestStartedAt = 0L
    private var currentRetryThresholdMs = 70_000L
    private var resendAttempts = 0
    private var resendInProgress = false
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
        // Match the whitespace normalization used by the page-side transcript reader.
        val normalized = raw.replace('\u00a0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val prompt = submittedPrompt.replace('\u00a0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
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
                else if (ch == '\\') escaped = true
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
              // ChatGPT may reflow text, insert zero-width characters, or change Unicode width.
              // Normalize those differences, then also compare a whitespace-free representation.
              const normalizeForMatch = value => clean(String(value || '')).normalize('NFKC')
                .replace(/[\u200B-\u200D\uFEFF]/g, '').replace(/\s+/g, ' ').trim().toLowerCase();
              const compactForMatch = value => normalizeForMatch(value).replace(/\s+/g, '');
              const promptMatch = normalizeForMatch(prompt);
              const promptCompact = compactForMatch(prompt);
              const isPromptMatch = value => {
                const normalized = normalizeForMatch(value);
                if (!promptMatch || !normalized) return false;
                if (normalized.includes(promptMatch)) return true;
                const compact = normalized.replace(/\s+/g, '');
                if (promptCompact && compact.includes(promptCompact)) return true;
                // Long prompts can be visually clipped in a virtualized DOM node.
                return promptCompact.length >= 96 &&
                  compact.includes(promptCompact.slice(0, 64)) &&
                  compact.includes(promptCompact.slice(-64));
              };
              const userRoleSelector = '[data-message-author-role="user"], [data-author="user"], [data-role="user"]';
              const userCandidateSelector = userRoleSelector + ', [data-testid*="user-message"], [data-testid*="user"]';
              const userCandidates = [];
              for (const root of roots) {
                try {
                  for (const node of root.querySelectorAll(userCandidateSelector)) {
                    if (!userCandidates.includes(node)) userCandidates.push(node);
                  }
                } catch (_) {}
              }
              const userPromptNodePresent = !!promptMatch && userCandidates.some(node =>
                isPromptMatch(node.innerText || node.textContent || '')
              );
              const sendStatus = String(window.__runeterraSendStatus || 'unknown');
              const roleSelector = '[data-message-author-role="assistant"], [data-author="assistant"], [data-role="assistant"]';
              const turns = [];
              for (const root of roots) {
                try {
                  for (const el of root.querySelectorAll('[data-testid^="conversation-turn-"], [data-testid*="conversation-turn"]')) {
                    if (visible(el) && !turns.includes(el)) turns.push(el);
                  }
                } catch (_) {}
              }
              // Locate the exact user turn first, then read the assistant turn that follows it.
              // This avoids requiring the full page transcript to contain an exact prompt copy.
              const readAssistantBody = node => {
                if (!node) return '';
                let bodies = [];
                try { bodies = Array.from(node.querySelectorAll('[data-message-content], .markdown, [class~="prose"]')).filter(visible); } catch (_) {}
                const leaf = bodies.filter(item => !bodies.some(other => other !== item && item.contains(other)));
                const chosen = leaf.length ? leaf : bodies;
                if (chosen.length) {
                  return clean(chosen.map(item => {
                    const clone = item.cloneNode(true);
                    clone.querySelectorAll('button, [role="toolbar"], nav, svg, [aria-hidden="true"]').forEach(n => n.remove());
                    return clone.innerText || clone.textContent || '';
                  }).filter(Boolean).join('\n\n'));
                }
                const clone = node.cloneNode(true);
                clone.querySelectorAll('button, [role="toolbar"], nav, svg, [aria-hidden="true"]').forEach(n => n.remove());
                return clean(clone.innerText || clone.textContent || '');
              };
              let pairedPromptFound = false, pairedAnswer = '', pairMethod = 'no-prompt-turn';
              let matchedTurnIndex = -1;
              if (promptMatch) {
                for (let i = turns.length - 1; i >= 0; i--) {
                  let userNode = null;
                  try { userNode = turns[i].querySelector(userRoleSelector); } catch (_) {}
                  if (!userNode || !visible(userNode)) continue;
                  const userText = normalizeForMatch(userNode.innerText || userNode.textContent || '');
                  if (userText && isPromptMatch(userText)) {
                    pairedPromptFound = true;
                    matchedTurnIndex = i;
                    pairMethod = 'matched-user-turn';
                    break;
                  }
                }
              }
              if (matchedTurnIndex >= 0) {
                for (let i = matchedTurnIndex + 1; i < turns.length; i++) {
                  let assistantNode = null;
                  try { assistantNode = turns[i].querySelector(roleSelector); } catch (_) {}
                  if (!assistantNode || !visible(assistantNode)) continue;
                  pairedAnswer = readAssistantBody(assistantNode);
                  if (pairedAnswer) {
                    pairMethod += '/next-assistant-turn';
                    break;
                  }
                }
              }
              // Fallback for layouts without conversation-turn wrappers: inspect role
              // messages in DOM order and take the first assistant message after the match.
              if (promptMatch && !pairedPromptFound) {
                for (const root of roots) {
                  try {
                    const messages = Array.from(root.querySelectorAll(userRoleSelector + ', ' + roleSelector)).filter(visible);
                    let at = -1;
                    for (let i = messages.length - 1; i >= 0; i--) {
                      if (!messages[i].matches(userRoleSelector)) continue;
                      const userText = normalizeForMatch(messages[i].innerText || messages[i].textContent || '');
                      if (userText && isPromptMatch(userText)) { at = i; break; }
                    }
                    if (at >= 0) {
                      pairedPromptFound = true;
                      const nextAssistant = messages.slice(at + 1).find(node => node.matches(roleSelector));
                      if (nextAssistant) {
                        pairedAnswer = readAssistantBody(nextAssistant);
                        if (pairedAnswer) pairMethod = 'role-message-order';
                      }
                      break;
                    }
                  } catch (_) {}
                }
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
                  // A ShadowRoot has no body/documentElement. Read its own text instead
                  // of silently skipping the subtree containing the conversation.
                  const container = root.body || root.documentElement || root;
                  const body = clean(container.innerText || container.textContent || '');
                  if (body) {
                    previews.push(body.slice(-700));
                    diagnosticParts.push(body);
                  }
                } catch (_) {}
              }
              // Do not take the last 50k characters of the whole conversation:
              // in a long chat that can cut the newly submitted prompt off entirely.
              // Find the latest exact prompt in each transcript first, then return
              // everything from that prompt onward so its complete answer is retained.
              const matchingTranscripts = diagnosticParts
                .map(body => {
                  const normalizedBody = normalizeForMatch(body);
                  return {body: normalizedBody, at: promptMatch ? normalizedBody.lastIndexOf(promptMatch) : -1};
                })
                .filter(item => item.at >= 0)
                .sort((a,b) => b.body.length-a.body.length);
              // The normalized transcript and normalized prompt share whitespace rules.
              // Keep a conservative fallback for unusual DOM structures; Kotlin still
              // requires the current prompt to be present before accepting any answer.
              const fallbackTranscripts = diagnosticParts
                .map(body => normalizeForMatch(body))
                .filter(body => body.length > 0)
                .sort((a,b) => b.length-a.length);
              // The full visible body is an additional fallback when role/test-id markup changes.
              const bodyPromptPresent = !!promptMatch && diagnosticParts.some(body => isPromptMatch(body));
              const promptPresent = matchingTranscripts.length > 0 || pairedPromptFound ||
                userPromptNodePresent || bodyPromptPresent;
              const diagnosticText = matchingTranscripts.length
                ? matchingTranscripts[0].body.slice(matchingTranscripts[0].at, matchingTranscripts[0].at + 150000)
                : (pairedPromptFound ? (prompt + '\n' + pairedAnswer) :
                  ((userPromptNodePresent || bodyPromptPresent) ? prompt : ''));
              const assistantCount = roots.reduce((n, root) => {
                try { return n + root.querySelectorAll(roleSelector).length; } catch (_) { return n; }
              }, 0);
              const rootDiagnostics = diagnosticParts.map(body => {
                const normalizedBody = normalizeForMatch(body);
                return {length: normalizedBody.length, promptOffset: promptMatch ? normalizedBody.lastIndexOf(promptMatch) : -1};
              });
              return JSON.stringify({
                text: answer, submittedPrompt: prompt, promptPresent: promptPresent,
                userPromptNodePresent: userPromptNodePresent,
                pairedPromptFound: pairedPromptFound, pairedAnswer: pairedAnswer,
                bodyPromptPresent: bodyPromptPresent, userCandidateCount: userCandidates.length,
                pairMethod: pairMethod, matchedTurnIndex: matchedTurnIndex,
                promptLength: promptMatch.length,
                matchingTranscriptCount: matchingTranscripts.length,
                diagnosticTextLength: diagnosticText.length,
                pairedAnswerLength: pairedAnswer.length,
                rootDiagnostics: rootDiagnostics,
                sendStatus: sendStatus, streaming: stop, assistantCount: assistantCount,
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
            // Use the strongest available source first. In v523 the DOM-selected
            // assistant text was discarded when transcript matching failed.
            val pairedPromptFound = data.optBoolean("pairedPromptFound")
            val pairedAnswer = data.optString("pairedAnswer").trim()
            val domAnswer = data.optString("text").trim()
            val promptPresent = data.optBoolean("promptPresent") || pairedPromptFound ||
                data.optBoolean("bodyPromptPresent") || data.optBoolean("userPromptNodePresent")
            val transcriptAnswer = if (promptPresent)
                extractLatestAnswerFromDiagnostic(diagnosticText, submittedPrompt) else ""
            var text = when {
                pairedPromptFound && pairedAnswer.isNotBlank() -> pairedAnswer
                transcriptAnswer.isNotBlank() -> transcriptAnswer
                promptPresent -> domAnswer
                else -> ""
            }
            val sendStatus = data.optString("sendStatus", "unknown")
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
            val currentAnswerDetected = promptPresent && text.isNotBlank() && isNewAnswer

            if (text.isNotBlank() && (force || currentAnswerDetected)) {
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
                        val finishedAt = System.currentTimeMillis()
                        recordSuccessfulDuration(finishedAt - requestStartedAt)
                        polling = false
                        resendInProgress = false
                        handler.removeCallbacks(pollTask)
                        val callback = worldRequestCallback
                        worldRequestCallback = null
                        activePromptText = ""
                        requestStartedAt = 0L
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
                status.text = "未找到助手回答：提问命中=" + promptPresent +
                    "，提问节点命中=" + data.optBoolean("userPromptNodePresent") +
                    "，本轮用户消息匹配=" + pairedPromptFound +
                    "，页面正文命中=" + data.optBoolean("bodyPromptPresent") +
                    "，用户节点候选=" + data.optInt("userCandidateCount") +
                    "，DOM回答字符=" + domAnswer.length +
                    "，配对方式=" + data.optString("pairMethod") +
                    "，匹配轮次=" + data.optInt("matchedTurnIndex") +
                    "，候选回答字符=" + data.optInt("pairedAnswerLength") +
                    "，自动重发=" + resendAttempts + "/" + maxResendAttempts +
                    "，当前阈值秒=" + (currentRetryThresholdMs / 1000) +
                    "，提问长度=" + data.optInt("promptLength") +
                    "，匹配记录数=" + data.optInt("matchingTranscriptCount") +
                    "，诊断文本=" + data.optInt("diagnosticTextLength") +
                    "，assistant节点=" + count + "，turn=" + turns +
                    "，DOM根=" + roots + "，Shadow=" + shadowHosts +
                    "，iframe=" + frames + "，页面字符=" + bodyLength +
                    "，元素=" + elements + "；方式=" + method +
                    "；页面=" + data.optString("url") + "；预览=" + preview +
                    "；框架=" + data.optJSONArray("iframeInfo")
            } else if (!force && !resendInProgress && resendAttempts < maxResendAttempts &&
                requestStartedAt > 0L &&
                System.currentTimeMillis() - requestStartedAt >= currentRetryThresholdMs &&
                (!promptPresent || !currentAnswerDetected)) {
                status.text = "超过历史平均耗时阈值（" + (currentRetryThresholdMs / 1000) +
                    "秒），提问命中=" + promptPresent + "，答案命中=" + currentAnswerDetected +
                    "；启动自动重发。"
                resendCurrentPrompt()
            } else if (!force && requestStartedAt > 0L &&
                System.currentTimeMillis() - requestStartedAt > maxPollDurationMs) {
                polling = false
                resendInProgress = false
                handler.removeCallbacks(pollTask)
                status.text = "等待超时（3分钟）：自动重发 " + resendAttempts +
                    " 次后仍没有检测到本轮完整回答。请检查网页、网络及诊断日志。"
                worldRequestCallback?.invoke("【ChatGPT网页等待超时】自动重发 " + resendAttempts + " 次后仍未检测到本轮完整回答。")
                worldRequestCallback = null
                activePromptText = ""
                requestStartedAt = 0L
            } else if (!force && text.isNotBlank() && !isNewAnswer) {
                status.text = "已读取到旧回答，继续等待本轮新回答……"
            } else if (!force && !promptPresent) {
                status.text = "暂未可靠定位本轮提问；发送状态=" + sendStatus +
                    "；等待阈值=" + (currentRetryThresholdMs / 1000) + "秒；自动重发=" +
                    resendAttempts + "/" + maxResendAttempts + "……"
            } else if (!force && text.isBlank()) {
                status.text = "已确认提问进入对话，正在等待完整回答…… assistant节点=" + count +
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
