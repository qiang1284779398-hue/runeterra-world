package com.runeterra.local
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AIInferenceService:Service(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
 private val mutex=Mutex()
 @Volatile private var model:LlamaModel?=null
 @Volatile private var modelPath=""
 private val messenger by lazy{Messenger(Handler(Looper.getMainLooper()){msg->handle(msg);true})}
 override fun onCreate(){super.onCreate();Log.i("RuneterraAI","AI_SERVICE_ON_CREATE pid="+android.os.Process.myPid())}
 override fun onBind(intent:Intent?):IBinder{Log.i("RuneterraAI","AI_SERVICE_ON_BIND");return messenger.binder}
 override fun onUnbind(intent:Intent?):Boolean{Log.i("RuneterraAI","AI_SERVICE_ON_UNBIND");return super.onUnbind(intent)}
 override fun onDestroy(){Log.e("RuneterraAI","AI_SERVICE_ON_DESTROY");scope.cancel();super.onDestroy()}
 private fun reply(to:Messenger?,id:Long,ok:Boolean,text:String="",load:Boolean=false){try{val b=Bundle().apply{putLong("id",id);putBoolean("ok",ok);putString("result",text);if(load)putBoolean("loadFinished",true)};to?.send(Message.obtain(null,100).apply{data=b})}catch(e:Throwable){Log.e("RuneterraAI","reply failed",e)}}
 private fun small(p:String)=File(p).length()<=2_000L*1024L*1024L
 private fun handle(msg:Message){val id=msg.data.getLong("id");val to=msg.replyTo
  when(msg.what){
   1->{val p=msg.data.getString("path")?:"";scope.launch{mutex.withLock{try{require(File(p).isFile){"模型文件不存在"};model?.let{Llama.releaseModel(it)};model=null;val s=small(p);Log.i("RuneterraAI","加载模型；bytes="+File(p).length()+" context="+(if(s)768 else 1536));val loaded=Llama.loadModel(modelPath=p,config=LlamaConfig(contextSize=if(s)768 else 1536,threads=if(s)2 else 3,gpuLayers=0,temperature=0.55f,topP=0.85f,topK=30));model=loaded;modelPath=p;reply(to,id,true,load=true)}catch(e:Throwable){Log.e("RuneterraAI","模型加载异常",e);reply(to,id,false,e.message?:e.toString(),true)}}}}
   2->scope.launch{mutex.withLock{model?.let{Llama.releaseModel(it)};model=null;modelPath=""}}
   3,4->{val prompt=msg.data.getString("prompt")?:"";val structured=msg.what==4;val system=if(structured)(msg.data.getString("system")?:"")+"\n/no_think\n直接输出最终JSON，不要解释。" else "你是符文之地居民的个人认知引擎。依据个人经历、知识和环境思考。/no_think";val max=msg.data.getInt("maxTokens",160)
    scope.launch{mutex.withLock{val active=model;if(active==null||!active.isLoaded){reply(to,id,false,"模型尚未加载");return@withLock};try{Log.i("RuneterraAI","开始原生推理 structured="+structured+" promptChars="+prompt.length);val cap=if(small(modelPath))2600 else 7000;val p=if(prompt.length>cap)prompt.take(cap) else prompt;val result=Llama.complete(model=active,prompt=if(structured&&!p.contains("/no_think"))p+"\n/no_think" else p,systemPrompt=system,maxTokens=if(small(modelPath))max.coerceAtMost(96) else max.coerceAtMost(160));Log.i("RuneterraAI","原生推理完成 chars="+result.text.length);reply(to,id,true,result.text.trim())}catch(e:Throwable){Log.e("RuneterraAI","推理异常",e);reply(to,id,false,"推理异常："+(e.message?:e.toString()))}}}
   }
  }
 }
}
