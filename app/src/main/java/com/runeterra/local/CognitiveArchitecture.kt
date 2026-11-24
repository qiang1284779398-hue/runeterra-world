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
    fun prepare(c:Context,a:String):String { val s=load(c,a)
        val contested=CognitiveStore.values(s.optJSONArray("contested"))
        val strengths=CognitiveStore.values(s.optJSONArray("strengths")).filter { it !in contested }
        return """【自我模型】
自我理解：${s.optString("concept","尚未形成明确自我概念")}
自我评价：${s.optString("assessment","尚未形成稳定自评")}
自认优势：${strengths.takeLast(5).joinToString("、").ifBlank{"尚未明确"}}
自认局限：${CognitiveStore.values(s.optJSONArray("limits")).takeLast(5).joinToString("、").ifBlank{"尚未明确"}}${if(contested.isNotEmpty()) "\n正在动摇的自我认知：${contested.takeLast(3).joinToString("、")}（这些曾经确信的自我判断正被新经历挑战，未必还成立；可以怀疑它们，不必强行维护）" else ""}
自我事实：${CognitiveStore.values(s.optJSONArray("facts")).takeLast(6).joinToString("；").ifBlank{"暂无"}}
自我认知置信度：${s.optInt("confidence",50)}/100。允许存在盲点，不得把未知能力当作事实。""" }
    fun capture(c:Context,a:String,day:Long,o:JSONObject?) { if(o==null)return; val p=CognitiveStore.prefs(c);val k=CognitiveStore.key(a);val s=load(c,a)
        CognitiveStore.str(o,"self_view").takeIf{it.isNotBlank()}?.let{s.put("concept",it)}
        CognitiveStore.str(o,"self_assessment").takeIf{it.isNotBlank()}?.let{s.put("assessment",it)}
        val st=s.optJSONArray("strengths")?:JSONArray();val li=s.optJSONArray("limits")?:JSONArray();val fa=s.optJSONArray("facts")?:JSONArray()
        CognitiveStore.values(o.optJSONArray("strengths")).forEach{CognitiveStore.add(st,it)}
        CognitiveStore.values(o.optJSONArray("limitations")).forEach{CognitiveStore.add(li,it)}
        CognitiveStore.values(o.optJSONArray("self_facts")).forEach{CognitiveStore.add(fa,it,40)}
        // Subtraction with due process: when a newly admitted limitation textually
        // collides with an existing strength, the strength collects a doubt vote.
        // Two independent collisions contest it (it leaves the settled strengths and
        // is shown as wavering); a later re-assertion without collision heals a vote.
        // One stray sentence can no longer permanently rewrite the self-model.
        val votes=s.optJSONObject("contestedVotes")?:JSONObject()
        CognitiveStore.values(s.optJSONArray("contested")).forEach { if(!votes.has(it)) votes.put(it,2) }
        val asserted=CognitiveStore.values(o.optJSONArray("strengths"))
        val limitTexts=CognitiveStore.values(li)
        val collided=mutableSetOf<String>()
        CognitiveStore.values(st).forEach { strength ->
            val grams=(0 until strength.length-1).map { strength.substring(it,it+2) }.toSet()
            if(grams.size>=2 && limitTexts.any { lim -> grams.count { g -> lim.contains(g) }>=2 }) collided.add(strength)
        }
        collided.forEach { votes.put(it, votes.optInt(it,0)+1) }
        asserted.filter { it !in collided }.forEach { if(votes.has(it)) votes.put(it,(votes.optInt(it,0)-1).coerceAtLeast(0)) }
        val ct=JSONArray(); CognitiveStore.values(st).filter { votes.optInt(it,0)>=2 }.forEach { ct.put(it) }
        s.put("contestedVotes",votes)
        if(o.has("self_confidence"))s.put("confidence",o.optInt("self_confidence",50).coerceIn(0,100))
        s.put("strengths",st).put("limits",li).put("facts",fa).put("contested",ct).put("updatedDay",day);p.edit().putString("${k}_self",s.toString()).apply()
    }
    fun report(c:Context,a:String)=prepare(c,a)
}

object MotivationEngine {
    private val names=listOf("survival","autonomy","belonging","mastery","justice","curiosity")
    private fun load(c:Context,a:String)=CognitiveStore.read(CognitiveStore.prefs(c),"${CognitiveStore.key(a)}_motives")

