package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

private object CognitiveStore {
    fun prefs(c: Context) = c.applicationContext.getSharedPreferences("runeterra_cognitive_runtime", Context.MODE_PRIVATE)
    fun key(actor: String) = "agent_" + actor.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    fun read(p: android.content.SharedPreferences, k: String) = runCatching { JSONObject(p.getString(k, "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun values(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a?.optString(it)?.trim()?.takeIf { v -> v.isNotBlank() } }
    fun add(a: JSONArray, v: String, max: Int = 24) { val x=v.trim().take(300); if(x.isBlank()||(0 until a.length()).any{a.optString(it).equals(x,true)})return; a.put(x); while(a.length()>max)a.remove(0) }
    fun str(o: JSONObject?, f: String, max: Int = 300) = o?.optString(f).orEmpty().trim().take(max)
}

object SelfModelEngine {
    private fun load(c:Context,a:String)=CognitiveStore.read(CognitiveStore.prefs(c),"${CognitiveStore.key(a)}_self")
    fun prepare(c:Context,a:String):String { val s=load(c,a); return """【自我模型】
自我理解：${s.optString("concept","尚未形成明确自我概念")}
自我评价：${s.optString("assessment","尚未形成稳定自评")}
自认优势：${CognitiveStore.values(s.optJSONArray("strengths")).takeLast(5).joinToString("、").ifBlank{"尚未明确"}}
自认局限：${CognitiveStore.values(s.optJSONArray("limits")).takeLast(5).joinToString("、").ifBlank{"尚未明确"}}
自我事实：${CognitiveStore.values(s.optJSONArray("facts")).takeLast(6).joinToString("；").ifBlank{"暂无"}}
自我认知置信度：${s.optInt("confidence",50)}/100。允许存在盲点，不得把未知能力当作事实。""" }
    fun capture(c:Context,a:String,day:Long,o:JSONObject?) { if(o==null)return; val p=CognitiveStore.prefs(c);val k=CognitiveStore.key(a);val s=load(c,a)
        CognitiveStore.str(o,"self_view").takeIf{it.isNotBlank()}?.let{s.put("concept",it)}
        CognitiveStore.str(o,"self_assessment").takeIf{it.isNotBlank()}?.let{s.put("assessment",it)}
        val st=s.optJSONArray("strengths")?:JSONArray();val li=s.optJSONArray("limits")?:JSONArray();val fa=s.optJSONArray("facts")?:JSONArray()
        CognitiveStore.values(o.optJSONArray("strengths")).forEach{CognitiveStore.add(st,it)}
        CognitiveStore.values(o.optJSONArray("limitations")).forEach{CognitiveStore.add(li,it)}
        CognitiveStore.values(o.optJSONArray("self_facts")).forEach{CognitiveStore.add(fa,it,40)}
        if(o.has("self_confidence"))s.put("confidence",o.optInt("self_confidence",50).coerceIn(0,100))
        s.put("strengths",st).put("limits",li).put("facts",fa).put("updatedDay",day);p.edit().putString("${k}_self",s.toString()).apply()
    }
    fun report(c:Context,a:String)=prepare(c,a)
}

object MotivationEngine {
    private val names=listOf("survival","autonomy","belonging","mastery","justice","curiosity")
    private fun load(c:Context,a:String)=CognitiveStore.read(CognitiveStore.prefs(c),"${CognitiveStore.key(a)}_motives")
    fun prepare(c:Context,a:String):String { val s=load(c,a);val d=s.optJSONObject("drives")?:JSONObject();val g=s.optJSONArray("goals")?:JSONArray()
        val drives=names.joinToString("；"){"$it=${d.optInt(it,50)}/100"}
        val goals=(0 until g.length()).mapNotNull{g.optJSONObject(it)}.filter{it.optString("status","active")=="active"}.sortedByDescending{it.optInt("priority",50)}.take(5).joinToString("\n"){ item -> val gn=item.optString("goal"); val pr=item.optInt("priority",50); val rs=item.optString("reason"); "- $gn（优先级$pr；缘由：$rs）" }.ifBlank{"尚未建立长期自主目标"}
        return """【内在动机】
驱动力：$drives
自主目标：
$goals
目标属于角色本人，可以主动产生、调整、搁置或放弃。""" }
    fun capture(c:Context,a:String,day:Long,o:JSONObject?) { if(o==null)return;val p=CognitiveStore.prefs(c);val k=CognitiveStore.key(a);val s=load(c,a);val d=s.optJSONObject("drives")?:JSONObject()
        names.forEach{if(!d.has(it))d.put(it,50)};val explicit=o.optJSONObject("motives")
        if(explicit!=null)names.forEach{n->if(explicit.has(n))d.put(n,explicit.optInt(n,50).coerceIn(0,100))}
        val goal=CognitiveStore.str(o,"new_goal").ifBlank{CognitiveStore.str(o,"goal")};val reason=CognitiveStore.str(o,"goal_reason").ifBlank{CognitiveStore.str(o,"reason")}
        val intentValue=CognitiveStore.str(o,"intent")
        if(explicit==null){val ev="$goal $reason $intentValue";val cues=mapOf("survival" to listOf("生存","饥饿","安全","危险"),"autonomy" to listOf("自由","独立","自主"),"belonging" to listOf("家人","朋友","帮助","关系"),"mastery" to listOf("学习","训练","技艺","变强"),"justice" to listOf("正义","不公","保护","守护"),"curiosity" to listOf("调查","探索","研究","真相"));cues.forEach{(n,ws)->if(ws.any(ev::contains))d.put(n,(d.optInt(n,50)+2).coerceAtMost(100))}}
        val gs=s.optJSONArray("goals")?:JSONArray();if(goal.isNotBlank()){val current=(0 until gs.length()).mapNotNull{gs.optJSONObject(it)}.firstOrNull{it.optString("status","active")=="active"};if(current==null||current.optString("goal")!=goal){current?.put("status","superseded")?.put("closedDay",day);gs.put(JSONObject().put("goal",goal).put("reason",reason).put("priority",o.optInt("goal_priority",50).coerceIn(0,100)).put("status","active").put("createdDay",day))}else if(reason.isNotBlank())current.put("reason",reason)}
        while(gs.length()>24)gs.remove(0);s.put("drives",d).put("goals",gs).put("updatedDay",day);p.edit().putString("${k}_motives",s.toString()).apply()
    }
    fun report(c:Context,a:String)=prepare(c,a)
}

object IdentityEvolutionEngine {
    private fun load(c:Context,a:String)=CognitiveStore.read(CognitiveStore.prefs(c),"${CognitiveStore.key(a)}_identity")
    fun prepare(c:Context,a:String):String {val s=load(c,a);val h=s.optJSONArray("history")?:JSONArray();val recent=(maxOf(0,h.length()-4) until h.length()).mapNotNull{h.optJSONObject(it)?.optString("change")}.joinToString("；").ifBlank{"尚无身份转折"}
        return """【身份演化】
身份版本：${s.optInt("version",0)}
核心价值：${CognitiveStore.values(s.optJSONArray("values")).takeLast(8).joinToString("、").ifBlank{"尚未明确"}}
近期转变：$recent
重大人格变化必须有具体经历、理由和本人认同；一次情绪波动不等于永久改变。"""}
    fun capture(c:Context,a:String,day:Long,o:JSONObject?){if(o==null)return;val p=CognitiveStore.prefs(c);val k=CognitiveStore.key(a);val s=load(c,a);val values=s.optJSONArray("values")?:JSONArray();CognitiveStore.values(o.optJSONArray("values")).forEach{CognitiveStore.add(values,it,20)}
        val h=s.optJSONArray("history")?:JSONArray();val change=CognitiveStore.str(o,"identity_change");val reason=CognitiveStore.str(o,"identity_change_reason",500)
        if(change.isNotBlank()&&reason.isNotBlank()&&h.optJSONObject(h.length()-1)?.optString("change")!=change){h.put(JSONObject().put("day",day).put("change",change).put("reason",reason));s.put("version",s.optInt("version",0)+1)}
        val belief=CognitiveStore.str(o,"belief");val committed=s.optString("committedBelief")
        if(belief.isNotBlank()&&committed.isBlank()){s.put("committedBelief",belief).put("pendingCount",0)}
        else if(belief.isNotBlank()&&belief!=committed){val candidate=s.optString("pendingBelief");val count=if(candidate==belief)s.optInt("pendingCount",0)+1 else 1;s.put("pendingBelief",belief).put("pendingCount",count);if(count>=2){h.put(JSONObject().put("day",day).put("change","信念稳定调整").put("reason","此前：$committed；现在：$belief。连续多轮表达一致。"));s.put("version",s.optInt("version",0)+1).put("committedBelief",belief).put("pendingCount",0)}}
        else if(belief.isNotBlank())s.put("pendingCount",0)
        while(h.length()>40)h.remove(0);s.put("values",values).put("history",h).put("updatedDay",day);p.edit().putString("${k}_identity",s.toString()).apply()}
    fun report(c:Context,a:String)=prepare(c,a)
}

object CognitiveArchitecture {
    private const val MAX_EPISODES=100
    private const val MAX_RAW=16000
    data class Evaluation(val duplicateThought:Boolean,val missingThought:Boolean,val continuityScore:Int,val note:String)
    fun prepare(context:Context,actorKey:String,day:Long,originalPrompt:String):String {val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val episodes=readEpisodes(p,k);val reflection=p.getString("${k}_reflection","").orEmpty()
        val recent=(maxOf(0,episodes.length()-8) until episodes.length()).mapNotNull{episodes.optJSONObject(it)}.joinToString("\n"){e->val ed=e.optLong("day");val th=e.optString("thought").take(150);val go=e.optString("goal").take(90);val ac=e.optString("action");val ou=e.optString("outcome").take(120);"第${ed}日：想法=$th；目标=$go；行动=$ac；结果=$ou"}.ifBlank{"尚无已完成的认知轮次。"}
        val add="""【持续认知｜第${day}日】
长期反思：${reflection.ifBlank{"经历尚少，暂不形成长期结论。"}}
近期经历：
$recent
${SelfModelEngine.prepare(context,actorKey)}
${MotivationEngine.prepare(context,actorKey)}
${IdentityEvolutionEngine.prepare(context,actorKey)}
区分亲历、转述、推测与未知；记住行动后果；允许主动产生目标、拒绝、等待或改变方向；不要每轮强行改变人格。每轮JSON除原有字段外必须包含：self_view（自我理解，简短字符串）、self_assessment（自我评价，简短字符串）、strengths（数组，最多3项）、limitations（数组，最多3项）、self_facts（有事实依据的数组，最多3项）、self_confidence（0至100整数）、motives（对象，survival/autonomy/belonging/mastery/justice/curiosity六项均为0至100整数）、new_goal（当前自主目标）、goal_reason（目标缘由）、goal_priority（0至100整数）、values（核心价值数组，最多5项）；identity_change和identity_change_reason仅在有真实经历支持重大变化时填写，否则留空。不输出隐藏思维链。"""
        return originalPrompt+"\n\n"+add+"\n\n"+PersonalLearningNetwork.prepare(context,actorKey,originalPrompt)+"\n\n"+AutonomousCognitionEngine.prepare(context,actorKey)}
    fun capture(context:Context,actorKey:String,day:Long,raw:String):Evaluation {val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k);val obj=extractObject(raw);val thought=obj?.optString("thought").orEmpty().trim();val norm=normalize(thought)
        val prev=(maxOf(0,a.length()-6) until a.length()).mapNotNull{a.optJSONObject(it)}.map{normalize(it.optString("thought"))}.filter{it.isNotBlank()};val duplicate=norm.isNotBlank()&&prev.any{similarity(norm,it)>=0.88};val missing=thought.isBlank();val score=when{missing->0;duplicate->35;a.length()==0->70;else->90}
        SelfModelEngine.capture(context,actorKey,day,obj);MotivationEngine.capture(context,actorKey,day,obj);IdentityEvolutionEngine.capture(context,actorKey,day,obj)
        a.put(JSONObject().put("day",day).put("thought",thought.take(600)).put("belief",obj?.optString("belief").orEmpty().take(300)).put("goal",obj?.optString("goal").orEmpty().take(300)).put("intent",obj?.optString("intent").orEmpty().take(300)).put("action",obj?.optString("action").orEmpty().take(100)).put("reason",obj?.optString("reason").orEmpty().take(300)).put("outcome","").put("raw",raw.take(MAX_RAW)).put("duplicateThought",duplicate).put("continuityScore",score))
        while(a.length()>MAX_EPISODES)a.remove(0);val ed=p.edit().putString("${k}_episodes",a.toString()).putString("${k}_last_evaluation",JSONObject().put("day",day).put("duplicateThought",duplicate).put("missingThought",missing).put("continuityScore",score).toString()).putString("${k}_last_raw",raw.take(MAX_RAW))
        if(a.length()%5==0)ed.putString("${k}_reflection",buildReflection(a));ed.apply()
        return Evaluation(duplicate,missing,score,when{missing->"缺少人物 thought 字段";duplicate->"与近期想法高度重复";else->"自我模型、内在动机与身份状态已更新"})}
    fun recordOutcome(context:Context,actorKey:String,day:Long,action:String,outcome:String){val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k);val last=a.optJSONObject(a.length()-1)?:return;if(last.optLong("day",-1L)!=day)return;last.put("action",action.take(100)).put("outcome",outcome.take(500));p.edit().putString("${k}_episodes",a.toString()).apply();PersonalLearningNetwork.recordOutcome(context,actorKey,day,action,outcome);AutonomousCognitionEngine.recordOutcome(context,actorKey,day,action,outcome);WorldAIProvider.recordVerifiedOutcome(context,actorKey,day,action,outcome)}
    fun report(context:Context,actorKey:String):String{val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k);val e=CognitiveStore.read(p,"${k}_last_evaluation");val score=e.optInt("continuityScore",-1);val dup=e.optBoolean("duplicateThought");val reflection=p.getString("${k}_reflection","尚未生成").orEmpty();return "认知轮次：${a.length()}\n连续性评分：$score/100\n重复想法：$dup\n长期反思：$reflection\n\n"+SelfModelEngine.report(context,actorKey)+"\n\n"+MotivationEngine.report(context,actorKey)+"\n\n"+IdentityEvolutionEngine.report(context,actorKey)+"\n\n"+PersonalLearningNetwork.report(context,actorKey)+"\n\n"+AutonomousCognitionEngine.prepare(context,actorKey)}
    private fun buildReflection(a:JSONArray):String{val r=(maxOf(0,a.length()-12) until a.length()).mapNotNull{a.optJSONObject(it)};val b=r.map{it.optString("belief")}.filter{it.isNotBlank()}.distinct().takeLast(4);val g=r.map{it.optString("goal")}.filter{it.isNotBlank()}.distinct().takeLast(4);val o=r.map{it.optString("outcome")}.filter{it.isNotBlank()}.takeLast(5);return buildString{if(b.isNotEmpty())append("信念轨迹：").append(b.joinToString(" → ")).append("。");if(g.isNotEmpty())append("目标轨迹：").append(g.joinToString(" → ")).append("。");if(o.isNotEmpty())append("行动后果：").append(o.joinToString("；")).append("。");if(isEmpty())append("目前经历不足以形成稳定反思。")}.take(1800)}
    private fun readEpisodes(p:android.content.SharedPreferences,k:String)=runCatching{JSONArray(p.getString("${k}_episodes","[]")?:"[]")}.getOrDefault(JSONArray())
    private fun extractObject(raw:String):JSONObject?{val t=raw.replace(Regex("(?is)<think>.*?</think>"),"").trim();for(s in t.indices)if(t[s]=='{'){var d=0;var q=false;var e=false;for(i in s until t.length){val c=t[i];if(q){if(e)e=false else if(c=='\\')e=true else if(c=='"')q=false}else when(c){'"'->q=true;'{'->d++;'}'->{d--;if(d==0)return runCatching{JSONObject(t.substring(s,i+1))}.getOrNull()}}}};return null}
    private fun normalize(v:String)=Normalizer.normalize(v,Normalizer.Form.NFKC).lowercase().replace(Regex("[\\p{Punct}\\p{IsPunctuation}\\s，。！？；：、“”‘’（）【】]+"),"")
    private fun similarity(a:String,b:String):Double{if(a.isBlank()||b.isBlank())return 0.0;if(a==b)return 1.0;val x=a.toSet();val y=b.toSet();val u=(x+y).size;return if(u==0)0.0 else x.intersect(y).size.toDouble()/u}
}