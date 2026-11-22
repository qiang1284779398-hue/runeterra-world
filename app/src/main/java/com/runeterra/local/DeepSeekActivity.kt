package com.runeterra.local

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlin.concurrent.thread

class DeepSeekActivity : Activity() {
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var output: TextView
    private val prefs by lazy { getSharedPreferences("deepseek_secure", MODE_PRIVATE) }
    private val alias = "runeterra_deepseek_aes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 24)
        }
        root.addView(TextView(this).apply {
            text = "DeepSeek 云端 AI"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "把 API Key 粘贴到这里。密钥只保存在本机加密存储，不会写入 GitHub。"
            textSize = 14f
            setPadding(0, 12, 0, 12)
        })
        keyInput = EditText(this).apply {
            hint = "粘贴 DeepSeek API Key"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
        }
        modelInput = EditText(this).apply {
            setText("deepseek-flash")
            hint = "模型名称"
            setSingleLine(true)
        }
        output = TextView(this).apply {
            text = "尚未测试"
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(0, 16, 0, 12)
        }
        val save = Button(this).apply {
            text = "加密保存 API Key"
            setOnClickListener {
                val key = keyInput.text.toString().trim()
                if (key.isBlank()) {
                    Toast.makeText(this@DeepSeekActivity, "请先粘贴 API Key", Toast.LENGTH_SHORT).show()
                } else try {
                    saveKey(key)
                    keyInput.setText("")
                    output.text = "API Key 已加密保存在本机。"
                } catch (e: Exception) {
                    output.text = "保存失败：" + (e.message ?: e.toString())
                }
            }
        }
        val test = Button(this).apply {
            text = "测试 DeepSeek 连接"
            setOnClickListener { testConnection() }
        }
        root.addView(keyInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(modelInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        root.addView(save)
        root.addView(test)
        root.addView(output, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(root)
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun saveKey(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("key_data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv_data", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("model", modelInput.text.toString().trim().ifBlank { "deepseek-flash" })
            .apply()
    }

    private fun loadKey(): String {
        val data = prefs.getString("key_data", null) ?: return ""
        val iv = prefs.getString("iv_data", null) ?: return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(),
            javax.crypto.spec.GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun testConnection() {
        val entered = keyInput.text.toString().trim()
        if (entered.isNotBlank()) {
            try { saveKey(entered); keyInput.setText("") } catch (e: Exception) {
                output.text = "密钥保存失败：" + (e.message ?: e.toString()); return
            }
        }
        val key = try { loadKey() } catch (e: Exception) { "" }
        if (key.isBlank()) {
            output.text = "请先粘贴并保存 API Key。"
            return
        }
        val model = modelInput.text.toString().trim().ifBlank { "deepseek-flash" }
        output.text = "正在连接 DeepSeek……"
        thread {
            var connection: HttpURLConnection? = null
            val message = try {
                connection = (URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("model", model)
                    .put("messages", org.json.JSONArray().put(JSONObject()
                        .put("role", "user").put("content", "请只回复：DeepSeek连接成功")))
                    .put("stream", false).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (code !in 200..299) {
                    "请求失败 HTTP $code：$response"
                } else {
                    val content = JSONObject(response).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").optString("content")
                    "连接成功！模型：$model\n返回：$content"
                }
            } catch (e: Exception) {
                "连接失败：" + (e.message ?: e.toString())
            } finally { connection?.disconnect() }
            runOnUiThread { output.text = message }
        }
    }
}