    fun prepare(c:Context,a:String):String {
        val s=load(c,a)
        val d=s.optJSONObject("drives")?:JSONObject()
        val g=s.optJSONArray("goals")?:JSONArray()
        val drives=names.joinToString("；") { "$it=${d.optInt(it,50)}/100" }
        val goals=(0 until g.length()).mapNotNull { g.optJSONObject(it) }
            .filter { it.optString("status","active")=="active" }
            .sortedByDescending { it.optInt("priority",50) }
            .take(5)
            .joinToString("\n") { item ->
                val gn=item.optString("goal")
                val pr=item.optInt("priority",50)
                val rs=item.optString("reason")
                val progress=item.optInt("progress",0)
                val next=item.optString("next_step","")
                "- $gn（优先级$pr；进展$progress/100；缘由：$rs；下一步：${next.ifBlank{"待判断"}}）"
            }.ifBlank { "尚未建立长期自主目标" }
        return """【长期目标与内在动机】
驱动力：$drives
持续目标：
$goals
必须维护目标连续性：短期意图、一次行动、临时需求不得自动取代长期目标。只有出现有事实依据的方向变化时才新增目标；可以同时维护多个目标，按优先级安排，不要每轮换目标。确实完成、搁置或放弃时，明确更新目标状态。""" + innerState(s,d)
    }

    private fun innerState(s:JSONObject,d:JSONObject):String {
        val boredom=s.optInt("boredom",0)
        val risk=s.optInt("riskTolerance",50)
        val cur=d.optInt("curiosity",50); val sur=d.optInt("survival",50)
        val bel=d.optInt("belonging",50); val aut=d.optInt("autonomy",50)
        val mas=d.optInt("mastery",50); val jus=d.optInt("justice",50)
        val conflicts=buildList {
            if(cur>=60 && sur<=45) add("好奇心想去看未知，生存本能却在把你拉回安全区，两股力正在拉扯")
            if(bel>=65 && aut>=65) add("归属感与自主欲同时很强：既想靠近人群，又不想被束缚")
            if(mas>=65 && sur<=40) add("精进的执念正在压过对风险的顾虑")
            if(jus>=65 && bel>=65) add("正义感与对亲近之人的维护可能发生冲突")
        }
        return buildString {
            append("\n内在状态：无聊度$boredom/100；风险偏好$risk/100。")
            if(conflicts.isNotEmpty()) append("当前内心冲突：").append(conflicts.joinToString("；")).append("。冲突不需要立刻解决，它会真实影响你的选择。")
            if(boredom>=55) append("你已经在同样的日子里重复太久，无聊正在累积：今天可以认真考虑做一件不一样的事——探索未知、冒一次可承受的风险，或把一直回避的冲突挑明。")
        }
    }

    fun boredomLevel(c:Context,a:String):Int = load(c,a).optInt("boredom",0)
    fun riskTolerance(c:Context,a:String):Int = load(c,a).optInt("riskTolerance",50)
    fun lastAction(c:Context,a:String):String = load(c,a).optString("lastAction","")

    /** Legacy saves have no boredom field: seed it from the actor's current same-action streak so a long routine does not restart from zero after the upgrade. No-op once boredom exists. */
    fun seedBoredom(c:Context,a:String,streak:Int) {
        val p=CognitiveStore.prefs(c); val k=CognitiveStore.key(a); val s=load(c,a)
        if(s.has("boredom")) return
        s.put("boredom", minOf(streak*6,80))
        p.edit().putString("${k}_motives",s.toString()).apply()
    }

    /** Risk appetite is sculpted by lived outcomes of risky actions: wins embolden, losses teach caution, asymmetrically. */
    fun onOutcome(c:Context,a:String,day:Long,action:String,outcome:String) {
        val act=action.trim().lowercase()
        if(act !in setOf("take_risk","explore_unknown","provoke")) return
        val p=CognitiveStore.prefs(c); val k=CognitiveStore.key(a); val s=load(c,a)
        var rt=s.optInt("riskTolerance",50)
        val negative=listOf("失败","受伤","损失","被拒绝","无法","死亡","被捕","代价")
        val positive=listOf("成功","获得","发现","改善","赢得","回报","建立")
        // Decomposed scoring: gains and costs are counted separately, so "succeeded
        // but got hurt" nets out slightly negative instead of reading as pure success.
        val delta=(if(positive.any(outcome::contains)) 4 else 0) + (if(negative.any(outcome::contains)) -6 else 0)
        rt=(rt+delta).coerceIn(5,95)
        s.put("riskTolerance",rt)
        p.edit().putString("${k}_motives",s.toString()).apply()
    }

    /** Goals that emerge from the actor's own accumulated memory (e.g. an unresolved knot found by reflection), not from a single turn's whim. They carry a concrete next step so the goal is actionable, not decorative. */
    fun addEmergentGoal(c:Context,a:String,day:Long,goal:String,reason:String,priority:Int,nextStep:String="") {
        val s=load(c,a)
        val gs=s.optJSONArray("goals")?:JSONArray()
        fun grams(t:String):Set<String>{val x=t.filter{!it.isWhitespace()};return if(x.length<2) setOf(x) else (0 until x.length-1).map { x.substring(it,it+2) }.toSet()}
        val gg=grams(goal)
        val already=(0 until gs.length()).mapNotNull { gs.optJSONObject(it) }
            .any { it.optString("status","active")=="active" && run { val og=grams(it.optString("goal")); val u=(gg+og).size; u>0 && gg.intersect(og).size.toDouble()/u>=0.5 } }
        if(already) return
        val o=JSONObject().put("new_goal",goal).put("goal_reason",reason).put("goal_priority",priority).put("next_step",nextStep)
        capture(c,a,day,o)
    }

