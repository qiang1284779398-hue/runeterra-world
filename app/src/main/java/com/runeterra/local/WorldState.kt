package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

data class WorldSnapshot(val day: Long, val residents: Int, val events: List<String>, val running: Boolean)

private data class Resident(
    val id: Int, var name: String, var region: String, var age: Int, var health: Int,
    var food: Int, var coins: Int, var job: String, var goal: String,
    var skill: Int = 1, var partnerId: Int = -1, var familyCount: Int = 0, var alive: Boolean = true
) {
    fun json() = JSONObject().put("id",id).put("name",name).put("region",region).put("age",age)
        .put("health",health).put("food",food).put("coins",coins).put("job",job).put("goal",goal)
        .put("skill",skill).put("partnerId",partnerId).put("familyCount",familyCount).put("alive",alive)
    companion object {
        fun from(j: JSONObject) = Resident(j.optInt("id"),j.optString("name"),j.optString("region"),
            j.optInt("age",18),j.optInt("health",100),j.optInt("food",5),j.optInt("coins",10),
            j.optString("job","采集者"),j.optString("goal","谋求安稳"),j.optInt("skill",1),
            j.optInt("partnerId",-1),j.optInt("familyCount",0),j.optBoolean("alive",true))
    }
}

object WorldEngine {
    private const val PREFS="runeterra_world"
    private const val KEY_DAY="day"
    private const val KEY_EVENTS="events"
    private const val KEY_RUNNING="running"
    private const val KEY_PEOPLE="people"
    private const val KEY_NEXT_ID="next_id"
    private val regions=listOf("德玛西亚","诺克萨斯","艾欧尼亚","皮尔特沃夫","祖安","弗雷尔卓德","恕瑞玛","巨神峰","比尔吉沃特","以绪塔尔")
    private val jobs=listOf("农夫","工匠","商贩","护卫","学徒","采集者","旅人")
    private val goals=listOf("积攒财富","寻找亲人","学习技艺","改善家园","探索远方","获得认可","守护家人")
    private val names=listOf("艾琳","卡伦","米娅","托伦","塞拉","诺安","伊芙","莱恩","凯娅","罗克","维娜","奥伦","塔莎","梅林","德拉","阿斯","菲恩")
    private fun people(p: android.content.SharedPreferences): MutableList<Resident> {
        val raw=p.getString(KEY_PEOPLE,null)
        if(raw!=null) return runCatching { val a=JSONArray(raw); (0 until a.length()).map { Resident.from(a.getJSONObject(it)) }.toMutableList() }.getOrDefault(mutableListOf())
        val rng=Random(24681357L)
        val list=(0 until 240).map { i -> Resident(i,names[rng.nextInt(names.size)]+"·"+(i+1),regions[i%regions.size],
            16+rng.nextInt(54),65+rng.nextInt(36),2+rng.nextInt(8),rng.nextInt(40),jobs[rng.nextInt(jobs.size)],goals[rng.nextInt(goals.size)]) }.toMutableList()
        p.edit().putInt(KEY_NEXT_ID,240).apply()
        savePeople(p,list); return list
    }
    private fun savePeople(p: android.content.SharedPreferences, list: List<Resident>) {
        val a=JSONArray(); list.forEach { a.put(it.json()) }; p.edit().putString(KEY_PEOPLE,a.toString()).apply()
    }
    fun load(context: Context): WorldSnapshot {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val events=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]")); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
        return WorldSnapshot(p.getLong(KEY_DAY,1L),people(p).count { it.alive },events,p.getBoolean(KEY_RUNNING,false))
    }
    fun advance(context: Context, days: Int=1): WorldSnapshot {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var day=p.getLong(KEY_DAY,1L)
        val pop=people(p)
        val events=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]")); (0 until a.length()).map { a.getString(it) }.toMutableList() }.getOrDefault(mutableListOf())
        repeat(days.coerceAtLeast(1)) {
            day++
            val rng=Random(day*7919L+17L)
            val newborns=mutableListOf<Resident>()
            pop.filter { it.alive }.forEach { r ->
                r.food=(r.food-1).coerceAtLeast(0)
                if(r.food==0) r.health=(r.health-2).coerceAtLeast(0)
                if(rng.nextInt(100)<(20+r.skill.coerceAtMost(15))) when(r.job) {
                    "农夫","采集者" -> {r.food+=2+rng.nextInt(3);r.coins++;r.skill=(r.skill+1).coerceAtMost(100)}
                    "商贩" -> if(r.food>1){r.food--;r.coins+=2;r.skill=(r.skill+1).coerceAtMost(100)}else r.food++
                    "工匠" -> {r.coins+=2;r.skill=(r.skill+1).coerceAtMost(100)}
                    "护卫" -> {r.coins++;if(rng.nextInt(100)<4)r.health=(r.health-3).coerceAtLeast(0)}
                    else -> {if(r.coins>0)r.coins--;if(rng.nextInt(100)<20)r.food++}
                }
                if(day%360L==0L) r.age++
                if(day%30L==0L && r.alive && r.partnerId<0 && r.age in 20..35 && rng.nextInt(1000)<2) {
                    val candidate=pop.firstOrNull { it.alive && it.id!=r.id && it.region==r.region && it.partnerId<0 && it.age in 20..35 }
                    if(candidate!=null) { r.partnerId=candidate.id;candidate.partnerId=r.id
                        events.add("第 $day 日：${r.region}的${r.name}与${candidate.name}建立伴侣关系。") }
                }
                if(day%30L==0L && r.partnerId>=0 && r.age in 20..38 && r.familyCount<3 && rng.nextInt(1000)<3) {
                    r.familyCount++
                    val mate=pop.firstOrNull { it.id==r.partnerId && it.alive }
                    if(mate!=null) mate.familyCount=r.familyCount
                    val id=p.getInt(KEY_NEXT_ID,240)+newborns.size
                    val child=Resident(id,names[rng.nextInt(names.size)]+"·"+id,r.region,0,100,3,0,"学徒","成长与学习")
                    newborns.add(child)
                    events.add("第 $day 日：${r.region}的${r.name}家庭迎来新生儿${child.name}。")
                }
                if(r.alive && r.food<=1 && rng.nextInt(1000)<3) {
                    val next=regions[(regions.indexOf(r.region)+1)%regions.size]
                    r.region=next
                    events.add("第 $day 日：${r.name}因生计压力迁往${next}。")
                }
                if(r.health<=0 || (r.age>78 && rng.nextInt(100)<2)) {
                    r.alive=false
                    events.add("第 $day 日：${r.name}（${r.region}，${r.age}岁，${r.job}）去世；人生记录留存。")
                    if(r.partnerId>=0) pop.firstOrNull { it.id==r.partnerId }?.partnerId=-1
                }
                if(r.alive && r.food==0 && rng.nextInt(100)<3) events.add("第 $day 日：${r.name}在${r.region}遭遇食物短缺，健康恶化。")
                if(r.alive && rng.nextInt(1000)<2) events.add("第 $day 日：${r.name}在${r.region}达成「${r.goal}」，开始追寻新的方向。")
            }
            pop.addAll(newborns)
            if(day%30L==0L) events.add("第 $day 日：月度结算，居民存粮、收入与健康影响生存。")
            if(day%90L==0L) events.add("第 $day 日：季节转变，地区居民面对环境压力。")
            if(day%360L==0L) events.add("第 $day 日：新年，居民年龄与人生阶段变化。")
        }
        while(events.size>1200)events.removeAt(0)
        savePeople(p,pop)
        val arr=JSONArray();events.forEach{arr.put(it)}
        p.edit().putInt(KEY_NEXT_ID,p.getInt(KEY_NEXT_ID,240)+pop.size-240)
            .putLong(KEY_DAY,day).putString(KEY_EVENTS,arr.toString()).apply()
        return WorldSnapshot(day,pop.count{it.alive},events,p.getBoolean(KEY_RUNNING,false))
    }
    fun setRunning(context: Context,running:Boolean) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,running).apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().putLong(KEY_DAY,1L).apply()
    }
}