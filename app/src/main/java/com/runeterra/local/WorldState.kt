package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

data class WorldSnapshot(val day: Long, val residents: Int, val events: List<String>, val running: Boolean)

private data class Resident(
    val id: Int, var name: String, var region: String, var age: Int, var health: Int,
    var food: Int, var coins: Int, var job: String, var goal: String,
    var skill: Int = 1, var partnerId: Int = -1, var familyCount: Int = 0, var alive: Boolean = true,
    var history: MutableList<String> = mutableListOf(),
    var parentIds: MutableList<Int> = mutableListOf(),
    var childIds: MutableList<Int> = mutableListOf(),
    var friendIds: MutableList<Int> = mutableListOf(),
    var mentorId: Int = -1,
    var studentIds: MutableList<Int> = mutableListOf(),
    var settlement: String = "",
     var travelOrigin: String = "",
     var travelDestination: String = "",
     var travelDaysRemaining: Int = 0,
     var travelPurpose: String = "",
     var jobBeforeTravel: String = "",
     var householdId: Int = -1,
     var lineage: String = "人类",
     var lifespanYears: Int = 95,
     var storyRecords: MutableList<String> = mutableListOf()
) {
    fun json() = JSONObject().put("id",id).put("name",name).put("region",region).put("age",age)
        .put("health",health).put("food",food).put("coins",coins).put("job",job).put("goal",goal)
        .put("skill",skill).put("partnerId",partnerId).put("familyCount",familyCount).put("alive",alive)
        .put("history",JSONArray(history)).put("parentIds",JSONArray(parentIds)).put("childIds",JSONArray(childIds))
        .put("friendIds",JSONArray(friendIds)).put("mentorId",mentorId).put("studentIds",JSONArray(studentIds)).put("settlement",settlement).put("travelOrigin",travelOrigin).put("travelDestination",travelDestination).put("travelDaysRemaining",travelDaysRemaining).put("travelPurpose",travelPurpose).put("jobBeforeTravel",jobBeforeTravel).put("householdId",householdId).put("lineage",lineage).put("lifespanYears",lifespanYears).put("storyRecords",JSONArray(storyRecords))
    companion object {
        fun from(j: JSONObject) = Resident(j.optInt("id"),j.optString("name"),j.optString("region"),
            j.optInt("age",18),j.optInt("health",100),j.optInt("food",5),j.optInt("coins",10),
            j.optString("job","采集者"),j.optString("goal","谋求安稳"),j.optInt("skill",1),
            j.optInt("partnerId",-1),j.optInt("familyCount",0),j.optBoolean("alive",true),
            (0 until (j.optJSONArray("history")?.length() ?: 0)).map { j.getJSONArray("history").getString(it) }.toMutableList(),
            (0 until (j.optJSONArray("parentIds")?.length() ?: 0)).map { j.getJSONArray("parentIds").getInt(it) }.toMutableList(),
            (0 until (j.optJSONArray("childIds")?.length() ?: 0)).map { j.getJSONArray("childIds").getInt(it) }.toMutableList(),
            (0 until (j.optJSONArray("friendIds")?.length() ?: 0)).map { j.getJSONArray("friendIds").getInt(it) }.toMutableList(),
            j.optInt("mentorId",-1),
            (0 until (j.optJSONArray("studentIds")?.length() ?: 0)).map { j.getJSONArray("studentIds").getInt(it) }.toMutableList(),
            j.optString("settlement",""),j.optString("travelOrigin",""),j.optString("travelDestination",""),j.optInt("travelDaysRemaining",0),j.optString("travelPurpose",""),j.optString("jobBeforeTravel",""),j.optInt("householdId",-1),j.optString("lineage","人类"),j.optInt("lifespanYears",95),(0 until (j.optJSONArray("storyRecords")?.length() ?: 0)).map { j.getJSONArray("storyRecords").getString(it) }.toMutableList())
    }
}

