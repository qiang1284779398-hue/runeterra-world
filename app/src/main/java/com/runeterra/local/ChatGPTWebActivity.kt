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
            text = "读取最新回答"
            setOnClickListener { readLatest(force = true) }
        }
        controls.addView(send, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(read, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        result = TextView(this).apply {
            text = "回答会显示在这里"
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
        val script = """
            (function() {
              const text = $quoted;
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
              return JSON.stringify({ok:true, reason:'已填入输入框，正在触发发送'});
            })();
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val parsed = try { JSONTokener(raw ?: "null").nextValue() as? String } catch (_: Exception) { null }
            val obj = try { JSONObject(parsed ?: "{}") } catch (_: Exception) { JSONObject() }
            if (obj.optBoolean("ok")) {
                baseline = lastText
                stableCount = 0
                result.text = "已提交发送动作，等待 ChatGPT 回答……"
                status.text = obj.optString("reason")
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

    private fun readLatest(force: Boolean) {
        // Support both the older role-attribute DOM and newer conversation-turn DOM.
        val script = """
            (function() {
              const visible = el => {
                if (!el || !el.isConnected) return false;
                const r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
              };
              const readText = el => {
                if (!el) return '';
                const md = el.querySelector('.markdown, [class*="markdown"], [data-testid*="message-content"]');
                return ((md && (md.innerText || md.textContent)) || el.innerText || el.textContent || '').trim();
              };
              const roleNodes = Array.from(document.querySelectorAll('[data-message-author-role]'));
              const assistants = roleNodes.filter(n =>
                (n.getAttribute('data-message-author-role') || '').toLowerCase() === 'assistant'
              );
              let node = assistants.length ? assistants[assistants.length - 1] : null;
              let text = readText(node);
              let method = node ? 'role-attribute' : 'none';

              // Newer ChatGPT layouts may expose conversation turns without role attributes.
              const turnNodes = Array.from(document.querySelectorAll('[data-testid^="conversation-turn-"]')).filter(visible);
              const articles = Array.from(document.querySelectorAll('main article, article')).filter(visible);
              const messageNodes = Array.from(document.querySelectorAll('[data-message-id], [data-testid*="assistant"], [class*="agent-turn"]')).filter(visible);
              if (!text) {
                const pools = [
                  {name:'conversation-turn', nodes:turnNodes},
                  {name:'article', nodes:articles},
                  {name:'message-container', nodes:messageNodes}
                ];
                for (const pool of pools) {
                  const usable = pool.nodes.map(el => ({el, text:readText(el)})).filter(x => x.text.length > 0);
                  if (usable.length) {
                    const candidate = usable[usable.length - 1];
                    node = candidate.el;
                    text = candidate.text;
                    method = pool.name;
                    break;
                  }
                }
              }

              // Remove common UI-only text if the selected container is broad.
              const stop = document.querySelector(
                '[data-testid="stop-button"], button[aria-label*="Stop"], button[aria-label*="停止"], button[aria-label*="Stop generating"]'
              );
              const previews = turnNodes.slice(-3).map(el => readText(el).slice(0, 100));
              return JSON.stringify({
                text:text,
                streaming:!!stop,
                assistantCount:assistants.length,
                roleCount:roleNodes.length,
                turnCount:turnNodes.length,
                articleCount:articles.length,
                messageContainerCount:messageNodes.length,
                method:method,
                previews:previews,
                pageReady:document.readyState,
                url:location.href
              });
            })();
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            val encoded = try { JSONTokener(raw ?: "null").nextValue() as? String } catch (_: Exception) { null }
            val data = try { JSONObject(encoded ?: "{}") } catch (_: Exception) { JSONObject() }
            val text = data.optString("text").trim()
            val streaming = data.optBoolean("streaming")
            val count = data.optInt("assistantCount", 0)
            val roleCount = data.optInt("roleCount", 0)
            val turnCount = data.optInt("turnCount", 0)
            val articleCount = data.optInt("articleCount", 0)
            val method = data.optString("method", "none")
            if (text.isNotBlank()) {
                result.text = text
                if (text == lastText) stableCount++ else stableCount = 0
                lastText = text
                if (force) status.text = "读取成功：0 字符；方式=$method；对话轮=$turnCount"
                if (!force && text != baseline && !streaming && stableCount >= 2) {
                    status.text = "回答读取完成：0 字符"
                    polling = false
                    handler.removeCallbacks(pollTask)
                } else if (!force && text != baseline) {
                    status.text = if (streaming) "正在读取生成中的回答……" else "回答内容已更新，等待内容稳定……"
                }
            } else if (force) {
                status.text = "暂未读到：角色节点=$roleCount，assistant=$count，对话轮=$turnCount，文章=$articleCount。请复制这行诊断发我。"
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
