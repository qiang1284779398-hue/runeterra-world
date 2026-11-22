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
    private const val KEY_MAGIC="magic_regions"
    private fun magicState(p:android.content.SharedPreferences):MutableMap<String,Int>{
        val raw=p.getString(KEY_MAGIC,null)
        if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).associate{a.getJSONObject(it).optString("region") to a.getJSONObject(it).optInt("level",10)}.toMutableMap()}}.getOrNull()?.let{if(it.keys.containsAll(regions))return it}
        return regions.associateWith{region->when(region){"艾欧尼亚","巨神峰","以绪塔尔"->65;"恕瑞玛"->45;"弗雷尔卓德"->35;else->20}}.toMutableMap().also{saveMagic(p,it)}
    }
    private fun saveMagic(p:android.content.SharedPreferences,m:Map<String,Int>){
        p.edit().putString(KEY_MAGIC,JSONArray().apply{m.forEach{(region,level)->put(JSONObject().put("region",region).put("level",level))}}.toString()).apply()
    }
    private fun tickMagic(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%90L!=0L)return
        val levels=magicState(p)
        regions.forEach{region->
            val level=levels[region]?:20
            val change=rng.nextInt(11)-5
            levels[region]=(level+change).coerceIn(0,100)
            if(levels[region]>=80 && rng.nextInt(100)<12){
                val residents=pop.filter{it.alive&&it.region==region}.shuffled(rng).take(1+rng.nextInt(3))
                residents.forEach{it.health=(it.health+rng.nextInt(8)-3).coerceIn(0,100)}
                markets[region]?.let{it.food=(it.food-rng.nextInt(20)).coerceAtLeast(0)}
                events.add("【魔法与符文·模拟】第 $day 日：${region}出现强烈魔法波动，影响${residents.size}名居民，区域魔法强度${levels[region]}。")
            }else if(levels[region]<=15 && rng.nextInt(100)<10){
                events.add("【魔法与符文·模拟】第 $day 日：${region}魔法活动趋于沉寂，相关研究与仪式难以开展。")
            }
        }
        saveMagic(p,levels)
    }
    private const val KEY_HEROES="heroes_state"
    private data class HeroAgent(val name:String,val region:String,val role:String,var status:String,var goal:String,var influence:Int)
    private val heroSeeds=listOf(
        HeroAgent("盖伦","德玛西亚","战士","活跃","维护德玛西亚秩序",70),
        HeroAgent("拉克丝","德玛西亚","法师","隐藏身份","保护秘密与自由",65),
        HeroAgent("卡特琳娜","诺克萨斯","刺客","行动中","执行家族与帝国任务",70),
        HeroAgent("艾瑞莉娅","艾欧尼亚","抵抗者","活动","守护艾欧尼亚",80),
        HeroAgent("阿卡丽","艾欧尼亚","刺客","游走","追寻自己的道路",65),
        HeroAgent("厄运小姐","比尔吉沃特","船长","掌权","掌控港口与海上势力",70),
        HeroAgent("瑟庄妮","弗雷尔卓德","部族首领","活动","带领凛冬之爪",70),
        HeroAgent("凯莎","恕瑞玛","虚空战士","巡行","抵御虚空威胁",65),
        HeroAgent("杰斯","皮尔特沃夫","发明家","研究","守护城市未来",60),
        HeroAgent("维克托","祖安","机械先驱","研究","推动进化理念",65)
    )
    private fun heroAgents(p:android.content.SharedPreferences):MutableList<HeroAgent>{
        val raw=p.getString(KEY_HEROES,null)
        if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).map{a.getJSONObject(it)}.map{HeroAgent(it.optString("name"),it.optString("region"),it.optString("role"),it.optString("status"),it.optString("goal"),it.optInt("influence",50))}.toMutableList()}}.getOrNull()?.let{return it}
        return heroSeeds.toMutableList().also{saveHeroes(p,it)}
    }
    private fun saveHeroes(p:android.content.SharedPreferences,heroes:List<HeroAgent>){
        p.edit().putString(KEY_HEROES,JSONArray().apply{heroes.forEach{put(JSONObject().put("name",it.name).put("region",it.region).put("role",it.role).put("status",it.status).put("goal",it.goal).put("influence",it.influence))}}.toString()).apply()
    }
    private fun tickHeroes(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val agents=heroAgents(p)
        agents.forEach{h->
            val regionResidents=pop.filter{it.alive&&it.region==h.region}
            if(rng.nextInt(100)<18){
                val target=regionResidents.randomOrNull()
                if(target!=null){
                    when(h.name){
                        "盖伦"->{target.health=(target.health+3).coerceAtMost(100);h.status="巡逻"}
                        "拉克丝"->{target.health=(target.health+2).coerceAtMost(100);h.status="秘密援助"}
                        "艾瑞莉娅"->{target.health=(target.health+2).coerceAtMost(100);h.status="抵抗行动"}
                        "厄运小姐"->{target.coins+=2;h.status="港口行动"}
                        "杰斯"->{markets[h.region]?.let{it.wealth+=5};h.status="技术研究"}
                        "维克托"->{markets[h.region]?.let{it.wealth+=3};h.status="技术实验"}
                        else->{h.status="行动中"}
                    }
                    events.add("【英雄行动·模拟】第 $day 日：${h.name}在${h.region}执行「${h.goal}」，影响居民${target.name}。")
                }else h.status="行动中"
            }
        }
        saveHeroes(p,agents)
    }
    private const val KEY_FACTIONS="factions_state"
    private data class Faction(var name:String,var leader:String,var military:Int,var stability:Int,var treasury:Int,var territory:MutableList<String>)
    private fun factionStates(p:android.content.SharedPreferences):MutableMap<String,Faction> {
        val raw=p.getString(KEY_FACTIONS,null)
        if(raw!=null) runCatching {
            val a=JSONArray(raw);val map=mutableMapOf<String,Faction>()
            for(i in 0 until a.length()){val j=a.getJSONObject(i);val lands=j.optJSONArray("territory")?:JSONArray();val list=(0 until lands.length()).map{lands.getString(it)}.toMutableList();map[j.optString("name")]=Faction(j.optString("name"),j.optString("leader"),j.optInt("military",100),j.optInt("stability",60),j.optInt("treasury",500),list)}
            if(map.size>=regions.size)return map
        }
        val initial=regions.associate { r-> Faction(r,"地方统治者",80+Random(r.hashCode().toLong()).nextInt(220),55+Random(r.hashCode().toLong()+1).nextInt(35),500,r.let{mutableListOf(it)}) }.toMutableMap()
        saveFactions(p,initial);return initial
    }
    private fun saveFactions(p:android.content.SharedPreferences,map:Map<String,Faction>){
        p.edit().putString(KEY_FACTIONS,JSONArray().apply{map.values.forEach{f->put(JSONObject().put("name",f.name).put("leader",f.leader).put("military",f.military).put("stability",f.stability).put("treasury",f.treasury).put("territory",JSONArray(f.territory)))}}.toString()).apply()
    }
    private fun tickFactionGovernance(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val states=factionStates(p)
        states.values.forEach { f ->
            val count=pop.count{it.alive&&it.region in f.territory}
            val marketWealth=f.territory.sumOf{markets[it]?.wealth?:0}
            f.treasury=(f.treasury+count/5+marketWealth/1000-rng.nextInt(8)).coerceAtLeast(0)
            f.stability=(f.stability+(if(count>0)1 else -2)+(if(f.treasury<150) -3 else 1)).coerceIn(0,100)
            f.military=(f.military+if(f.treasury>800 && rng.nextInt(100)<10)5 else 0).coerceAtMost(10000)
            if(f.stability<25 && rng.nextInt(100)<12){
                events.add("【政局】第 $day 日：${f.name}局势动荡，财政${f.treasury}，稳定度${f.stability}；出现抗议、权力斗争或地方自治迹象。")
                val region=f.territory.randomOrNull()
                if(region!=null)pop.filter{it.alive&&it.region==region}.take(3).forEach{it.health=(it.health-1).coerceAtLeast(0)}
            }
        }
        saveFactions(p,states)
    }
    private const val KEY_DIPLO="diplomacy"
    private data class PairTension(val a:String,val b:String,var tension:Int,var war:Boolean=false,var aWins:Int=0,var bWins:Int=0)
    private val factionRegions=mapOf("德玛西亚" to "德玛西亚","诺克萨斯" to "诺克萨斯","艾欧尼亚" to "艾欧尼亚","皮尔特沃夫" to "皮尔特沃夫","祖安" to "祖安","弗雷尔卓德" to "弗雷尔卓德","恕瑞玛" to "恕瑞玛","巨神峰" to "巨神峰","比尔吉沃特" to "比尔吉沃特","以绪塔尔" to "以绪塔尔")
    private fun diplomacy(p:android.content.SharedPreferences):MutableList<PairTension> {
        val raw=p.getString(KEY_DIPLO,null)
        if(raw!=null) runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { PairTension(it.optString("a"),it.optString("b"),it.optInt("tension",10),it.optBoolean("war",false),it.optInt("aWins",0),it.optInt("bWins",0)) }.toMutableList() } }.getOrNull()?.let { if(it.isNotEmpty()) return it }
        return mutableListOf(PairTension("诺克萨斯","艾欧尼亚",72),PairTension("诺克萨斯","德玛西亚",58),PairTension("皮尔特沃夫","祖安",65),PairTension("弗雷尔卓德","诺克萨斯",32),PairTension("恕瑞玛","诺克萨斯",28),PairTension("比尔吉沃特","诺克萨斯",24)).also { saveDiplomacy(p,it) }
    }
    private fun saveDiplomacy(p:android.content.SharedPreferences,relations:List<PairTension>) {
        p.edit().putString(KEY_DIPLO,JSONArray().apply { relations.forEach { put(JSONObject().put("a",it.a).put("b",it.b).put("tension",it.tension).put("war",it.war).put("aWins",it.aWins).put("bWins",it.bWins)) } }.toString()).apply()
    }
    private fun tickDiplomacy(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        if(day%30L!=0L)return
        val relations=diplomacy(p)
        relations.forEach { rel ->
            if(!rel.war) rel.tension=(rel.tension+(rng.nextInt(7)-3)).coerceIn(0,100)
            if(!rel.war && rel.tension>=85 && rng.nextInt(100)<18) {
                rel.war=true
                events.add("【模拟外交】第 $day 日：${rel.a}与${rel.b}冲突升级为战争。")
            } else if(rel.war && rng.nextInt(100)<4) {
                rel.war=false; rel.tension=(rel.tension-35).coerceAtLeast(20)
                events.add("【模拟外交】第 $day 日：${rel.a}与${rel.b}暂时停战，局势仍不稳定。")
            }
            if(rel.war) {
                val factions=factionStates(p)
                val attacker=factions[rel.a]
                val defender=factions[rel.b]
                if(attacker!=null && defender!=null) {
                    val powerA=attacker.military+rng.nextInt(100)
                    val powerB=defender.military+rng.nextInt(100)
                    attacker.military=(attacker.military-(2+rng.nextInt(8))).coerceAtLeast(0)
                    defender.military=(defender.military-(2+rng.nextInt(8))).coerceAtLeast(0)
                    attacker.treasury=(attacker.treasury-(8+rng.nextInt(18))).coerceAtLeast(0)
                    defender.treasury=(defender.treasury-(8+rng.nextInt(18))).coerceAtLeast(0)
                    if(powerA>=powerB) {
                        rel.aWins++
                        defender.stability=(defender.stability-2).coerceAtLeast(0)
                        events.add("【战场结算·模拟】第 $day 日：${rel.a}在与${rel.b}的交战中取得局部胜利（${rel.aWins}比${rel.bWins}）。")
                    } else {
                        rel.bWins++
                        attacker.stability=(attacker.stability-2).coerceAtLeast(0)
                        events.add("【战场结算·模拟】第 $day 日：${rel.b}击退${rel.a}的进攻（${rel.aWins}比${rel.bWins}）。")
                    }
                    val winner=when { rel.aWins>=3 -> rel.a; rel.bWins>=3 -> rel.b; else -> null }
                    if(winner!=null) {
                        val loser=if(winner==rel.a) rel.b else rel.a
                        val losingFaction=factions[loser]
                        val winningFaction=factions[winner]
                        val lostRegion=losingFaction?.territory?.firstOrNull()
                        if(lostRegion!=null && winningFaction!=null && losingFaction!=null) {
                            losingFaction.territory.remove(lostRegion)
                            if(lostRegion !in winningFaction.territory) winningFaction.territory.add(lostRegion)
                            events.add("【领土变化·模拟】第 $day 日：${winner}取得战局优势，${lostRegion}的控制权转移；这属于本模拟世界的历史分支。")
                        }
                        rel.war=false
                        rel.tension=35
                        rel.aWins=0
                        rel.bWins=0
                        events.add("【战争结束·模拟】第 $day 日：${winner}与${loser}结束本轮战争，双方开始承受战后重建压力。")
                    }
                }
                if(rng.nextInt(100)<20) {
                    val regionA=factionRegions[rel.a] ?: rel.a; val regionB=factionRegions[rel.b] ?: rel.b
                    listOf(regionA,regionB).forEach { region ->
                        val victims=pop.filter { it.alive && it.region==region }.shuffled(rng).take(1+rng.nextInt(4))
                        victims.forEach { r -> r.health=(r.health-5-rng.nextInt(12)).coerceAtLeast(0) }
                        markets[region]?.let { it.food=(it.food-15-rng.nextInt(30)).coerceAtLeast(0) }
                        if(victims.isNotEmpty()) events.add("【战争损害】第 $day 日：${region}遭冲突波及，${victims.size}名居民受伤，粮食储备下降。")
                    }
                }
                saveFactions(p,factions)
            }
        }
        saveDiplomacy(p,relations)
    }
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
    // Regional specializations are simulation modifiers inspired by lore, not canonical game stats.
    private fun foodBonus(region:String):Int = when(region) {
        "德玛西亚" -> 1
        "弗雷尔卓德" -> 0
        "恕瑞玛" -> -1
        "比尔吉沃特" -> -1
        "以绪塔尔" -> 1
        else -> 0
    }
    private fun craftBonus(region:String):Int = when(region) {
        "皮尔特沃夫","祖安" -> 2
        "艾欧尼亚" -> 1
        else -> 0
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
            tickDiplomacy(day,p,pop,markets,events,rng)
            tickFactionGovernance(day,p,pop,markets,events,rng)
            tickHeroes(day,p,pop,markets,events,rng)
            tickMagic(day,p,pop,markets,events,rng)
            val newborns=mutableListOf<Resident>()
            var nextId=pop.maxOfOrNull { it.id }?.plus(1) ?: p.getInt(KEY_NEXT_ID,240)
            pop.filter { it.alive }.forEach { r ->
                r.food=(r.food-1).coerceAtLeast(0)
                if(r.food==0) r.health=(r.health-2).coerceAtLeast(0)
                if(rng.nextInt(100)<(42+r.skill.coerceAtMost(15))) when(r.job) {
                    "农夫","采集者" -> {r.food+=(2+rng.nextInt(3)+foodBonus(r.region)).coerceAtLeast(1);r.coins++;markets[r.region]?.let { it.food+=(2+foodBonus(r.region)).coerceAtLeast(1);it.wealth++ };r.skill=(r.skill+1).coerceAtMost(100)}
                    "商贩" -> if(r.food>1){r.food--;r.coins+=2;markets[r.region]?.let { if(it.food>0)it.food--;it.wealth+=2 };r.skill=(r.skill+1).coerceAtMost(100)}else r.food++
                    "工匠" -> {r.coins+=2+craftBonus(r.region);markets[r.region]?.let { it.wealth+=2+craftBonus(r.region) };r.skill=(r.skill+1).coerceAtMost(100)}
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