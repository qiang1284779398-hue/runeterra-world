package com.runeterra.local

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/** One provider shared by every hero and resident turn. */
object WorldAIProvider {
 private const val PREFS="zhipu_secure"
 private const val ALIAS="runeterra_zhipu_aes"
 private const val ENDPOINT="https://open.bigmodel.cn/api/paas/v4/chat/completions"
 fun isAvailable(context:Context)=isConfigured(context)||ResidentAI.isLoaded()
 private fun isConfigured(context:Context)=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("key_data",null)?.isNotBlank()==true
 private fun decrypt(context:Context):String {
  val p=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val data=p.getString("key_data",null)?:return ""
  val iv=p.getString("iv_data",null)?:return ""
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val secret=ks.getKey(ALIAS,null) as? SecretKey ?: return ""
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))
  return String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8)
 }
 fun ask(context:Context,prompt:String,maxTokens:Int,systemPrompt:String,callback:(String)->Unit) {
  val app=context.applicationContext
  val p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val apiKey=try{decrypt(app)}catch(_:Throwable){""}
  if(apiKey.isBlank()){if(ResidentAI.isLoaded()) ResidentAI.askStructured(prompt,maxTokens,systemPrompt,callback) else callback("未配置云端API，且本地模型未加载");return}
  val model=p.getString("model","glm-4-flash")?.trim().orEmpty().ifBlank{"glm-4-flash"}
  thread(name="SharedCloudAI"){
   var conn:HttpURLConnection?=null
   var output=""
   var cloudFailed=false
   try {
    conn=(URL(ENDPOINT).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=15000;readTimeout=90000;doOutput=true;setRequestProperty("Authorization","Bearer $apiKey");setRequestProperty("Content-Type","application/json")}
    val body=JSONObject().put("model",model).put("messages",JSONArray().put(JSONObject().put("role","system").put("content",systemPrompt)).put(JSONObject().put("role","user").put("content",prompt))).put("max_tokens",maxTokens).put("temperature",0.7).put("stream",false).toString()
    conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
    val code=conn.responseCode
    val stream=if(code in 200..299)conn.inputStream else conn.errorStream
    val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?: "无响应正文"
    if(code !in 200..299){cloudFailed=true;output="云端HTTP $code：$raw"}else {
     val content=JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
     output=when(content){is String->content;is JSONArray->content.toString();else->""}.trim().ifBlank{"智谱返回空内容"}
    }
   }catch(e:Throwable){cloudFailed=true;output="云端请求异常："+(e.message?:e.javaClass.simpleName)}finally{conn?.disconnect()}
   if(cloudFailed&&ResidentAI.isLoaded()) ResidentAI.askStructured(prompt,maxTokens,systemPrompt){callback("【云端失败，回退本地】\n"+it)} else callback(output)
  }
 }
}