object WorldEngine {
    private const val PREFS="runeterra_world"
    // Versioned factual life packets: snapshots, event classification and contemporaneous relationship state.
    private fun recordLife(r:Resident,day:Long,detail:String,role:String="individual_transition"){
        r.history.add("第${day}日：${detail}")
        val type=when {
            detail.contains("出生") || detail.contains("孩子") || detail.contains("家庭") || detail.contains("伴侣") || detail.contains("父母") || detail.contains("子女") -> "家庭与关系"
            detail.contains("去世") || detail.contains("离世") -> "死亡与人生终点"
            detail.contains("迁") || detail.contains("旅途") || detail.contains("转至") -> "迁徙与居住"
            detail.contains("职业") || detail.contains("工匠") || detail.contains("学徒") || detail.contains("转行") || detail.contains("谋生") -> "职业与技能"
            detail.contains("战争") || detail.contains("战线") || detail.contains("停战") || detail.contains("入侵") -> "战争与政治"
            detail.contains("粮食") || detail.contains("口粮") || detail.contains("市场") || detail.contains("积蓄") || detail.contains("经济") -> "生计与经济"
            detail.contains("魔法") || detail.contains("灵性") || detail.contains("黑雾") || detail.contains("星界") -> "魔法与超自然"
            detail.contains("生态") || detail.contains("污染") || detail.contains("水源") || detail.contains("植被") -> "环境与生态"
            else -> "个人经历与社会变化"
        }
        val stage=when { r.age<6 -> "幼年"; r.age<13 -> "童年"; r.age<18 -> "少年"; r.age<30 -> "青年"; r.age<50 -> "成年"; r.age<70 -> "中年"; else -> "晚年" }
        val sequence=r.storyRecords.size+1
        val relations=JSONObject().put("partnerId",r.partnerId).put("parentIds",JSONArray(r.parentIds))
            .put("childIds",JSONArray(r.childIds)).put("friendIds",JSONArray(r.friendIds))
            .put("mentorId",r.mentorId).put("studentIds",JSONArray(r.studentIds)).put("householdId",r.householdId)
        val packet=JSONObject().put("schemaVersion",2).put("eventId","life-${r.id}-${day}-${sequence}")
            .put("sequence",sequence).put("day",day).put("age",r.age).put("lifeStage",stage).put("eventType",type)
            .put("region",r.region).put("settlement",r.settlement).put("job",r.job)
            .put("health",r.health).put("skill",r.skill).put("coins",r.coins).put("food",r.food).put("alive",r.alive)
            .put("detail",detail).put("residentRole",role).put("travelOrigin",r.travelOrigin).put("travelDestination",r.travelDestination)
            .put("travelDaysRemaining",r.travelDaysRemaining).put("travelPurpose",r.travelPurpose)
            .put("relationships",relations)
            .put("cause","由模拟状态变化或已记录的世界事件触发；具体因果未保存时不得自行补全")
            .put("dialogueRecorded",false).put("psychologyRecorded",false)
        r.storyRecords.add(packet.toString())
        while(r.storyRecords.size>5000) r.storyRecords.removeAt(0)
    }
    // No random bystander sampling: every resident whose region and livelihood match a regional impact receives the record.
    private fun recordLocalStory(pop:List<Resident>,day:Long,region:String,detail:String,rng:Random,limit:Int=3,roles:Set<String> = emptySet()) {
        pop.filter { it.alive && it.region==region && (roles.isEmpty() || it.job in roles) }
            .forEach { recordLife(it,day,detail,if(roles.isEmpty()) "regional_context_affected" else "livelihood_affected") }
    }
    private const val KEY_DAY="day"
    private const val KEY_RNG_STATE="world_rng_state"
    private const val KEY_ERA="world_era"
    private const val KEY_IONIA_INVASION_STARTED="ionia_invasion_started"
    private const val KEY_IONIA_INVASION_DAY="ionia_invasion_day"
    private const val KEY_IONIA_WAR_ENDED="ionia_war_ended"
    private const val KEY_IONIA_PLACIDIUM="ionia_placidium_front"
    private const val KEY_SWAIN_COUP="noxus_swain_coup"
    private const val KEY_SWAIN_DISGRACED="noxus_swain_disgraced"
    private const val KEY_SWAIN_DISGRACED_DAY="noxus_swain_disgraced_day"
    private const val KEY_LORE_TRIGGER_DAYS="lore_trigger_days"
    private const val ERA_PRE_IONIA="pre_ionia_invasion"
    private const val KEY_EVENTS="events"
    private const val KEY_RUNNING="running"
    private const val KEY_PEOPLE="people"
    private const val KEY_NEXT_ID="next_id"
    private const val KEY_MARKETS="markets"
    private const val KEY_CARAVANS="caravans"
    private const val KEY_MAGIC="magic_regions"
    private data class RegionalLore(val name:String,val political:String,val magic:String,val forces:String,val boundary:String)
    // Official setting anchors; future simulation is a branch history, not a rewrite of canon.
    private val regionalLore=listOf(
        RegionalLore("德玛西亚","王室君主制与贵族家族、边境领主并存","禁魔制度与禁魔石影响魔法的公开使用，不代表当地没有魔法","嘉文三世在位；王室、冕卫、搜魔人、贵族与法师社群围绕秩序和魔法管制互动","禁魔石抑制魔法，不是消灭世界中的魔法"),
        RegionalLore("诺克萨斯","由皇帝博拉姆·达克威尔统治的跨地域扩张帝国；军方、宫廷与秘密势力角力","帝国倾向于利用魔法作为力量，具体政策服务于军政目标","皇帝宫廷、诺克萨斯军团、旧贵族、地方总督与黑色玫瑰","诺克萨斯不是单一民族国家，征服与吸纳并存；崔法利议会尚未建立"),
        RegionalLore("艾欧尼亚","众多省地、社群与传统构成的群岛，没有统一中央政权","物质领域与精神领域边界较薄，灵性、自然与魔法交织","地方自治、武道寺院、各省民兵、均衡教派与地方领袖","不能写成高度集权的单一国家；诺克萨斯入侵尚未发生"),
        RegionalLore("皮尔特沃夫","议会、贸易家族与精英阶层主导的城邦","海克斯科技将魔法水晶与技术结合，不等同普通法术","议会、贸易家族、发明家、工坊与上层城市秩序","与祖安是相连的双城社会，不是互不相关的国家"),
        RegionalLore("祖安","位于皮尔特沃夫下方的地下城邦，企业、帮派与社区并立","炼金与化学科技塑造城市生活，污染和资源不平等长期存在","化学男爵、萤火、独立发明家、工人社区与底层居民","不能简化成只有反派帮派的城市"),
        RegionalLore("弗雷尔卓德","严酷冰原上的部族世界，联盟与战母权威不断变化","古老冰霜力量、冰裔、真冰与远古存在交织","阿瓦罗萨、凛冬之爪、霜卫、三姐妹传说与旧神信仰","不是统一王国；真冰稀有，部族会迁徙"),
        RegionalLore("恕瑞玛","古代帝国遗产、独立绿洲城邦与商路网络并存","飞升者、太阳圆盘、古代遗迹与虚空历史影响魔法格局","地方城邦、绿洲统治者、商路、古代遗迹守望者与沉睡的飞升者遗产","当前开局不视为阿兹尔已经复兴；不能把整个沙漠视为统一帝国"),
        RegionalLore("巨神峰","山地部族、朝圣者与宗教传统构成的区域","星界力量、星灵化身与登峰试炼影响少数人的命运","烈阳、皎月、拉阔尔人与登峰者","星灵不是普通地方官僚，登峰不是日常活动"),
        RegionalLore("比尔吉沃特","蓝焰群岛的港口城市，没有稳定中央政府","海怪、古老海洋信仰与布胡鲁传统影响沿海生活","船长、海盗、悬赏猎人、港口帮派与厄运小姐的权力格局","权力随船长和帮派斗争变化，不是常规王国"),
        RegionalLore("以绪塔尔","以绪奥肯为核心、长期对外隐匿的文明","元素魔法是知识、教育和社会秩序的核心","云塔（Yun Tal）、元素法师、边境居民与隐秘政策","不能写成普通魔法学院或公开贸易国家"),
        RegionalLore("班德尔城","精神领域中的约德尔人家园，通过入口与符文之地相连","精神领域环境使空间和时间体验异于常态","约德尔社群、班德尔入口与跨界旅者","不作为普通人类国家，不生成常规人类人口"),
        RegionalLore("暗影岛","福光岛灾变后被破败诅咒与黑雾侵蚀的区域","黑雾、亡灵与灵魂束缚是核心威胁，不是普通魔法天气","黑雾、亡灵、破败诅咒与抵抗侵蚀的力量","福光岛与赫利亚是历史遗址，不生成普通人类常住人口"),
        RegionalLore("虚空","跨越地区边界的异界威胁，不是符文之地上的普通国家","虚空侵蚀现实、吞噬生命并扭曲生态","虚空生物、监视者、虚空先知及抵御虚空的守护者","不得当作第十三个普通地区或常规外交国家")
    )
    fun timelineDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val day=p.getLong(KEY_DAY,1L)
        val stored=p.getString(KEY_ERA,null)
        val era=stored ?: if(day<=1L) ERA_PRE_IONIA else "legacy_unanchored"
        if(stored==null) p.edit().putString(KEY_ERA,era).apply()
        val phase=when(era) {
            ERA_PRE_IONIA -> "诺克萨斯第一次入侵艾欧尼亚之前"
            else -> "旧存档：未能确认历史起点；建议重置后从指定时代开始"
        }
        return listOf(
            "【当前历史锚点】\n$phase\n模拟纪年：第${day}日（不是官方公历年份）\n官方时间线定位：以诺克萨斯第一次入侵艾欧尼亚之前作为起点；官方背景没有给出可直接换算的统一公历日期。",
            "【历史事实与模拟分支的边界】\n福光岛灾变／破败之咒属于遥远的既往历史，不会在本次模拟中作为新发生的事件重演；暗影岛的黑雾与佛耶戈封印状态属于开局背景。\n诺克萨斯第一次入侵艾欧尼亚是未来可能发生的重大历史转折，必须由外交、军政、补给与组织条件共同促成。",
            "【后续事件原则】\n重大事件不按固定模拟日强制播放。事件触发需要满足前置条件；战事结束后才记录战后余波。模拟产生的结果属于本世界分支历史，不冒充官方正史。",
            "【存档提示】\n如果这是在旧版本中已经运行过的存档，单纯显示新历史锚点不会改写旧人物经历；如需严格从该时期开始，请使用重置世界建立新存档。"
        )
    }
    fun timelineStatus(context:Context):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return when(p.getString(KEY_ERA,null)) {
            ERA_PRE_IONIA -> "诺克萨斯第一次入侵艾欧尼亚之前"
            "legacy_unanchored" -> "旧存档：历史起点未确认"
            else -> "历史锚点未设定"
        }
    }
    fun regionalLoreDirectory():List<String> = regionalLore.map { lore ->
        "【${lore.name}】\n政治与社会：${lore.political}\n魔法与超自然：${lore.magic}\n核心力量：${lore.forces}\n模拟边界：${lore.boundary}\n说明：以上为世界设定锚点；模拟器生成的后续事件属于分支历史，不代表官方正史。"
    }
    private fun magicState(p:android.content.SharedPreferences):MutableMap<String,Int>{
        val raw=p.getString(KEY_MAGIC,null)
        if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).associate{a.getJSONObject(it).optString("region") to a.getJSONObject(it).optInt("level",10)}.toMutableMap()}}.getOrNull()?.let{if(it.isNotEmpty()){regions.filter{region->region !in it}.forEach{region->it[region]=when(region){"艾欧尼亚","巨神峰","以绪塔尔"->65;"恕瑞玛"->45;"弗雷尔卓德"->35;"班德尔城"->90;"暗影岛"->100;else->20}};saveMagic(p,it);return it}}
        return regions.associateWith{region->when(region){"艾欧尼亚","巨神峰","以绪塔尔"->65;"恕瑞玛"->45;"弗雷尔卓德"->35;"班德尔城"->90;"暗影岛"->100;else->20}}.toMutableMap().also{saveMagic(p,it)}
    }
    private fun saveMagic(p:android.content.SharedPreferences,m:Map<String,Int>){
        p.edit().putString(KEY_MAGIC,JSONArray().apply{m.forEach{(region,level)->put(JSONObject().put("region",region).put("level",level))}}.toString()).apply()
    }
    private fun tickMagic(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%90L!=0L)return
        val levels=magicState(p)
        regions.forEach{region->
            val level=levels[region]?:20
            val change=when(region) {
                "德玛西亚","以绪塔尔" -> rng.nextInt(5)-2
                "艾欧尼亚" -> rng.nextInt(13)-4
                "弗雷尔卓德" -> rng.nextInt(7)-3
                "恕瑞玛","巨神峰" -> rng.nextInt(9)-4
                "班德尔城","暗影岛" -> 0
                else -> rng.nextInt(11)-5
            }
            val newLevel=when(region) {
                "班德尔城" -> 90
                "暗影岛" -> 100
                else -> (level+change).coerceIn(0,100)
            }
            levels[region]=newLevel
            val surgeChance=when(region) {
                "艾欧尼亚" -> 18
                "巨神峰" -> 14
                "以绪塔尔" -> 10
                "恕瑞玛" -> 8
                "弗雷尔卓德" -> 6
                "暗影岛" -> 25
                "班德尔城" -> 0
                else -> 5
            }
            if(newLevel>=65 && rng.nextInt(100)<surgeChance) {
                val residents=pop.filter{it.alive&&it.region==region}.shuffled(rng).take(1+rng.nextInt(3))
                when(region) {
                    "艾欧尼亚" -> events.add("【精神领域异动·模拟】第 $day 日：艾欧尼亚物质与精神领域边界出现局部波动，地方灵性传统对此作出不同解释。")
                    "巨神峰" -> events.add("【星界征兆·模拟】第 $day 日：巨神峰周边出现异常天象或星界征兆，朝圣者与宗教团体开始关注。")
                    "以绪塔尔" -> events.add("【元素魔法·模拟】第 $day 日：以绪塔尔部分元素环境出现波动，元素法师与云塔相关机构加强观察。")
                    "恕瑞玛" -> events.add("【古代遗迹·模拟】第 $day 日：恕瑞玛某处古代遗迹或飞升者相关力量出现异动，商路与探索活动受到影响。")
                    "弗雷尔卓德" -> events.add("【远古冰霜·模拟】第 $day 日：弗雷尔卓德出现异常冰霜征兆，部族萨满将其与古老力量联系起来。")
                    "暗影岛" -> events.add("【黑雾侵袭·模拟】第 $day 日：暗影岛黑雾活跃程度上升，侵蚀范围扩大；这不是普通魔法天气。")
                    "班德尔城" -> events.add("【班德尔入口·模拟】第 $day 日：班德尔城与符文之地之间的入口发生短暂异动。")
                    else -> events.add("【魔法与符文·模拟】第 $day 日：${region}出现局部魔法异常，区域魔法活动强度${newLevel}。")
                }
                if(residents.isNotEmpty()) residents.forEach { recordLife(it,day,"身处${region}时遭遇魔法异动；异常现象引发当地居民对安全、信仰或研究的不同反应。") }
                if(region!="暗影岛" && region!="班德尔城" && residents.isNotEmpty() && rng.nextInt(100)<20) {
                    residents.forEach { it.health=(it.health+rng.nextInt(5)-2).coerceIn(0,100); recordLife(it,day,"魔法异动对身体状态造成短暂影响。") }
                }
            } else if(newLevel<=15 && rng.nextInt(100)<10) {
                events.add("【魔法与符文·模拟】第 $day 日：${region}魔法活动趋于沉寂，相关研究与仪式难以开展。")
            }
        }
        saveMagic(p,levels)
    }
    private const val KEY_HERO_BONDS="hero_relationships_state"
    private data class HeroBond(val a:String,val b:String,val basis:String,var trust:Int,var tension:Int,var lastEventDay:Long=0L)
    // Relationship baselines reflect established character lore; numeric values are simulation state, not canon facts.
    private val heroBondSeeds=listOf(
        HeroBond("盖伦","拉克丝","姐弟；共同面对德玛西亚禁魔制度与家族责任",92,18),
        HeroBond("斯维因","德莱厄斯","诺克萨斯军政体系中的两名重要人物，未来可能形成合作",58,24),
        HeroBond("斯维因","乐芙兰","帝国权力斗争；彼此提防的长期暗战",18,88),
        HeroBond("慎","劫","均衡教派中的师兄弟与理念差异；此时尚未发生影流分裂",72,18),
        HeroBond("艾希","瑟庄妮","弗雷尔卓德部族出身与未来道路存在差异；尚未发展为战母间的全面竞争",48,28),
        HeroBond("阿兹尔","泽拉斯","昔日主仆与友人；飞升仪式背叛造成宿敌关系",3,98),
        HeroBond("蕾欧娜","黛安娜","烈阳与皎月信仰冲突及个人历史",20,84),
        HeroBond("凯特琳","蔚","皮尔特沃夫执法搭档；彼此信任但行事方式不同",86,22),
        HeroBond("蔚","金克丝","姐妹关系；创伤与分离造成复杂情感",48,72),
        HeroBond("艾瑞莉娅","斯维因","诺克萨斯入侵前双方尚无直接战争对立；关系将随历史事件演化",50,8),
        HeroBond("瑟庄妮","丽桑卓","凛冬之爪与霜卫之间的历史性敌意",10,92),
        HeroBond("阿卡丽","慎","师承与理念分歧；阿卡丽选择独立道路",42,48),
        HeroBond("艾克","金克丝","祖安旧日同伴；如今走上不同道路",38,58),
        HeroBond("厄运小姐","俄洛伊","比尔吉沃特权力现实与布胡鲁信仰之间的交集",54,28)
    )
    private const val KEY_HEROES="heroes_state"
    private data class HeroAgent(val name:String,val region:String,var role:String,var status:String,var goal:String,var influence:Int)
    private val heroSeeds=listOf(
        HeroAgent("盖伦","德玛西亚","战士","活跃","维护德玛西亚秩序",70),
        HeroAgent("拉克丝","德玛西亚","法师","隐藏身份","保护秘密与自由",65),
        HeroAgent("嘉文四世","德玛西亚","王位继承者","政治与军事责任","在王国危机中承担王室责任",72),
        HeroAgent("菲奥娜","德玛西亚","决斗家","家族事务","维护劳伦特家族荣誉并挑战贵族旧秩序",62),
        HeroAgent("薇恩","德玛西亚","猎魔人","追猎","追猎黑暗生物与威胁家园的怪物",58),
        HeroAgent("卡特琳娜","诺克萨斯","刺客","行动中","执行家族与帝国任务",70),
        HeroAgent("斯维因","诺克萨斯","诺克萨斯将领（尚未掌握帝国最高权力）","军政体系内的将领，尚未建立崔法利议会","通过军事与战略能力提升地位",76),
        HeroAgent("德莱厄斯","诺克萨斯","军团统帅","军事行动","以诺克萨斯军团推进帝国利益",76),
        HeroAgent("乐芙兰","诺克萨斯","秘密结社领袖","暗中布局","通过黑色玫瑰操纵帝国深层权力",82),
        HeroAgent("艾瑞莉娅","艾欧尼亚","艾欧尼亚地方居民与舞者","生活于家乡，尚未成为战争中的抵抗象征","守护家人与故乡的文化传统",45),
        HeroAgent("阿卡丽","艾欧尼亚","均衡教派年轻弟子","接受教派训练，尚未离开均衡教派独立行动","保护家园并学习忍术",42),
        HeroAgent("慎","艾欧尼亚","均衡教派继承人","在父亲与教派长者指导下修行，尚未继任暮光之眼","守护物质领域与精神领域之间的均衡",55),
        HeroAgent("劫","艾欧尼亚","均衡教派年轻弟子","在均衡教派中修行，尚未创建影流","学习力量并守护艾欧尼亚",48),
        HeroAgent("厄运小姐","比尔吉沃特","年轻赏金猎人与船员","尚未成为掌控港口的船长","在海港谋生并追查家族仇敌",42),
        HeroAgent("俄洛伊","比尔吉沃特","布胡鲁大祭司","履行信仰使命","传播娜迦卡波洛丝的意志并考验灵魂",68),
        HeroAgent("瑟庄妮","弗雷尔卓德","凛冬之爪部族成员（尚未确立战母地位）","在严酷冰原中磨炼生存与领导能力","维护部族生存与独立",48),
        HeroAgent("艾希","弗雷尔卓德","阿瓦罗萨部族成员（尚未成为战母）","在部族传统与未来道路之间成长","寻求部族间更稳定的生存与合作",48),
        HeroAgent("丽桑卓","弗雷尔卓德","霜卫领袖","守望冰封威胁","维持远古冰封秘密并阻止监视者苏醒",84),
        HeroAgent("阿兹尔","恕瑞玛","古代恕瑞玛皇帝（历史人物）","沉寂于古代遗迹与飞升者历史中的力量，尚未进入本模拟当代活动","其复兴是否发生取决于古代仪式、遗迹与相关势力条件",82),
        HeroAgent("泽拉斯","恕瑞玛","古代飞升者（历史遗留威胁）","沉寂于古代封印与遗迹相关历史中，尚未进入本模拟当代活动","寻求力量与摆脱束缚",78),
        HeroAgent("塔莉垭","恕瑞玛","岩石编织者","游历","保护家园与普通沙漠居民",62),
        HeroAgent("黛安娜","巨神峰","皎月教派相关人物","探索与冲突","追寻皎月力量并面对烈阳教派",64),
        HeroAgent("蕾欧娜","巨神峰","烈阳战士","守护信仰","维护烈阳教派与拉阔尔传统",66),
        HeroAgent("潘森","巨神峰","战争星灵宿主","抗争","以凡人意志对抗星界命运",70),
        HeroAgent("杰斯","皮尔特沃夫","发明家","研究","守护城市未来",60),
        HeroAgent("凯特琳","皮尔特沃夫","执法官","调查","维护皮尔特沃夫秩序并调查犯罪",62),
        HeroAgent("蔚","皮尔特沃夫","执法者","行动","以自己的方式维护双城秩序",60),
        HeroAgent("维克托","祖安","机械先驱","研究","推动进化理念",65),
        HeroAgent("金克丝","祖安","犯罪者","制造混乱","以不可预测的行动冲击双城权力秩序",62),
        HeroAgent("艾克","祖安","发明家与社区守护者","保护社区","守护祖安街区与萤火成员",64),
        HeroAgent("奇亚娜","以绪塔尔","元素法师与王族","争夺继承权","争取以绪塔尔王位与云塔认可",70),
        HeroAgent("妮蔻","以绪塔尔","瓦斯塔亚变形者","寻找归属","寻找同伴并保护瓦斯塔亚族群",58),
        HeroAgent("凯莎","恕瑞玛","虚空战士","巡行","抵御虚空威胁",65),
        HeroAgent("内瑟斯","恕瑞玛","飞升者与知识守护者","守护古代知识","阻止恕瑞玛历史与飞升者遗产被滥用",76),
        HeroAgent("雷克顿","恕瑞玛","飞升者战士","受疯狂与仇恨驱使","追寻内瑟斯并寻求复仇",70),
        HeroAgent("亚托克斯","恕瑞玛","暗裔","被封存的远古威胁","寻求挣脱武器封印并终结自身痛苦",88),
        HeroAgent("佛耶戈","暗影岛","破败之王","受封印的亡灵君王","执念于失去的王后，破败诅咒仍留下后果",82),
        HeroAgent("锤石","暗影岛","灵魂典狱长","黑雾中的操纵者","收集并折磨灵魂，扩大对生者的威胁",80),
        HeroAgent("瑞兹","符文之地","符文法师","隐秘巡行","寻找并封存世界符文，避免再次引发灾难",92),
        HeroAgent("奥恩","弗雷尔卓德","半神与锻造之神","隐居","远离凡人纷争，守护古老锻造力量",78),
        HeroAgent("沃利贝尔","弗雷尔卓德","半神与风暴之神","远古力量苏醒","维护原始荒野与古老信仰",82),
        HeroAgent("艾尼维亚","弗雷尔卓德","半神与冰霜之灵","守护冰原","在灾厄来临时守护弗雷尔卓德生命",82),
        HeroAgent("卑尔维斯","虚空","虚空女皇","异界扩张","在虚空深处扩张领域并威胁现实世界",90),
        HeroAgent("莫德凯撒","诺克萨斯","亡灵征服者","不朽领域中的潜在威胁","积蓄力量并谋求重返生者世界",86),
        HeroAgent("提莫","班德尔城","约德尔斥候","跨界巡行","在符文之地执行斥候任务并维持约德尔社群联系",68),
        HeroAgent("崔丝塔娜","班德尔城","约德尔炮手","守望边界","保护班德尔入口与约德尔同伴",68),
        HeroAgent("璐璐","班德尔城","约德尔魔法师","游历物质领域","以奇异魔法探索世界并帮助或捉弄旅者",62),
        HeroAgent("兰博","班德尔城","约德尔发明家","机械实验","以自制机甲证明自己的发明能力",58),
        HeroAgent("库奇","班德尔城","约德尔飞行员","空中巡逻","驾驶飞行器执行巡逻与运输任务",58),
        HeroAgent("维迦","班德尔城","约德尔法师","追求强大力量","在黑暗魔法与自我认知之间行动",66),
        HeroAgent("黑默丁格","皮尔特沃夫","约德尔科学家","科学研究","以科学研究推动皮尔特沃夫发展并警惕技术风险",72),
        HeroAgent("悠米","班德尔城","魔法猫与魔典守护者","寻找主人","借助魔典寻找诺拉并穿梭于不同领域",62)
    )
    private fun heroAgents(p:android.content.SharedPreferences):MutableList<HeroAgent>{
        val raw=p.getString(KEY_HEROES,null)
        if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).map{a.getJSONObject(it)}.map{HeroAgent(it.optString("name"),it.optString("region"),it.optString("role"),it.optString("status"),it.optString("goal"),it.optInt("influence",50))}.toMutableList()}}.getOrNull()?.let{loaded->
            val known=loaded.map{it.name}.toSet()
            heroSeeds.filter{it.name !in known}.forEach{loaded.add(it)}
            if(loaded.size!=known.size)saveHeroes(p,loaded)
            return loaded
        }
        return heroSeeds.toMutableList().also{saveHeroes(p,it)}
    }
    private fun saveHeroes(p:android.content.SharedPreferences,heroes:List<HeroAgent>){
        p.edit().putString(KEY_HEROES,JSONArray().apply{heroes.forEach{put(JSONObject().put("name",it.name).put("region",it.region).put("role",it.role).put("status",it.status).put("goal",it.goal).put("influence",it.influence))}}.toString()).apply()
    }
    private fun heroBonds(p:android.content.SharedPreferences):MutableList<HeroBond>{
        val raw=p.getString(KEY_HERO_BONDS,null)
        val loaded=if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).map{a.getJSONObject(it)}.map{HeroBond(it.optString("a"),it.optString("b"),it.optString("basis"),it.optInt("trust",50),it.optInt("tension",50),it.optLong("lastEventDay",0L))}.toMutableList()}}.getOrNull() else null
        val bonds=loaded?:mutableListOf()
        val known=bonds.map{it.a+"|"+it.b}.toSet()
        heroBondSeeds.filter{(it.a+"|"+it.b) !in known}.forEach{bonds.add(it.copy())}
        if(loaded==null || bonds.size!=known.size)saveHeroBonds(p,bonds)
        return bonds
    }
    private fun saveHeroBonds(p:android.content.SharedPreferences,bonds:List<HeroBond>){
        p.edit().putString(KEY_HERO_BONDS,JSONArray().apply{bonds.forEach{put(JSONObject().put("a",it.a).put("b",it.b).put("basis",it.basis).put("trust",it.trust).put("tension",it.tension).put("lastEventDay",it.lastEventDay))}}.toString()).apply()
    }
    private fun tickHeroBonds(day:Long,p:android.content.SharedPreferences,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val bonds=heroBonds(p)
        val factions=factionStates(p)
        val diplomacy=diplomacy(p)
        val groups=organizations(p)
        bonds.forEach{bond->
            val region=heroSeeds.firstOrNull{it.name==bond.a}?.region
            val localPressure=(factions[region]?.internalTension?:25)
            val hostilePair=bond.tension>=65
            val warPressure=if(setOf(bond.a,bond.b)==setOf("艾瑞莉娅","斯维因")) {
                diplomacy.firstOrNull{setOf(it.a,it.b)==setOf("诺克萨斯","艾欧尼亚")}?.let{if(it.war)5 else if(it.tension>=70)2 else 0}?:0
            } else 0
            val groupPressure=when {
                setOf(bond.a,bond.b)==setOf("慎","劫") -> if((groups.firstOrNull{it.id=="ionia_shadow_order"}?.influence?:0)>(groups.firstOrNull{it.id=="ionia_kinkou"}?.influence?:0))2 else 0
                setOf(bond.a,bond.b)==setOf("艾希","瑟庄妮") -> if((groups.firstOrNull{it.id=="freljord_winter_claw"}?.influence?:0)>70)2 else 0
                setOf(bond.a,bond.b)==setOf("阿兹尔","泽拉斯") -> 1
                else -> 0
            }
            val drift=rng.nextInt(3)-1
            if(hostilePair || warPressure>0) bond.tension=(bond.tension+drift+warPressure+groupPressure+(if(localPressure>=70)1 else 0)).coerceIn(0,100)
            else bond.trust=(bond.trust+drift+(if(localPressure<35)1 else 0)-(if(localPressure>=75)1 else 0)).coerceIn(0,100)
            if(bond.tension>=75) {
                when(setOf(bond.a,bond.b)) {
                    setOf("斯维因","乐芙兰") -> factions["诺克萨斯"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    setOf("慎","劫") -> factions["艾欧尼亚"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    setOf("艾希","瑟庄妮") -> factions["弗雷尔卓德"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    setOf("阿兹尔","泽拉斯") -> factions["恕瑞玛"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    setOf("蕾欧娜","黛安娜") -> factions["巨神峰"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    setOf("蔚","金克丝") -> {
                        factions["皮尔特沃夫"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                        factions["祖安"]?.let{it.internalTension=(it.internalTension+1).coerceAtMost(100)}
                    }
                }
            }
            val notable=(bond.tension>=95 || bond.trust<=5)
            if(notable && (bond.lastEventDay==0L || day-bond.lastEventDay>=180L)) {
                events.add("【人物关系变化·模拟】第 $day 日：${bond.a}与${bond.b}之间的关系因「${bond.basis}」持续承压；信任${bond.trust}，矛盾${bond.tension}。这是模拟分支中的关系状态，不改写官方既定经历。")
                bond.lastEventDay=day
            }
        }
        saveHeroBonds(p,bonds)
        saveFactions(p,factions)
    }
    private fun heroLineage(name:String):String=when(name){
        "提莫","崔丝塔娜","璐璐","兰博","库奇","维迦","黑默丁格" -> "约德尔人"
        "悠米" -> "魔法猫（非约德尔人）"
        "妮蔻" -> "瓦斯塔亚"
        "阿兹尔","泽拉斯","内瑟斯","雷克顿" -> "飞升者"
        "亚托克斯" -> "暗裔"
        "奥恩","沃利贝尔","艾尼维亚" -> "弗雷尔卓德半神"
        "卑尔维斯" -> "虚空存在"
        "佛耶戈","锤石","莫德凯撒" -> "亡灵／不朽领域相关存在"
        "黛安娜" -> "皎月星灵宿主"
        "蕾欧娜" -> "烈阳星灵宿主"
        "潘森" -> "人类宿主（曾承载战争星灵）"
        "凯莎" -> "人类（与虚空生物共生）"
        "瑟庄妮","艾希","丽桑卓" -> "冰裔（人类中的特殊个体）"
        else -> "人类或人类出身（具体个体背景见传记）"
    }
    fun heroDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return heroAgents(p).sortedBy{it.region}.map{
            "【${it.name}】\\n所在领域：${it.region}\\n族类／本质：${heroLineage(it.name)}\\n身份：${it.role}\\n当前状态：${it.status}\\n个人目标：${it.goal}\\n影响力指标：${it.influence}/100\\n说明：此处只记录世界模拟中的人物状态；神祇、半神、亡灵、暗裔与虚空存在不作为普通居民参与人口、家庭和职业系统。"
        }
    }
    fun heroRelationshipDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return heroBonds(p).sortedByDescending{it.tension}.map{
            "【${it.a} ↔ ${it.b}】\\n关系背景：${it.basis}\\n模拟信任：${it.trust}/100\\n模拟矛盾：${it.tension}/100\\n说明：关系背景取自英雄联盟人物设定；数值及后续变化是本世界的模拟状态，不代表官方数值或固定剧情。"
        }
    }
    private fun tickHeroes(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val agents=heroAgents(p)
        val groups=organizations(p)
        val factions=factionStates(p)
        agents.forEach { h ->
            if(h.name=="阿兹尔" || h.name=="泽拉斯") {
                if(h.status.startsWith("沉寂") || h.status.startsWith("古代恕瑞玛") || h.status.startsWith("古代飞升者")) return@forEach
            }
            // Independent actors make decisions on individual cadences, rather than a player-triggered random event roll.
            val identityHash=h.name.hashCode().toLong().let { if(it<0L) -it else it }
            val actionCycle=when { h.influence>=80 -> 1L; h.influence>=55 -> 2L; else -> 3L }
            if((day/30L+identityHash)%actionCycle==0L) {
                val group=when(h.name) {
                    "盖伦" -> groups.firstOrNull { it.id=="demacia_vanguard" }
                    "拉克丝" -> groups.firstOrNull { it.id=="demacia_mage_resistance" }
                    "厄运小姐" -> groups.firstOrNull { it.id=="bilgewater_captains" }
                    "瑟庄妮" -> groups.firstOrNull { it.id=="freljord_winter_claw" }
                    else -> null
                }
                when(h.name) {
                    "盖伦" -> {
                        group?.let { it.resources=(it.resources+1).coerceAtMost(100); it.publicSupport=(it.publicSupport+1).coerceAtMost(100) }
                        factions["德玛西亚"]?.let { it.stability=(it.stability+1).coerceAtMost(100) }
                        h.status="边境巡防与军事部署"
                    }
                    "拉克丝" -> {
                        group?.let { it.resources=(it.resources+1).coerceAtMost(100); it.publicSupport=(it.publicSupport+2).coerceAtMost(100) }
                        h.status="秘密保护受压迫的法师"
                    }
                    "卡特琳娜" -> {
                        factions["诺克萨斯"]?.let { it.internalTension=(it.internalTension+rng.nextInt(3)-1).coerceIn(0,100) }
                        h.status="执行秘密任务；具体行动不为外界所知"
                    }
                    "艾瑞莉娅" -> {
                        if(p.getBoolean(KEY_IONIA_INVASION_STARTED,false)) {
                            h.role="艾欧尼亚抵抗者"
                            h.goal="守护艾欧尼亚并组织地方抵抗"
                            h.status="诺克萨斯入侵后参与组织艾欧尼亚地方抵抗；具体经历由本世界事件生成"
                            h.influence=(h.influence+1).coerceAtMost(100)
                        } else h.status="生活于艾欧尼亚地方社群；尚未因诺克萨斯入侵而成为抵抗运动领袖"
                    }
                    "阿卡丽" -> {
                        h.status=if(p.getBoolean(KEY_IONIA_INVASION_STARTED,false)) "在战争与教派分歧中逐渐形成独立判断" else "接受均衡教派训练，尚未离开教派独立行动"
                    }
                    "厄运小姐" -> {
                        group?.let { it.influence=(it.influence+1).coerceAtMost(100); it.publicSupport=(it.publicSupport+1).coerceAtMost(100) }
                        h.status="争夺比尔吉沃特港口与航路影响力"
                    }
                    "瑟庄妮" -> {
                        h.status="在凛冬之爪部族中成长并参与生存行动；尚未确立战母统治地位"
                    }
                    "凯莎" -> {
                        h.status="追踪并阻止虚空威胁"
                        events.add("【虚空威胁·模拟】第 $day 日：凯莎在恕瑞玛相关区域追踪虚空活动；虚空仍是跨地区威胁，不构成普通领土势力。")
                    }
                    "杰斯" -> {
                        markets["皮尔特沃夫"]?.let { it.wealth=(it.wealth+2).coerceAtMost(100000) }
                        h.status="推进海克斯科技研究与城邦防务"
                    }
                    "维克托" -> {
                        markets["祖安"]?.let { it.wealth=(it.wealth+1).coerceAtMost(100000) }
                        h.status="推进机械进化研究，与祖安社会产生复杂影响"
                    }
                    "嘉文四世" -> {
                        factions["德玛西亚"]?.let { it.stability=(it.stability+1).coerceAtMost(100); it.internalTension=(it.internalTension-1).coerceAtLeast(0) }
                        h.status="处理王位继承与王国危机"
                    }
                    "菲奥娜" -> {
                        groups.firstOrNull { it.id=="demacia_crownguard" }?.let { it.influence=(it.influence+1).coerceAtMost(100) }
                        h.status="维护劳伦特家族荣誉并挑战贵族旧秩序"
                    }
                    "薇恩" -> h.status="追猎威胁德玛西亚民众的黑暗生物"
                    "斯维因" -> {
                        if(p.getBoolean(KEY_SWAIN_COUP,false)) {
                            h.role="崔法利议会统治者"
                            h.status="以远见与谋略维持崔法利议会秩序，同时防范黑色玫瑰与帝国内部权力角力"
                            factions["诺克萨斯"]?.let { it.stability=(it.stability+1).coerceAtMost(100) }
                        } else if(p.getBoolean(KEY_SWAIN_DISGRACED,false)) {
                            h.role="失势的诺克萨斯将领"
                            h.status="在艾欧尼亚战场遭到贬黜，公开声望受损；暗中寻找重返权力中心的机会"
                        } else h.status="作为诺克萨斯将领观察帝国军政局势；尚未建立崔法利议会或成为最高统治者"
                    }
                    "德莱厄斯" -> {
                        groups.firstOrNull { it.id=="noxus_legions" }?.let { it.resources=(it.resources+2).coerceAtMost(100); it.influence=(it.influence+1).coerceAtMost(100) }
                        h.status=if(p.getBoolean(KEY_SWAIN_COUP,false)) "作为崔法利议会的力量代表统领诺克萨斯军团" else "整顿军团并执行帝国军事任务"
                    }
                    "乐芙兰" -> {
                        groups.firstOrNull { it.id=="noxus_black_rose" }?.let { it.influence=(it.influence+2).coerceAtMost(100) }
                        factions["诺克萨斯"]?.let { it.internalTension=(it.internalTension+1).coerceAtMost(100) }
                        h.status="通过黑色玫瑰的秘密网络影响帝国政治"
                    }
                    "慎" -> {
                        groups.firstOrNull { it.id=="ionia_kinkou" }?.let { it.influence=(it.influence+1).coerceAtMost(100); it.publicSupport=(it.publicSupport+1).coerceAtMost(100) }
                        if(p.getBoolean(KEY_IONIA_PLACIDIUM,false)) {
                            h.role="暮光之眼"
                            h.status="在均衡教派遭受战争冲击后承担守护均衡的职责，与影流的分裂持续影响艾欧尼亚"
                        } else h.status="在教派长者指导下修行；尚未继任暮光之眼"
                    }
                    "劫" -> {
                        if(p.getBoolean(KEY_IONIA_PLACIDIUM,false)) {
                            val shadow=groups.firstOrNull { it.id=="ionia_shadow_order" }
                            if(shadow!=null && shadow.status=="尚未形成") { shadow.status="形成初期"; shadow.influence=24; shadow.resources=18; shadow.publicSupport=20 }
                            h.role="影流创建者"
                            h.goal="以自己的道路守护艾欧尼亚"
                            h.status="战争与理念冲突推动其脱离旧有道路，影流开始形成"
                        } else h.status="仍在均衡教派中修行；尚未创建影流"
                    }
                    "艾希" -> {
                        h.status="在阿瓦罗萨部族传统中成长；尚未以战母身份建立联盟"
                    }
                    "丽桑卓" -> {
                        groups.firstOrNull { it.id=="freljord_frostguard" }?.let { it.resources=(it.resources+1).coerceAtMost(100); it.influence=(it.influence+1).coerceAtMost(100) }
                        h.status="维持霜卫守望，压制监视者苏醒的威胁"
                    }
                    "阿兹尔" -> h.status="古代恕瑞玛皇帝的历史遗产；复兴尚未在当前世界发生"
                    "泽拉斯" -> h.status="古代飞升者与封印历史的遗留威胁；尚未在当前世界公开活动"
                    "塔莉垭" -> h.status="穿行沙漠，保护家园与普通恕瑞玛居民"
                    "黛安娜" -> {
                        groups.firstOrNull { it.id=="targon_lunari" }?.let { it.publicSupport=(it.publicSupport+1).coerceAtMost(100); it.influence=(it.influence+1).coerceAtMost(100) }
                        h.status="追寻皎月力量并面对烈阳与皎月的历史冲突"
                    }
                    "蕾欧娜" -> {
                        groups.firstOrNull { it.id=="targon_solari" }?.let { it.influence=(it.influence+1).coerceAtMost(100) }
                        h.status="维护烈阳教派秩序与拉阔尔传统"
                    }
                    "潘森" -> h.status="以凡人意志对抗星界命运与战争宿命"
                    "凯特琳" -> {
                        groups.firstOrNull { it.id=="piltover_council" }?.let { it.publicSupport=(it.publicSupport+1).coerceAtMost(100) }
                        h.status="调查皮尔特沃夫犯罪与双城跨区案件"
                    }
                    "蔚" -> h.status="执行执法行动，处理皮尔特沃夫与祖安之间的案件"
                    "金克丝" -> {
                        factions["皮尔特沃夫"]?.let { it.internalTension=(it.internalTension+1).coerceAtMost(100) }
                        factions["祖安"]?.let { it.internalTension=(it.internalTension+1).coerceAtMost(100) }
                        h.status="以不可预测的破坏行动冲击双城权力秩序"
                    }
                    "艾克" -> {
                        groups.firstOrNull { it.id=="zaun_firelights" }?.let { it.publicSupport=(it.publicSupport+1).coerceAtMost(100); it.resources=(it.resources+1).coerceAtMost(100) }
                        h.status="保护祖安街区与萤火社区"
                    }
                    "奇亚娜" -> {
                        groups.firstOrNull { it.id=="ixtal_council" }?.let { it.influence=(it.influence+1).coerceAtMost(100) }
                        factions["以绪塔尔"]?.let { it.internalTension=(it.internalTension+1).coerceAtMost(100) }
                        h.status="争取王族继承权并挑战以绪塔尔既有权力秩序"
                    }
                    "俄洛伊" -> {
                        groups.firstOrNull { it.id=="bilgewater_buhru" }?.let { it.influence=(it.influence+1).coerceAtMost(100); it.publicSupport=(it.publicSupport+1).coerceAtMost(100) }
                        h.status="履行娜迦卡波洛丝信仰使命并考验灵魂"
                    }
                    "妮蔻" -> h.status="寻找同伴并保护瓦斯塔亚族群"
                    "内瑟斯" -> h.status="守护恕瑞玛古代知识，警惕飞升者遗产再次引发灾难"
                    "雷克顿" -> h.status="在恕瑞玛沙漠中受仇恨驱使，追寻内瑟斯"
                    "亚托克斯" -> {
                        h.status="暗裔意识依附于武器，持续寻找宿主与挣脱封印的机会"
                        events.add("【暗裔威胁·模拟】第 $day 日：亚托克斯相关的暗裔力量出现征兆；暗裔是远古飞升者堕落后的存在，不是普通军队。")
                    }
                    "佛耶戈" -> {
                        h.status="仍受破败事件后的封印与残余诅咒影响"
                        groups.firstOrNull { it.id=="shadow_black_mist" }?.let { it.influence=(it.influence+1).coerceAtMost(100) }
                    }
                    "锤石" -> {
                        h.status="在黑雾与灵魂领域中继续操纵被囚禁的灵魂"
                        groups.firstOrNull { it.id=="shadow_black_mist" }?.let { it.resources=(it.resources+1).coerceAtMost(100) }
                    }
                    "瑞兹" -> h.status="追踪世界符文的线索，避免凡人再次触碰足以毁灭文明的力量"
                    "奥恩" -> h.status="隐居于炉乡与弗雷尔卓德荒野，不主动参与凡人政权"
                    "沃利贝尔" -> {
                        h.status="古老风暴信仰在弗雷尔卓德部分地区回响"
                        factions["弗雷尔卓德"]?.let { it.internalTension=(it.internalTension+1).coerceAtMost(100) }
                    }
                    "艾尼维亚" -> h.status="以冰霜与重生传说守护弗雷尔卓德生命"
                    "卑尔维斯" -> {
                        h.status="虚空深处的意识扩张，寻找侵入现实的机会"
                        events.add("【虚空威胁·模拟】第 $day 日：虚空女皇卑尔维斯相关的异界活动增强；虚空不受符文之地常规国家边界约束。")
                    }
                    "莫德凯撒" -> h.status="仍处于不朽领域，影响通过古老秘密与追随者传闻渗入诺克萨斯"
                    "提莫" -> h.status="以斥候身份穿梭符文之地，维持与班德尔城的联系"
                    "崔丝塔娜" -> h.status="巡查约德尔社群与跨界通道，守护同伴"
                    "璐璐" -> h.status="在物质领域与精神领域边缘游历，施展不可预测的约德尔魔法"
                    "兰博" -> h.status="改良自制机甲，寻找能证明其发明价值的机会"
                    "库奇" -> h.status="驾驶飞行器执行巡逻与运输，不参与普通国家军队编制"
                    "维迦" -> h.status="钻研黑暗魔法并追求强大力量，其自我认知与外界评价并不一致"
                    "黑默丁格" -> h.status="在皮尔特沃夫推进科学研究，同时警惕技术失控风险"
                    "悠米" -> h.status="借助魔典寻找诺拉，在不同地点与领域间穿行"
                    else -> h.status="在所属地区推进个人目标"
                }
                // Influence changes only through concrete actions above, not monthly random jitter.
                events.add("【重要人物行动·模拟】第 $day 日：${h.name}在${h.region}推进「${h.goal}」；当前状态：${h.status}。")
            }
        }
        saveHeroes(p,agents)
        saveOrganizations(p,groups)
        saveFactions(p,factions)
    }
    private const val KEY_FACTIONS="factions_state"
    private data class Faction(
        var name:String,var leader:String,var military:Int,var stability:Int,var treasury:Int,
        var territory:MutableList<String>,
        var government:String="",var rulingBloc:String="",var opposition:String="",
        var policy:String="",var internalTension:Int=20
    )
    private data class FactionLore(
        val government:String,val rulingBloc:String,val opposition:String,val policy:String,
        val leader:String
    )
    // Lore-grounded institutional identities. Simulated political changes are alternate history, not canon.
    private val factionLore=mapOf(
        "德玛西亚" to FactionLore("王国君主制；嘉文三世在位，王室与贵族共同维持秩序","王室、冕卫家族与各大贵族","法师社群、改革派贵族与反禁魔者","王室权威、贵族义务与魔法管制共同影响王国秩序","国王嘉文三世"),
        "诺克萨斯" to FactionLore("扩张型帝国；皇帝博拉姆·达克威尔统治，军方与宫廷争夺影响","皇帝宫廷、军团统帅与地方总督","旧贵族、黑色玫瑰与军政派系","维持帝国秩序、扩张边境并处理军政权力竞争","皇帝博拉姆·达克威尔及其宫廷"),
        "艾欧尼亚" to FactionLore("多地灵性传统并存；无统一中央政权","地方长老、寺院与自治社群","地方领袖之间的理念分歧与资源争议","守护土地、精神传统与地方自治；外部入侵尚未成为当前现实","地方议会与长老"),
        "皮尔特沃夫" to FactionLore("城邦议会与精英家族影响","议会、贸易家族与工坊巨头","底层工人、改革派与反垄断团体","推动海克斯科技、贸易和城市秩序，承受阶层压力","皮尔特沃夫议会"),
        "祖安" to FactionLore("地下城邦；工坊、帮派与企业势力并立","化学男爵及大型工坊势力","工人社区、独立发明家与反企业组织","争夺资源、空气与生存空间，科技繁荣伴随污染和剥削","化学男爵议会"),
        "弗雷尔卓德" to FactionLore("部族联盟与氏族政治","各部族首领及联盟","敌对部族、流亡者与争夺资源的氏族","在严酷环境中争夺生存资源、盟约与部族领导权","部族首领议会"),
        "恕瑞玛" to FactionLore("古帝国遗产下的绿洲城邦、部族与商路聚落并存；当前没有统一复兴帝国","各绿洲城邦统治者、部族首领、商路商人与遗迹守望者","彼此竞争的城邦、掠夺集团与对古帝国遗产持不同立场的群体","争夺水源、商路、安全与古代遗迹；帝国复兴仍只是部分群体的愿望","各地城邦与部族领袖（无统一君主）"),
        "巨神峰" to FactionLore("山地城邦、宗教组织与星灵影响","烈阳教派与地方守护者","皎月教派及不同信仰群体","信仰、登山朝圣与星灵力量影响地方秩序","烈阳教派长老"),
        "比尔吉沃特" to FactionLore("港口自治；船长、帮派与海上势力共治","港口商会、悬赏网络与强势船长","敌对海盗船队、走私团伙与底层码头工","争夺港口、航线、赏金和海上贸易控制权","港口势力领袖"),
        "以绪塔尔" to FactionLore("以绪奥肯为核心的隐秘元素魔法文明","云塔（Yun Tal）统治阶层与元素法师","边境村镇居民、异议者与外来势力","维护元素知识、城邦秩序与对外隔绝","云塔统治阶层"),
        "班德尔城" to FactionLore("班德尔城及其通往符文之地各处的班德尔入口；约德尔人社会","约德尔社群与各地入口守护者","外界威胁、入口失序与误入者带来的风险","维护班德尔城与各入口的联系及约德尔社群生活","约德尔社群代表（模拟）"),
        "暗影岛" to FactionLore("受黑雾与破败诅咒影响的岛屿；并非普通主权国家","亡灵势力与黑雾影响（非统一政府）","生者、幸存者与抵抗黑雾的力量","黑雾侵蚀、灵魂束缚与生者避险","无统一统治者")
    )
    private fun factionStates(p:android.content.SharedPreferences):MutableMap<String,Faction> {
        val raw=p.getString(KEY_FACTIONS,null)
        if(raw!=null) runCatching {
            val a=JSONArray(raw);val map=mutableMapOf<String,Faction>()
            for(i in 0 until a.length()){val j=a.getJSONObject(i);val lands=j.optJSONArray("territory")?:JSONArray();val list=(0 until lands.length()).map{lands.getString(it)}.toMutableList();val name=j.optString("name");val lore=factionLore[name];map[name]=Faction(name,j.optString("leader",lore?.leader?:"地方统治者"),j.optInt("military",100),j.optInt("stability",60),j.optInt("treasury",500),list,j.optString("government",lore?.government?:"地方政体"),j.optString("rulingBloc",lore?.rulingBloc?:"地方统治集团"),j.optString("opposition",lore?.opposition?:"地方反对势力"),j.optString("policy",lore?.policy?:"维持地方秩序"),j.optInt("internalTension",20))}
            if(map.isNotEmpty()) {
                map.remove("班德尔城")
                map.remove("暗影岛")
                val shurimaAwakened=runCatching { JSONArray(p.getString(KEY_LORE,"[]")).let { a -> (0 until a.length()).any { a.getString(it)=="shurima_rise" } } }.getOrDefault(false)
                if(!shurimaAwakened && p.getString(KEY_ERA,ERA_PRE_IONIA)==ERA_PRE_IONIA) map["恕瑞玛"]?.let { shurima ->
                    val lore=factionLore["恕瑞玛"]!!
                    shurima.government=lore.government;shurima.rulingBloc=lore.rulingBloc
                    shurima.opposition=lore.opposition;shurima.policy=lore.policy;shurima.leader=lore.leader
                }
                polityRegions.filter { it !in map }.forEach { r ->
                    map[r]=Faction(r,factionLore[r]?.leader?: "地方统治者",80+Random(r.hashCode().toLong()).nextInt(220),55+Random(r.hashCode().toLong()+1).nextInt(35),500,mutableListOf(r),factionLore[r]?.government?: "地方政体",factionLore[r]?.rulingBloc?: "地方统治集团",factionLore[r]?.opposition?: "地方反对势力",factionLore[r]?.policy?: "维持地方秩序",15+Random(r.hashCode().toLong()+2).nextInt(30))
                }
                saveFactions(p,map)
                return map
            }
        }
        val initial=polityRegions.associateWith { r -> Faction(r,factionLore[r]?.leader?: "地方统治者",80+Random(r.hashCode().toLong()).nextInt(220),55+Random(r.hashCode().toLong()+1).nextInt(35),500,mutableListOf(r),factionLore[r]?.government?: "地方政体",factionLore[r]?.rulingBloc?: "地方统治集团",factionLore[r]?.opposition?: "地方反对势力",factionLore[r]?.policy?: "维持地方秩序",15+Random(r.hashCode().toLong()+2).nextInt(30)) }.toMutableMap()
        saveFactions(p,initial);return initial
    }
    private fun saveFactions(p:android.content.SharedPreferences,map:Map<String,Faction>){
        p.edit().putString(KEY_FACTIONS,JSONArray().apply{map.values.forEach{f->put(JSONObject().put("name",f.name).put("leader",f.leader).put("military",f.military).put("stability",f.stability).put("treasury",f.treasury).put("territory",JSONArray(f.territory)).put("government",f.government).put("rulingBloc",f.rulingBloc).put("opposition",f.opposition).put("policy",f.policy).put("internalTension",f.internalTension))}}.toString()).apply()
    }
    private fun tickFactionGovernance(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val states=factionStates(p)
        val groups=organizations(p)
        states.values.forEach { f ->
            val regionalGroups=groups.filter { it.region in f.territory }
            val strongest=regionalGroups.maxByOrNull { it.influence + it.publicSupport/2 }
            if(strongest!=null && strongest.influence>=70 && strongest.publicSupport>=65 && rng.nextInt(100)<12) {
                val proposed="回应「"+strongest.name+"」的诉求："+strongest.agenda
                if(f.policy!=proposed) {
                    f.policy=proposed
                    f.internalTension=(f.internalTension+2).coerceAtMost(100)
                    events.add("【政策形成·模拟】第 "+day+" 日："+f.name+"的决策层受到"+strongest.name+"影响，政策调整为「"+proposed+"」。")
                }
            }
            regionalGroups.filter { it.influence>=60 && it.publicSupport>=55 }.forEach { group ->
                val rivals=group.rivals.mapNotNull { id -> groups.firstOrNull { it.id==id } }
                if(rivals.any { it.influence>=60 && it.publicSupport>=55 } && rng.nextInt(100)<10) {
                    f.internalTension=(f.internalTension+2).coerceAtMost(100)
                    events.add("【社会压力·模拟】第 "+day+" 日："+f.name+"境内的"+group.name+"与竞争组织围绕社会方向形成对立，政治压力上升。")
                }
            }
            val count=pop.count{it.alive&&it.region in f.territory}
            val marketWealth=f.territory.sumOf{markets[it]?.wealth?:0}
            f.treasury=(f.treasury+count/5+marketWealth/1000-rng.nextInt(8)).coerceAtLeast(0)
            f.internalTension=(f.internalTension+(rng.nextInt(7)-3)+(if(f.stability<35)2 else 0)+(if(f.treasury<150)2 else 0)).coerceIn(0,100)
            f.stability=(f.stability+(if(count>0)1 else -2)+(if(f.treasury<150) -3 else 1)+(if(f.internalTension>75)-2 else 0)).coerceIn(0,100)
            if(f.internalTension>=75 && rng.nextInt(100)<18) {
                events.add("【势力内政·模拟】第 $day 日：${f.name}内部矛盾激化，${f.rulingBloc}与${f.opposition}围绕「${f.policy}」发生政治冲突。")
                f.stability=(f.stability-2).coerceAtLeast(0)
            }
            f.military=(f.military+if(f.treasury>800 && rng.nextInt(100)<10)5 else 0).coerceAtMost(10000)
            if(f.stability<25 && rng.nextInt(100)<12){
                events.add("【政局】第 $day 日：${f.name}局势动荡，财政${f.treasury}，稳定度${f.stability}；出现抗议、权力斗争或地方自治迹象。")
                val region=f.territory.randomOrNull()
                if(region!=null)pop.filter{it.alive&&it.region==region}.take(3).forEach{it.health=(it.health-1).coerceAtLeast(0)}
            }
        }
        // Demacia's mage restrictions are a continuing social conflict, not a one-off scripted rebellion.
        // Residents are recorded as witnesses; the simulation never presumes they are mages.
        val demacia=states["德玛西亚"]
        val seekers=groups.firstOrNull { it.id=="demacia_mage_seekers" }
        val mageNetwork=groups.firstOrNull { it.id=="demacia_mage_resistance" }
        if(demacia!=null && seekers!=null && mageNetwork!=null) {
            val enforcement=seekers.influence+seekers.publicSupport/2
            val underground=mageNetwork.influence+mageNetwork.publicSupport/2
            if(enforcement>=underground+18 && demacia.internalTension>=30 && rng.nextInt(100)<18) {
                seekers.resources=(seekers.resources-2).coerceAtLeast(0)
                seekers.influence=(seekers.influence+1).coerceAtMost(100)
                mageNetwork.influence=(mageNetwork.influence+2).coerceAtMost(100)
                mageNetwork.resources=(mageNetwork.resources-1).coerceAtLeast(0)
                demacia.internalTension=(demacia.internalTension+2).coerceAtMost(100)
                demacia.policy="加强对未经许可魔法活动的搜查与登记；地方执行程度不一"
                val witnesses=pop.filter { it.alive && it.region=="德玛西亚" }.shuffled(rng).take(1+rng.nextInt(2))
                witnesses.forEach { recordLife(it,day,"在当地社区见到搜魔审查行动及其引发的恐惧与争议；本人是否具有魔法天赋未作预设。") }
                events.add("【德玛西亚社会冲突·模拟】第 $day 日：搜魔人加强对魔法活动的审查；部分社区出现隐匿、互助与恐惧，法师地下联络网络因此获得新的支持。")
            } else if(underground>=enforcement+15 && demacia.internalTension>=45 && mageNetwork.resources>=8 && rng.nextInt(100)<15) {
                mageNetwork.resources=(mageNetwork.resources-3).coerceAtLeast(0)
                mageNetwork.influence=(mageNetwork.influence+1).coerceAtMost(100)
                mageNetwork.publicSupport=(mageNetwork.publicSupport+2).coerceAtMost(100)
                seekers.publicSupport=(seekers.publicSupport-1).coerceAtLeast(0)
                demacia.internalTension=(demacia.internalTension+1).coerceAtMost(100)
                demacia.policy="地方出现隐匿与互助网络；王室与搜魔人面临执行尺度争议"
                val witnesses=pop.filter { it.alive && it.region=="德玛西亚" }.shuffled(rng).take(1+rng.nextInt(2))
                witnesses.forEach { recordLife(it,day,"在社区中听闻有人帮助受搜查威胁者转移或躲避审查；事件细节并不完全为外界所知。") }
                events.add("【德玛西亚地下互助·模拟】第 $day 日：法师与同情者组成的地方联络网络帮助受审查威胁者躲避搜查；搜魔人对社区的控制受到局部挑战。")
            }
        }
        saveOrganizations(p,groups)
        saveFactions(p,states)
    }
    private const val KEY_ORGANIZATIONS="organizations_state"
    private data class Organization(
        val id:String,var name:String,val region:String,val kind:String,var agenda:String,
        val rivals:MutableList<String>,val allies:MutableList<String>,
        var leader:String,var influence:Int,var resources:Int,var publicSupport:Int,
        var status:String="活跃",val members:MutableList<Int> = mutableListOf()
    )
    // Organizations are independent actors, not interchangeable with whole regions or states.
    private val organizationSeeds=listOf(
        Organization("demacia_crown","德玛西亚王室与王国机构","德玛西亚","国家机构","维护王国统治、法律秩序与边境安全",mutableListOf("demacia_mage_seekers"),mutableListOf(), "王室与王国官员",78,85,72),
        Organization("demacia_crownguard","冕卫家族","德玛西亚","贵族家族","维护家族地位、军事传统与王国影响力",mutableListOf("demacia_mage_seekers"),mutableListOf("demacia_crown"),"冕卫家族长",62,72,55),
        Organization("demacia_mage_seekers","搜魔人组织","德玛西亚","国家组织","执行针对非法魔法活动的搜查与管制",mutableListOf("demacia_crownguard"),mutableListOf("demacia_crown"),"搜魔人高阶官员",55,65,48),
        Organization("demacia_vanguard","无畏先锋","德玛西亚","军事组织","保卫德玛西亚并承担高风险军事任务",mutableListOf(),mutableListOf("demacia_crown","demacia_crownguard"),"无畏先锋军官",68,70,76),
        Organization("noxus_trifarix","诺克萨斯帝国宫廷","诺克萨斯","最高统治机构","维护皇帝博拉姆·达克威尔的帝国统治与军政秩序",mutableListOf("noxus_black_rose"),mutableListOf("noxus_legions"),"皇帝宫廷与军政官员",82,90,64),
        Organization("noxus_legions","诺克萨斯军团","诺克萨斯","军事组织","扩张帝国疆域并维持军事控制",mutableListOf(),mutableListOf("noxus_trifarix"),"军团统帅",82,90,70),
        Organization("noxus_black_rose","黑色玫瑰","诺克萨斯","秘密结社","通过秘密网络与古老力量影响权力格局",mutableListOf("noxus_trifarix"),mutableListOf(),"秘密议会",76,82,20),
        Organization("ionia_kinkou","均衡教派","艾欧尼亚","宗教与武术组织","维护精神领域与物质领域之间的均衡",mutableListOf("ionia_shadow_order"),mutableListOf(),"教派长老",62,45,60),
        Organization("ionia_provincial_militias","艾欧尼亚各省民兵与地方守卫","艾欧尼亚","军事组织","保护各自省地的村镇、道路与居民；不存在统一中央军队",mutableListOf(),mutableListOf("ionia_kinkou"),"各省地方领袖",52,42,58),
        Organization("ionia_monasteries","艾欧尼亚武道寺院与僧侣社群","艾欧尼亚","宗教与武术组织","守护寺院、灵性传统与地方居民，不隶属于统一军队",mutableListOf(),mutableListOf("ionia_kinkou"),"寺院长老",48,35,62),
        Organization("ionia_shadow_order","影流（尚未形成）","艾欧尼亚","武装组织","尚未形成独立组织；未来可能因战争创伤与理念冲突出现分裂",mutableListOf("ionia_kinkou"),mutableListOf(),"尚无组织领袖",0,0,0,"尚未形成"),
        Organization("freljord_avarosan","阿瓦罗萨部族联盟","弗雷尔卓德","部族联盟","团结部族并争取和平与生存空间",mutableListOf("freljord_winter_claw"),mutableListOf(),"部族联盟领袖",64,50,65),
        Organization("freljord_winter_claw","凛冬之爪","弗雷尔卓德","部族联盟","以强硬的生存法则与征战维系部族",mutableListOf("freljord_avarosan"),mutableListOf(),"战母",61,55,54),
        Organization("zaun_chem_barons","祖安化学男爵势力","祖安","商业与帮派联盟","控制工业、炼金资源与地下贸易网络",mutableListOf("zaun_firelights"),mutableListOf(),"化学男爵议会",75,88,32),
        Organization("zaun_firelights","萤火","祖安","地下抵抗组织","保护社区并反抗压迫与环境破坏",mutableListOf("zaun_chem_barons"),mutableListOf(),"社区行动者",42,28,68),
        Organization("piltover_council","皮尔特沃夫议会与贸易家族","皮尔特沃夫","政治与商业联盟","维护城邦贸易、海克斯科技与精英秩序",mutableListOf(),mutableListOf(),"议会代表",78,92,62),
        Organization("bilgewater_captains","比尔吉沃特船长与港口势力","比尔吉沃特","港口联盟","争夺港口、航线、悬赏与海上贸易",mutableListOf(),mutableListOf(),"港口头目",60,62,45),
        Organization("shurima_sun_disc","太阳圆盘遗产守望者","恕瑞玛","古代遗产与宗教社群","守护太阳圆盘遗迹、古代知识与复兴传说；尚未建立统一帝国",mutableListOf(),mutableListOf(),"遗迹守望者（模拟）",48,44,42),
        Organization("targon_solari","烈阳教派","巨神峰","宗教组织","维护烈阳信仰与相关宗教秩序",mutableListOf("targon_lunari"),mutableListOf(),"烈阳教长",57,45,55),
        Organization("targon_lunari","皎月教派","巨神峰","宗教组织","守护皎月信仰与其信徒",mutableListOf("targon_solari"),mutableListOf(),"皎月教派代表",45,30,35),
        Organization("ixtal_council","云塔（Yun Tal）","以绪塔尔","统治阶层","维护元素知识、以绪奥肯秩序与对外隔绝",mutableListOf(),mutableListOf(),"统治阶层代表",80,75,70),
        Organization("demacia_mage_resistance","德玛西亚法师与反抗网络","德玛西亚","地下社群","保护受禁魔制度影响的法师及其家人，争取生存与自由",mutableListOf("demacia_mage_seekers"),mutableListOf(),"地方联络者",48,35,58),
        Organization("freljord_frostguard","霜卫部族","弗雷尔卓德","古老部族与守望者","守护古老秘密、监视冰封威胁并维持霜卫传统",mutableListOf(),mutableListOf(),"霜卫领袖",58,52,34),
        Organization("bilgewater_buhru","布胡鲁信仰共同体","比尔吉沃特","宗教与地方社群","遵循娜迦卡波洛丝信仰与仪式，维系群岛本土传统",mutableListOf(),mutableListOf(),"祭司与神庙守护者",52,38,50),
        Organization("bilgewater_sailors","比尔吉沃特船员与赏金网络","比尔吉沃特","海上松散网络","争夺航线、悬赏、船只与港口生计",mutableListOf(),mutableListOf("bilgewater_captains"),"船长与悬赏掮客",55,48,45),
        Organization("shurima_city_leagues","恕瑞玛绿洲城邦与商路联盟","恕瑞玛","城邦与商贸网络","维护水源、商路与城邦自治，防止帝国势力垄断地方生活",mutableListOf(),mutableListOf(),"城邦代表（模拟）",50,54,58),
        Organization("shurima_xerath_followers","泽拉斯追随者网络","恕瑞玛","秘密政治与魔法势力","扩大泽拉斯在恕瑞玛的影响并挑战阿兹尔复兴秩序",mutableListOf("shurima_sun_disc"),mutableListOf(),"秘密代理人",42,46,18),
        Organization("targon_rakkor","拉阔尔部族社群","巨神峰","山地部族社群","维护拉阔尔传统、山地生活与朝圣道路",mutableListOf(),mutableListOf(),"部族长者",46,42,48),
        Organization("zaun_independent_inventors","祖安独立发明家与工坊","祖安","技术社群","在化学男爵控制之外维持实验、交易与社区技术网络",mutableListOf("zaun_chem_barons"),mutableListOf("zaun_firelights"),"工坊代表",44,36,50),
        Organization("piltover_academy","皮尔特沃夫科学与发明社群","皮尔特沃夫","学术与技术社群","推动科学、海克斯科技研究与知识传播",mutableListOf(),mutableListOf("piltover_council"),"学者与发明家代表",58,62,57),
        Organization("bandle_yordle_communities","班德尔城约德尔社群","班德尔城","非人种族社群","维系班德尔城社群、班德尔树与通往物质世界的入口联系",mutableListOf(),mutableListOf(),"约德尔社群长者（模拟）",68,64,72),
        Organization("shadow_black_mist","黑雾与亡灵势力","暗影岛","超自然威胁网络","黑雾侵蚀、束缚灵魂并向外扩散；不是具有正常成员制度的政治组织",mutableListOf(),mutableListOf(),"无统一领袖",92,90,0)
    )
    private fun organizations(p:android.content.SharedPreferences):MutableList<Organization>{
        val raw=p.getString(KEY_ORGANIZATIONS,null)
        if(raw!=null) runCatching {
            val a=JSONArray(raw)
            val loaded=(0 until a.length()).map { i ->
                val j=a.getJSONObject(i)
                fun strings(key:String)= (0 until (j.optJSONArray(key)?.length()?:0)).map { j.getJSONArray(key).getString(it) }.toMutableList()
                val members=(0 until (j.optJSONArray("members")?.length()?:0)).map { j.getJSONArray("members").getInt(it) }.toMutableList()
                Organization(j.optString("id"),j.optString("name"),j.optString("region"),j.optString("kind"),j.optString("agenda"),strings("rivals"),strings("allies"),j.optString("leader"),j.optInt("influence",30),j.optInt("resources",20),j.optInt("publicSupport",30),j.optString("status","活跃"),members)
            }.toMutableList()
            val known=loaded.map { it.id }.toSet()
            organizationSeeds.filter { it.id !in known }.forEach { seed ->
                loaded.add(seed.copy(rivals=seed.rivals.toMutableList(),allies=seed.allies.toMutableList(),members=mutableListOf()))
            }
            saveOrganizations(p,loaded)
            return loaded
        }
        return organizationSeeds.map { it.copy(rivals=it.rivals.toMutableList(),allies=it.allies.toMutableList(),members=mutableListOf()) }.toMutableList().also { saveOrganizations(p,it) }
    }
    private fun saveOrganizations(p:android.content.SharedPreferences,list:List<Organization>){
        p.edit().putString(KEY_ORGANIZATIONS,JSONArray().apply { list.forEach { o ->
            put(JSONObject().put("id",o.id).put("name",o.name).put("region",o.region).put("kind",o.kind).put("agenda",o.agenda)
                .put("rivals",JSONArray(o.rivals)).put("allies",JSONArray(o.allies)).put("leader",o.leader)
                .put("influence",o.influence).put("resources",o.resources).put("publicSupport",o.publicSupport)
                .put("status",o.status).put("members",JSONArray(o.members)))
        }}.toString()).apply()
    }
    private fun tickOrganizations(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val groups=organizations(p)
        groups.forEach { group ->
            if(group.status=="尚未形成") {
                if(group.id=="ionia_shadow_order" && p.getBoolean(KEY_IONIA_PLACIDIUM,false)) {
                    group.status="形成初期"; group.influence=24; group.resources=18; group.publicSupport=20
                    events.add("【组织形成·模拟分支】第 $day 日：战争创伤与理念分歧推动艾欧尼亚影流开始形成；其发展仍取决于人物选择与地方局势。")
                    recordLocalStory(pop,day,"艾欧尼亚","战争后的理念分歧推动影流开始形成；当地居民开始感受到教派与地方社群之间的变化。",rng,4)
                } else return@forEach
            }
            val livingMembers=group.members.mapNotNull { id -> pop.firstOrNull { it.id==id && it.alive } }
            group.members.retainAll(livingMembers.map { it.id }.toSet())
            if(group.kind=="非人种族社群" || group.kind=="超自然威胁网络") {
                group.resources=(group.resources+rng.nextInt(5)-2).coerceIn(0,100)
                group.influence=(group.influence+rng.nextInt(5)-2).coerceIn(0,100)
                group.status=if(group.kind=="超自然威胁网络") { if(group.influence>=70)"黑雾侵蚀活跃" else "侵蚀暂缓" } else "约德尔社群活动"
                if(rng.nextInt(100)<5) events.add("【特殊存在活动·模拟】第 $day 日：${group.name}的状态变化为「${group.status}」。")
                return@forEach
            }
            val candidates=pop.filter { it.alive && it.region==group.region && it.id !in group.members && it.age>=16 }
            val recruit=candidates.filter { person ->
                when(group.kind){
                    "军事组织" -> person.job=="护卫" || person.goal.contains("守护") || person.goal.contains("战")
                    "宗教组织","宗教与武术组织" -> person.goal.contains("信仰") || person.goal.contains("修行") || person.job=="学徒"
                    "贵族家族" -> person.coins>=20 || person.job=="护卫"
                    "商业与帮派联盟","政治与商业联盟" -> person.job=="商贩" || person.job=="工匠"
                    "地下抵抗组织","武装组织" -> person.goal.contains("自由") || person.goal.contains("复仇") || person.job=="护卫"
                    "国家机构","最高统治机构","统治机构" -> person.job=="护卫" || person.job=="工匠" || person.job=="商贩"
                    else -> person.job in listOf("商贩","工匠","护卫","学徒")
                }
            }.randomOrNull()
            if(recruit!=null && group.members.size<80 && rng.nextInt(100)<24){
                group.members.add(recruit.id)
                recordLife(recruit,day,"加入组织「"+group.name+"」，开始参与其活动。")
                events.add("【组织活动·模拟】第 "+day+" 日："+recruit.name+"加入"+group.name+"。")
            }
            // Membership is voluntary and can end; organizations should not accumulate immortal, permanent rosters.
            val departureChance=if(group.resources<=5 || group.publicSupport<20)18 else if(group.publicSupport<35)7 else 1
            if(group.members.isNotEmpty() && rng.nextInt(100)<departureChance) {
                val leavingId=group.members.randomOrNull()
                val leaving=pop.firstOrNull { it.id==leavingId && it.alive }
                if(leavingId!=null) group.members.remove(leavingId)
                if(leaving!=null) {
                    recordLife(leaving,day,"退出组织「"+group.name+"」，不再参与其活动。")
                    events.add("【组织变动·模拟】第 "+day+" 日："+leaving.name+"退出"+group.name+"。")
                }
            }
            group.resources=(group.resources+(livingMembers.size/8)+(if(group.kind.contains("商业")||group.kind.contains("帮派"))rng.nextInt(4) else 0)-rng.nextInt(3)).coerceIn(0,100)
            group.publicSupport=(group.publicSupport+(if(group.resources<15)-1 else if(group.agenda.contains("保护")||group.agenda.contains("团结"))1 else 0)+rng.nextInt(3)-1).coerceIn(0,100)
            val supportShift=if(group.publicSupport>=75)1 else if(group.publicSupport<=25)-1 else 0
            group.influence=(group.influence+(livingMembers.size/20)+supportShift+rng.nextInt(3)-1).coerceIn(0,100)
            if(group.resources==0 || group.influence<8) group.status="衰弱"
            else if(group.members.size>=45 && group.influence>=75) group.status="强盛"
            else group.status="活跃"
            if(group.influence>=70 && rng.nextInt(100)<8) events.add("【组织政治·模拟】第 "+day+" 日："+group.name+"影响力上升，开始更积极地推动「"+group.agenda+"」。")
        }
        groups.forEach { group ->
            group.rivals.filter { it>group.id }.forEach { rivalId ->
                val rival=groups.firstOrNull { it.id==rivalId }
                if(rival!=null && group.influence>=65 && rival.influence>=65 && rng.nextInt(100)<4){
                    events.add("【组织冲突·模拟】第 "+day+" 日："+group.name+"与"+rival.name+"围绕理念、地盘或资源发生冲突。")
                    recordLocalStory(pop,day,group.region,"当地的"+group.name+"与"+rival.name+"发生公开冲突；居民开始面对治安、资源与立场选择带来的压力。",rng,3)
                    group.resources=(group.resources-2).coerceAtLeast(0)
                    rival.resources=(rival.resources-2).coerceAtLeast(0)
                    group.publicSupport=(group.publicSupport-1).coerceAtLeast(0)
                    rival.publicSupport=(rival.publicSupport-1).coerceAtLeast(0)
                }
            }
        }
        saveOrganizations(p,groups)
    }
    fun organizationDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val allPeople=people(p)
        val groups=organizations(p)
        return groups.sortedByDescending { it.influence }.map { o ->
            val memberNames=o.members.mapNotNull { id -> allPeople.firstOrNull { it.id==id && it.alive }?.name }
            "【"+o.name+"】\n所属地区："+o.region+"\n组织性质："+o.kind+"\n核心目标："+o.agenda+"\n领袖/机构："+o.leader+"\n成员："+if(o.kind=="非人种族社群"||o.kind=="超自然威胁网络")"不适用（非普通人类居民组织）" else o.members.size.toString()+"人"+"\n影响力："+o.influence+"｜资源："+o.resources+"｜公众支持："+o.publicSupport+"\n状态："+o.status+"\n盟友："+o.allies.joinToString("、").ifBlank { "暂无" }+"\n竞争/敌对组织："+o.rivals.joinToString("、").ifBlank { "暂无" }+"\n成员示例："+if(o.kind=="非人种族社群"||o.kind=="超自然威胁网络")"不适用" else memberNames.take(8).joinToString("、").ifBlank { "尚无成员" }+"\n（组织的后续变化属于模拟历史分支；超自然力量不按普通居民组织机制运行。）"
        }
    }

    private const val KEY_DIPLO="diplomacy"
    private data class PairTension(val a:String,val b:String,var tension:Int,var war:Boolean=false,var aWins:Int=0,var bWins:Int=0,var warMonths:Int=0,var exhaustionA:Int=0,var exhaustionB:Int=0)
    private val factionRegions=mapOf("德玛西亚" to "德玛西亚","诺克萨斯" to "诺克萨斯","艾欧尼亚" to "艾欧尼亚","皮尔特沃夫" to "皮尔特沃夫","祖安" to "祖安","弗雷尔卓德" to "弗雷尔卓德","恕瑞玛" to "恕瑞玛","巨神峰" to "巨神峰","比尔吉沃特" to "比尔吉沃特","以绪塔尔" to "以绪塔尔")
    private fun diplomacy(p:android.content.SharedPreferences):MutableList<PairTension> {
        val raw=p.getString(KEY_DIPLO,null)
        if(raw!=null) runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { PairTension(it.optString("a"),it.optString("b"),it.optInt("tension",10),it.optBoolean("war",false),it.optInt("aWins",0),it.optInt("bWins",0),it.optInt("warMonths",0),it.optInt("exhaustionA",0),it.optInt("exhaustionB",0)) }.toMutableList() } }.getOrNull()?.let { if(it.isNotEmpty()) {
            val corrected=it.filterNot { relation -> setOf(relation.a,relation.b)==setOf("皮尔特沃夫","祖安") }.toMutableList()
            if(corrected.size!=it.size) saveDiplomacy(p,corrected)
            return corrected
        } }
        return mutableListOf(PairTension("诺克萨斯","艾欧尼亚",72),PairTension("诺克萨斯","德玛西亚",58),PairTension("弗雷尔卓德","诺克萨斯",32),PairTension("恕瑞玛","诺克萨斯",28),PairTension("比尔吉沃特","诺克萨斯",24)).also { saveDiplomacy(p,it) }
    }
    private fun saveDiplomacy(p:android.content.SharedPreferences,relations:List<PairTension>) {
        p.edit().putString(KEY_DIPLO,JSONArray().apply { relations.forEach { put(JSONObject().put("a",it.a).put("b",it.b).put("tension",it.tension).put("war",it.war).put("aWins",it.aWins).put("bWins",it.bWins).put("warMonths",it.warMonths).put("exhaustionA",it.exhaustionA).put("exhaustionB",it.exhaustionB)) } }.toString()).apply()
    }
    private fun tickDiplomacy(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        if(day%30L!=0L)return
        val relations=diplomacy(p)
        val factions=factionStates(p)
        relations.forEach { rel ->
            val sideA=factions[rel.a]
            val sideB=factions[rel.b]
            if(!rel.war) {
                val structuralPressure=((sideA?.internalTension?:20)+(sideB?.internalTension?:20))/35
                rel.tension=(rel.tension+rng.nextInt(7)-3+(if(structuralPressure>=4)1 else 0)).coerceIn(0,100)
                val isNoxusIonia=setOf(rel.a,rel.b)==setOf("诺克萨斯","艾欧尼亚")
                val noxus=factions["诺克萨斯"]
                val legions=organizations(p).firstOrNull { it.id=="noxus_legions" }
                val invasionReady=!isNoxusIonia || (noxus!=null && noxus.military>=60 && noxus.treasury>=250 && noxus.stability>=35 && (legions?.influence?:0)>=50)
                if(rel.tension>=85 && invasionReady && rng.nextInt(100)<18) {
                    rel.war=true
                    if(setOf(rel.a,rel.b)==setOf("诺克萨斯","艾欧尼亚")) {
                        p.edit().putBoolean(KEY_IONIA_INVASION_STARTED,true).putLong(KEY_IONIA_INVASION_DAY,day).apply()
                        events.add("【历史转折·模拟分支】第 $day 日：诺克萨斯与艾欧尼亚之间的结构性冲突升级为军事入侵。此时点由本世界的外交、军政与组织条件促成，不代表官方正史日期。")
                    }
                    rel.warMonths=0
                    rel.exhaustionA=0
                    rel.exhaustionB=0
                    rel.aWins=0
                    rel.bWins=0
                    events.add("【战争爆发·模拟分支】第 $day 日：${rel.a}与${rel.b}的外交危机升级为公开战争。战争目标、补给与国内政治将共同影响后续进程。")
                recordLocalStory(pop,day,rel.a,"国家与${rel.b}正式进入战争状态；征兵、税赋、边境安全与贸易前景开始成为当地社会议题。",rng,3,setOf("护卫","商贩","工匠"))
                recordLocalStory(pop,day,rel.b,"国家与${rel.a}正式进入战争状态；边境安全、物资供应与家人去向成为当地社会议题。",rng,3,setOf("护卫","商贩","工匠"))
                }
            }
            if(rel.war && sideA!=null && sideB!=null) {
                rel.warMonths++
                val pressureA=(100-sideA.stability)/30+(if(sideA.treasury<100)2 else 0)+(if(sideA.military<45)2 else 0)
                val pressureB=(100-sideB.stability)/30+(if(sideB.treasury<100)2 else 0)+(if(sideB.military<45)2 else 0)
                rel.exhaustionA=(rel.exhaustionA+1+pressureA+rng.nextInt(3)).coerceAtMost(100)
                rel.exhaustionB=(rel.exhaustionB+1+pressureB+rng.nextInt(3)).coerceAtMost(100)
                val militaryCostA=(1+rng.nextInt(4)+(if(rel.exhaustionA>65)1 else 0)).coerceAtMost(sideA.military)
                val militaryCostB=(1+rng.nextInt(4)+(if(rel.exhaustionB>65)1 else 0)).coerceAtMost(sideB.military)
                sideA.military=(sideA.military-militaryCostA).coerceAtLeast(0)
                sideB.military=(sideB.military-militaryCostB).coerceAtLeast(0)
                val treasuryCostA=(5+rng.nextInt(12)).coerceAtMost(sideA.treasury)
                val treasuryCostB=(5+rng.nextInt(12)).coerceAtMost(sideB.treasury)
                sideA.treasury=(sideA.treasury-treasuryCostA).coerceAtLeast(0)
                sideB.treasury=(sideB.treasury-treasuryCostB).coerceAtLeast(0)
                sideA.stability=(sideA.stability-(if(rel.exhaustionA>60)2 else 1)).coerceAtLeast(0)
                sideB.stability=(sideB.stability-(if(rel.exhaustionB>60)2 else 1)).coerceAtLeast(0)
                val regionA=factionRegions[rel.a]?:rel.a
                val regionB=factionRegions[rel.b]?:rel.b
                val isNoxusIonia=setOf(rel.a,rel.b)==setOf("诺克萨斯","艾欧尼亚")
                val warImpactChance=if(isNoxusIonia)55 else 38
                if(rng.nextInt(100)<warImpactChance) {
                    val region=if(isNoxusIonia) { if(rng.nextInt(100)<70) "艾欧尼亚" else "诺克萨斯" } else if(rng.nextBoolean())regionA else regionB
                    val victimCount=if(isNoxusIonia) 2+rng.nextInt(4) else 1+rng.nextInt(2)
                    val victims=pop.filter{it.alive&&it.region==region}.shuffled(rng).take(victimCount)
                    victims.forEach{r->
                        r.health=(r.health-2-rng.nextInt(7)).coerceAtLeast(0)
                        recordLife(r,day,"战争持续影响${region}，遭遇冲突、征用或补给中断。")
                    }
                    markets[region]?.let{it.food=(it.food-5-rng.nextInt(16)).coerceAtLeast(0);it.wealth=(it.wealth-2-rng.nextInt(8)).coerceAtLeast(0)}
                    if(victims.isNotEmpty())events.add("【战争与平民生活·模拟】第 $day 日：${region}的战事与征用影响${victims.size}名居民，地方市场补给承压。")
                }
                if(isNoxusIonia && rel.warMonths>=6 && !p.getBoolean(KEY_IONIA_PLACIDIUM,false)) {
                    val ionianGroups=organizations(p)
                    val militias=ionianGroups.firstOrNull { it.id=="ionia_provincial_militias" }
                    val localReadiness=(militias?.influence?:0)>=55 || (sideB.stability<=65)
                    if(localReadiness && rng.nextInt(100)<30) {
                        militias?.let { it.influence=(it.influence+8).coerceAtMost(100); it.publicSupport=(it.publicSupport+8).coerceAtMost(100) }
                        events.add("【普雷西典战线·模拟分支】第 $day 日：诺克萨斯军团的推进威胁到艾欧尼亚重要圣地与省地交通；地方民兵、武道寺院和居民开始更广泛地组织抵抗。战局结果仍由后续资源、动员与军事行动决定。")
                        recordLocalStory(pop,day,"艾欧尼亚","普雷西典战线局势恶化；地方社群开始组织抵抗与互助，个人生活受到战争前线变化牵动。",rng,8)
                        p.edit().putBoolean(KEY_IONIA_PLACIDIUM,true).apply()
                        saveOrganizations(p,ionianGroups)
                    }
                }
                if(rel.warMonths%3==0) {
                    events.add("【战争进程·模拟】第 $day 日：${rel.a}与${rel.b}的战争已持续${rel.warMonths}个月；双方军力、财政与社会稳定持续消耗。")
                    recordLocalStory(pop,day,regionA,"战争持续至第${rel.warMonths}个月；征用、物价与亲友安危逐渐改变当地人的生活判断。",rng,2)
                    recordLocalStory(pop,day,regionB,"战争持续至第${rel.warMonths}个月；补给压力与国内局势影响当地居民对未来的预期。",rng,2)
                }
                val exhaustion=rel.exhaustionA+rel.exhaustionB
                val domesticCrisis=sideA.stability<25 || sideB.stability<25 || sideA.treasury<35 || sideB.treasury<35
                val minimumWarMonths=if(isNoxusIonia)24 else 3
                val negotiationChance=when{
                    rel.warMonths<minimumWarMonths -> 0
                    exhaustion>=130 || domesticCrisis -> 35
                    exhaustion>=95 -> 22
                    exhaustion>=70 -> 12
                    else -> 0
                }
                if(negotiationChance>0 && rng.nextInt(100)<negotiationChance) {
                    rel.war=false
                    if(setOf(rel.a,rel.b)==setOf("诺克萨斯","艾欧尼亚") && p.getBoolean(KEY_IONIA_INVASION_STARTED,false)) {
                        p.edit().putBoolean(KEY_IONIA_WAR_ENDED,true).apply()
                        if(p.getBoolean(KEY_IONIA_PLACIDIUM,false)) {
                            val heroes=heroAgents(p)
                            heroes.firstOrNull { it.name=="斯维因" }?.let { it.role="失势的诺克萨斯将领"; it.status="在艾欧尼亚战场遭到贬黜，公开声望受损；尚未发动夺权" }
                            saveHeroes(p,heroes)
                            p.edit().putBoolean(KEY_SWAIN_DISGRACED,true).putLong(KEY_SWAIN_DISGRACED_DAY,day).apply()
                            events.add("【诺克萨斯军政危机·模拟分支】第 $day 日：艾欧尼亚战场的失败使斯维因遭到达克威尔贬黜，帝国内部开始重新评估最高统治者的战争决策。")
                        }
                        events.add("【历史阶段变化·模拟分支】第 $day 日：诺克萨斯—艾欧尼亚战事进入停战阶段；战后影响将在后续依据居民、地方组织与资源状况展开。")
                    }
                    rel.tension=(rel.tension-18).coerceAtLeast(45)
                    events.add("【停战谈判·模拟分支】第 $day 日：长期战争造成的财政、军力与社会压力迫使${rel.a}和${rel.b}接受暂时停火；边界争端与政治敌意尚未解决。")
                    recordLocalStory(pop,day,regionA,"停战消息传来；居民开始评估失散亲友、贸易恢复与家园重建的可能。",rng,5)
                    recordLocalStory(pop,day,regionB,"停战消息传来；居民对边境是否安全、军队是否撤离仍抱有疑虑。",rng,5)
                    rel.warMonths=0
                    rel.exhaustionA=0
                    rel.exhaustionB=0
                }
            }
        }
        saveFactions(p,factions)
        saveDiplomacy(p,relations)
    }
    private fun tickNoxusSuccession(day:Long,p:android.content.SharedPreferences,events:MutableList<String>,rng:Random) {
        if(day%30L!=0L || !p.getBoolean(KEY_IONIA_WAR_ENDED,false) || !p.getBoolean(KEY_SWAIN_DISGRACED,false) || day-p.getLong(KEY_SWAIN_DISGRACED_DAY,day)<90L || p.getBoolean(KEY_SWAIN_COUP,false)) return
        val factions=factionStates(p)
        val noxus=factions["诺克萨斯"] ?: return
        val groups=organizations(p)
        val legions=groups.firstOrNull { it.id=="noxus_legions" } ?: return
        val blackRose=groups.firstOrNull { it.id=="noxus_black_rose" } ?: return
        val heroes=heroAgents(p)
        val swain=heroes.firstOrNull { it.name=="斯维因" } ?: return
        val domesticCrisis=noxus.internalTension>=48 || noxus.stability<=48 || noxus.treasury<=380
        val politicalBase=swain.influence>=72 && legions.influence>=55 && blackRose.influence>=60
        if(domesticCrisis && politicalBase && rng.nextInt(100)<12) {
            noxus.leader="斯维因与崔法利议会（模拟分支）"
            noxus.government="崔法利议会制；由力量、远见与狡诈三种权力原则构成（模拟分支）"
            noxus.rulingBloc="斯维因、德莱厄斯与身份未公开的第三席代表"
            noxus.opposition="黑色玫瑰、旧贵族与地方总督之间的持续权力竞争"
            noxus.policy="重建军政秩序，整合军团并防范秘密势力干预"
            noxus.internalTension=(noxus.internalTension+8).coerceAtMost(100)
            noxus.stability=(noxus.stability-4).coerceAtLeast(0)
            val council=groups.firstOrNull { it.id=="noxus_trifarix" }
            if(council!=null) {
                council.name="崔法利议会"
                council.leader="斯维因、德莱厄斯与身份未公开的第三席代表"
                council.agenda="以力量、远见与狡诈三种原则维系帝国统治"
                council.influence=78; council.resources=72; council.publicSupport=58; council.status="新政权建立"
            }
            swain.role="崔法利议会统治者"
            swain.status="在诺克萨斯战后政治危机中推翻达克威尔统治，建立崔法利议会"
            events.add("【诺克萨斯政权更替·模拟分支】第 $day 日：战后军政危机与派系压力促成斯维因推翻达克威尔统治，崔法利议会建立。第三席身份不在模拟中擅自指定；此事件的具体时点与过程属于模拟分支。")
            p.edit().putBoolean(KEY_SWAIN_COUP,true).apply()
            saveFactions(p,factions); saveOrganizations(p,groups); saveHeroes(p,heroes)
        }
    }

    private fun tickSocialRelations(day:Long,pop:MutableList<Resident>,events:MutableList<String>,rng:Random) {
        if(day%30L!=0L)return
        val living=pop.filter{it.alive}
        living.filter{it.friendIds.size<5}.shuffled(rng).take((living.size/18).coerceAtLeast(1)).forEach { person ->
            val candidate=living.filter{it.id!=person.id && it.region==person.region && it.friendIds.size<5 && person.id !in it.friendIds && it.job==person.job}.randomOrNull()
            if(candidate!=null && rng.nextInt(100)<22) {
                person.friendIds.add(candidate.id);candidate.friendIds.add(person.id)
                recordLife(person,day,"与"+candidate.name+"结为朋友，彼此开始建立信任。")
                recordLife(candidate,day,"与"+person.name+"结为朋友，彼此开始建立信任。")
                events.add("【社会关系·模拟】第 "+day+" 日："+person.name+"与"+candidate.name+"在"+person.region+"成为朋友。")
            }
        }
        living.filter{it.job=="学徒" && it.mentorId<0}.shuffled(rng).take((living.size/30).coerceAtLeast(1)).forEach { apprentice ->
            val teacher=living.filter{it.id!=apprentice.id && it.region==apprentice.region && it.skill>=15 && it.job in listOf("工匠","护卫") && it.studentIds.size<4}.randomOrNull()
            if(teacher!=null && rng.nextInt(100)<35) {
                apprentice.mentorId=teacher.id
                if(apprentice.id !in teacher.studentIds)teacher.studentIds.add(apprentice.id)
                recordLife(apprentice,day,"拜"+teacher.name+"为师，开始向其学习技艺。")
                recordLife(teacher,day,"收"+apprentice.name+"为徒，承担传授技艺的责任。")
                events.add("【师徒关系·模拟】第 "+day+" 日："+apprentice.name+"拜"+teacher.name+"为师，地点："+apprentice.region+"。")
            }
        }
        living.filter{it.mentorId>=0}.forEach { student ->
            val mentor=living.firstOrNull{it.id==student.mentorId}
            if(mentor==null) {
                student.mentorId=-1
                recordLife(student,day,"师长离世或失联，独自继续学习。")
            } else if(student.skill<100 && rng.nextInt(100)<18) {
                student.skill=(student.skill+1).coerceAtMost(100)
            }
        }
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
        val groups=organizations(p)
        val factions=factionStates(p)
        val relations=diplomacy(p)
        val heroes=heroAgents(p)
        val triggerDays=runCatching { JSONObject(p.getString(KEY_LORE_TRIGGER_DAYS,"{}")) }.getOrDefault(JSONObject())
        fun group(id:String)=groups.firstOrNull { it.id==id }
        fun ready(id:String):Boolean=when(id) {
            "ionia_war" -> p.getBoolean(KEY_IONIA_WAR_ENDED,false)
            "demacia_mage" -> (group("demacia_mage_resistance")?.influence?:0)>=58 || (factions["德玛西亚"]?.internalTension?:0)>=58
            "shurima_rise" -> (group("shurima_sun_disc")?.influence?:0)>=64 && (group("shurima_city_leagues")?.influence?:0)>=45
            "ruination" -> false // The Ruination is ancient background history, not a future event to replay in this era.
            "piltover_zaun" -> day>=180L && (group("zaun_chem_barons")?.influence?:0)>=78 && (group("zaun_firelights")?.publicSupport?:0)>=55
            "freljord" -> (markets["弗雷尔卓德"]?.food?:1000)<90 && (group("freljord_winter_claw")?.influence?:0)>=60
            "bilgewater" -> (group("bilgewater_captains")?.influence?:0)>=64 && (group("bilgewater_sailors")?.influence?:0)>=58
            "ixtal" -> day>=240L && (group("ixtal_council")?.influence?:0)>=82 && (group("ixtal_council")?.publicSupport?:0)<65
            else -> false
        }
        // Pulse day values are minimum simulation milestones, not official canon dates.
        lorePulses.filter { it.id !in triggered && day>=it.day && ready(it.id) }.forEach { pulse ->
            val market=markets[pulse.region]
            if(market!=null) market.food=(market.food-pulse.foodLoss).coerceAtLeast(0)
            val affected=pop.filter { it.alive && it.region==pulse.region }
            affected.forEach { r ->
                r.health=(r.health-pulse.healthLoss).coerceAtLeast(0)
                recordLife(r,day,"经历模拟世界事件「${pulse.title}」的冲击，身处${pulse.region}。")
            }
            val moved=affected.filter { rng.nextInt(100)<18 }
            val destinationScores=residentRegions.filter { it!=pulse.region && markets.containsKey(it) }.associateWith { region ->
                val market=markets[region]!!
                market.food*2+market.wealth/80+(market.stock["粮食"]?:0)/3-pop.count { it.alive&&it.region==region }*3
            }
            moved.forEach { r ->
                val destination=destinationScores.maxByOrNull { it.value+rng.nextInt(18) }?.key ?: return@forEach
                val origin=r.region
                r.region=destination
                r.settlement=defaultSettlement(destination,r.id)
                recordLife(r,day,"因「${pulse.title}」引发的动荡，从${origin}迁往${destination}；目的地依据粮食供应、市场财富与人口压力选择。")
            }
            events.add("【世界演化·模拟分支】第 $day 日 · ${pulse.title}：${pulse.description} 影响${affected.size}名居民，${moved.size}人迁离${pulse.region}；目的地依据生计条件选择。事件不是预设的官方正史时间表。")
            triggered.add(pulse.id)
            triggerDays.put(pulse.id,day)
            if(pulse.id=="shurima_rise") {
                heroes.firstOrNull { it.name=="阿兹尔" }?.let { it.role="复苏的飞升者皇帝"; it.status="太阳圆盘仪式后复苏，开始寻求重建恕瑞玛" }
                heroes.firstOrNull { it.name=="泽拉斯" }?.let { it.role="苏醒的飞升者"; it.status="古代封印与帝国复兴引发其力量重新进入当代历史" }
                factions["恕瑞玛"]?.let { shurima ->
                    shurima.government="多个绿洲城邦与新兴帝国复兴势力并存；尚未形成对全境的统一统治"
                    shurima.rulingBloc="阿兹尔的复兴核心、地方城邦统治者、部族与商路力量"
                    shurima.opposition="拒绝帝国统合的城邦、地方首领、泽拉斯势力及其他竞争者"
                    shurima.policy="围绕水源、遗迹、地方自治与帝国合法性展开政治竞争"
                    shurima.leader="阿兹尔（复苏后提出帝国复兴主张；实际统治范围由模拟行动决定）"
                    shurima.internalTension=(shurima.internalTension+8).coerceAtMost(100)
                }
                saveHeroes(p,heroes);saveFactions(p,factions)
                events.add("【恕瑞玛历史转折·模拟分支】阿兹尔与泽拉斯重新进入当代事件链；复苏的具体过程和结果属于本世界模拟，不代表官方正史。")
            }
        }
        p.edit().putString(KEY_LORE,JSONArray().apply { triggered.forEach { put(it) } }.toString()).putString(KEY_LORE_TRIGGER_DAYS,triggerDays.toString()).apply()
        saveOrganizations(p,groups)
        saveFactions(p,factions)
    }
    private data class LoreChainStage(val day:Long,val id:String,val title:String,val region:String,val foodDelta:Int,val healthDelta:Int,val migrationChance:Int)
    // Multi-stage event arcs; dates are simulation days, not canon chronology.
    // These are relative offsets from the root event, never absolute world-day dates.
    private val chainStages=listOf(
        LoreChainStage(30L,"ionia_front","战线扩大：艾欧尼亚村落受到波及","艾欧尼亚",-45,-2,24),
        LoreChainStage(90L,"ionia_resistance","抵抗网络形成：当地居民开始组织互助","艾欧尼亚",20,0,0),
        LoreChainStage(30L,"ionia_recovery","战后重建或长期创伤：结果取决于人口与资源","艾欧尼亚",30,1,0),
        LoreChainStage(30L,"ruination_spread","黑雾扩散：港口周边出现新的失踪与逃亡","比尔吉沃特",-60,-4,35),
        LoreChainStage(90L,"ruination_survivors","幸存者组织临时避难与补给","比尔吉沃特",15,0,0),
        LoreChainStage(150L,"ruination_aftermath","危机余波：商路恢复或继续衰退","比尔吉沃特",25,1,0),
        LoreChainStage(30L,"zaun_incident","祖安工业事故：底层居民承受健康与经济损失","祖安",-20,-3,12),
        LoreChainStage(90L,"zaun_response","社区互助与工坊自救","祖安",15,0,0),
        LoreChainStage(150L,"zaun_reform","治理与技术路线变化，模拟社会恢复","祖安",20,1,0)
    )
    private fun applyLoreChainStage(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        val done=runCatching { JSONArray(p.getString("lore_chain_done","[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() } }.getOrDefault(mutableSetOf())
        val triggered=runCatching { JSONArray(p.getString(KEY_LORE,"[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet())
        val triggerDays=runCatching { JSONObject(p.getString(KEY_LORE_TRIGGER_DAYS,"{}")) }.getOrDefault(JSONObject())
        chainStages.filter { stage ->
            val root=when {
                stage.id=="ionia_recovery" -> "ionia_war"
                stage.id.startsWith("ionia_") -> "ionia_invasion"
                stage.id.startsWith("ruination_") -> "ruination"
                stage.id.startsWith("zaun_") -> "piltover_zaun"
                else -> ""
            }
            val rootDay=if(root=="ionia_invasion") p.getLong(KEY_IONIA_INVASION_DAY,-1L) else triggerDays.optLong(root,-1L)
            val rootReady=if(root=="ionia_invasion") p.getBoolean(KEY_IONIA_INVASION_STARTED,false) else root in triggered
            rootDay>=0L && day==rootDay+stage.day && stage.id !in done && rootReady
        }.forEach { stage ->
            markets[stage.region]?.let { it.food=(it.food+stage.foodDelta).coerceAtLeast(0) }
            val residents=pop.filter { it.alive && it.region==stage.region }
            residents.forEach { r -> r.health=(r.health+stage.healthDelta).coerceIn(0,100); recordLife(r,day,"受到历史事件「${stage.title}」影响，身处${stage.region}。") }
            val displaced=residents.filter { rng.nextInt(100)<stage.migrationChance }
            val localPopulation=residentRegions.associateWith { region -> pop.count { it.alive && it.region==region } }.toMutableMap()
            val saferDestinations=residentRegions.filter { it!=stage.region && markets.containsKey(it) }
                .sortedByDescending { region ->
                    val market=markets[region]!!
                    val crowdPenalty=(localPopulation[region]?:0)*3
                    market.food*2 + market.wealth/80 + (market.stock["粮食"]?:0)/3 - crowdPenalty
                }.take(4)
            displaced.forEach { r ->
                val destination=saferDestinations.maxByOrNull { region ->
                    val market=markets[region]!!
                    market.food*2 + market.wealth/80 + (market.stock["粮食"]?:0)/3 - (localPopulation[region]?:0)*3 + rng.nextInt(18)
                } ?: return@forEach
                val origin=r.region
                r.region=destination
                r.settlement=defaultSettlement(destination,r.id)
                localPopulation[destination]=(localPopulation[destination]?:0)+1
                recordLife(r,day,"因「${stage.title}」从${origin}迁往${destination}，选择当时粮食供应与生计条件相对较好的可居住聚落。")
            }
            events.add("【历史事件链·模拟】第 ${day} 日 ${stage.title}；${stage.region}影响${residents.size}人，迁徙${displaced.size}人；目的地依据粮食库存、市场财富与人口压力选择，而非固定轮转。")
            done.add(stage.id)
        }
        p.edit().putString("lore_chain_done",JSONArray().apply { done.forEach { put(it) } }.toString()).apply()
    }
    // The simulation includes the ten core political/cultural regions plus Bandle City and the Shadow Isles; the Void is a transregional threat, not a conventional state.
    private val regions=listOf("德玛西亚","诺克萨斯","艾欧尼亚","皮尔特沃夫","祖安","弗雷尔卓德","恕瑞玛","巨神峰","比尔吉沃特","以绪塔尔","班德尔城","暗影岛")
    // Only these regions have conventional territorial political administrations in this simplified polity layer.
    private val polityRegions=regions.filterNot { it=="班德尔城" || it=="暗影岛" }
    // Ordinary human resident generation and migration must not treat the spirit-realm Bandle City or cursed Shadow Isles as normal human provinces.
    private val residentRegions=regions.filterNot { it=="班德尔城" || it=="暗影岛" }
    // Canon-named places are used only where Riot lore identifies them; generic settlements are explicitly simulation placeholders.
    private val settlementSeeds=mapOf("德玛西亚" to listOf("德玛西亚雄都","高银沼","未命名边境村镇（模拟）"),"诺克萨斯" to listOf("不朽堡垒","诺克萨斯军团驻地（模拟）","帝国行省聚落（模拟）"),"艾欧尼亚" to listOf("普雷西典","均衡教派寺院（泛称）","艾欧尼亚地方村落（模拟）"),"皮尔特沃夫" to listOf("皮尔特沃夫城","太阳之门","海港与工坊区（模拟）"),"祖安" to listOf("海滨长廊区（Promenade）","中层区（Entresol）","污水坑区（Sump）"),"弗雷尔卓德" to listOf("霜卫要塞","拉克尔斯泰克","阿瓦罗萨／凛冬之爪流动营地（模拟）"),"恕瑞玛" to listOf("太阳圆盘遗址","纳施拉美","绿洲与商路聚落（模拟）"),"巨神峰" to listOf("烈阳教派圣所（模拟）","皎月信徒隐居地（模拟）","山麓朝圣聚落（模拟）"),"比尔吉沃特" to listOf("屠夫之桥","屠宰码头","布胡鲁神庙"),"以绪塔尔" to listOf("以绪奥肯","奥姆卡（Ohmka）","索伦（Xolen）／帕雷萨（Paretha）"),"班德尔城" to listOf("班德尔城","符文之地班德尔入口（模拟）","班德尔居民聚落（模拟）"),"暗影岛" to listOf("福光岛遗迹（Blessed Isles）","赫利亚遗迹（Helia）","黑雾侵蚀区域（模拟）"))
    private data class SettlementMeta(val type:String,val habitable:Boolean,val note:String)
    private fun settlementMeta(region:String,name:String):SettlementMeta = when {
        region=="暗影岛" && name.contains("福光岛") -> SettlementMeta("历史遗址",false,"福光岛是暗影岛灾变前的历史名称；不作为普通居民居住地")
        region=="暗影岛" && name.contains("赫利亚") -> SettlementMeta("历史遗址",false,"赫利亚为灾变后的遗址，不作为普通居民聚落")
        region=="暗影岛" -> SettlementMeta("诅咒危险区",false,"黑雾侵蚀区域；普通居民不会在此建立常规定居点")
        region=="班德尔城" && name.contains("入口") -> SettlementMeta("跨界入口",false,"连接班德尔城与符文之地的特殊通道；入口位置和显现状态并非固定")
        region=="班德尔城" -> SettlementMeta("约德尔人特殊领域",false,"班德尔城属于约德尔人的特殊家园，不纳入普通人类居民人口")
        name=="太阳之门" -> SettlementMeta("地标／海上通道",false,"皮尔特沃夫的重要海上通道与基础设施，不是独立居民聚落")
        name=="太阳圆盘遗址" -> SettlementMeta("历史遗址",false,"古代恕瑞玛文明遗址，不作为普通居民聚落")
        name=="布胡鲁神庙" || name.contains("圣所") || name.contains("寺院") -> SettlementMeta("神庙／宗教据点",true,"以信仰活动为核心的据点；人口统计仅代表模拟常住者")
        name=="拉克尔斯泰克" -> SettlementMeta("朝圣地／部族据点",true,"弗雷尔卓德重要部族与朝圣活动相关地点；常住规模属于模拟设定")
        name.contains("营地") || name.contains("驻地") -> SettlementMeta("军事／流动营地",true,"人口随部族、军团或季节活动变化；仅作模拟人口统计")
        name.contains("村镇") || name.contains("村落") || name.contains("聚落") || name.contains("绿洲") -> SettlementMeta("村庄／聚落",true,"模拟补充的普通居住点，并非官方地图对该名称的确认")
        else -> SettlementMeta("城市／城区",true,"以官方已知地名为基础；人口与职业结构属于模拟数据")
    }
    private fun defaultSettlement(region:String,id:Int):String {
        val options=(settlementSeeds[region] ?: listOf(region+"聚落")).filter { settlementMeta(region,it).habitable }
        return options.getOrElse(Math.floorMod(id,options.size.coerceAtLeast(1))) { region+"聚落（模拟）" }
    }
    private val jobs=listOf("农夫","工匠","商贩","护卫","学徒","采集者","旅人","伐木工","矿工","药师","魔法材料采集者","海克斯技师")
    private val goals=listOf("积攒财富","寻找亲人","学习技艺","改善家园","探索远方","获得认可","守护家人")
    private val names=listOf("艾琳","卡伦","米娅","托伦","塞拉","诺安","伊芙","莱恩","凯娅","罗克","维娜","奥伦","塔莎","梅林","德拉","阿斯","菲恩")
    private val goods=listOf("粮食","木材","矿石","药材","工具","魔法材料","海克斯器件")
    private data class Market(var food:Int,var wealth:Int,var stock:MutableMap<String,Int> = mutableMapOf()) {
        fun json(region:String)=JSONObject().put("region",region).put("food",food).put("wealth",wealth)
            .put("stock",JSONObject().apply { stock.forEach { (k,v)->put(k,v) } })
    }
    private data class Caravan(
        val id:Int,var from:String,var to:String,var cargo:Int,var daysRemaining:Int,
        var owner:String,var good:String="粮食",var alive:Boolean=true
    ) {
        fun json()=JSONObject().put("id",id).put("from",from).put("to",to).put("cargo",cargo)
            .put("daysRemaining",daysRemaining).put("owner",owner).put("good",good).put("alive",alive)
        companion object {
            fun from(j:JSONObject)=Caravan(j.optInt("id"),j.optString("from"),j.optString("to"),
                j.optInt("cargo"),j.optInt("daysRemaining"),j.optString("owner","独立商队"),
                j.optString("good","粮食"),j.optBoolean("alive",true))
        }
    }
    private fun regionalGoods(region:String):Map<String,Int> = when(region) {
        "皮尔特沃夫" -> mapOf("工具" to 3,"海克斯器件" to 5,"矿石" to 1)
        "祖安" -> mapOf("工具" to 2,"海克斯器件" to 2,"药材" to 1)
        "德玛西亚" -> mapOf("粮食" to 3,"木材" to 2,"工具" to 1)
        "诺克萨斯" -> mapOf("工具" to 2,"矿石" to 2,"粮食" to 1)
        "艾欧尼亚" -> mapOf("药材" to 4,"木材" to 2,"魔法材料" to 2)
        "弗雷尔卓德" -> mapOf("矿石" to 3,"木材" to 1,"药材" to 1)
        "恕瑞玛" -> mapOf("魔法材料" to 3,"矿石" to 2,"粮食" to 1)
        "比尔吉沃特" -> mapOf("药材" to 2,"木材" to 1,"矿石" to 1)
        "以绪塔尔" -> mapOf("药材" to 3,"魔法材料" to 3,"木材" to 1)
        "巨神峰" -> mapOf("魔法材料" to 3,"矿石" to 1,"药材" to 1)
        else -> mapOf("木材" to 1,"药材" to 1)
    }
    private fun goodPrice(market:Market,good:String):Int {
        val stock=if(good=="粮食")market.food else market.stock[good]?:0
        val base=when(good){"海克斯器件"->8;"魔法材料"->7;"工具"->4;"矿石"->3;"药材"->3;"木材"->2;else->1}
        val scarcityFactor=150.0/(stock+50).coerceAtLeast(1)
        return (base*scarcityFactor).toInt().coerceIn(1,base*4)
    }
    private fun ensureGoods(market:Market,region:String) {
        goods.forEach { good -> market.stock.putIfAbsent(good,if(good=="粮食")market.food else 25+regionalGoods(region).getOrDefault(good,0)*5) }
    }
    // Regional specializations are simulation modifiers inspired by lore, not canonical game stats.
    private const val KEY_ECOLOGY="regional_ecology_state"
    private data class Ecology(
        val region:String,val climate:String,var temperature:Int,var water:Int,
        var vegetation:Int,var soil:Int,var wildlife:Int,var pollution:Int
    ) {
        fun json()=JSONObject().put("region",region).put("climate",climate)
            .put("temperature",temperature).put("water",water).put("vegetation",vegetation)
            .put("soil",soil).put("wildlife",wildlife).put("pollution",pollution)
    }
    // Environmental baselines are simulation abstractions, not official numeric geography.
    private fun ecologySeeds():Map<String,Ecology> = listOf(
        Ecology("德玛西亚","温带丘陵与农耕地",58,68,72,76,62,12),
        Ecology("诺克萨斯","多样化温带大陆与边境",52,58,55,60,55,20),
        Ecology("艾欧尼亚","群岛森林、湿地与灵性自然",62,78,88,74,84,8),
        Ecology("皮尔特沃夫","沿海高密度城市",66,52,28,38,30,34),
        Ecology("祖安","地下城层与工业污染环境",48,34,18,25,22,82),
        Ecology("弗雷尔卓德","寒冷苔原、冰原与短暂夏季",12,42,26,35,58,5),
        Ecology("恕瑞玛","干旱沙漠与绿洲",82,18,20,28,38,10),
        Ecology("巨神峰","高海拔山地",24,48,42,46,56,4),
        Ecology("比尔吉沃特","热带海岛与沿海生态",78,76,68,52,82,24),
        Ecology("以绪塔尔","热带雨林与元素环境",80,88,94,82,92,7),
        Ecology("班德尔城","精神领域特殊生态",60,70,80,70,80,0),
        Ecology("暗影岛","黑雾侵蚀的诅咒生态",30,5,2,4,8,100)
    ).associateBy{it.region}
    private fun ecologyState(p:android.content.SharedPreferences):MutableMap<String,Ecology>{
        val loaded=runCatching{
            val a=JSONArray(p.getString(KEY_ECOLOGY,"[]"))
            (0 until a.length()).map{ i ->
                val j=a.getJSONObject(i); val region=j.optString("region")
                Ecology(region,j.optString("climate","未知环境"),j.optInt("temperature",50),
                    j.optInt("water",50),j.optInt("vegetation",50),j.optInt("soil",50),
                    j.optInt("wildlife",50),j.optInt("pollution",0))
            }.associateBy{it.region}.toMutableMap()
        }.getOrDefault(mutableMapOf())
        ecologySeeds().forEach{(region,seed)->if(!loaded.containsKey(region))loaded[region]=seed.copy()}
        return loaded
    }
    private fun saveEcology(p:android.content.SharedPreferences,state:Map<String,Ecology>){
        p.edit().putString(KEY_ECOLOGY,JSONArray().apply{state.values.forEach{put(it.json())}}.toString()).apply()
    }
    private fun tickEcology(day:Long,p:android.content.SharedPreferences,pop:List<Resident>,events:MutableList<String>,rng:Random,state:MutableMap<String,Ecology>){
        if(day%30L!=0L)return
        state.values.forEach{e->
            val residents=pop.count{it.alive&&it.region==e.region}
            val pressure=(residents/45).coerceIn(0,5)
            val season=when(((day/90L)%4L).toInt()){0->-2;1->2;2->3;else->-3}
            val temperatureShift=when(e.region){"弗雷尔卓德"->season*2;"恕瑞玛"->season;"比尔吉沃特","以绪塔尔"->season/2;else->season}
            e.temperature=(e.temperature+temperatureShift+rng.nextInt(3)-1).coerceIn(0,100)
            val waterShift=when(e.region){
                "恕瑞玛"->-1+(if(day%360L in 90L..179L)2 else 0)
                "弗雷尔卓德"->if(day%360L in 90L..179L)2 else -1
                "以绪塔尔","艾欧尼亚","比尔吉沃特"->1
                "祖安"->-1
                else->0
            }
            e.water=(e.water+waterShift-pressure/2+rng.nextInt(3)-1).coerceIn(0,100)
            val plantRecovery=when(e.region){"以绪塔尔","艾欧尼亚"->2;"德玛西亚","比尔吉沃特"->1;else->0}
            e.vegetation=(e.vegetation+plantRecovery-pressure+rng.nextInt(3)-1).coerceIn(0,100)
            e.soil=(e.soil+(if(e.water>45&&e.vegetation>40)1 else 0)-if(pressure>=3)1 else 0).coerceIn(0,100)
            e.wildlife=(e.wildlife+(if(e.vegetation>55&&e.water>35)1 else 0)-if(pressure>=3)1 else 0).coerceIn(0,100)
            val pollutionChange=when(e.region){
                "祖安"->if(rng.nextInt(100)<35)1 else 0
                "皮尔特沃夫"->if(rng.nextInt(100)<18)1 else 0
                "比尔吉沃特"->if(rng.nextInt(100)<12)1 else 0
                "艾欧尼亚","以绪塔尔","弗雷尔卓德"->if(rng.nextInt(100)<30)-1 else 0
                else->0
            }
            e.pollution=(e.pollution+pollutionChange).coerceIn(0,100)
            if(e.region=="暗影岛"){e.vegetation=0;e.wildlife=0;e.pollution=100}
            if(e.region=="班德尔城"){e.pollution=0}
            if(day%360L==0L && (e.water<20||e.vegetation<20||e.pollution>75)){
                val cause=when{e.pollution>75->"污染累积";e.water<20->"水源压力";else->"植被衰退"}
                events.add("【生态变化·模拟】第 "+day+" 日："+e.region+"出现"+cause+"，可能影响当地生计、健康与迁徙选择。")
                recordLocalStory(pop,day,e.region,"当地出现"+cause+"；居民开始担心水源、土地、污染或生计条件对未来的影响。",rng,3,setOf("农夫","采集者","药师","伐木工"))
            }
        }
        saveEcology(p,state)
    }
    private const val KEY_FAUNA="regional_fauna_state"
    private data class FaunaPopulation(
        val id:String,val region:String,val guild:String,val role:String,
        var abundance:Int,var health:Int
    ){
        fun json()=JSONObject().put("id",id).put("region",region).put("guild",guild)
            .put("role",role).put("abundance",abundance).put("health",health)
    }
    // Guilds are broad ecological roles, not invented canon species or official census counts.
    private fun faunaSeeds():List<FaunaPopulation>{
        val seeds=mutableListOf<FaunaPopulation>()
        fun add(region:String,guild:String,role:String,abundance:Int){
            seeds.add(FaunaPopulation(region+"|"+guild,region,guild,role,abundance,70))
        }
        listOf("德玛西亚","诺克萨斯").forEach{r->
            add(r,"草食兽群","植被消费者",62);add(r,"林地捕食者","中型捕食者",30);add(r,"鸟类与小型动物","种子传播与昆虫控制",55)
        }
        add("艾欧尼亚","森林草食动物","植被消费者",72);add("艾欧尼亚","林地捕食者","中型捕食者",34)
        add("艾欧尼亚","灵性生态生物","与精神领域关联的特殊生态位",28)
        add("皮尔特沃夫","沿岸鸟类","沿岸食物网",26);add("皮尔特沃夫","城市小型动物","城市适应型动物",35)
        add("祖安","耐污染小型动物","受污染环境筛选的动物",16);add("祖安","地下捕食者","地下食物网",9)
        add("弗雷尔卓德","苔原草食兽群","寒地植被消费者",48);add("弗雷尔卓德","寒地大型捕食者","顶级捕食者",24)
        add("弗雷尔卓德","冰原鸟类","季节性活动动物",40)
        add("恕瑞玛","沙漠草食动物","稀疏植被消费者",32);add("恕瑞玛","沙漠捕食者","干旱区捕食者",22)
        add("恕瑞玛","绿洲水生与鸟类","依赖绿洲水源",28)
        add("巨神峰","高山动物","高海拔植被消费者",38);add("巨神峰","山地捕食者","山地捕食者",20)
        add("巨神峰","高山灵性生物","与巨神峰魔法环境相关",18)
        add("比尔吉沃特","近海鱼群","海洋食物网基础",75);add("比尔吉沃特","海鸟与沿岸动物","海岸生态",62)
        add("比尔吉沃特","大型海洋捕食者","海洋捕食者",30)
        add("以绪塔尔","雨林草食动物","雨林植被消费者",82);add("以绪塔尔","雨林捕食者","雨林捕食者",42)
        add("以绪塔尔","元素环境适应生物","特殊环境生态位",34)
        add("班德尔城","精神领域小型生物","特殊领域生态",45)
        add("暗影岛","黑雾衍生灵体","非自然灵体活动",12)
        return seeds
    }
    private fun faunaState(p:android.content.SharedPreferences):MutableList<FaunaPopulation>{
        val loaded=runCatching{
            val a=JSONArray(p.getString(KEY_FAUNA,"[]"))
            (0 until a.length()).map{i->val j=a.getJSONObject(i)
                FaunaPopulation(j.optString("id"),j.optString("region"),j.optString("guild"),
                    j.optString("role"),j.optInt("abundance",0),j.optInt("health",50))
            }.toMutableList()
        }.getOrDefault(mutableListOf())
        val ids=loaded.map{it.id}.toSet()
        faunaSeeds().forEach{seed->if(seed.id !in ids)loaded.add(seed.copy())}
        return loaded
    }
    private fun saveFauna(p:android.content.SharedPreferences,state:List<FaunaPopulation>){
        p.edit().putString(KEY_FAUNA,JSONArray().apply{state.forEach{put(it.json())}}.toString()).apply()
    }
    private fun tickFauna(day:Long,p:android.content.SharedPreferences,ecology:Map<String,Ecology>,pop:List<Resident>,events:MutableList<String>,rng:Random,fauna:MutableList<FaunaPopulation>){
        if(day%30L!=0L)return
        fauna.groupBy{it.region}.forEach{(region,groups)->
            val e=ecology[region]?:return@forEach
            val herbivores=groups.filter{it.role.contains("植被消费者")||it.role.contains("草食")}.sumOf{it.abundance}
            val predators=groups.filter{it.role.contains("捕食者")}.sumOf{it.abundance}
            groups.forEach{f->
                val humanPressure=(pop.count{it.alive&&it.region==region}/45).coerceIn(0,4)
                val habitat=when(f.role){
                    "非自然灵体活动"->if(e.pollution>=80||e.vegetation<=5)75 else 20
                    "特殊领域生态"->if(region=="班德尔城")75 else 35
                    "与精神领域关联的特殊生态位","与巨神峰魔法环境相关","特殊环境生态位"->(e.vegetation+e.water+e.wildlife)/3
                    "海洋食物网基础","大型海洋捕食者","海岸生态","沿岸食物网"->(e.water+e.wildlife)/2
                    "依赖绿洲水源"->(e.water+e.vegetation)/2
                    "寒地植被消费者","顶级捕食者","季节性活动动物"->(100-e.temperature+e.water)/2
                    "干旱区捕食者","稀疏植被消费者"->(100-e.water+e.temperature)/2
                    else->(e.vegetation+e.water+e.wildlife)/3
                }.coerceIn(0,100)
                val foodSupport=when{
                    f.role.contains("捕食者")->(herbivores/3).coerceAtMost(100)
                    f.role.contains("植被消费者")||f.role.contains("草食")->e.vegetation
                    f.role.contains("海洋")->e.water
                    else->(e.vegetation+e.water)/2
                }
                val target=((habitat*2+foodSupport)/3).coerceIn(0,100)
                val drift=when{
                    f.abundance<target-12->2
                    f.abundance>target+12->-2
                    else->0
                }
                val stress=if(humanPressure>=3)1 else 0
                val predation=when{f.role.contains("捕食者")&&herbivores<35->2;(f.role.contains("植被消费者")||f.role.contains("草食"))&&predators>45->2;else->0}
                val special=if(region=="暗影岛") {
                    if(f.role=="非自然灵体活动") 1 else -4
                } else 0
                f.abundance=(f.abundance+drift-stress-predation+special+rng.nextInt(3)-1).coerceIn(0,100)
                f.health=(f.health+(if(target>55)1 else -1)-stress).coerceIn(0,100)
                if(f.abundance<=8&&target>=45&&day%360L==0L){
                    events.add("【生态警讯·模拟】第 "+day+" 日："+region+"的"+f.guild+"数量降至极低水平，栖息地或食物网承受压力。")
                    recordLocalStory(pop,day,region,"当地"+f.guild+"数量降至极低水平；依赖狩猎、采集或野外通行的居民开始调整生计与活动范围。",rng,2,setOf("采集者","农夫","护卫","药师"))
                }
            }
            val meanAbundance=if(groups.isEmpty())0 else groups.map{it.abundance}.average().toInt()
            e.wildlife=((e.wildlife*2+meanAbundance)/3).coerceIn(0,100)
            if(herbivores>110 && region!="暗影岛" && region!="班德尔城") e.vegetation=(e.vegetation-1).coerceAtLeast(0)
        }
        saveFauna(p,fauna)
    }
    fun faunaDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val fauna=faunaState(p)
        return fauna.groupBy{it.region}.toSortedMap().map{(region,groups)->
            "【"+region+" · 野生生物群落】\\n"+groups.joinToString("\\n"){it.guild+"："+it.abundance+"/100（健康 "+it.health+"/100；"+it.role+"）"}+
            "\\n说明：这是按生态功能划分的模拟种群指数，不是官方物种名录或真实数量；暗影岛灵体、班德尔城特殊领域生物不属于普通野生动物。"
        }
    }
    private fun foodBonus(region:String,ecology:Map<String,Ecology>):Int {
        val base=when(region) {
            "德玛西亚"->1;"弗雷尔卓德"->0;"恕瑞玛","比尔吉沃特"->-1
            "以绪塔尔"->1;"暗影岛"->-2;else->0
        }
        val e=ecology[region]?:return base
        val environmental=(e.water+e.vegetation+e.soil)/3
        val modifier=when{environmental>=75->2;environmental>=60->1;environmental<20->-2;environmental<35->-1;else->0}
        return (base+modifier-if(e.pollution>=75)1 else 0).coerceIn(-3,3)
    }
    fun ecologyDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val state=ecologyState(p)
        val all=people(p)
        return state.values.sortedBy{it.region}.map{e->
            val population=all.count{it.alive&&it.region==e.region}
            val harvest=when{(e.water+e.vegetation+e.soil)/3>=75->"自然条件较好";(e.water+e.vegetation+e.soil)/3<30->"自然条件严峻";else->"自然条件中等"}
            "【"+e.region+" · "+e.climate+"】\\n温度环境指数："+e.temperature+"/100\\n水源条件："+e.water+"/100\\n植被覆盖："+e.vegetation+"/100\\n土壤状况："+e.soil+"/100\\n野生生物承载："+e.wildlife+"/100\\n污染压力："+e.pollution+"/100\\n模拟普通居民："+population+"人\\n综合判断："+harvest+"\\n说明：指数仅用于模拟环境变化，不是官方地理测量；班德尔城与暗影岛属于特殊生态，不参与普通农耕逻辑。"
        }
    }

    
    // Approximate inter-region travel graph; values are simulation days, not canonical measurements.
    private val routeLinks=mapOf(
        "德玛西亚" to listOf("诺克萨斯" to 5,"皮尔特沃夫" to 4,"弗雷尔卓德" to 6),
        "诺克萨斯" to listOf("德玛西亚" to 5,"艾欧尼亚" to 7,"弗雷尔卓德" to 6,"恕瑞玛" to 8),
        "艾欧尼亚" to listOf("诺克萨斯" to 7,"比尔吉沃特" to 5),
        "皮尔特沃夫" to listOf("德玛西亚" to 4,"祖安" to 1,"比尔吉沃特" to 5),
        "祖安" to listOf("皮尔特沃夫" to 1,"比尔吉沃特" to 6),
        "弗雷尔卓德" to listOf("德玛西亚" to 6,"诺克萨斯" to 6),
        "恕瑞玛" to listOf("诺克萨斯" to 8,"以绪塔尔" to 6,"比尔吉沃特" to 7,"巨神峰" to 5),
        "巨神峰" to listOf("恕瑞玛" to 5,"以绪塔尔" to 6),
        "比尔吉沃特" to listOf("皮尔特沃夫" to 5,"祖安" to 6,"艾欧尼亚" to 5,"恕瑞玛" to 7),
        "以绪塔尔" to listOf("恕瑞玛" to 6,"巨神峰" to 6)
    )
    private fun routeDays(from:String,to:String):Int {
        if(from==to)return 0
        val distance=mutableMapOf(from to 0);val pending=mutableSetOf(from)
        while(pending.isNotEmpty()) {
            val current=pending.minByOrNull { distance[it]?:Int.MAX_VALUE } ?: break
            pending.remove(current)
            if(current==to)return distance[current]?:1
            routeLinks[current].orEmpty().forEach { (next,cost) ->
                val candidate=(distance[current]?:0)+cost
                if(candidate<(distance[next]?:Int.MAX_VALUE)){distance[next]=candidate;pending.add(next)}
            }
        }
        return 9
    }
    private fun beginTravel(r:Resident,destination:String,day:Long,events:MutableList<String>,purpose:String) {
        if(r.travelDaysRemaining>0 || destination==r.region)return
        r.travelOrigin=r.region;r.travelDestination=destination
        r.travelDaysRemaining=routeDays(r.region,destination).coerceAtLeast(1)
        r.travelPurpose=purpose;r.jobBeforeTravel=r.job;r.job="旅人"
        recordLife(r,day,"从${r.region}出发，踏上前往${destination}的旅途，目的：${purpose}，预计约${r.travelDaysRemaining}个模拟日。")
        events.add("【旅行·模拟】第 $day 日：${r.name}离开${r.region}，前往${destination}。")
    }
    private fun beginHouseholdTravel(r:Resident,destination:String,day:Long,events:MutableList<String>,purpose:String,pop:List<Resident>) {
        if(r.travelDaysRemaining>0 || destination==r.region) return
        val household=pop.filter { it.alive && it.householdId==r.householdId && it.region==r.region && it.settlement==r.settlement && it.travelDaysRemaining<=0 }
        if(household.isEmpty()) { beginTravel(r,destination,day,events,purpose); return }
        household.forEach { member -> beginTravel(member,destination,day,events,if(member.id==r.id) purpose else "随家庭迁徙：$purpose") }
        if(household.size>1) events.add("【家庭迁徙·模拟】第 $day 日：${household.size} 名家庭成员共同离开${r.region}，前往${destination}。")
    }
    private fun tickTravel(r:Resident,day:Long,events:MutableList<String>,rng:Random):Boolean {
        if(r.travelDaysRemaining<=0)return false
        r.travelDaysRemaining--
        if(rng.nextInt(100)<2) {
            r.health=(r.health-3).coerceAtLeast(0)
            recordLife(r,day,"旅途中遭遇意外，健康受损。")
            events.add("【旅途意外·模拟】第 $day 日：${r.name}在前往${r.travelDestination}的途中受伤。")
        }
        if(r.travelDaysRemaining<=0) {
            val destination=r.travelDestination;val origin=r.travelOrigin
            r.region=destination;r.settlement=defaultSettlement(destination,r.id)
            r.job=if(r.jobBeforeTravel.isNotBlank())r.jobBeforeTravel else "旅人"
            recordLife(r,day,"结束从${origin}到${destination}的旅程，抵达${r.settlement}。")
            events.add("【旅行抵达·模拟】第 $day 日：${r.name}抵达${destination}的${r.settlement}。")
            r.travelOrigin="";r.travelDestination="";r.travelPurpose="";r.jobBeforeTravel=""
        }
        return true
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
            for(i in 0 until a.length()) {
                val j=a.getJSONObject(i);val region=j.optString("region")
                val stock=mutableMapOf<String,Int>();val rawStock=j.optJSONObject("stock")
                if(rawStock!=null) goods.forEach { good -> stock[good]=rawStock.optInt(good,0) }
                val market=Market(j.optInt("food",300),j.optInt("wealth",1000),stock)
                ensureGoods(market,region);map[region]=market
            }
            if(map.isNotEmpty()) {
                regions.filter { it !in map }.forEach { region ->
                    val market=Market(300+Random(region.hashCode().toLong()).nextInt(250),1000)
                    ensureGoods(market,region);map[region]=market
                }
                saveMarkets(p,map)
                return map
            }
        }
        val fresh=regions.associateWith { region ->
            val market=Market(300+Random(region.hashCode().toLong()).nextInt(250),1000)
            ensureGoods(market,region);market
        }.toMutableMap()
        saveMarkets(p,fresh); return fresh
    }

    private fun caravans(p:android.content.SharedPreferences):MutableList<Caravan> {
        val raw=p.getString(KEY_CARAVANS,null) ?: return mutableListOf()
        return runCatching {
            val a=JSONArray(raw)
            (0 until a.length()).map { Caravan.from(a.getJSONObject(it)) }.toMutableList()
        }.getOrDefault(mutableListOf())
    }
    private fun saveCaravans(p:android.content.SharedPreferences,list:List<Caravan>) {
        p.edit().putString(KEY_CARAVANS,JSONArray().apply { list.forEach { put(it.json()) } }.toString()).apply()
    }
    private fun tickCaravans(day:Long,p:android.content.SharedPreferences,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random) {
        val active=caravans(p)
        active.filter { it.alive }.forEach { caravan ->
            caravan.daysRemaining--
            if(caravan.daysRemaining<=0) {
                val destination=markets[caravan.to]
                if(destination!=null) {
                    val settlementCost=caravan.cargo*goodPrice(destination,caravan.good)
                    if(caravan.good=="粮食") destination.food+=caravan.cargo
                    else destination.stock[caravan.good]=(destination.stock[caravan.good]?:0)+caravan.cargo
                    destination.wealth=(destination.wealth-settlementCost).coerceAtLeast(0)
                    events.add("【商队抵达·模拟】第 $day 日：${caravan.owner}从${caravan.from}运抵${caravan.to}，交付${caravan.cargo}单位${caravan.good}。")
                }
                caravan.alive=false
            } else if(rng.nextInt(100)<2) {
                val lost=maxOf(1,caravan.cargo/3)
                caravan.cargo=(caravan.cargo-lost).coerceAtLeast(0)
                events.add("【商路损失·模拟】第 $day 日：${caravan.from}至${caravan.to}的${caravan.owner}遭遇劫掠或事故，损失${lost}单位货物。")
                if(caravan.cargo==0) caravan.alive=false
            }
        }
        active.removeAll { !it.alive }
        if(day%30L==0L) {
            val candidates=routeLinks.flatMap { (from,links) -> links.map { (to,days) -> Triple(from,to,days) } }
                .distinctBy { minOf(it.first.hashCode(),it.second.hashCode()).toString()+":"+maxOf(it.first.hashCode(),it.second.hashCode()) }
                .filter { (from,to,_) ->
                    val a=markets[from];val b=markets[to]
                    a!=null && b!=null && b.wealth>=20 && goods.any { good ->
                        val sourceStock=if(good=="粮食")a.food else a.stock[good]?:0
                        val targetStock=if(good=="粮食")b.food else b.stock[good]?:0
                        val sourceMinimum=if(good=="粮食") 90 else 35
                        val targetMaximum=if(good=="粮食") 180 else 12
                        sourceStock >= sourceMinimum && targetStock <= targetMaximum
                    }
                }
                .sortedByDescending { (from,to,_) ->
                    val a=markets[from]!!;val b=markets[to]!!
                    goods.maxOf { good ->
                        val sourceStock=if(good=="粮食")a.food else a.stock[good]?:0
                        val targetStock=if(good=="粮食")b.food else b.stock[good]?:0
                        sourceStock-targetStock
                    }
                }
            candidates.take((2-active.size).coerceAtLeast(0)).forEach { (from,to,days) ->
                val source=markets[from] ?: return@forEach
                val destination=markets[to] ?: return@forEach
                val good=goods.filter { item ->
                    val sourceStock=if(item=="粮食")source.food else source.stock[item]?:0
                    val targetStock=if(item=="粮食")destination.food else destination.stock[item]?:0
                    val sourceMinimum=if(item=="粮食") 90 else 35
                    val targetMaximum=if(item=="粮食") 180 else 12
                    sourceStock >= sourceMinimum && targetStock <= targetMaximum
                }.maxByOrNull { item ->
                    val sourceStock=if(item=="粮食")source.food else source.stock[item]?:0
                    val targetStock=if(item=="粮食")destination.food else destination.stock[item]?:0
                    sourceStock-targetStock
                } ?: return@forEach
                val sourceStock=if(good=="粮食")source.food else source.stock[good]?:0
                val cargo=minOf(35,sourceStock-(if(good=="粮食")60 else 20),(destination.wealth/2).coerceAtLeast(1))
                if(cargo<8)return@forEach
                if(good=="粮食")source.food-=cargo else source.stock[good]=(source.stock[good]?:0)-cargo
                source.wealth+=cargo*goodPrice(source,good)
                val id=(active.maxOfOrNull { it.id } ?: 0)+1
                val owner=when(from) {
                    "皮尔特沃夫","祖安" -> "双城商贸行"
                    "比尔吉沃特" -> "海港自由商队"
                    "恕瑞玛" -> "绿洲商路商队"
                    else -> "独立粮食商队"
                }
                active.add(Caravan(id,from,to,cargo,days,owner,good))
                events.add("【商队出发·模拟】第 $day 日：${owner}从${from}出发前往${to}，运送${cargo}单位${good}，预计${days}个模拟日。")
            }
        }
        saveCaravans(p,active)
    }
    private fun saveMarkets(p: android.content.SharedPreferences, map: Map<String,Market>) {
        val a=JSONArray(); map.forEach { (name,m)->a.put(m.json(name)) }; p.edit().putString(KEY_MARKETS,a.toString()).apply()
    }
    private fun people(p: android.content.SharedPreferences): MutableList<Resident> {
        val raw=p.getString(KEY_PEOPLE,null)
        if(raw!=null) {
            val loaded=runCatching { val a=JSONArray(raw); (0 until a.length()).map { Resident.from(a.getJSONObject(it)) }.toMutableList() }.getOrDefault(mutableListOf())
            var changed=false
            loaded.forEach {
                if(it.settlement.isBlank() || !settlementMeta(it.region,it.settlement).habitable) {
                    val previous=it.settlement
                    it.settlement=defaultSettlement(it.region,it.id)
                    if(previous.isNotBlank()) recordLife(it,p.getLong(KEY_DAY,1L),"因原居住地点「"+previous+"」被标记为遗址、特殊领域或非居住地，迁至"+it.settlement+"。")
                    changed=true
                }
            }
            if(changed) savePeople(p,loaded)
            return loaded
        }
        val rng=Random(System.nanoTime() xor System.currentTimeMillis())
        val list=(0 until 240).map { i ->
            val region=residentRegions[i%residentRegions.size]
            val lineage=when {
                region=="弗雷尔卓德" && i%50==5 -> "冰裔血脉（人类）"
                region=="艾欧尼亚" && i%80==2 -> "瓦斯塔亚"
                region=="恕瑞玛" && i%100==6 -> "瓦斯塔亚"
                region=="比尔吉沃特" && i%100==8 -> "瓦斯塔亚"
                else -> "人类"
            }
            val lifespan=if(lineage=="瓦斯塔亚") 500+rng.nextInt(701) else 95+rng.nextInt(11)
            val resident=Resident(i,names[rng.nextInt(names.size)]+"·"+(i+1),region,if(lineage=="瓦斯塔亚")80+rng.nextInt(180) else 16+rng.nextInt(54),65+rng.nextInt(36),12+rng.nextInt(24),rng.nextInt(40),jobs[rng.nextInt(jobs.size)],goals[rng.nextInt(goals.size)],settlement=defaultSettlement(region,i),lineage=lineage,lifespanYears=lifespan)
            recordLife(resident,1,"世界模拟档案建立；当时${resident.age}岁，居住于${resident.region}的${resident.settlement}，职业为${resident.job}。")
            resident
        }.toMutableList()
        p.edit().putInt(KEY_NEXT_ID,240).apply()
        savePeople(p,list); return list
    }
    private const val KEY_HOUSEHOLDS="households_state"
    private data class Household(val id:Int,var foodReserve:Int=0,var sharedWealth:Int=0,var foundedDay:Long=1L)
    private fun householdStates(p:android.content.SharedPreferences,pop:MutableList<Resident>):MutableList<Household>{
        val raw=p.getString(KEY_HOUSEHOLDS,null)
        val stored=if(raw==null) mutableListOf() else runCatching {
            val a=JSONArray(raw); (0 until a.length()).map { i -> a.getJSONObject(i).let { Household(it.optInt("id"),it.optInt("foodReserve"),it.optInt("sharedWealth"),it.optLong("foundedDay",1L)) } }.toMutableList()
        }.getOrDefault(mutableListOf())
        var next=(stored.maxOfOrNull { it.id } ?: -1)+1
        val redirects=mutableMapOf<Int,Int>()
        fun root(id:Int):Int {
            var current=id
            while(redirects.containsKey(current)) current=redirects[current]!!
            return current
        }
        pop.filter { it.householdId>=0 }.forEach { r ->
            val partner=pop.firstOrNull { it.id==r.partnerId && it.householdId>=0 && it.region==r.region && it.settlement==r.settlement }
            if(partner!=null && root(partner.householdId)!=root(r.householdId)) {
                val keep=minOf(root(r.householdId),root(partner.householdId))
                val drop=maxOf(root(r.householdId),root(partner.householdId))
                redirects[drop]=keep
                redirects.entries.toList().forEach { (key,value) -> if(value==drop) redirects[key]=keep }
                pop.filter { root(it.householdId)==drop }.forEach { it.householdId=keep }
            }
        }
        pop.forEach { if(it.householdId>=0) it.householdId=root(it.householdId) }
        pop.filter { it.householdId<0 }.forEach { r ->
            val related=pop.firstOrNull { other -> other.id!=r.id && other.householdId>=0 && other.region==r.region && other.settlement==r.settlement &&
                (other.id==r.partnerId || other.id in r.parentIds || other.id in r.childIds || r.id in other.parentIds || r.id in other.childIds) }
            r.householdId=related?.householdId ?: next++
        }
        val ids=pop.map { it.householdId }.distinct().filter { it>=0 }
        val map=stored.groupBy { root(it.id) }.mapValues { (id,items) ->
            Household(id,items.sumOf { it.foodReserve },items.sumOf { it.sharedWealth },items.minOfOrNull { it.foundedDay } ?: p.getLong(KEY_DAY,1L))
        }
        val result=ids.map { id -> map[id] ?: Household(id,foundedDay=p.getLong(KEY_DAY,1L)) }.toMutableList()
        if(result.size!=stored.size || stored.map { it.id }.toSet()!=ids.toSet()) {
            saveHouseholds(p,result); savePeople(p,pop)
        }
        return result
    }
    private fun saveHouseholds(p:android.content.SharedPreferences,items:List<Household>){
        p.edit().putString(KEY_HOUSEHOLDS,JSONArray().apply { items.forEach { h ->
            put(JSONObject().put("id",h.id).put("foodReserve",h.foodReserve).put("sharedWealth",h.sharedWealth).put("foundedDay",h.foundedDay))
        }}.toString()).apply()
    }
    private fun savePeople(p: android.content.SharedPreferences, list: List<Resident>) {
        val a=JSONArray(); list.forEach { a.put(it.json()) }; p.edit().putString(KEY_PEOPLE,a.toString()).apply()
    }
    fun load(context: Context): WorldSnapshot {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(p.getString(KEY_ERA,null)==null) p.edit().putString(KEY_ERA,if(p.getLong(KEY_DAY,1L)<=1L) ERA_PRE_IONIA else "legacy_unanchored").apply()
        val events=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]")); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
        return WorldSnapshot(p.getLong(KEY_DAY,1L),people(p).count { it.alive },events,p.getBoolean(KEY_RUNNING,false))
    }
    fun advance(context: Context, days: Int=1): WorldSnapshot {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var day=p.getLong(KEY_DAY,1L)
        val pop=people(p)
        val households=householdStates(p,pop)
        val markets=markets(p)
        val ecology=ecologyState(p)
        val fauna=faunaState(p)
        val events=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]")); (0 until a.length()).map { a.getString(it) }.toMutableList() }.getOrDefault(mutableListOf())
        var nextId=pop.maxOfOrNull { it.id }?.plus(1) ?: p.getInt(KEY_NEXT_ID,240)
        var nextHouseholdId=(households.maxOfOrNull { it.id } ?: -1)+1
        var rngState=p.getLong(KEY_RNG_STATE,0x4D595DF4D0F33173L xor (day*7919L+17L))
        repeat(days.coerceAtLeast(1)) {
            day++
            val rng=Random(rngState)
            tickCaravans(day,p,markets,events,rng)
            applyLorePulse(day,p,pop,markets,events,rng)
            applyLoreChainStage(day,p,pop,markets,events,rng)
            tickDiplomacy(day,p,pop,markets,events,rng)
            tickOrganizations(day,p,pop,events,rng)
            tickFactionGovernance(day,p,pop,markets,events,rng)
            tickNoxusSuccession(day,p,events,rng)
            tickHeroes(day,p,pop,markets,events,rng)
            tickHeroBonds(day,p,events,rng)
            tickSocialRelations(day,pop,events,rng)
            tickMagic(day,p,pop,markets,events,rng)
            tickEcology(day,p,pop,events,rng,ecology)
            tickFauna(day,p,ecology,pop,events,rng,fauna)
            // Continuous food production follows local growers, skill and ecological conditions.
            residentRegions.forEach { region ->
                val market=markets[region] ?: return@forEach
                val local=pop.filter { it.alive && it.region==region }
                var growers=local.filter { it.travelDaysRemaining<=0 && (it.job=="农夫" || it.job=="采集者") }
                val targetGrowers=(local.size/8).coerceAtLeast(if(local.isEmpty()) 0 else 1)
                if(local.isNotEmpty() && market.food < local.size*3 && growers.size < targetGrowers) {
                    val recruit=local.filter { it.travelDaysRemaining<=0 && it.job in setOf("旅人","学徒","伐木工","矿工","商贩") }.minByOrNull { it.skill }
                    if(recruit!=null) {
                        val oldJob=recruit.job
                        recruit.job=if(foodBonus(region,ecology)>=0) "农夫" else "采集者"
                        recordLife(recruit,day,"因社区粮食储备告急，从${oldJob}转向${recruit.job}，参与恢复本地食物生产。")
                        events.add("【紧急生计调整·模拟】第 $day 日：${region}粮食储备告急，${recruit.name}转向${recruit.job}。")
                        growers=local.filter { it.travelDaysRemaining<=0 && (it.job=="农夫" || it.job=="采集者") }
                    }
                }
                val ecologicalYield=foodBonus(region,ecology)
                val dailyHarvest=growers.sumOf { (6 + it.skill/25 + ecologicalYield).coerceIn(3,11) }
                market.food=(market.food+dailyHarvest).coerceAtMost(5000)
            }
            val newborns=mutableListOf<Resident>()
            pop.filter { it.alive }.forEach { r ->
                // Capture the real before-state so biographies can record meaningful transitions,
                // rather than inventing routine diary entries or sampling random bystanders.
                val biographyBeforeSkill=r.skill
                val biographyBeforeCoins=r.coins
                val biographyBeforeHealth=r.health
                val biographyBeforeFood=r.food
                val biographyBeforeRegion=r.region
                val biographyBeforeJob=r.job
                r.food=(r.food-1).coerceAtLeast(0)
                if(r.food==0) {
                    val household=households.firstOrNull { it.id==r.householdId }
                    if(household!=null && household.foodReserve>0) {
                        household.foodReserve--
                        r.food=1
                    } else r.health=(r.health-2).coerceAtLeast(0)
                }
                val traveling=tickTravel(r,day,events,rng)
                if(!traveling && rng.nextInt(100)<(42+r.skill.coerceAtMost(15))) when(r.job) {
                    "农夫","采集者" -> {r.food+=(2+rng.nextInt(3)+foodBonus(r.region,ecology)).coerceAtLeast(1);r.coins++;r.skill=(r.skill+1).coerceAtMost(100)}
                    "商贩" -> if(r.food>1){r.food--;r.coins+=2;markets[r.region]?.let { if(it.food>0)it.food--;it.wealth+=2 };r.skill=(r.skill+1).coerceAtMost(100)}else r.food++
                    "工匠" -> {r.coins+=2+craftBonus(r.region);markets[r.region]?.let { it.wealth+=2+craftBonus(r.region) };r.skill=(r.skill+1).coerceAtMost(100)}
                    "伐木工","矿工","药师","魔法材料采集者","海克斯技师" -> {r.coins+=1+if(r.job=="海克斯技师")craftBonus(r.region).coerceAtLeast(0) else 0;r.skill=(r.skill+1).coerceAtMost(100)}
                    "护卫" -> {r.coins++;if(rng.nextInt(100)<4)r.health=(r.health-3).coerceAtLeast(0)}
                    else -> {r.coins++;if(rng.nextInt(100)<20)r.food++}
                }
                if(r.food<=2) {
                    val market=markets[r.region]
                    if(market!=null && market.food>0 && r.coins>0) {
                        val unitPrice=goodPrice(market,"粮食")
                        val affordable=r.coins/unitPrice
                        var bought=minOf(3-r.food,market.food,affordable)
                        val publicRelief=bought==0 && r.coins<unitPrice && r.food<=1 && market.food>0 && market.wealth>=unitPrice
                        if(publicRelief) bought=1
                        if(bought>0) {
                            val cost=bought*unitPrice
                            market.food-=bought;r.food+=bought
                            if(publicRelief) market.wealth=(market.wealth-cost).coerceAtLeast(0)
                            else { r.coins-=cost;market.wealth+=cost }
                            if(publicRelief && biographyBeforeFood>1) recordLife(r,day,"因个人收入不足获得当地市场公共储备的口粮援助，暂时避免断粮。")
                        }
                    }
                }
                if(day%360L==0L) {
                    r.age++
                    if(r.alive && r.age in 18..25 && r.partnerId<0 && r.householdId>=0 &&
                        rng.nextInt(100)<12 &&
                        (r.coins>=8 || r.skill>=30) &&
                        pop.any { it.alive && it.id in r.parentIds && it.householdId==r.householdId }) {
                        val previousHousehold=r.householdId
                        val independent=Household(nextHouseholdId++,foundedDay=day)
                        households.add(independent)
                        r.householdId=independent.id
                        recordLife(r,day,"成年后离开原生家庭，建立独立生活与经济单位（家庭${independent.id}）。")
                        pop.filter { it.id in r.parentIds && it.householdId==previousHousehold }.forEach { parent ->
                            recordLife(parent,day,"子女${r.name}成年后离开家庭，开始独立生活。")
                        }
                        events.add("【家庭分立·模拟】第 $day 日：${r.name}在${r.region}离开原生家庭，建立独立家庭${independent.id}。")
                    }
                }
                if(day%90L==0L && r.alive && !traveling && r.skill>=10 && r.job=="学徒") {
                    r.job="工匠"
                    recordLife(r,day,"在${r.region}完成学徒阶段，成为工匠。")
                    events.add("第 $day 日：${r.name}在${r.region}完成学徒阶段，成为工匠。")
                }
                if(day%90L==0L && r.alive && !traveling && r.skill>=25 && r.job=="旅人") {
                    r.job="护卫"
                    recordLife(r,day,"在${r.region}积累经验，转为护卫。")
                    events.add("第 $day 日：${r.name}在${r.region}积累经验，转为护卫。")
                }
                if(day%90L==0L && r.alive && !traveling && r.skill>=20 && r.job=="采集者" && rng.nextInt(100)<8) {
                    val specializations=when(r.region) {
                        "皮尔特沃夫","祖安" -> listOf("海克斯技师","工匠")
                        "德玛西亚" -> listOf("伐木工","工匠")
                        "诺克萨斯","弗雷尔卓德" -> listOf("矿工","伐木工")
                        "艾欧尼亚" -> listOf("药师","魔法材料采集者")
                        "恕瑞玛","巨神峰","以绪塔尔" -> listOf("魔法材料采集者","药师")
                        "比尔吉沃特" -> listOf("药师","伐木工")
                        else -> listOf("药师","伐木工")
                    }
                    r.job=specializations[rng.nextInt(specializations.size)]
                    recordLife(r,day,"在${r.region}根据当地生计需求转为${r.job}。")
                    events.add("第 $day 日：${r.name}在${r.region}转行为${r.job}，当地专业劳动力结构发生变化。")
                }
                if(day%90L==0L && r.alive && !traveling && r.skill>=50 && r.job=="采集者") {
                    r.job="商贩"
                    recordLife(r,day,"在${r.region}凭经验开始经营贸易。")
                    events.add("第 $day 日：${r.name}在${r.region}凭经验开始经营贸易。")
                }
                if(day%30L==0L && r.alive && r.partnerId<0 && r.age in 20..35 && rng.nextInt(1000)<2) {
                    val candidate=pop.firstOrNull { it.alive && it.id!=r.id && it.region==r.region && it.settlement==r.settlement && it.partnerId<0 && it.age in 20..35 }
                    if(candidate!=null) { r.partnerId=candidate.id;candidate.partnerId=r.id
                        val mergedHousehold=minOf(r.householdId,candidate.householdId)
                        val formerHouseholds=setOf(r.householdId,candidate.householdId)
                        pop.filter { it.householdId in formerHouseholds }.forEach { it.householdId=mergedHousehold }
                        recordLife(r,day,"与${candidate.name}建立伴侣关系。");recordLife(candidate,day,"与${r.name}建立伴侣关系。")
                        events.add("第 $day 日：${r.region}的${r.name}与${candidate.name}建立伴侣关系。") }
                }
                if(day%30L==0L && r.partnerId>=0 && r.id<r.partnerId && r.age in 20..38 && r.familyCount<3 && rng.nextInt(1000)<3) {
                    r.familyCount++
                    val mate=pop.firstOrNull { it.id==r.partnerId && it.alive }
                    if(mate!=null) mate.familyCount=r.familyCount
                    val id=nextId++
                    val mateForLineage=pop.firstOrNull { it.id==r.partnerId && it.alive }
                    val childLineage=if(r.lineage=="瓦斯塔亚" || mateForLineage?.lineage=="瓦斯塔亚") "混合血脉（非瓦斯塔亚）" else if(r.lineage=="冰裔血脉（人类）" || mateForLineage?.lineage=="冰裔血脉（人类）") "冰裔血脉（人类）" else "人类"
                    val child=Resident(id,names[rng.nextInt(names.size)]+"·"+id,r.region,0,100,3,0,"学徒","成长与学习",settlement=r.settlement,householdId=r.householdId,lineage=childLineage,lifespanYears=95+rng.nextInt(11))
                    child.parentIds.add(r.id)
                    r.childIds.add(id)
                    recordLife(r,day,"家庭迎来孩子${child.name}（居民编号$id）。")
                    recordLife(child,day,"出生于${r.region}；父母之一为${r.name}（编号${r.id}）。")
                    mate?.let {
                        if(it.id !in child.parentIds) child.parentIds.add(it.id)
                        if(id !in it.childIds) it.childIds.add(id)
                        recordLife(it,day,"家庭迎来孩子${child.name}（居民编号$id）。")
                        recordLife(child,day,"另一位父母为${it.name}（编号${it.id}）。")
                    }
                    newborns.add(child)
                    events.add("第 $day 日：${r.region}的${r.name}家庭迎来新生儿${child.name}。")
                }
                if(r.alive && r.food<=0 && day%30L==0L && rng.nextInt(100)<12) {
                    val oldRegion=r.region
                    val destinations=residentRegions.filter { it!=oldRegion }
                    val next=destinations.maxByOrNull { candidate ->
                        val supply=markets[candidate]?.food?:0
                        val crowd=pop.count { it.alive && it.region==candidate }
                        supply-crowd*2+(markets[candidate]?.wealth?:0)/100
                    } ?: oldRegion
                    if(next!=oldRegion) beginHouseholdTravel(r,next,day,events,"粮食短缺与生计压力",pop)
                }
                if(r.health<=0 || (r.age>=r.lifespanYears-15 && rng.nextInt(100)<1) || (r.age>=r.lifespanYears && rng.nextInt(100)<12)) {
                    r.alive=false
                    recordLife(r,day,"在${r.region}以${r.age}岁离世，生前职业：${r.job}。")
                    events.add("第 $day 日：${r.name}（${r.region}，${r.age}岁，${r.job}）去世；人生记录留存。")
                    if(r.partnerId>=0) {
                        val formerPartner=pop.firstOrNull { it.id==r.partnerId }
                        if(formerPartner!=null) {
                            recordLife(formerPartner,day,"伴侣${r.name}离世，关系因死亡而结束。")
                            formerPartner.partnerId=-1
                        }
                        r.partnerId=-1
                    }
                }
                if(r.alive && r.food==0 && rng.nextInt(100)<3) events.add("第 $day 日：${r.name}在${r.region}遭遇食物短缺，健康恶化。")
                if(r.alive && rng.nextInt(1000)<2) { recordLife(r,day,"实现阶段目标「${r.goal}」，开始追寻新的方向。");events.add("第 $day 日：${r.name}在${r.region}达成「${r.goal}」，开始追寻新的方向。") }

                // Biography milestones are written only when the simulation actually crosses
                // a consequential threshold. Ordinary daily work and small numerical drift stay out.
                if(biographyBeforeSkill<10 && r.skill>=10) recordLife(r,day,"长期工作与练习初见成效，掌握了职业基础技能。")
                if(biographyBeforeSkill<25 && r.skill>=25) recordLife(r,day,"技能积累达到新的阶段，已能独立处理较复杂的工作。")
                if(biographyBeforeSkill<50 && r.skill>=50) recordLife(r,day,"持续积累的实践经验使其成为熟练从业者，开始拥有更大的职业选择空间。")
                if(biographyBeforeSkill<75 && r.skill>=75) recordLife(r,day,"技艺达到资深水准，在当地同行中具备较强经验。")
                if(biographyBeforeSkill<100 && r.skill>=100) recordLife(r,day,"职业技能臻于当前模拟体系的顶点，成为当地极有经验的从业者。")
                if(biographyBeforeCoins<25 && r.coins>=25) recordLife(r,day,"积蓄首次达到25枚，开始拥有应对短期意外的经济余地。")
                if(biographyBeforeCoins<75 && r.coins>=75) recordLife(r,day,"个人积蓄达到75枚，具备改善居住或生产条件的可能。")
                if(biographyBeforeCoins>=25 && r.coins<10) recordLife(r,day,"积蓄大幅消耗，经济状况转为脆弱，日常生计更依赖市场与互助。")
                if(biographyBeforeFood>1 && r.food<=1 && r.alive) recordLife(r,day,"个人粮食储备降至危险水平，必须尽快购买、采集或寻求家庭互助。")
                if(biographyBeforeHealth>=70 && r.health<40 && r.alive) recordLife(r,day,"健康状况明显恶化，已影响正常生活与劳动能力。")
                if(biographyBeforeHealth<40 && r.health>=70 && r.alive) recordLife(r,day,"身体状况从严重虚弱中恢复，重新具备较稳定的劳动能力。")
                if(biographyBeforeRegion!=r.region && r.alive && r.travelDaysRemaining<=0) recordLife(r,day,"生活地点发生变化：从${biographyBeforeRegion}转至${r.region}，开始适应新的社会与生计环境。")
                if(biographyBeforeJob!=r.job && r.alive) recordLife(r,day,"职业身份发生变化：由${biographyBeforeJob}转为${r.job}，生活方式与谋生技能随之改变。")
            }
            pop.addAll(newborns)
            if(day%30L==0L) {
                var savingHouseholds=0
                var contributedCoins=0
                var contributedFood=0
                households.forEach { household ->
                    val members=pop.filter { it.alive && it.householdId==household.id }
                    if(members.isNotEmpty()) {
                        val contributors=members.filter { it.coins>=3 && it.travelDaysRemaining<=0 }
                        contributors.forEach { it.coins--; household.sharedWealth++ }
                        val foodDonor=members.filter { it.food>4 && it.travelDaysRemaining<=0 }.maxByOrNull { it.food }
                        if(foodDonor!=null) { foodDonor.food--; household.foodReserve++ }
                        if(contributors.isNotEmpty() || foodDonor!=null) {
                            savingHouseholds++
                            contributedCoins+=contributors.size
                            if(foodDonor!=null) contributedFood++
                        }
                    }
                }
                if(savingHouseholds>0) events.add("【家庭经济·模拟】第 $day 日：$savingHouseholds 个家庭向共同储备转入$contributedCoins 枚金币与$contributedFood 单位粮食；这些资产已从成员个人库存扣除。")
            }
            regions.forEach { region ->
                val market=markets[region] ?: return@forEach
                market.food=market.food.coerceAtLeast(0)
                if(day%30L==0L) {
                    val local=pop.filter { it.alive && it.region==region }
                    val farmers=local.count { it.job=="农夫" || it.job=="采集者" }
                    val artisans=local.count { it.job=="工匠" }
                    val guards=local.count { it.job=="护卫" }
                    regionalGoods(region).forEach { (good,capacity) ->
                        if(good=="粮食") return@forEach
                        val specialists=when(good) {
                            "粮食" -> local.filter { it.job=="农夫" || it.job=="采集者" }
                            "木材" -> local.filter { it.job=="伐木工" }
                            "矿石" -> local.filter { it.job=="矿工" }
                            "药材" -> local.filter { it.job=="药师" }
                            "工具" -> local.filter { it.job=="工匠" }
                            "魔法材料" -> local.filter { it.job=="魔法材料采集者" }
                            "海克斯器件" -> local.filter { it.job=="海克斯技师" }
                            else -> emptyList()
                        }
                        // Output depends on actual workers and their accumulated craft skill.
                        val labor= specialists.sumOf { 1+(it.skill/25) }
                        val amount=if(specialists.isEmpty()) 0 else (labor*capacity/2).coerceAtMost(capacity+12)
                        if(good=="粮食") market.food+=amount
                        else market.stock[good]=(market.stock[good]?:0)+amount
                    }
                    // Food scarcity can trigger a real livelihood change instead of passive famine.
                    if(local.isNotEmpty() && market.food < local.size*5) {
                        val recruit=local.filter { it.travelDaysRemaining<=0 && it.job in setOf("旅人","学徒","伐木工","矿工","商贩") }
                            .minByOrNull { it.skill }
                        if(recruit!=null) {
                            val oldJob=recruit.job
                            recruit.job=if(foodBonus(region,ecology)>=0) "农夫" else "采集者"
                            recordLife(recruit,day,"因当地粮食储备持续不足，从${oldJob}转向${recruit.job}，参与维持社区口粮。")
                            events.add("【生计调整·模拟】第 $day 日：${region}粮食储备偏低，${recruit.name}由${oldJob}转为${recruit.job}，增加本地食物生产。")
                        }
                    }
                    val populationDemand=(local.size/45).coerceAtLeast(0)
                    val demand=mapOf(
                        "木材" to (1+populationDemand+artisans/14),
                        "矿石" to (1+populationDemand+artisans/18),
                        "药材" to (1+populationDemand+local.count { it.health<45 }/12),
                        "工具" to (1+populationDemand+artisans/10+guards/25),
                        "魔法材料" to (1+populationDemand+local.count { it.job=="学徒" }/12),
                        "海克斯器件" to (if(region=="皮尔特沃夫" || region=="祖安") 2+populationDemand+artisans/8 else 1+populationDemand)
                    )
                    demand.forEach { (good,amount) ->
                        market.stock[good]=((market.stock[good]?:0)-amount).coerceAtLeast(0)
                    }
                    val shortages=goods.filter { it!="粮食" && (market.stock[it]?:0)<=5 }
                    if(shortages.isNotEmpty()) {
                        events.add("第 $day 日市场：${region}出现商品短缺——${shortages.joinToString("、")}；供需变化正在影响当地价格。")
                        val directlyPressured=local.filter { it.food<=2 || (it.coins<goodPrice(market,"粮食") && it.food<=4) }
                        directlyPressured.forEach { resident ->
                            recordLife(resident,day,"当地市场出现${shortages.joinToString("、")}短缺；其个人储粮或购买能力已处于脆弱水平，面临价格与供应压力。","livelihood_affected")
                        }
                    }
                }
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
            rngState=rng.nextLong()
        }
        while(events.size>1200)events.removeAt(0)
        savePeople(p,pop)
        saveHouseholds(p,households)
        saveMarkets(p,markets)
        val arr=JSONArray();events.forEach{arr.put(it)}
        p.edit().putInt(KEY_NEXT_ID,nextId)
            .putLong(KEY_DAY,day).putLong(KEY_RNG_STATE,rngState).putString(KEY_EVENTS,arr.toString()).apply()
        return WorldSnapshot(day,pop.count{it.alive},events,p.getBoolean(KEY_RUNNING,false))
    }
    fun settlementDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=people(p)
        return settlementSeeds.flatMap { (region,names) -> names.map { name ->
            val meta=settlementMeta(region,name)
            val residents=all.filter { it.alive && it.region==region && it.settlement==name }
            val farmers=residents.count { it.job=="农夫" || it.job=="采集者" }
            val artisans=residents.count { it.job=="工匠" }
            val traders=residents.count { it.job=="商贩" }
            val guards=residents.count { it.job=="护卫" }
            "【"+name+"】\\n所属区域："+region+"\\n实体类型："+meta.type+"\\n可居住性："+(if(meta.habitable)"可作为常规居住点" else "非普通居民居住地")+"\\n说明："+meta.note+"\\n常住居民："+(if(meta.habitable)residents.size.toString()+"人" else "不适用")+"\\n生产与职业：农采"+farmers+"、工匠"+artisans+"、商贩"+traders+"、护卫"+guards+"\\n（人口、职业与聚落活动属于模拟数据；官方地名不代表官方确认了此处的模拟人口。）"
        } }.sorted()
    }
    fun factionDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val states=factionStates(p)
        return states.values.sortedBy{it.name}.map { f ->
            "【${f.name}地区政治格局】\n政治形态：${f.government}\n主要权力结构：${f.rulingBloc}\n内部异议与竞争力量：${f.opposition}\n当前治理议题：${f.policy}\n主要领袖/机构：${f.leader}\n模拟治理指标：力量${f.military}｜资源${f.treasury}｜社会稳定${f.stability}｜内部张力${f.internalTension}\n涉及地区：${f.territory.joinToString("、")}\n说明：这里描述的是地区政治生态的抽象指标，不代表该地区必然是单一国家或统一政府。艾欧尼亚、弗雷尔卓德、巨神峰、比尔吉沃特、班德尔城与暗影岛尤其不能被视作普通中央集权国家。\n（所有数值变化属于模拟历史分支，不代表官方剧情。）"
        }
    }
    fun caravanDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val active=caravans(p).filter { it.alive }
        val marketMap=markets(p)
        val rows=active.map {
            "${it.owner}｜${it.from} → ${it.to}｜${it.good} ${it.cargo}｜剩余 ${it.daysRemaining} 日"
        }.toMutableList()
        rows.add(0,"在途商队：${active.size} 支")
        rows.addAll(marketMap.entries.sortedBy { it.key }.map { (region,m) ->
            val price=when {
                m.food<60 -> "高"
                m.food<140 -> "偏高"
                m.food>350 -> "低"
                else -> "平稳"
            }
            "${region}｜粮食库存 ${m.food}｜市场财富 ${m.wealth}｜粮价：${price}｜商品：" +
                goods.filter { it!="粮食" }.joinToString("、") { good -> "${good}${m.stock[good]?:0}（价${goodPrice(m,good)}）" }
        })
        return rows
    }
    fun householdDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=people(p); val households=householdStates(p,all)
        return households.map { h ->
            val members=all.filter { it.householdId==h.id }; val living=members.filter { it.alive }
            val location=living.groupBy { it.region+" · "+it.settlement }.maxByOrNull { it.value.size }?.key ?: "无在世成员"
            val names=living.sortedBy { it.age }.joinToString("、") { it.name }.ifBlank { "无在世成员" }
            "【家庭 ${h.id}】\\n主要居住地：$location\\n成员：$names\\n成员总数：${members.size}（在世${living.size}）\\n共同储粮：${h.foodReserve}\\n共同资产：${h.sharedWealth}\\n建立记录：第${h.foundedDay}日\\n说明：家庭是独立社会实体。成员每月可从个人金币与食物中缴入共同储备；个人食物耗尽时可消耗家庭储粮。共同资产仍未接入市场采购、住房和工资制度。"
        }.sortedBy { it.substringBefore("】") }
    }
    fun lineageDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=people(p)
        val living=all.filter{it.alive}
        val groups=listOf(
            Triple("人类","符文之地广泛分布的凡人群体；各地区文化、制度、语言与生活方式不同。","普通居民按人类寿命参数运行。"),
            Triple("瓦斯塔亚","与古老瓦斯塔亚希瑞（Vastayashai'rei）及精神领域力量相关的奇异族群；不同部族外形、习俗与能力差异很大。","官方设定提及其族群长期未有新生代；本模拟不按普通人类出生率繁衍。"),
            Triple("冰裔血脉（人类）","弗雷尔卓德少数具有特殊耐受能力的凡人，与真冰及古老寒冰力量相关；冰裔不是独立的非人种族，也不代表所有弗雷尔卓德人。","寿命仍按人类模型；冰裔身份不自动赋予所有人相同能力，真冰本身极为稀有。"),
            Triple("混合血脉（非瓦斯塔亚）","与瓦斯塔亚存在血缘联系、但不被直接归类为瓦斯塔亚的模拟后裔。","此类个体是谨慎的模拟分类，不宣称为官方固定族群。")
        )
        val rows=groups.map{(name,description,rule)->
            val count=living.count{it.lineage==name}
            "【${name}】\\n当前模拟在世人口：${count}\\n世界设定：${description}\\n运行规则：${rule}\\n注意：人口数量和寿命参数均为模拟数据，不是官方人口统计。"
        }.toMutableList()
        val yordles=heroAgents(p).filter{it.name in setOf("提莫","崔丝塔娜","璐璐","兰博","库奇","维迦","黑默丁格")}
        rows.add("【约德尔人】\\n独立特殊社群：约德尔人不混入普通人类居民人口。当前已登记重要人物：${yordles.size}位（${yordles.joinToString("、"){it.name}}）。\\n世界设定：班德尔城位于精神领域，入口与符文之地相连；入口并非随时对所有人开放。约德尔人的时间体验与凡人不同，不能直接套用人类人口年龄模型。\\n运行规则：约德尔人物作为独立世界行动者记录，不计入普通居民出生、死亡与市场人口。")
        rows.add("【其他特殊存在】\\n飞升者、暗裔、半神、星灵、亡灵与虚空生物按独立超自然实体处理，不归入普通居民族群；其存在与活动通过英雄行动、组织、地区魔法和世界危机影响凡人社会。")
        return rows
    }
    fun residentDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return people(p).sortedWith(compareBy<Resident>{!it.alive}.thenByDescending{it.age}).map {
            "${it.id}｜${it.name}｜${it.lineage}｜${it.age}岁｜${if(it.travelDaysRemaining>0)"途中：${it.travelOrigin}→${it.travelDestination}" else it.region}｜${it.job}｜${if(it.alive)"在世" else "已故"}"
        }
    }
    // Stable export API: returns recorded biography text, or null when the resident does not exist.
    fun residentBiographyExport(context:Context,id:Int):String? {
        val exists=people(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)).any { it.id==id }
        return if(exists) residentHistory(context,id) else null
    }
    fun residentHistory(context:Context,id:Int):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=people(p)
        val r=all.firstOrNull{it.id==id} ?: return "未找到该居民档案。"
        val partner=all.firstOrNull{it.id==r.partnerId}
        val parents=all.filter { it.id in r.parentIds }
        val children=all.filter { it.id in r.childIds }
        val friends=all.filter { it.id in r.friendIds }
        val mentor=all.firstOrNull{it.id==r.mentorId}
        val students=all.filter{it.id in r.studentIds}
        val groups=organizations(p).filter { r.id in it.members }
        val worldEvents=runCatching {
            val a=JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        val packets=r.storyRecords.mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }.sortedBy { it.optLong("day") }
        return buildString {
            append("符文之地 · 居民完整人生档案\n档案编号：${r.id}｜姓名：${r.name}\n")
            append("档案用途：供长篇小说创作使用的模拟事实素材。只记录真实保存的状态和事件，不补写未发生的对白、动作或心理活动。\n\n")
            append("【人物基础资料】\n生命类别：${r.lineage}\n年龄：${r.age}岁\n模拟寿命参数：${r.lifespanYears}年（非官方固定寿命）\n")
            append("当前地区：${r.region}\n当前聚落：${r.settlement.ifBlank { "未记录" }}\n当前职业：${r.job}\n")
            append("人生状态：${if(r.alive) "在世" else "已故"}\n健康：${r.health}/100｜技能：${r.skill}/100｜财富：${r.coins}｜粮食：${r.food}\n")
            if(r.travelDaysRemaining>0) append("旅途：${r.travelOrigin}→${r.travelDestination}；剩余${r.travelDaysRemaining}日；目的：${r.travelPurpose}\n")
            append("\n【人物关系网络】\n伴侣：${partner?.name ?: if(r.partnerId>=0) "关系对象档案不可用" else "暂无记录"}\n")
            append("父母：${parents.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n子女：${children.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n")
            append("朋友：${friends.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n师长：${mentor?.name ?: "暂无记录"}\n")
            append("学生：${students.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n所属组织：${groups.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n家庭编号：${r.householdId}\n")
            append("\n【人生事件 · 结构化场景素材】\n个人事件：${r.history.size}条｜结构化场景包：${packets.size}条\n")
            if(packets.isEmpty()) append("旧存档尚无结构化场景包；不会对缺失历史进行虚构补写。\n")
            packets.forEachIndexed { index,e ->
                append("\n场景素材 ${index+1}｜第${e.optLong("day")}日｜${e.optInt("age")}岁\n")
                append("地点：${e.optString("region")} · ${e.optString("settlement").ifBlank { "聚落未记录" }}\n")
                append("当时身份：${e.optString("job")}｜健康${e.optInt("health")}/100｜技能${e.optInt("skill")}/100｜财富${e.optInt("coins")}｜粮食${e.optInt("food")}\n")
                append("事实记录：${e.optString("detail")}\n")
                val d=e.optLong("day"); val region=e.optString("region")
                val context=worldEvents.filter { (it.contains("第 $d 日") || it.contains("第${d}日")) && (it.contains(region) || it.contains(r.name)) }
                if(context.isNotEmpty()) { append("同日相关世界背景：\n"); context.take(4).forEach { append("· ").append(it).append("\n") } }
            }
            append("\n【人生阶段与事件分类索引】\n")
            packets.groupBy { it.optString("lifeStage","阶段未记录") }.forEach { (stage,items) ->
                append("${stage}：${items.size}个节点\n")
                items.forEach { append("  · 第${it.optLong("day")}日｜${it.optString("eventType","未分类")}｜${it.optString("detail")}\n") }
            }
            packets.groupBy { it.optString("eventType","未分类") }.forEach { (type,items) ->
                append("${type}：${items.size}条\n")
            }
            append("\n【关系网络的历史快照】\n")
            packets.filter { it.optJSONObject("relationships")!=null }.forEach { e ->
                val rel=e.optJSONObject("relationships")!!
                val partnerName=all.firstOrNull { it.id==rel.optInt("partnerId",-1) }?.name ?: "无/档案不可用"
                val parentNames=(0 until (rel.optJSONArray("parentIds")?.length() ?: 0)).mapNotNull { idx -> all.firstOrNull { it.id==rel.optJSONArray("parentIds")!!.optInt(idx) }?.name }
                val childNames=(0 until (rel.optJSONArray("childIds")?.length() ?: 0)).mapNotNull { idx -> all.firstOrNull { it.id==rel.optJSONArray("childIds")!!.optInt(idx) }?.name }
                append("第${e.optLong("day")}日｜伴侣：${partnerName}｜父母：${parentNames.joinToString("、").ifBlank{"未记录"}}｜子女：${childNames.joinToString("、").ifBlank{"未记录"}}\n")
            }
            append("\n【可作为章节锚点的转折事件】\n")
            packets.filter { it.optString("eventType") in setOf("家庭与关系","死亡与人生终点","迁徙与居住","职业与技能","战争与政治","生计与经济","环境与生态","魔法与超自然") }.forEachIndexed { index,e ->
                append("${index+1}. 第${e.optLong("day")}日｜${e.optString("eventType")}｜${e.optString("detail")}\n")
            }
            append("说明：章节锚点只整理已有事实；节点之间缺失的行动、对白和心理过程仍然未知。\n")
            append("\n【旧版本人生时间线】\n")
            if(r.history.isEmpty()) append("暂无旧版人生事件。\n") else r.history.forEach { append("• ").append(it).append("\n") }
            append("\n【创作边界】\n档案可用于组织人物成长、时代背景、迁徙、职业、关系和重大事件章节。未记录的对白、具体动作、心理活动及旧版本缺失经历均属于未知，不能冒充既成事实。\n")
        }
    }
    fun setRunning(context: Context,running:Boolean) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,running).apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().putLong(KEY_DAY,1L).putString(KEY_ERA,ERA_PRE_IONIA).apply()
    }
}