    fun capture(c:Context,a:String,day:Long,o:JSONObject?) {
        if(o==null)return
        val p=CognitiveStore.prefs(c)
        val k=CognitiveStore.key(a)
        val s=load(c,a)
        val d=s.optJSONObject("drives")?:JSONObject()
        names.forEach { if(!d.has(it))d.put(it,50) }
        val explicit=o.optJSONObject("motives")
        if(explicit!=null) names.forEach { n -> if(explicit.has(n))d.put(n,explicit.optInt(n,50).coerceIn(0,100)) }

        val gs=s.optJSONArray("goals")?:JSONArray()
        val active=(0 until gs.length()).mapNotNull { gs.optJSONObject(it) }
            .filter { it.optString("status","active")=="active" }
        val reportedGoal=CognitiveStore.str(o,"goal")
        val requestedStatus=CognitiveStore.str(o,"goal_status").lowercase()
        if(requestedStatus in setOf("completed","paused","abandoned")) {
            val target=active.firstOrNull { reportedGoal.isNotBlank() && it.optString("goal")==reportedGoal }
                ?: active.firstOrNull()
            target?.put("status",requestedStatus)?.put("closedDay",day)
        }

        val proposal=CognitiveStore.str(o,"new_goal").ifBlank { CognitiveStore.str(o,"long_term_goal") }
            .ifBlank { if(active.isEmpty()) reportedGoal else "" }
        val reason=CognitiveStore.str(o,"goal_reason").ifBlank { CognitiveStore.str(o,"reason") }
        val nextStep=CognitiveStore.str(o,"next_step",240)
        val progress=if(o.has("goal_progress"))o.optInt("goal_progress",0).coerceIn(0,100) else -1
        if(proposal.isNotBlank()) {
            val existing=(0 until gs.length()).mapNotNull { gs.optJSONObject(it) }
                .firstOrNull { it.optString("status","active")=="active" && it.optString("goal").equals(proposal,true) }
            if(existing!=null) {
                if(reason.isNotBlank())existing.put("reason",reason)
                if(nextStep.isNotBlank())existing.put("next_step",nextStep)
                if(progress>=0)existing.put("progress",progress)
                existing.put("lastUpdatedDay",day)
            } else {
                val priority=o.optInt("goal_priority",50).coerceIn(0,100)
                val currentActive=(0 until gs.length()).mapNotNull { gs.optJSONObject(it) }
                    .filter { it.optString("status","active")=="active" }
                val newStatus=if(currentActive.size<5) "active" else {
                    val lowest=currentActive.minByOrNull { it.optInt("priority",50) }
                    if(lowest!=null && priority>=lowest.optInt("priority",50)+12) {
                        lowest.put("status","paused").put("pausedDay",day)
                        "active"
                    } else "candidate"
                }
                gs.put(JSONObject().put("goal",proposal).put("reason",reason)
                    .put("priority",priority).put("status",newStatus).put("progress",progress.coerceAtLeast(0))
                    .put("next_step",nextStep).put("createdDay",day).put("lastUpdatedDay",day))
            }
        } else if(nextStep.isNotBlank() || progress>=0) {
            val current=active.maxByOrNull { it.optInt("priority",50) }
            if(current!=null) {
                if(nextStep.isNotBlank())current.put("next_step",nextStep)
                if(progress>=0)current.put("progress",progress)
                current.put("lastUpdatedDay",day)
            }
        }

        if(explicit==null) {
            val ev="$proposal $reason ${CognitiveStore.str(o,"intent")}"
            val cues=mapOf(
                "survival" to listOf("生存","饥饿","安全","危险"),
                "autonomy" to listOf("自由","独立","自主"),
                "belonging" to listOf("家人","朋友","帮助","关系"),
                "mastery" to listOf("学习","训练","技艺","变强"),
                "justice" to listOf("正义","不公","保护","守护"),
                "curiosity" to listOf("调查","探索","研究","真相")
            )
            cues.forEach { (n,ws) -> if(ws.any(ev::contains))d.put(n,(d.optInt(n,50)+2).coerceAtMost(100)) }
        }
        // Boredom is a property of a life, not of a prompt: repeating the same action
        // day after day accumulates it; novelty and risk discharge it.
        val act=CognitiveStore.str(o,"action").lowercase()
        if(act.isNotBlank()) {
            val lastAct=s.optString("lastAction","")
            var boredom=s.optInt("boredom",0)
            boredom=when {
                act in setOf("take_risk","explore_unknown","provoke") -> (boredom-25).coerceAtLeast(0)
                lastAct.isNotBlank() && act==lastAct -> (boredom+6).coerceAtMost(100)
                else -> (boredom-10).coerceAtLeast(0)
            }
            s.put("boredom",boredom).put("lastAction",act)
        }
        if(!s.has("riskTolerance")) s.put("riskTolerance",50)
        // Gentle mean reversion on quiet days: an extreme risk attitude formed in one
        // hot streak slowly relaxes toward the character's baseline instead of
        // sticking at the clamp forever. Risky days themselves are exempt.
        if(act !in setOf("take_risk","explore_unknown","provoke")) {
            val rt0=s.optInt("riskTolerance",50)
            if(kotlin.math.abs(rt0-50)>20) s.put("riskTolerance", rt0 + if(rt0>50) -1 else 1)
        }
        while(gs.length()>24)gs.remove(0)
        s.put("drives",d).put("goals",gs).put("updatedDay",day)
        p.edit().putString("${k}_motives",s.toString()).apply()
    }

