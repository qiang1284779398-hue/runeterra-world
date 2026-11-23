package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Persistent cognitive runtime shared by heroes and residents. GLM generates responses;
 * this layer retrieves episodic memory, reflects on experience and records continuity checks. */
object CognitiveArchitecture {
    private const val PREFS = "runeterra_cognitive_runtime"
    private const val MAX_EPISODES = 80
    private const val MAX_RAW = 12000

    data class Evaluation(val duplicateThought:Boolean, val missingThought:Boolean, val continuityScore:Int, val note:String)

    fun prepare(context:Context, actorKey:String, day:Long, originalPrompt:String):String {
        val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val key=safeKey(actorKey)
        val episodes=readEpisodes(prefs,key)
        val reflection=prefs.getString("${key}_reflection","").orEmpty()
        val recent=(maxOf(0,episodes.length()-8) until episodes.length()).mapNotNull { episodes.optJSONObject(it) }
            .joinToString("\n") { e ->
                "第${e.optLong("day")}日：想法=${e.optString("thought").take(160)}；目标=${e.optString("goal").take(90)}；意图=${e.optString("intent").take(90)}；行动=${e.optString("action")}；结果=${e.optString("outcome").take(100)}"
            }.ifBlank { "尚无已完成的认知轮次。" }
        val continuity="""
【持久认知运行时｜第${day}日】
以下是该人物跨轮次保存的独立经历索引，不是模型临时上下文。必须把它当作本人过去真实发生且已记录的经历。
长期反思摘要：${reflection.ifBlank { "经历尚少，暂不形成长期结论。" }}
最近认知轮次：
${recent}
连续性要求：
1. 区分亲历事实、他人转述、推测和未知；不得把推测升级成事实。
2. 记住过去行动及其结果。失败行动不能在没有新条件时重复宣称成功。
3. 目标可以因新事实改变，但改变必须有原因；稳定人格允许长期保持同一立场。
4. 新一轮要推进人物人生状态：可行动、等待、拒绝、求助、调查或修正计划；不得编造事件。
5. 不输出隐藏思维链，只给最终人物判断及结构化决策。
"""
        return originalPrompt+"\n\n"+continuity
    }

    fun capture(context:Context, actorKey:String, day:Long, raw:String):Evaluation {
        val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val key=safeKey(actorKey)
        val episodes=readEpisodes(prefs,key)
        val obj=extractObject(raw)
        val thought=obj?.optString("thought").orEmpty().trim()
        val normalized=normalize(thought)
        val previous=(maxOf(0,episodes.length()-6) until episodes.length()).mapNotNull { episodes.optJSONObject(it) }
            .map { normalize(it.optString("thought")) }.filter { it.isNotBlank() }
        val duplicate=normalized.isNotBlank() && previous.any { similarity(normalized,it)>=0.88 }
        val missing=thought.isBlank()
        val score=when { missing->0; duplicate->35; episodes.length()==0->70; else->90 }
        val episode=JSONObject().put("day",day).put("thought",thought.take(600))
            .put("belief",obj?.optString("belief").orEmpty().take(240))
            .put("goal",obj?.optString("goal").orEmpty().take(240))
            .put("intent",obj?.optString("intent").orEmpty().take(240))
            .put("action",obj?.optString("action").orEmpty().take(80))
            .put("reason",obj?.optString("reason").orEmpty().take(240))
            .put("raw",raw.take(MAX_RAW)).put("duplicateThought",duplicate).put("continuityScore",score)
        episodes.put(episode)
        while(episodes.length()>MAX_EPISODES) episodes.remove(0)
        val editor=prefs.edit().putString("${key}_episodes",episodes.toString())
            .putString("${key}_last_evaluation",JSONObject().put("day",day).put("duplicateThought",duplicate)
                .put("missingThought",missing).put("continuityScore",score).toString())
            .putString("${key}_last_raw",raw.take(MAX_RAW))
        if(episodes.length()%5==0) editor.putString("${key}_reflection",buildReflection(episodes))
        editor.apply()
        val note=when { missing->"缺少人物 thought 字段"; duplicate->"与近期想法高度重复"; else->"基础连续性检查通过" }
        return Evaluation(duplicate,missing,score,note)
    }

    fun report(context:Context, actorKey:String):String {
        val prefs=context.applicationContext.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val key=safeKey(actorKey)
        val episodes=readEpisodes(prefs,key)
        val e=runCatching { JSONObject(prefs.getString("${key}_last_evaluation","{}")?:"{}") }.getOrDefault(JSONObject())
        return "认知轮次：${episodes.length()}\n最近评估：${e.optInt("continuityScore",-1)}/100\n重复想法：${e.optBoolean("duplicateThought")}\n长期反思：${prefs.getString("${key}_reflection","尚未生成").orEmpty()}"
    }

    private fun buildReflection(episodes:JSONArray):String {
        val recent=(maxOf(0,episodes.length()-10) until episodes.length()).mapNotNull { episodes.optJSONObject(it) }
        val beliefs=recent.map { it.optString("belief") }.filter { it.isNotBlank() }.distinct().takeLast(3)
        val goals=recent.map { it.optString("goal") }.filter { it.isNotBlank() }.distinct().takeLast(3)
        val actions=recent.map { it.optString("action") }.filter { it.isNotBlank() && it!="stay" }.takeLast(5)
        val reasons=recent.map { it.optString("reason") }.filter { it.isNotBlank() }.takeLast(3)
        return buildString {
            if(beliefs.isNotEmpty()) append("反复体现的信念：").append(beliefs.joinToString("；")).append("。")
            if(goals.isNotEmpty()) append("目标轨迹：").append(goals.joinToString(" → ")).append("。")
            if(actions.isNotEmpty()) append("近期行动：").append(actions.joinToString("、")).append("。")
            if(reasons.isNotEmpty()) append("经历教训：").append(reasons.joinToString("；")).append("。")
            if(isEmpty()) append("目前经历不足以形成稳定反思。")
        }.take(1200)
    }

    private fun readEpisodes(prefs:android.content.SharedPreferences,key:String)=
        runCatching { JSONArray(prefs.getString("${key}_episodes","[]")?:"[]") }.getOrDefault(JSONArray())

    private fun extractObject(raw:String):JSONObject? {
        val text=raw.replace(Regex("(?is)<think>.*?</think>"),"").replace("```json","").replace("```","").trim()
        for(start in text.indices) {
            if(text[start]!='{') continue
            var depth=0; var quoted=false; var escaped=false
            for(i in start until text.length) {
                val c=text[i]
                if(quoted) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false }
                else when(c) {
                    '"'->quoted=true
                    '{'->depth++
                    '}'->{ depth--; if(depth==0) return runCatching { JSONObject(text.substring(start,i+1)) }.getOrNull() }
                }
            }
        }
        return null
    }

    private fun normalize(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKC).lowercase()
        .replace(Regex("[\\p{Punct}\\p{IsPunctuation}\\s，。！？；：、“”‘’（）【】]+"),"")
    private fun similarity(a:String,b:String):Double {
        if(a.isBlank()||b.isBlank()) return 0.0
        if(a==b) return 1.0
        val aa=a.toSet(); val bb=b.toSet(); val union=(aa+bb).size
        return if(union==0) 0.0 else aa.intersect(bb).size.toDouble()/union
    }
    private fun safeKey(value:String)="agent_"+value.replace(Regex("[^A-Za-z0-9_-]"),"_").take(80)
}
