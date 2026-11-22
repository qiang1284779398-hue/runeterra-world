package com.runeterra.local
import android.app.ActivityManager
import android.content.*
import android.os.*
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** IPC client: native inference runs in :ai; rebinds after Android reports a dead binding. */
object ResidentAI {
 private const val LOAD=1; private const val UNLOAD=2; private const val ASK=3; private const val STRUCT=4; private const val RESULT=100
 private val main=Handler(Looper.getMainLooper()); private val ids=AtomicLong(1)
 private val pending=ConcurrentHashMap<Long,(String?)->Unit>()
 @Volatile private var app:Context?=null
 @Volatile private var remote:Messenger?=null
 @Volatile private var bound=false
 @Volatile private var binding=false
 @Volatile private var loading=false
 @Volatile private var loaded=false
 @Volatile private var path:String?=null
 private val incoming=Messenger(Handler(Looper.getMainLooper()){msg->
  if(msg.what==RESULT){val b=msg.data;val id=b.getLong("id");val cb=pending.remove(id);if(b.getBoolean("loadFinished")){loading=false;loaded=b.getBoolean("ok")};if(b.getBoolean("unloaded"))loaded=false;cb?.invoke(if(b.getBoolean("ok"))null else b.getString("result")?:"AI进程错误")}
  true
 })
 private fun failPending(reason:String){val callbacks=pending.values.toList();pending.clear();callbacks.forEach{try{it(reason)}catch(_:Throwable){}}}
 private val connection=object:ServiceConnection{
  override fun onServiceConnected(n:ComponentName?,b:IBinder?){remote=if(b!=null)Messenger(b) else null;binding=false;bound=true;record(if(remote!=null)"AI独立进程已连接" else "AI服务返回空Binder")}
  override fun onServiceDisconnected(n:ComponentName?){remote=null;loaded=false;loading=false;record("AI独立进程意外退出；Android正在尝试重连");failPending("AI进程意外退出；请重新加载模型")}
  override fun onBindingDied(n:ComponentName?){remote=null;loaded=false;loading=false;binding=false;record("AI进程绑定死亡；解除旧绑定并安排重新绑定");failPending("AI进程绑定已死亡；正在恢复连接，请稍后重新加载");main.post{try{if(bound){app?.unbindService(this);bound=false}}catch(e:Throwable){record("解除死亡绑定失败："+e.message)};main.postDelayed({app?.let{connect(it)}},1200)}}
  override fun onNullBinding(n:ComponentName?){remote=null;binding=false;record("AI进程空绑定；检查Service声明");try{if(bound){app?.unbindService(this);bound=false}}catch(_:Throwable){}}
 }
 private fun record(s:String){val c=app?:return;val line=SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(Date())+" | "+s;Log.i("RuneterraAI",line);try{File(c.filesDir,"ai_diagnostics.log").appendText(line+"\n")}catch(_:Throwable){};c.getSharedPreferences("runeterra_ai_diagnostics",Context.MODE_PRIVATE).edit().putString("last_stage",line).putString("last_model",path?:"").commit()}
 @Synchronized private fun connect(c:Context){app=c.applicationContext;if(remote!=null||binding||bound)return;try{binding=true;val ok=app!!.bindService(Intent(app,AIInferenceService::class.java),connection,Context.BIND_AUTO_CREATE);bound=ok;if(!ok){binding=false;record("bindService返回false")}}catch(e:Throwable){binding=false;record("绑定AI进程失败："+e.message)}}
 private fun send(what:Int,b:Bundle,cb:(String?)->Unit){val id=ids.getAndIncrement();b.putLong("id",id);pending[id]=cb;val r=remote;if(r==null){pending.remove(id);cb("AI独立进程未连接，请重新连接后重试");return};try{val m=Message.obtain(null,what);m.data=b;m.replyTo=incoming;r.send(m);main.postDelayed({pending.remove(id)?.invoke("AI请求超时或AI进程崩溃；主模拟器进程仍然存活")},300000)}catch(e:Throwable){pending.remove(id);cb("IPC发送失败："+e.message)}}
 fun load(context:Context,p:String,callback:(String?)->Unit){path=p;connect(context);if(loading){callback("模型正在加载");return};loading=true;loaded=false;record("请求AI独立进程加载模型；存在="+File(p).exists()+"；大小="+File(p).length());main.postDelayed({if(remote==null){loading=false;callback("AI进程尚未连接；请稍后再次点击加载") }else send(LOAD,Bundle().apply{putString("path",p)}){e->loading=false;loaded=e==null;callback(e)}},900)}
 fun unload(){loaded=false;try{remote?.send(Message.obtain(null,UNLOAD))}catch(_:Throwable){};record("请求卸载AI模型")}
 fun isLoaded()=loaded
 fun isLoading()=loading
 fun modelPath()=path
 fun ask(prompt:String,callback:(String)->Unit){if(!loaded){callback("请先加载本地GGUF模型");return};record("向AI进程发送普通推理；promptChars="+prompt.length);send(ASK,Bundle().apply{putString("prompt",prompt)}){e->callback(e?:"AI未返回结果")}}
 fun askStructured(prompt:String,maxTokens:Int=192,systemPrompt:String="你是符文之地模拟世界的天道指令解析器。只输出符合要求的JSON，不要输出解释。",callback:(String)->Unit){if(!loaded){callback("请先加载本地GGUF模型");return};record("向AI进程发送结构化推理；promptChars="+prompt.length);send(STRUCT,Bundle().apply{putString("prompt",prompt);putString("system",systemPrompt);putInt("maxTokens",maxTokens)}){e->callback(e?:"AI未返回结果")}}
 fun diagnosticReport(context:Context):String{val c=context.applicationContext;val p=c.getSharedPreferences("runeterra_ai_diagnostics",Context.MODE_PRIVATE);val am=c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;val mi=ActivityManager.MemoryInfo().also{am.getMemoryInfo(it)};val f=File(c.filesDir,"ai_diagnostics.log");val tail=if(f.exists())f.readLines().takeLast(50).joinToString("\n") else "暂无日志";val model=p.getString("last_model",path?:"")?:"";return "AI独立进程："+(if(remote!=null)"已连接" else "未连接")+"\n绑定中："+binding+"；已绑定："+bound+"\n模型已加载："+loaded+"\n最近阶段："+p.getString("last_stage","无记录")+"\n模型路径："+model+"\n模型大小："+(if(model.isNotBlank()&&File(model).exists())(File(model).length()/1024/1024).toString()+" MB" else "未知")+"\n可用内存："+(mi.availMem/1024/1024)+" MB\n低内存："+mi.lowMemory+"\n\n最近日志：\n"+tail}
 fun systemInfo()="llama.cpp CPU/NEON，独立 :ai 进程"
}