    fun report(c:Context,a:String)=prepare(c,a)
}

object IdentityEvolutionEngine {
    private fun load(c:Context,a:String)=CognitiveStore.read(CognitiveStore.prefs(c),"${CognitiveStore.key(a)}_identity")
    private fun similarBelief(a:String,b:String):Boolean { fun grams(s:String):Set<String>{val t=s.filter{!it.isWhitespace()};return if(t.length<2) setOf(t) else (0 until t.length-1).map { t.substring(it,it+2) }.toSet()}; val x=grams(a); val y=grams(b); val u=(x+y).size; return u>0 && x.intersect(y).size.toDouble()/u>=0.5 }
    fun prepare(c:Context,a:String):String {val s=load(c,a);val h=s.optJSONArray("history")?:JSONArray();val recent=(maxOf(0,h.length()-4) until h.length()).mapNotNull{h.optJSONObject(it)?.optString("change")}.joinToString("；").ifBlank{"尚无身份转折"}
        val candsP=s.optJSONArray("beliefCandidates")?:JSONArray()
        val topCand=(0 until candsP.length()).mapNotNull{candsP.optJSONObject(it)}.maxByOrNull{it.optInt("count",0)}
        val pending=topCand?.optString("belief").orEmpty(); val pendingCount=topCand?.optInt("count",0)?:0
        val wavering=(pending.isNotBlank() && pendingCount>=1) || (s.optString("pendingBelief").isNotBlank() && s.optInt("pendingCount",0)>0)
        val former=CognitiveStore.values(s.optJSONArray("formerBeliefs")).takeLast(2).joinToString("；")
        return """【身份演化】
身份版本：${s.optInt("version",0)}
核心价值：${CognitiveStore.values(s.optJSONArray("values")).takeLast(8).joinToString("、").ifBlank{"尚未明确"}}
近期转变：$recent${if(wavering) "\n信念动摇中：你已不止一次冒出与既有信念不同的想法——「${(pending.ifBlank { s.optString("pendingBelief") }).take(60)}」——尚未定论。动摇本身是真实状态，不必强行压回去，也不必立刻改口。" else ""}${if(former.isNotBlank()) "\n曾经的信念：$former" else ""}
人格变化往往始于动摇：一次强烈亲历可以当场动摇信念，先如实记录动摇，不必等到彻底想通；但无依据的反复横跳也不是成长。"""}
    fun capture(c:Context,a:String,day:Long,o:JSONObject?){if(o==null)return;val p=CognitiveStore.prefs(c);val k=CognitiveStore.key(a);val s=load(c,a);val values=s.optJSONArray("values")?:JSONArray();CognitiveStore.values(o.optJSONArray("values")).forEach{CognitiveStore.add(values,it,20)}
        val h=s.optJSONArray("history")?:JSONArray();val change=CognitiveStore.str(o,"identity_change");val reason=CognitiveStore.str(o,"identity_change_reason",500)
        if(change.isNotBlank()&&reason.isNotBlank()&&h.optJSONObject(h.length()-1)?.optString("change")!=change){h.put(JSONObject().put("day",day).put("change",change).put("reason",reason));s.put("version",s.optInt("version",0)+1)}
        val belief=CognitiveStore.str(o,"belief");val committed=s.optString("committedBelief")
        if(belief.isNotBlank()&&committed.isBlank()){s.put("committedBelief",belief).put("pendingCount",0)}
        else if(belief.isNotBlank()&&belief!=committed){
            // Evidence accumulates PER TOPIC, never in one global pool: doubts about
            // three different subjects must not add up to one personality change
            // (the v1 global counter allowed exactly that cross-topic pollution).
            // Each wavering topic keeps its own candidate record with its own count.
            val cands=s.optJSONArray("beliefCandidates")?:JSONArray()
            var match:JSONObject?=null
            for(ci in 0 until cands.length()){val cj=cands.optJSONObject(ci);if(cj!=null&&similarBelief(cj.optString("belief"),belief)){match=cj;break}}
            val cand=match ?: JSONObject().put("belief",belief).put("count",0).put("firstDay",day).also{cands.put(it)}
            cand.put("belief",belief).put("count",cand.optInt("count",0)+1).put("lastDay",day)
            while(cands.length()>6){var wi=0;var wc=Int.MAX_VALUE;var wd=Long.MAX_VALUE;for(ci in 0 until cands.length()){val cj=cands.optJSONObject(ci)?:continue;val cc=cj.optInt("count",0);val cd=cj.optLong("lastDay",0);if(cc<wc||(cc==wc&&cd<wd)){wc=cc;wd=cd;wi=ci}};cands.remove(wi)}
            s.put("beliefCandidates",cands)
            // Consecutive same-wording path (kept from the original design).
            val consec=if(s.optString("pendingBelief")==belief)s.optInt("pendingCount",0)+1 else 1
            s.put("pendingBelief",belief).put("pendingCount",consec)
            // A self-declared identity_change counts as a strong event only when its
            // reason cites a concrete happening (being attacked, betrayed, saved,
            // losing someone...), not merely a mood or an opinion.
            val concrete=listOf("被","杀","死","伤","背叛","离开","失去","救","败","胜","发现")
            val strongEvent=change.isNotBlank()&&reason.length>=10&&concrete.any(reason::contains)
            if(cand.optInt("count",0)>=3||consec>=2||strongEvent){
                val former=s.optJSONArray("formerBeliefs")?:JSONArray()
                if(committed.isNotBlank())CognitiveStore.add(former,"曾相信：$committed（第${day}日因新经历改变）",12)
                s.put("formerBeliefs",former)
                h.put(JSONObject().put("day",day).put("change","信念调整").put("reason","此前：$committed；现在：$belief。"+when{strongEvent->"由具体经历当场触发。";cand.optInt("count",0)>=3->"同一主题的动摇多次累积后形成。";else->"连续多轮表达一致。"}))
                s.put("version",s.optInt("version",0)+1).put("committedBelief",belief).put("pendingCount",0)
                val rest=JSONArray();for(ci in 0 until cands.length()){val cj=cands.optJSONObject(ci);if(cj!=null&&cj!==cand)rest.put(cj)}
                s.put("beliefCandidates",rest)
            }
        }
        else if(belief.isNotBlank())s.put("pendingCount",0)
        while(h.length()>40)h.remove(0);s.put("values",values).put("history",h).put("updatedDay",day);p.edit().putString("${k}_identity",s.toString()).apply()}
    fun report(c:Context,a:String)=prepare(c,a)
}

