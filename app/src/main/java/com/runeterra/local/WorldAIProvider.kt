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

/** One shared Zhipu provider for hero and resident turns. */
object WorldAIProvider {
 private const val PREFS="zhipu_secure"
 private const val ALIAS="runeterra_zhipu_aes"
 private const val ENDPOINT="https://open.bigmodel.cn/api/paas/v4/chat/completions"

 fun isAvailable(context:Context)=isConfigured(context)||isBackendConfigured(context)||ResidentAI.isLoaded()
 private fun isBackendConfigured(context:Context)=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("cognitive_backend_url",null)?.isNotBlank()==true
 private fun isConfigured(context:Context)=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("key_data",null)?.isNotBlank()==true

 private fun decryptStored(context:Context,dataKey:String,ivKey:String):String {
  val p=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val data=p.getString(dataKey,null)?:return ""
  val iv=p.getString(ivKey,null)?:return ""
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val secret=ks.getKey(ALIAS,null) as? SecretKey ?: return ""
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))
  return String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8)
 }

 private fun decrypt(context:Context)=decryptStored(context,"key_data","iv_data")

 /** Extract a JSON object without assuming that the model omitted harmless fences or think tags. */
 private fun hasCharacterJson(raw:String):Boolean {
  var text=raw.trim().replace(Regex("(?is)<think>.*?</think>"),"")
  val open=text.indexOf("<think>",ignoreCase=true)
  if(open>=0) {
   val close=text.indexOf("</think>",open,ignoreCase=true)
   if(close<0)return false
   text=text.removeRange(open,close+8)
  }
  text=text.replace(Regex("(?i)^\\s*```\\s*(json)?"),"").replace("```","").trim()
  for(start in text.indices) {
   if(text[start]!='{')continue
   var depth=0
   var quoted=false
   var escaped=false
   for(i in start until text.length) {
    val ch=text[i]
    if(quoted) {
     if(escaped)escaped=false else if(ch=='\\')escaped=true else if(ch=='"')quoted=false
    } else when(ch) {
     '"'->quoted=true
     '{'->depth++
     '}'->{
      depth--
      if(depth==0) {
       val obj=runCatching{JSONObject(text.substring(start,i+1))}.getOrNull()
       if(obj!=null) {
        val thought=obj.opt("thought")
        if(thought is String && thought.isNotBlank())return true
       }
       break
      }
     }
    }
   }
  }
  return false
 }

 private fun request(
  apiKey:String,model:String,systemPrompt:String,prompt:String,maxTokens:Int,
  previousBad:String?=null
 ):String {
  var conn:HttpURLConnection?=null
  try {
   conn=(URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
    requestMethod="POST";connectTimeout=20000;readTimeout=90000;doOutput=true
    setRequestProperty("Authorization","Bearer $apiKey")
    setRequestProperty("Content-Type","application/json")
   }
   val messages=JSONArray()
    .put(JSONObject().put("role","system").put("content",systemPrompt))
   if(previousBad!=null) {
    messages.put(JSONObject().put("role","assistant").put("content",previousBad.take(6000)))
    messages.put(JSONObject().put("role","user").put("content",
     "上一次输出无法解析。请修正并重新输出完整结果。必须是单个合法JSON对象，不要Markdown、解释或<think>标签；必须包含非空字符串字段thought。保留原本人物设定和语义。"))
   } else {
    messages.put(JSONObject().put("role","user").put("content",prompt))
   }
   val body=JSONObject()
    .put("model",model)
    .put("messages",messages)
    .put("max_tokens",maxTokens.coerceAtLeast(256))
    .put("temperature",1.0)
    .put("top_p",0.95)
    .put("reasoning_effort","max")
    .put("stream",false)
    .put("response_format",JSONObject().put("type","json_object"))
    .toString()
   conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
   val code=conn.responseCode
   val stream=if(code in 200..299)conn.inputStream else conn.errorStream
   val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:"无响应正文"
   if(code !in 200..299)return "云端HTTP $code：$raw"
   val content=JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
   return when(content) {
    is String->content.trim()
    is JSONArray->buildString {
     for(i in 0 until content.length()) {
      val item=content.optJSONObject(i)
      append(item?.optString("text").orEmpty())
     }
    }.trim()
    else->"智谱返回空内容"
   }.ifBlank{"智谱返回空内容"}
  } finally {conn?.disconnect()}
 }

 private fun requestCognitiveBackend(
  baseUrl:String,token:String,worldId:String,actorId:String,actorName:String,day:Long,
  characterContext:String,prompt:String,systemPrompt:String
 ):String {
  var conn:HttpURLConnection?=null
  try {
   conn=(URL(baseUrl.trimEnd('/')+"/v1/think").openConnection() as HttpURLConnection).apply {
    requestMethod="POST";connectTimeout=15000;readTimeout=180000;doOutput=true
    setRequestProperty("Content-Type","application/json")
    if(token.isNotBlank())setRequestProperty("X-Runeterra-Token",token)
   }
   val body=JSONObject().put("world_id",worldId).put("actor_id",actorId).put("actor_name",actorName)
    .put("day",day).put("character_context",characterContext.take(16000))
    .put("prompt",prompt.take(20000)).put("system_prompt",systemPrompt.take(12000)).toString()
   conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
   val code=conn.responseCode
   val stream=if(code in 200..299)conn.inputStream else conn.errorStream
   val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:"后端无响应正文"
   if(code !in 200..299)throw IllegalStateException("认知后端 HTTP $code：$raw")
   return JSONObject(raw).optString("output").ifBlank{throw IllegalStateException("认知后端未返回 output")}
  } finally {conn?.disconnect()}
 }

 fun ask(
  context:Context,prompt:String,maxTokens:Int,systemPrompt:String,
  actorId:String?=null,actorName:String?=null,worldDay:Long=0L,
  callback:(String)->Unit
 ) {
  val app=context.applicationContext
  val p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val backendUrl=p.getString("cognitive_backend_url","").orEmpty().trim()
  val backendToken=try{decryptStored(app,"cognitive_backend_token_data","cognitive_backend_token_iv")}catch(_:Throwable){""}
  val apiKey=try{decrypt(app)}catch(_:Throwable){""}
  if(apiKey.isBlank() && backendUrl.isBlank()) {
   if(ResidentAI.isLoaded())ResidentAI.askStructured(prompt,maxTokens,systemPrompt,callback)
   else callback("未配置云端API、认知后端，且本地模型未加载")
   return
  }
  val model=p.getString("model","glm-5.3-flashx")?.trim().orEmpty().ifBlank{"glm-5.3-flashx"}
  thread(name="SharedCloudAI") {
   if(backendUrl.isNotBlank() && !actorId.isNullOrBlank() && !actorName.isNullOrBlank()) {
    try {
     val backendOutput=requestCognitiveBackend(
      backendUrl,backendToken,"runeterra-world",actorId,actorName,worldDay,
      prompt,prompt,systemPrompt
     )
     callback(backendOutput)
     return@thread
    } catch(_:Throwable) {
     // Keep the world playable if the optional cognitive backend is unavailable.
     // The direct Zhipu path below remains the fallback.
    }
   }
   if(apiKey.isBlank()) {
    if(ResidentAI.isLoaded()) ResidentAI.askStructured(prompt,maxTokens,systemPrompt,callback)
    else callback("认知后端连接失败，且未配置智谱直连或本地模型")
    return@thread
   }
   var output=""
   var cloudFailed=false
   try {
    output=request(apiKey,model,systemPrompt,prompt,maxTokens)
    if(!output.startsWith("云端HTTP") && !output.startsWith("云端请求异常") && !hasCharacterJson(output)) {
     val repaired=runCatching{request(apiKey,model,systemPrompt,prompt,maxTokens,output)}.getOrElse{"格式纠正请求失败：${it.message?:it.javaClass.simpleName}"}
     if(hasCharacterJson(repaired))output=repaired
     else output="角色JSON格式纠正后仍失败。首次输出：${output.take(1200)}；重试输出：${repaired.take(1200)}"
    }
    if(output.startsWith("云端HTTP")||output.startsWith("云端请求异常"))cloudFailed=true
   } catch(e:Throwable) {
    cloudFailed=true
    output="云端请求异常："+(e.message?:e.javaClass.simpleName)
   }
   if(cloudFailed&&ResidentAI.isLoaded())ResidentAI.askStructured(prompt,maxTokens,systemPrompt){callback("【云端失败，回退本地】\n"+it)}
   else callback(output)
  }
 }
}
