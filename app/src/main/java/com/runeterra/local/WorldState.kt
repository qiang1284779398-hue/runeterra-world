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
    private const val KEY_MARKETS="markets"
    private const val KEY_LORE="lore_triggers"
    private data class LorePulse(val id:String,val day:Long,val title:String,val description:String,val region:String,val foodLoss:Int,val healthLoss:Int)
    // Official lore inspirations; dates are simulation milestones, not canon chronology.
    private val lorePulses=listOf(
        LorePulse("ionia_war",120L,"艾欧尼亚战争的余波","诺克萨斯入侵的模拟历史冲击：抵抗、伤亡与流离失所开始影响当地居民。","艾欧尼亚",70,3),
        LorePulse("demacia_mage",240L,"德玛西亚魔法禁令加剧","模拟禁魔政治压力：法师家庭隐匿身份、离开城镇，社会信任出现裂痕。","德玛西亚",25,1),
        LorePulse("shurima_rise",360L,"恕瑞玛复兴浪潮","模拟古帝国复兴带来的遗迹探索、商路复苏与权力争夺。","恕瑞玛",10,0),
        LorePulse("ruination",540L,"黑雾侵袭的传闻","模拟破败之王相关黑雾危机：比尔吉沃特周边出现恐慌、失踪与逃难。","比尔吉沃特",90,8),
        LorePulse("piltover_zaun",720L,"双城科技与阶层冲突","皮尔特沃夫与祖安的模拟工业发展加速，财富增长伴随事故与贫富矛盾。","祖安",35,2),
        LorePulse("freljord",900L,"弗雷尔卓德寒潮与部族冲突","模拟严冬压迫资源，部族之间争夺生存物资，旅人风险上升。","弗雷尔卓德",110,5),
        LorePulse("bilgewater",1080L,"比尔吉沃特海上争端","模拟港口贸易、海盗势力与悬赏活动造成的治安波动。","比尔吉沃特",30,2),
        LorePulse("ixtal",1260L,"以绪塔尔边境与元素异动","模拟隐秘文明受到外界探索影响，元素环境与边境冲突出现变化。","以绪塔尔",20,1)
    )
    private fun applyLorePulse(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        val triggered=runCatching { JSONArray(p.getString(KEY_LORE,"[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } }.getOrDefault(mutableSetOf())
        lorePulses.filter { it.day==day && !triggered.contains(it.id) }.forEach { pulse ->
            val market=markets[pulse.region]
            if(market!=null) market.food=(market.food-pulse.foodLoss).coerceAtLeast(0)
            val affected=pop.filter { it.alive && it.region==pulse.region }
            affected.forEach { r -> r.health=(r.health-pulse.healthLoss).coerceAtLeast(0) }
            val moved=affected.filter { rng.nextInt(100)<18 }
            moved.forEach { r ->
                val dest=regions[(regions.indexOf(pulse.region)+1)%regions.size]
                r.region=dest
            }
            events.add("【官方背景事件·模拟演绎】第 $day 日 · ${pulse.title}：${pulse.description} 影响${affected.size}名居民，${moved.size}人逃离${pulse.region}。")
            triggered.add(pulse.id)
        }
        p.edit().putString(KEY_LORE,JSONArray().apply { triggered.forEach { put(it) } }.toString()).apply()
    }
    private data class LoreChainStage(val day:Long,val id:String,val title:String,val region:String,val foodDelta:Int,val healthDelta:Int,val migrationChance:Int)
    // Multi-stage event arcs; dates are simulation days, not canon chronology.
    private val chainStages=listOf(
        LoreChainStage(121L,"ionia_front","战线扩大：艾欧尼亚村落受到波及","艾欧尼亚",-45,-2,24),
        LoreChainStage(180L,"ionia_resistance","抵抗网络形成：当地居民开始组织互助","艾欧尼亚",20,0,0),
        LoreChainStage(300L,"ionia_recovery","战后重建或长期创伤：结果取决于人口与资源","艾欧尼亚",30,1,0),
        LoreChainStage(541L,"ruination_spread","黑雾扩散：港口周边出现新的失踪与逃亡","比尔吉沃特",-60,-4,35),
        LoreChainStage(600L,"ruination_survivors","幸存者组织临时避难与补给","比尔吉沃特",15,0,0),
        LoreChainStage(660L,"ruination_aftermath","危机余波：商路恢复或继续衰退","比尔吉沃特",25,1,0),
        LoreChainStage(721L,"zaun_incident","祖安工业事故：底层居民承受健康与经济损失","祖安",-20,-3,12),
        LoreChainStage(780L,"zaun_response","社区互助与工坊自救","祖安",15,0,0),
        LoreChainStage(840L,"zaun_reform","治理与技术路线变化，模拟社会恢复","祖安",20,1,0)
    )
    private fun applyLoreChainStage(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        val done=runCatching { JSONArray(p.getString("lore_chain_done","[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } }.getOrDefault(mutableSetOf())
        chainStages.filter { it.day==day && it.id !in done }.forEach { stage ->
            markets[stage.region]?.let { it.food=(it.food+stage.foodDelta).coerceAtLeast(0) }
            val residents=pop.filter { it.alive && it.region==stage.region }
            residents.forEach { r -> r.health=(r.health+stage.healthDelta).coerceIn(0,100) }
            val displaced=residents.filter { rng.nextInt(100)<stage.migrationChance }
            displaced.forEach { r -> r.region=regions[(regions.indexOf(stage.region)+1)%regions.size] }
            events.add("【历史事件链·模拟】第 ${day} 日 ${stage.title}；${stage.region}影响${residents.size}人，迁徙${displaced.size}人。")
            done.add(stage.id)
        }
        p.edit().putString("lore_chain_done",JSONArray().apply { done.forEach { put(it) } }.toString()).apply()
    }
    private val regions=listOf("德玛西亚","诺克萨斯","艾欧尼亚","皮尔特沃夫","祖安","弗雷尔卓德","恕瑞玛","巨神峰","比尔吉沃特","以绪塔尔")
    private val jobs=listOf("农夫","工匠","商贩","护卫","学徒","采集者","旅人")
    private val goals=listOf("积攒财富","寻找亲人","学习技艺","改善家园","探索远方","获得认可","守护家人")
    private val names=listOf("艾琳","卡伦","米娅","托伦","塞拉","诺安","伊芙","莱恩","凯娅","罗克","维娜","奥伦","塔莎","梅林","德拉","阿斯","菲恩")
    private data class Market(var food: Int, var wealth: Int) {
        fun json(region:String)=JSONObject().put("region",region).put("food",food).put("wealth",wealth)
    }
    private fun markets(p: android.content.SharedPreferences): MutableMap<String,Market> {
        val raw=p.getString(KEY_MARKETS,null)
        if(raw!=null) runCatching {
            val a=JSONArray(raw); val map=mutableMapOf<String,Market>()
            for(i in 0 until a.length()) { val j=a.getJSONObject(i); map[j.optString("region")]=Market(j.optInt("food",300),j.optInt("wealth",1000)) }
            if(map.keys.containsAll(regions)) return map
        }
        val fresh=regions.associateWith { Market(300+Random(it.hashCode().toLong()).nextInt(250),1000) }.toMutableMap()
        saveMarkets(p,fresh); return fresh
    }
    private fun saveMarkets(p: android.content.SharedPreferences, map: Map<String,Market>) {
        val a=JSONArray(); map.forEach { (name,m)->a.put(m.json(name)) }; p.edit().putString(KEY_MARKETS,a.toString()).apply()
    }
    private fun people(p: android.content.SharedPreferences): MutableList<Resident> {
        val raw=p.getString(KEY_PEOPLE,null)
        if(raw!=null) return runCatching { val a=JSONArray(raw); (0 until a.length()).map { Resident.from(a.getJSONObject(it)) }.toMutableList() }.getOrDefault(mutableListOf())
        val rng=Random(24681357L)
        val list=(0 until 240).map { i -> Resident(i,names[rng.nextInt(names.size)]+"·"+(i+1),regions[i%regions.size],
            16+rng.nextInt(54),65+rng.nextInt(36),12+rng.nextInt(24),rng.nextInt(40),jobs[rng.nextInt(jobs.size)],goals[rng.nextInt(goals.size)]) }.toMutableList()
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
        val markets=markets(p)
        val events=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]")); (0 until a.length()).map { a.getString(it) }.toMutableList() }.getOrDefault(mutableListOf())
        repeat(days.coerceAtLeast(1)) {
            day++
            val rng=Random(day*7919L+17L)
            applyLorePulse(day,p,pop,markets,events,rng)
            applyLoreChainStage(day,p,pop,markets,events,rng)
            val newborns=mutableListOf<Resident>()
            var nextId=pop.maxOfOrNull { it.id }?.plus(1) ?: p.getInt(KEY_NEXT_ID,240)
            pop.filter { it.alive }.forEach { r ->
                r.food=(r.food-1).coerceAtLeast(0)
                if(r.food==0) r.health=(r.health-2).coerceAtLeast(0)
                if(rng.nextInt(100)<(42+r.skill.coerceAtMost(15))) when(r.job) {
                    "农夫","采集者" -> {r.food+=2+rng.nextInt(3);r.coins++;markets[r.region]?.let { it.food+=2;it.wealth++ };r.skill=(r.skill+1).coerceAtMost(100)}
                    "商贩" -> if(r.food>1){r.food--;r.coins+=2;markets[r.region]?.let { if(it.food>0)it.food--;it.wealth+=2 };r.skill=(r.skill+1).coerceAtMost(100)}else r.food++
                    "工匠" -> {r.coins+=2;markets[r.region]?.let { it.wealth+=2 };r.skill=(r.skill+1).coerceAtMost(100)}
                    "护卫" -> {r.coins++;if(rng.nextInt(100)<4)r.health=(r.health-3).coerceAtLeast(0)}
                    else -> {if(r.coins>0)r.coins--;if(rng.nextInt(100)<20)r.food++}
                }
                if(day%360L==0L) r.age++
                if(day%90L==0L && r.alive && r.skill>=10 && r.job=="学徒") {
                    r.job="工匠"
                    events.add("第 $day 日：${r.name}在${r.region}完成学徒阶段，成为工匠。")
                }
                if(day%90L==0L && r.alive && r.skill>=25 && r.job=="旅人") {
                    r.job="护卫"
                    events.add("第 $day 日：${r.name}在${r.region}积累经验，转为护卫。")
                }
                if(day%90L==0L && r.alive && r.skill>=50 && r.job=="采集者") {
                    r.job="商贩"
                    events.add("第 $day 日：${r.name}在${r.region}凭经验开始经营贸易。")
                }
                if(day%30L==0L && r.alive && r.partnerId<0 && r.age in 20..35 && rng.nextInt(1000)<2) {
                    val candidate=pop.firstOrNull { it.alive && it.id!=r.id && it.region==r.region && it.partnerId<0 && it.age in 20..35 }
                    if(candidate!=null) { r.partnerId=candidate.id;candidate.partnerId=r.id
                        events.add("第 $day 日：${r.region}的${r.name}与${candidate.name}建立伴侣关系。") }
                }
                if(day%30L==0L && r.partnerId>=0 && r.id<r.partnerId && r.age in 20..38 && r.familyCount<3 && rng.nextInt(1000)<3) {
                    r.familyCount++
                    val mate=pop.firstOrNull { it.id==r.partnerId && it.alive }
                    if(mate!=null) mate.familyCount=r.familyCount
                    val id=nextId++
                    val child=Resident(id,names[rng.nextInt(names.size)]+"·"+id,r.region,0,100,3,0,"学徒","成长与学习")
                    newborns.add(child)
                    events.add("第 $day 日：${r.region}的${r.name}家庭迎来新生儿${child.name}。")
                }
                if(r.alive && r.food<=0 && day%30L==0L && rng.nextInt(100)<12) {
                    val next=regions[(regions.indexOf(r.region)+1)%regions.size]
                    r.region=next
                    events.add("第 $day 日：${r.name}因生计压力迁往${next}。")
                }
                if(r.health<=0 || (r.age>78 && rng.nextInt(100)<1) || (r.age>95 && rng.nextInt(100)<12)) {
                    r.alive=false
                    events.add("第 $day 日：${r.name}（${r.region}，${r.age}岁，${r.job}）去世；人生记录留存。")
                    if(r.partnerId>=0) pop.firstOrNull { it.id==r.partnerId }?.partnerId=-1
                }
                if(r.alive && r.food==0 && rng.nextInt(100)<3) events.add("第 $day 日：${r.name}在${r.region}遭遇食物短缺，健康恶化。")
                if(r.alive && rng.nextInt(1000)<2) events.add("第 $day 日：${r.name}在${r.region}达成「${r.goal}」，开始追寻新的方向。")
            }
            pop.addAll(newborns)
            regions.forEach { region ->
                val regionalPopulation=pop.count { it.alive && it.region==region }
                val market=markets[region]
                if(market!=null) market.food=(market.food-regionalPopulation/4).coerceAtLeast(0)
            }
            if(day%30L==0L) {
                regions.forEach { region ->
                    val count=pop.count { it.alive && it.region==region }
                    val market=markets[region] ?: return@forEach
                    val price=if(market.food<100) "高" else if(market.food>500) "低" else "稳定"
                    events.add("第 $day 日市场：$region，人口$count，粮食库存${market.food}，财富指数${market.wealth}，粮价$price。")
                }
            }
            if(day%90L==0L) events.add("第 $day 日：季节转变，地区居民面对环境压力。")
            if(day%360L==0L) events.add("第 $day 日：新年，居民年龄与人生阶段变化。")
        }
        while(events.size>1200)events.removeAt(0)
        savePeople(p,pop)
        saveMarkets(p,markets)
        val arr=JSONArray();events.forEach{arr.put(it)}
        p.edit().putInt(KEY_NEXT_ID,nextId)
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