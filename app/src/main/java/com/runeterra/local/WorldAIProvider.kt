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

 fun isAvailable(context:Context):Boolean {
  val p=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  return if(p.getString("world_ai_provider","zhipu")=="chatgpt") true
  else isConfigured(context)
 }
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

 /** Keep credential/decryption failures visible instead of silently disguising them as ordinary offline cognition. */
 private fun loadApiKey(context:Context):Pair<String,String> {
  val p=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  if(p.getString("key_data",null).isNullOrBlank()) return "" to "本机没有保存智谱 API Key"
  return try {
   val key=decrypt(context)
   if(key.isBlank()) "" to "智谱 API Key 解密后为空，请在智谱设置页重新保存密钥"
   else key to ""
  } catch(e:Throwable) {
   android.util.Log.e("RuneterraAI","智谱 API Key 解密失败",e)
   "" to "智谱 API Key 解密失败：${e.message?:e.javaClass.simpleName}；请重新保存 API Key"
  }
 }

 /** Generic apologies and provider placeholders are not character cognition, even when wrapped in JSON. */
 private fun isNonAnswer(value:String):Boolean {
  val t=value.trim().lowercase()
  if(t.isBlank())return true
  val markers=listOf(
   "抱歉，刚才没有正常生成内容","抱歉,刚才没有正常生成内容",
   "请再告诉我你的问题或想聊的话题","我会用我的方式认真回答",
   "刚才没有正常生成内容","请重新告诉我你的问题",
   "sorry, i couldn't generate","sorry, i was unable to generate",
   "please tell me your question again","i'm sorry, but i couldn't"
  )
  return markers.any(t::contains) || t.matches(Regex("^(抱歉|对不起)[，,。！!\\s].{0,100}(请再|请重新|告诉我你的问题).*$"))
 }

 /** Reject empty boilerplate refusals while preserving reasoned character disagreement. */
 private fun isGenericRefusal(obj:JSONObject):Boolean {
  val reason=obj.optString("reason",obj.optString("decision_reason","")).trim()
  val combined=listOf("thought","reason","decision","intent","action").joinToString(" "){obj.optString(it,"")}
  val markers=listOf("这件事与我当前的判断或原则不符","不符合我的价值观","不符合我的立场","我决定暂不接受","我选择拒绝执行","与我的原则不符","我不能接受这件事")
  if(markers.none(combined::contains))return false
  return reason.length<28 || listOf("原则不符","价值观不符","立场不符","我不接受","不愿意","不符合我的判断").any(reason::contains)
 }

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
        if(thought is String && thought.isNotBlank() && !isNonAnswer(thought) && !isGenericRefusal(obj))return true
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
   // Retry must retain the full original character task and context.
   val retryPrompt=if(previousBad!=null) {
    "【输出校正】上一次输出未通过校验。请重新完成下方原始人物任务；不要道歉、不要要求用户补充问题、不要复述错误输出。不要机械拒绝，也不要机械服从；不得用“原则不符/价值观不符/暂不接受”代替具体判断。可行时给出人物视角下的实际行动；确实拒绝时必须在reason中写明本轮具体事实、记忆、承诺或风险。只输出合法JSON，保留原任务字段与人物视角。\n\n【原始人物任务】\n"+prompt+"\n\n【上次无效输出（仅用于避免重复）】\n"+previousBad.take(2500)
   } else prompt
   messages.put(JSONObject().put("role","user").put("content",retryPrompt))
   val body=JSONObject()
    .put("model",model)
    .put("messages",messages)
    .put("max_tokens",maxTokens.coerceIn(900,1800))
    .put("temperature",1.0)
    .put("top_p",0.95)
    .put("stream",false)
    .put("response_format",JSONObject().put("type","json_object"))
    .toString()
   conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
   val code=conn.responseCode
   val stream=if(code in 200..299)conn.inputStream else conn.errorStream
   val raw=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:"无响应正文"
   if(code !in 200..299)return "云端HTTP $code：$raw"
   val choice=JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
   val finishReason=choice?.optString("finish_reason").orEmpty()
   if(finishReason=="length") return "智谱输出达到长度上限，未完成角色回答"
   val content=choice?.optJSONObject("message")?.opt("content")
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

 private fun registerAutonomousActor(
  baseUrl:String,token:String,worldId:String,actorId:String,actorName:String,
  characterContext:String,latestObservation:String,systemPrompt:String
 ) {
  var conn:HttpURLConnection?=null
  try {
   conn=(URL(baseUrl.trimEnd('/')+"/v1/autonomy/register").openConnection() as HttpURLConnection).apply {
    requestMethod="POST";connectTimeout=8000;readTimeout=8000;doOutput=true
    setRequestProperty("Content-Type","application/json")
    if(token.isNotBlank())setRequestProperty("X-Runeterra-Token",token)
   }
   val body=JSONObject().put("world_id",worldId).put("actor_id",actorId).put("actor_name",actorName)
    .put("character_context",characterContext.take(16000))
    .put("latest_observation",latestObservation.take(20000))
    .put("system_prompt",systemPrompt.take(12000))
    .put("interval_seconds",300).put("enabled",true).toString()
   conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
   val code=conn.responseCode
   if(code !in 200..299) {
    val raw=(conn.errorStream?:conn.inputStream).bufferedReader(Charsets.UTF_8).use{it.readText()}
    throw IllegalStateException("自主角色注册 HTTP $code：$raw")
   }
  } finally {conn?.disconnect()}
 }

 private fun requestCognitiveBackend(
  baseUrl:String,token:String,worldId:String,actorId:String,actorName:String,day:Long,
  characterContext:String,prompt:String,systemPrompt:String
 ):String {
  try {
   registerAutonomousActor(baseUrl,token,worldId,actorId,actorName,characterContext,prompt,systemPrompt)
  } catch(_:Throwable) {
   // Registration is best-effort; ordinary cognition must remain available if the scheduler API is down.
  }
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

 fun recordVerifiedOutcome(context:Context,actorId:String,day:Long,action:String,outcome:String) {
  if(actorId.isBlank()||outcome.isBlank())return
  runCatching { NativeAgentRuntime.recordWorldEvent(context.applicationContext,actorId,day,action,outcome) }
   .onFailure { android.util.Log.w("RuneterraAI","原生世界经历写入失败",it) }
 }

 fun ask(
  context:Context,prompt:String,maxTokens:Int,systemPrompt:String,
  actorId:String?=null,actorName:String?=null,worldDay:Long=0L,
  callback:(String)->Unit
 ) {
  val app=context.applicationContext
  val p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val effectivePrompt = if (!actorId.isNullOrBlank()) NativeAgentRuntime.enrich(app, actorId, worldDay, prompt) else prompt
  fun deliver(raw: String) {
   if (!actorId.isNullOrBlank() && hasCharacterJson(raw)) {
    runCatching { NativeAgentRuntime.record(app, actorId, worldDay, raw) }
     .onFailure { android.util.Log.w("RuneterraAI", "原生智能体记忆写入失败", it) }
   }
   callback(raw)
  }
  if(p.getString("world_ai_provider","zhipu")=="chatgpt") {
   val chatPrompt="【系统角色与输出规范】\n"+systemPrompt+
    "\n\n【本轮人物任务】\n"+effectivePrompt+
    "\n\n【硬性要求】只输出一个完整、合法的JSON对象；不得输出Markdown代码围栏、解释、道歉或要求补充问题。必须保留任务要求的字段，thought必须是自然、具体的第一人称人物思考。"
   ChatGPTWorldBridge.ask(chatPrompt) { first ->
    if(hasCharacterJson(first)) {
     deliver(first)
    } else {
     val retry="【输出校正】上一次ChatGPT网页回答未通过人物JSON校验。请重新完成完整任务，不要道歉、不要要求补充问题、不要机械拒绝或机械服从。只输出合法JSON对象。\n\n【系统要求】\n"+systemPrompt+
      "\n\n【原始人物任务】\n"+effectivePrompt+
      "\n\n【上次无效输出，仅供避免重复】\n"+first.take(2200)
     ChatGPTWorldBridge.ask(retry) { second ->
      if(hasCharacterJson(second)) deliver(second)
      else callback("【ChatGPT网页决策失败】连续两次未生成有效人物JSON。首次输出："+first.take(600)+"；重试输出："+second.take(900))
     }
    }
   }
   return
  }
  val (apiKey,keyDiagnostic)=loadApiKey(app)
  val model=p.getString("model","glm-5.3-flashx")?.trim().orEmpty().ifBlank{"glm-5.3-flashx"}
  thread(name="SharedCloudAI") {
   if(apiKey.isBlank()) {
    callback("【云端决策失败】未生成角色决策。$keyDiagnostic")
    return@thread
   }
   var output=""
   var cloudFailed=false
   try {
    output=request(apiKey,model,systemPrompt,effectivePrompt,maxTokens)
    if(!output.startsWith("云端HTTP") && !output.startsWith("云端请求异常") && !hasCharacterJson(output)) {
     val repaired=runCatching{request(apiKey,model,systemPrompt,effectivePrompt,maxTokens,output)}.getOrElse{"格式纠正请求失败：${it.message?:it.javaClass.simpleName}"}
     if(hasCharacterJson(repaired))output=repaired
     else {
      output="智谱连续两次未生成有效人物内容。首次输出：${output.take(500)}；重试输出：${repaired.take(500)}"
      cloudFailed=true
     }
    }
    if(output.startsWith("云端HTTP")||output.startsWith("云端请求异常")||output.startsWith("智谱输出达到长度上限"))cloudFailed=true
   } catch(e:Throwable) {
    cloudFailed=true
    output="云端请求异常："+(e.message?:e.javaClass.simpleName)
   }
   if(cloudFailed) callback("【云端决策失败】本轮没有生成角色决策。"+listOf(keyDiagnostic,output.take(900)).filter{it.isNotBlank()}.joinToString("；"))
   else deliver(output)
  } }
}