object CognitiveArchitecture {
    private const val MAX_EPISODES=100
    private const val MAX_RAW=16000
    data class Evaluation(val duplicateThought:Boolean,val missingThought:Boolean,val continuityScore:Int,val note:String)
    fun prepare(context:Context,actorKey:String,day:Long,originalPrompt:String):String {val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val episodes=readEpisodes(p,k);val reflection=p.getString("${k}_reflection","").orEmpty()
        // Memory retrieval is activation-based, not a pure recency window: the last few
        // rounds for continuity, plus older high-importance episodes ("anchors") that a
        // real person would still be carrying — a betrayal from 30 days ago must be able
        // to outrank yesterday's routine.
        val allEpisodes=(0 until episodes.length()).mapNotNull{episodes.optJSONObject(it)}
        val recentList=allEpisodes.takeLast(4)
        val anchors=allEpisodes.dropLast(4).filter { it.optInt("importance",3)>=6 }
            .sortedWith(compareByDescending<JSONObject>{it.optInt("importance",3)}.thenByDescending{it.optLong("day")}).take(3)
        val recent=recentList.joinToString("\n"){e->val ed=e.optLong("day");val th=e.optString("thought").take(150);val go=e.optString("goal").take(90);val ac=e.optString("action");val ou=e.optString("outcome").take(120);"第${ed}日：想法=$th；目标=$go；行动=$ac；结果=$ou"}.ifBlank{"尚无已完成的认知轮次。"}
        val anchorText=anchors.joinToString("\n"){e->"第${e.optLong("day")}日：想法=${e.optString("thought").take(120)}；行动=${e.optString("action")}；结果=${e.optString("outcome").take(110)}"}
        val add="""【持续认知｜第${day}日】
长期反思：${reflection.ifBlank{"经历尚少，暂不形成长期结论。"}}
近期经历：
$recent${if(anchorText.isNotBlank()) "\n深层记忆（久远但刻骨，仍会影响今天的判断）：\n"+anchorText else ""}
${SelfModelEngine.prepare(context,actorKey)}
${MotivationEngine.prepare(context,actorKey)}
${IdentityEvolutionEngine.prepare(context,actorKey)}
区分亲历、转述、推测与未知；记住行动后果；允许主动产生目标、拒绝、等待或改变方向。人格可以稳定，也可以被经历改变：强烈的亲历可以当场动摇信念，动摇、犹豫、自我怀疑都是真实状态，可以如实写入 belief 或 identity_change 并注明尚在动摇，不必等到彻底想通；也不得为了显得不同而无依据地改变。每轮JSON除原有字段外必须包含：self_view（自我理解，简短字符串）、self_assessment（自我评价，简短字符串）、strengths（数组，最多3项）、limitations（数组，最多3项）、self_facts（有事实依据的数组，最多3项）、self_confidence（0至100整数）、motives（对象，survival/autonomy/belonging/mastery/justice/curiosity六项均为0至100整数）、new_goal（仅在确有新长期方向时填写，不要把短期意图当成长目标）、long_term_goal（可选长期目标）、goal_reason（目标缘由）、goal_priority（0至100整数）、goal_status（active/completed/paused/abandoned）、goal_progress（0至100整数）、next_step（目标下一步），values（核心价值数组，最多5项）；长期目标默认保持连续，不得每轮随意改写或覆盖；但当经历形成新的执念、教训或内心冲突时，可以新增目标与旧目标并存甚至冲突，并写明缘由；identity_change和identity_change_reason在有真实经历支持变化或动摇时填写（动摇也可填写并注明），否则留空。不输出隐藏思维链。"""
        // Stagnation intervention: duplicate detection used to only lower a score.
        // When the previous turn was flagged as a near-repeat, this turn's prompt
        // says so plainly and demands a change of method — detection with teeth.
        val lastEval=CognitiveStore.read(p,"${k}_last_evaluation")
        val stagnation=if(lastEval.optBoolean("duplicateThought")) "\n【停滞警告】你上一轮的想法与之前高度重复。这一轮必须改变：换一个能真正执行的方法、降低目标，或明确暂时放下这件事；不许再复述同一个计划。" else ""
        return originalPrompt+"\n\n"+add+stagnation+"\n\n"+PersonalLearningNetwork.prepare(context,actorKey,originalPrompt,day)+"\n\n"+AutonomousCognitionEngine.prepare(context,actorKey)}
    fun capture(context:Context,actorKey:String,day:Long,raw:String):Evaluation {val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k);val obj=extractObject(raw);val thought=obj?.optString("thought").orEmpty().trim();val norm=normalize(thought)
        val prev=(maxOf(0,a.length()-6) until a.length()).mapNotNull{a.optJSONObject(it)}.map{normalize(it.optString("thought"))}.filter{it.isNotBlank()};val duplicate=norm.isNotBlank()&&prev.any{similarity(norm,it)>=0.88};val missing=thought.isBlank();val score=when{missing->0;duplicate->35;a.length()==0->70;else->90}
        // Seed boredom for legacy saves from the actor's current same-action streak,
        // so a 25-day routine does not restart from boredom 0 after the upgrade.
        val streakSeed=run { val curAct=obj?.optString("action").orEmpty(); var n=if(curAct.isNotBlank())1 else 0; var si=a.length()-1; while(si>=0 && curAct.isNotBlank() && a.optJSONObject(si)?.optString("action")==curAct){n++;si--}; n }
        MotivationEngine.seedBoredom(context,actorKey,streakSeed)
        SelfModelEngine.capture(context,actorKey,day,obj);MotivationEngine.capture(context,actorKey,day,obj);IdentityEvolutionEngine.capture(context,actorKey,day,obj)
        a.put(JSONObject().put("day",day).put("thought",thought.take(600)).put("belief",obj?.optString("belief").orEmpty().take(300)).put("goal",obj?.optString("goal").orEmpty().take(300)).put("intent",obj?.optString("intent").orEmpty().take(300)).put("action",obj?.optString("action").orEmpty().take(100)).put("reason",obj?.optString("reason").orEmpty().take(300)).put("outcome","").put("importance",episodeImportance(thought,obj?.optString("action").orEmpty(),obj?.optString("reason").orEmpty(),duplicate)).put("raw",raw.take(MAX_RAW)).put("duplicateThought",duplicate).put("continuityScore",score))
        while(a.length()>MAX_EPISODES)a.remove(0);val ed=p.edit().putString("${k}_episodes",a.toString()).putString("${k}_last_evaluation",JSONObject().put("day",day).put("duplicateThought",duplicate).put("missingThought",missing).put("continuityScore",score).toString()).putString("${k}_last_raw",raw.take(MAX_RAW))
        // Reflection is triggered by accumulated importance (Generative-Agents style),
        // not only by a fixed round count: quiet routine days may pass without deep
        // reflection, while a few heavy days in a row force one quickly.
        val lastEp=a.optJSONObject(a.length()-1)
        // Fallback cadence uses a persistent episode sequence, NOT a.length()%5: once
        // the pool is full the length is pinned at MAX_EPISODES (100, divisible by 5),
        // which would make a length-based fallback fire on every single capture.
        val seq=p.getInt("${k}_episode_seq",0)+1
        ed.putInt("${k}_episode_seq",seq)
        val sinceReflection=seq-p.getInt("${k}_last_reflection_seq",0)
        // A long same-action streak is itself reflection-worthy: monotony accumulates
        // "something is wrong" pressure even when each quiet day scores low importance.
        var streak=0; val lastAct=lastEp?.optString("action").orEmpty()
        if(lastAct.isNotBlank()){ var si=a.length()-1; while(si>=0 && a.optJSONObject(si)?.optString("action")==lastAct){streak++;si--} }
        val streakBonus=if(streak>=5)(streak-4)*2 else 0
        val debt=p.getInt("${k}_reflection_debt",0)+(lastEp?.optInt("importance",3)?:3)+streakBonus
        val reflectionResult=if(debt>=60||sinceReflection>=5) buildReflection(a) else null
        if(reflectionResult!=null){ed.putString("${k}_reflection",reflectionResult.text);ed.putInt("${k}_reflection_debt",0);ed.putInt("${k}_last_reflection_seq",seq)}else ed.putInt("${k}_reflection_debt",debt)
        ed.apply()
        if(reflectionResult?.emergentGoal!=null)MotivationEngine.addEmergentGoal(context,actorKey,day,reflectionResult.emergentGoal,reflectionResult.emergentReason.orEmpty(),62,reflectionResult.emergentNextStep.orEmpty())
        return Evaluation(duplicate,missing,score,when{missing->"缺少人物 thought 字段";duplicate->"与近期想法高度重复";else->"自我模型、内在动机与身份状态已更新"})}
    fun recordOutcome(context:Context,actorKey:String,day:Long,action:String,outcome:String){val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k)
        // Backfill must find the episode this outcome belongs to: the previous rule only
        // looked at the very last episode and silently dropped the outcome whenever
        // capture order or day alignment shifted, leaving episodes outcome-less forever.
        var target:JSONObject?=null
        var i=a.length()-1
        while(i>=0 && i>=a.length()-10){val e=a.optJSONObject(i);if(e!=null && e.optLong("day",-1L)==day && e.optString("outcome").isBlank()){target=e;break};i--}
        val last=target ?: a.optJSONObject(a.length()-1)?.takeIf{it.optLong("day",-1L)==day} ?: return
        last.put("action",action.take(100)).put("outcome",outcome.take(500))
        val strongOutcome=listOf("死亡","背叛","受伤","失败","成功","发现","获得","失去")
        if(strongOutcome.any(outcome::contains)) last.put("importance",(last.optInt("importance",3)+2).coerceAtMost(10))
        p.edit().putString("${k}_episodes",a.toString()).apply();PersonalLearningNetwork.recordOutcome(context,actorKey,day,action,outcome);AutonomousCognitionEngine.recordOutcome(context,actorKey,day,action,outcome);MotivationEngine.onOutcome(context,actorKey,day,action,outcome);WorldAIProvider.recordVerifiedOutcome(context,actorKey,day,action,outcome)}
    fun report(context:Context,actorKey:String):String{val p=CognitiveStore.prefs(context);val k=CognitiveStore.key(actorKey);val a=readEpisodes(p,k);val e=CognitiveStore.read(p,"${k}_last_evaluation");val score=e.optInt("continuityScore",-1);val dup=e.optBoolean("duplicateThought");val reflection=p.getString("${k}_reflection","尚未生成").orEmpty();return "认知轮次：${a.length()}\n连续性评分：$score/100\n重复想法：$dup\n长期反思：$reflection\n\n"+SelfModelEngine.report(context,actorKey)+"\n\n"+MotivationEngine.report(context,actorKey)+"\n\n"+IdentityEvolutionEngine.report(context,actorKey)+"\n\n"+PersonalLearningNetwork.report(context,actorKey)+"\n\n"+AutonomousCognitionEngine.prepare(context,actorKey)}
    private data class ReflectionResult(val text:String,val emergentGoal:String?,val emergentReason:String?,val emergentNextStep:String?)
    private fun episodeImportance(thought:String,action:String,reason:String,duplicate:Boolean):Int {
        val text=thought+" "+reason
        var score=3
        val strong=listOf("死亡","背叛","杀","危险","受伤","离开","决定","发现","秘密","挑战","冲突","失去","获得","真相")
        score+=strong.count(text::contains).coerceAtMost(4)
        if(action.trim().lowercase() in setOf("take_risk","explore_unknown","provoke","join_event","investigate_event")) score+=2
        if(duplicate) score-=2
        return score.coerceIn(1,10)
    }
    private fun emergentGoalFor(knot:String):Triple<String,String,String> = when {
        listOf("训练","练习","身法","技艺","技能","掌控","变强").any(knot::contains) -> Triple("把反复练习的心结变成一次真正的验证","同一件心事已反复出现多次，与其无限重复，不如用一次真实检验了断它，或者放下它去寻找新的道路。","今天不要再重复同样的练习：选择 take_risk 或 explore_unknown 做一次真实检验，或找一个真实对手、事件来验证所学。")
        listOf("背叛","信任","朋友","关系","欺骗").any(knot::contains) -> Triple("弄清一段反复困扰自己的关系","同一段关系在心中反复出现，回避不会让它消失，需要一次真正的面对或了断。","今天主动推进一格：找当事人谈一次（socialize），或把矛盾挑明（provoke），不要再原地猜测。")
        listOf("离开","远方","未知","外面","探索").any(knot::contains) -> Triple("亲自去看看一直牵挂的未知","对未知的牵挂反复出现，与其在原地猜测，不如亲自去确认一次。","今天选择 explore_unknown，亲自去确认一次那片未知。")
        else -> Triple("直面反复出现的心结","同一件心事已反复出现多次，它不会自己消失，需要采取一次真正的行动去推进或了断。","今天选择一个与往日不同的动作，把心结推进一格，而不是再想一遍。")
    }
    private fun buildReflection(a:JSONArray):ReflectionResult {
        val all=(0 until a.length()).mapNotNull{a.optJSONObject(it)}
        val r=all.takeLast(12)
        val b=r.map{it.optString("belief")}.filter{it.isNotBlank()}.distinct().takeLast(4)
        val g=r.map{it.optString("goal")}.filter{it.isNotBlank()}.distinct().takeLast(4)
        val o=r.map{it.optString("outcome")}.filter{it.isNotBlank()}.takeLast(5)
        // Lessons come from the strongest outcomes across the WHOLE stored life, not
        // just the last window, so a costly lesson is not forgotten when it slides out.
        val strongWords=listOf("成功","失败","受伤","死亡","背叛","获得","失去","发现","被拒绝","获救")
        val lessons=all.filter { e -> val out=e.optString("outcome"); out.isNotBlank() && strongWords.any(out::contains) }
            .sortedByDescending { it.optInt("importance",3) }.take(3)
            .map { "第${it.optLong("day")}日：${it.optString("outcome").take(90)}" }
        // Unresolved knot: the largest cluster of mutually similar thoughts. A thought
        // the character keeps re-expressing for weeks is an obsession, and the
        // reflection must name it instead of splicing it away.
        var knotText:String?=null; var knotCount=0
        val thoughts=all.map{it.optString("thought")}.filter{it.isNotBlank()}
        val normalizedThoughts=thoughts.map{normalize(it)}
        for(idx in thoughts.indices){ val n=normalizedThoughts[idx]; val c=normalizedThoughts.count { similarity(n,it)>=0.6 }; if(c>knotCount){knotCount=c;knotText=thoughts[idx]} }
        val knot=knotText
        val text=buildString{
            if(b.isNotEmpty())append("信念轨迹：").append(b.joinToString(" → ")).append("。")
            if(g.isNotEmpty())append("目标轨迹：").append(g.joinToString(" → ")).append("。")
            if(o.isNotEmpty())append("行动后果：").append(o.joinToString("；")).append("。")
            if(lessons.isNotEmpty())append("刻骨的经历：").append(lessons.joinToString("；")).append("。")
            if(knot!=null&&knotCount>=3)append("未解心结（已反复出现${knotCount}次）：").append(knot.take(90)).append("。")
            if(isEmpty())append("目前经历不足以形成稳定反思。")
        }.take(1800)
        val emergent=if(knot!=null&&knotCount>=3) emergentGoalFor(knot) else null
        return ReflectionResult(text,emergent?.first,emergent?.second,emergent?.third)
    }
    private fun readEpisodes(p:android.content.SharedPreferences,k:String)=runCatching{JSONArray(p.getString("${k}_episodes","[]")?:"[]")}.getOrDefault(JSONArray())
    private fun extractObject(raw:String):JSONObject?{val t=raw.replace(Regex("(?is)<think>.*?</think>"),"").trim();for(s in t.indices)if(t[s]=='{'){var d=0;var q=false;var e=false;for(i in s until t.length){val c=t[i];if(q){if(e)e=false else if(c=='\\')e=true else if(c=='"')q=false}else when(c){'"'->q=true;'{'->d++;'}'->{d--;if(d==0)return runCatching{JSONObject(t.substring(s,i+1))}.getOrNull()}}}};return null}
    private fun normalize(v:String)=Normalizer.normalize(v,Normalizer.Form.NFKC).lowercase().replace(Regex("[\\p{Punct}\\p{IsPunctuation}\\s，。！？；：、“”‘’（）【】]+"),"")
    private fun similarity(a:String,b:String):Double{if(a.isBlank()||b.isBlank())return 0.0;if(a==b)return 1.0
        // Bigram-set Jaccard: single-character sets overstate similarity for Chinese
        // (shared high-frequency characters make unrelated sentences look alike);
        // bigrams track wording and topic far better.
        fun grams(s:String):Set<String> = if(s.length<2) setOf(s) else (0 until s.length-1).map { s.substring(it,it+2) }.toSet()
        val x=grams(a);val y=grams(b);val u=(x+y).size;return if(u==0)0.0 else x.intersect(y).size.toDouble()/u}
}