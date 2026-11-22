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
     var storyRecords: MutableList<String> = mutableListOf(),
    var mindTrait: Int = 50, var belief: String = "重视生存与身边的人", var currentThought: String = "",
    var intent: String = "维持当前生活", var mindMemory: MutableList<String> = mutableListOf(),
    var gender:String="未记录", var appearance:String="", var abilities:String="", var headSevered:Boolean=false, var survivalOverride:Boolean=false,
    var identityMemory:String="",
    var dailyActivity:String="日常生活", var fatigue:Int=0, var morale:Int=50
) {
    fun json() = JSONObject().put("id",id).put("name",name).put("region",region).put("age",age)
        .put("health",health).put("food",food).put("coins",coins).put("job",job).put("goal",goal)
        .put("skill",skill).put("partnerId",partnerId).put("familyCount",familyCount).put("alive",alive)
        .put("history",JSONArray(history)).put("parentIds",JSONArray(parentIds)).put("childIds",JSONArray(childIds))
        .put("friendIds",JSONArray(friendIds)).put("mentorId",mentorId).put("studentIds",JSONArray(studentIds)).put("settlement",settlement).put("travelOrigin",travelOrigin).put("travelDestination",travelDestination).put("travelDaysRemaining",travelDaysRemaining).put("travelPurpose",travelPurpose).put("jobBeforeTravel",jobBeforeTravel).put("householdId",householdId).put("lineage",lineage).put("lifespanYears",lifespanYears).put("storyRecords",JSONArray(storyRecords)).put("mindTrait",mindTrait).put("belief",belief).put("currentThought",currentThought).put("intent",intent).put("mindMemory",JSONArray(mindMemory)).put("gender",gender).put("appearance",appearance).put("abilities",abilities).put("headSevered",headSevered).put("survivalOverride",survivalOverride).put("identityMemory",identityMemory).put("dailyActivity",dailyActivity).put("fatigue",fatigue).put("morale",morale)
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
            j.optString("settlement",""),j.optString("travelOrigin",""),j.optString("travelDestination",""),j.optInt("travelDaysRemaining",0),j.optString("travelPurpose",""),j.optString("jobBeforeTravel",""),j.optInt("householdId",-1),j.optString("lineage","人类"),j.optInt("lifespanYears",95),(0 until (j.optJSONArray("storyRecords")?.length() ?: 0)).map { j.getJSONArray("storyRecords").getString(it) }.toMutableList(), j.optInt("mindTrait",50), j.optString("belief","重视生存与身边的人"), j.optString("currentThought",""), j.optString("intent","维持当前生活"), (0 until (j.optJSONArray("mindMemory")?.length() ?: 0)).map { j.getJSONArray("mindMemory").getString(it) }.toMutableList(), j.optString("gender","未记录"), j.optString("appearance",""), j.optString("abilities",""), j.optBoolean("headSevered",false), j.optBoolean("survivalOverride",false), j.optString("identityMemory",""), j.optString("dailyActivity","日常生活"), j.optInt("fatigue",0), j.optInt("morale",50))
    }
}

object WorldEngine {
    private const val PREFS="runeterra_world"
    private val beliefs=listOf("重视亲情与互助","追求财富与独立","相信传统与秩序","渴望探索未知","警惕权力与不公","倾向冒险改变命运","珍视平静生活","希望证明自己的能力","关注家乡与共同体","怀疑传闻并寻求证据")
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
            detail.contains("天道") || detail.contains("外部信息") -> "天道干预与世界本体"
            detail.contains("魔法") || detail.contains("灵性") || detail.contains("黑雾") || detail.contains("星界") -> "魔法与超自然"
            detail.contains("生态") || detail.contains("污染") || detail.contains("水源") || detail.contains("植被") -> "环境与生态"
            detail.contains("英雄") || detail.contains("交集") -> "英雄交集与个人经历"
            else -> "个人经历与社会变化"
        }
        val stage=when { r.age<6 -> "幼年"; r.age<13 -> "童年"; r.age<18 -> "少年"; r.age<30 -> "青年"; r.age<50 -> "成年"; r.age<70 -> "中年"; else -> "晚年" }
        val sequence=(r.storyRecords.lastOrNull()?.let { raw -> runCatching { JSONObject(raw).optInt("sequence",0) }.getOrDefault(0) } ?: 0)+1
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
    // Daily life continues for every resident between local-model reasoning turns.
    // This is a persistent state process, not a quest or scripted storyline.
    private fun tickDailyLife(day:Long,pop:MutableList<Resident>,rng:Random) {
        val workingJobs=setOf("农夫","采集者","商贩","工匠","护卫","伐木工","矿工","药师","魔法材料采集者","海克斯技师")
        pop.filter { it.alive }.forEach { r ->
            val previous=r.dailyActivity
            val next=when {
                r.travelDaysRemaining>0 -> "旅途："+r.travelPurpose.ifBlank { "迁徙" }
                r.health<30 -> "休养与寻求照料"
                r.food<=1 -> "寻找口粮与生计"
                r.age<6 -> "依赖家庭照料"
                r.age<13 -> if(r.mentorId>=0) "跟随师长学习" else "家庭生活与基础学习"
                r.job=="学徒" -> "跟随师长实践技艺"
                r.fatigue>=85 -> "休息与恢复体力"
                r.job in workingJobs -> when(r.job) {
                    "农夫" -> if(r.region=="恕瑞玛") "灌溉、耕作与维护水渠" else "耕作、播种与收获"
                    "采集者" -> when(r.region) {
                        "弗雷尔卓德" -> "追踪猎物、采集耐寒食材并辨认风雪路线"
                        "恕瑞玛" -> "寻找绿洲水源、可食植物与商路补给"
                        "比尔吉沃特" -> "近海采集、处理渔获并辨认潮汐"
                        "艾欧尼亚" -> "在林地与溪谷采集食材、药草和生活材料"
                        else -> "采集当地食物与生活材料"
                    }
                    "商贩" -> when(r.region) {
                        "恕瑞玛" -> "经营绿洲补给与沙漠商路交易"
                        "比尔吉沃特" -> "经营港口货物、船员补给与悬赏消息"
                        "皮尔特沃夫" -> "经营工坊制品、进口货物与城市市场交易"
                        "祖安" -> "在地下市场经营零件、药剂与生活物资"
                        else -> "采购、议价与经营交易"
                    }
                    "工匠" -> when(r.region) {
                        "皮尔特沃夫" -> "在工坊制造精密器件并维护城市设施"
                        "祖安" -> "在地下工坊修理机械、处理化学材料"
                        "弗雷尔卓德" -> "修补猎具、皮革、御寒装备与营地器物"
                        "比尔吉沃特" -> "修船、修理索具与制作航海器具"
                        "艾欧尼亚" -> "制作农具、器物并维护地方建筑"
                        else -> "制作工具与日用器物"
                    }
                    "护卫" -> when(r.region) {
                        "诺克萨斯" -> "执行军团巡逻、驻防或道路护送"
                        "德玛西亚" -> "执行城镇守卫、边境巡逻与道路护送"
                        "弗雷尔卓德" -> "守望营地、护送部族成员并警戒野兽"
                        "比尔吉沃特" -> "看守码头、护送货物并防范海盗袭击"
                        "艾欧尼亚" -> "守护村社、寺院道路与地方安全"
                        else -> "巡逻、护送与维持治安"
                    }
                    "伐木工" -> "采伐木材与林地维护"
                    "矿工" -> "采矿与矿料分拣"
                    "药师" -> "采药、配制与照料病患"
                    "魔法材料采集者" -> "谨慎采集并辨识特殊材料"
                    "海克斯技师" -> "研究、装配与维修海克斯器件"
                    else -> "职业劳动"
                }
                else -> "处理个人事务与社区往来"
            }
            r.dailyActivity=next
            when {
                next.startsWith("旅途") -> r.fatigue=(r.fatigue+7).coerceIn(0,100)
                next=="休息与恢复体力" || next=="休养与寻求照料" -> r.fatigue=(r.fatigue-24).coerceAtLeast(0)
                next=="依赖家庭照料" || next.contains("学习") -> r.fatigue=(r.fatigue-5).coerceAtLeast(0)
                next.contains("劳动") || next.contains("耕作") || next.contains("采集") || next.contains("经营") || next.contains("巡逻") || next.contains("采伐") || next.contains("采矿") || next.contains("采药") || next.contains("工坊") || next.contains("装配") -> r.fatigue=(r.fatigue+8+rng.nextInt(5)).coerceIn(0,100)
                else -> r.fatigue=(r.fatigue-8).coerceAtLeast(0)
            }
            val familyNearby=pop.any { it.alive && it.id!=r.id && it.householdId>=0 && it.householdId==r.householdId && it.region==r.region && it.travelDaysRemaining<=0 }
            val socialNearby=pop.any { it.alive && it.id in r.friendIds && it.region==r.region && it.settlement==r.settlement && it.travelDaysRemaining<=0 }
            val moraleShift=when {
                r.health<30 || r.food<=0 -> -2
                r.food>=3 && (familyNearby || socialNearby) && rng.nextInt(100)<18 -> 1
                r.food>=3 && r.health>=65 && rng.nextInt(100)<8 -> 1
                r.fatigue>=90 -> -1
                else -> 0
            }
            r.morale=(r.morale+moraleShift).coerceIn(0,100)
            if(previous!=next && (next.startsWith("旅途") || next=="休养与寻求照料" || next=="寻找口粮与生计" || previous.startsWith("旅途"))) {
                r.mindMemory.add("第"+day+"日，生活状态变化："+previous+" → "+next+"。")
                while(r.mindMemory.size>120) r.mindMemory.removeAt(0)
            }
            if(r.fatigue>=95 && previous!="因过度疲劳而被迫休息") {
                r.dailyActivity="休息与恢复体力"
                r.fatigue=(r.fatigue-20).coerceAtLeast(0)
                recordLife(r,day,"长期劳动与奔波造成明显疲劳，被迫暂停日常工作以恢复体力。","daily_life_transition")
            }
        }
    }
    // Individual cognition: residents evaluate their own situation, update beliefs/intents, and retain bounded private memory.
    private fun thinkIndividually(day:Long,pop:MutableList<Resident>,markets:Map<String,Market>,rng:Random){
        if(day%30L!=0L)return
        pop.filter{it.alive}.forEach { r ->
            // Preserve the resident's latest private LLM cognition instead of replacing it with fallback heuristics.
            if(r.currentThought.contains("本地模型推理")) return@forEach
            val market=markets[r.region]
            val foodPressure=r.food<=1 || (market!=null && market.food<pop.count{it.alive&&it.region==r.region}*2)
            val healthPressure=r.health<45
            val wealthPressure=r.coins<8
            val oldBelief=r.belief
            val options=mutableListOf<String>()
            val observations=mutableListOf<String>()
            if(foodPressure){observations.add("口粮或当地供给变得紧张");options.add("优先寻找食物、采集或互助")}
            if(healthPressure){observations.add("身体状况正在限制行动");options.add("减少风险并寻求治疗")}
            if(wealthPressure){observations.add("积蓄不足，抗风险能力弱");options.add("寻找收入或降低支出")}
            if(r.skill>=30)options.add("利用已有技能寻找更好的谋生机会")
            if(r.friendIds.isNotEmpty()||r.partnerId>=0)options.add("向可信关系寻求帮助或合作")
            if(options.isEmpty()){observations.add("当前生活暂时稳定");options.add("继续当前职业，积累经验与资源")}
            val selected=if(r.mindTrait>=70) options.firstOrNull{it.contains("风险")||it.contains("稳定")||it.contains("优先")} ?: options.first()
                else if(r.mindTrait<=30) options.last() else options[rng.nextInt(options.size)]
            val thought="第${day}日：我观察到${observations.joinToString("；")}。我倾向于${selected}。"
            r.currentThought=thought
            r.intent=selected
            // Residents can turn their private intention into a concrete livelihood decision.
            if(day%180L==0L && r.travelDaysRemaining<=0 && r.age>=16 && r.age<70 && r.skill>=12 && r.job !in setOf("旅人","护卫","学徒")) {
                val alternatives=when {
                    foodPressure -> listOf("农夫","采集者","药师")
                    wealthPressure -> listOf("商贩","工匠","矿工")
                    r.mindTrait>=75 -> listOf("护卫","工匠","农夫")
                    r.mindTrait<=25 -> listOf("商贩","采集者","海克斯技师")
                    else -> listOf("工匠","采集者","商贩")
                }.filter { it!=r.job }
                if(alternatives.isNotEmpty() && r.mindTrait+r.skill/2>=65 && (foodPressure || wealthPressure || rng.nextInt(100)<12)) {
                    val previousJob=r.job
                    r.job=alternatives[rng.nextInt(alternatives.size)]
                    r.goal="尝试通过${r.job}改善生活"
                    recordLife(r,day,"经过个人评估后，决定离开${previousJob}，尝试从事${r.job}以改变生计。","resident_decision_action")
                }
            }
            if(foodPressure)r.belief="生存资源并不可靠，需要为短缺做准备"
            else if(healthPressure)r.belief="身体状况会限制我的选择"
            else if(wealthPressure)r.belief="没有积蓄就难以应对意外"
            else if(r.skill>=50)r.belief="熟练技能能为我争取更多选择"
            else if(r.mindTrait>=70)r.belief="谨慎准备比冒险更重要"
            else if(r.mindTrait<=30)r.belief="机会值得尝试，不能只守着眼前"
            if(r.belief!=oldBelief || day%180L==0L)r.mindMemory.add(thought+"（信念：${r.belief}）")
            while(r.mindMemory.size>120)r.mindMemory.removeAt(0)
            if(foodPressure && day%90L==0L && r.goal !in setOf("寻找稳定食物来源","改善生计并建立储备")) {
                r.goal=if(r.mindTrait>=55)"寻找稳定食物来源" else "改善生计并建立储备"
                recordLife(r,day,"在资源压力下重新审视个人目标，决定${r.goal}。","autonomous_goal_revision")
            } else if(!foodPressure && !healthPressure && day%180L==0L && r.skill>=25 && r.goal in setOf("寻找稳定食物来源","改善生计并建立储备")) {
                r.goal="提升技能并寻找更好的机会"
                recordLife(r,day,"生计压力缓解后重新评估方向，将目标调整为${r.goal}。","autonomous_goal_revision")
            }
        }
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
    private const val KEY_EVENT_ARCHIVE="world_event_archive_v1"
    private const val KEY_HEAVENLY_ARCHIVE="heavenly_intervention_archive_v1"
    private const val KEY_DIVINE_RULES="heavenly_active_rules_v1"
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

    /**
     * External information channel. It never overwrites a recipient's will or belief.
     */
    fun heavenlyStatus(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val archive=runCatching { JSONArray(p.getString(KEY_HEAVENLY_ARCHIVE,"[]") ?: "[]") }.getOrDefault(JSONArray())
        val latest=if(archive.length()>0) runCatching { JSONObject(archive.getString(archive.length()-1)) }.getOrNull() else null
        return listOf(
            "天道系统：已启用",
            "世界日期：第"+p.getLong(KEY_DAY,1L)+"日",
            "已记录干预：${archive.length()}次",
            "最近干预："+(latest?.let { "第"+it.optLong("day")+"日｜"+it.optString("mode")+"｜"+it.optString("message") } ?: "尚无"),
            "作用原则：天道可直接改变人物属性、生死、位置、记忆、政体、时间与世界规则；信息传达只是其中一种能力。",
            "认知原则：接收者可能相信、怀疑、误解、拒绝、研究或传播；不同人物会形成不同解释。",
            "研究原则：居民可将相关经历纳入知识体系，尝试提出假说并寻找可验证证据。",
            "边界原则：每次干预都属于模拟分支历史，官方符文之地设定不会因此被改写。"
        )
    }

    fun heavenlyInterventionHistory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val archive=runCatching { JSONArray(p.getString(KEY_HEAVENLY_ARCHIVE,"[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until archive.length()).mapNotNull { i ->
            runCatching {
                val item=JSONObject(archive.getString(i))
                "第${item.optLong("day")}日｜${item.optString("mode")}｜${item.optString("scope")}｜接收者：${item.optString("recipients")}\n${item.optString("message")}\n结果："+(item.optJSONArray("outcomes")?.let { a -> (0 until a.length()).joinToString("；") { a.optString(it) } } ?: "接收信息已写入相关人物记忆；是否相信及后续行动尚未预设。")
            }.getOrNull()
        }.asReversed()
    }

    fun heavenlyIntervene(context:Context,scope:String,targetId:Int=-1,targetName:String="",mode:String="神谕",message:String):String {
        val clean=message.trim().take(1800)
        if(clean.isBlank()) return "天道信息不能为空。"
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val day=p.getLong(KEY_DAY,1L)
        val peopleNow=people(p)
        val heroesNow=heroAgents(p).toMutableList()
        val residentTargets=when(scope) {
            "全世界" -> peopleNow.filter { it.alive }
            "指定居民" -> peopleNow.filter { it.alive && it.id==targetId }
            else -> emptyList()
        }
        val heroTargets=when(scope) {
            "全世界" -> heroesNow
            "指定英雄" -> heroesNow.filter { it.name==targetName }
            else -> emptyList()
        }
        if(scope !in setOf("全世界","指定居民","指定英雄")) return "未知的天道干预范围。"
        if(scope=="指定居民" && residentTargets.isEmpty()) return "没有找到这名在世居民。"
        if(scope=="指定英雄" && heroTargets.isEmpty()) return "没有找到这名英雄或特殊存在。"
        val recipients=mutableListOf<String>()
        residentTargets.forEach { r ->
            val memory="第${day}日，接收到天道干预（${mode}）：${clean}。这是一条外部信息；其真实性、含义与来源仍需由我自行判断。"
            r.mindMemory.add(memory)
            while(r.mindMemory.size>120) r.mindMemory.removeAt(0)
            r.currentThought="第${day}日：我接收到一条被称为“天道”的外部信息，尚未决定如何理解。"
            recordLife(r,day,"接收到天道干预（${mode}）：${clean}。该记录只证明信息抵达，不代表此人相信其内容。","heavenly_intervention_received")
            recipients.add(r.name+"（居民#"+r.id+"）")
        }
        if(residentTargets.isNotEmpty()) savePeople(p,peopleNow)
        heroTargets.forEach { h ->
            h.memory.add("第${day}日，接收到天道干预（${mode}）：${clean}。信息来源与真实性仍待独立判断。")
            while(h.memory.size>100) h.memory.removeAt(0)
            h.history.add("第${day}日｜接收到天道干预（${mode}）：${clean}。接收不等于相信，后续解释与行动未预设。")
            while(h.history.size>3000) h.history.removeAt(0)
            recipients.add(h.name)
        }
        if(heroTargets.isNotEmpty()) saveHeroes(p,heroesNow)
        val recipientLabel=if(scope=="全世界") "所有在世居民与已登记英雄/特殊存在" else recipients.joinToString("、")
        val item=JSONObject().put("day",day).put("mode",mode).put("scope",scope)
            .put("targetId",targetId).put("targetName",targetName).put("recipients",recipientLabel)
            .put("message",clean).put("beliefForced",false).put("canonChanged",false)
        val archive=runCatching { JSONArray(p.getString(KEY_HEAVENLY_ARCHIVE,"[]") ?: "[]") }.getOrDefault(JSONArray())
        archive.put(item)
        while(archive.length()>10000) archive.remove(0)
        val events=runCatching { JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]") }.getOrDefault(JSONArray())
        events.put("【天道干预·模拟分支】第${day}日：以${mode}方式向${recipientLabel}传达信息：“${clean}”。接收者不被强制相信。")
        while(events.length()>300) events.remove(0)
        p.edit().putString(KEY_HEAVENLY_ARCHIVE,archive.toString()).putString(KEY_EVENTS,events.toString()).apply()
        return "天道信息已送达。\n接收对象：${recipientLabel}\n已写入人物记忆与历史档案；没有强制改变任何人的信念或选择。"
    }


    /**
     * Natural-language omnipotent command interface. The 8B model translates intent
     * into a bounded operation plan; this engine validates targets and performs writes.
     */
    fun interpretHeavenlyCommand(context:Context,command:String,callback:(String)->Unit) {
        val clean=command.trim().take(2400)
        if(clean.isBlank()) { callback("指令不能为空。"); return }
        if(!ResidentAI.isLoaded()) { callback("请先导入并加载本地 Qwen 8B GGUF 模型。没有模型时无法可靠理解任意自然语言指令。"); return }
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val residents=people(p)
        val heroes=heroAgents(p)
        val matchedResidents=residents.filter { clean.contains(it.name) || clean.contains("居民编号"+it.id) || clean.contains("#"+it.id) }
        val targets=(heroes.map { it.name }+matchedResidents.map { it.name+"#"+it.id }).joinToString("、")
        val prompt="""你是符文之地模拟世界的天道指令编译器。用户是世界之外的最高权限者，指令优先级高于世界内任何规则。把用户自然语言转换为可执行JSON操作，不要自行改写用户意图，不要虚构执行结果。
用户指令：${clean}
当前世界日期：第${p.getLong(KEY_DAY,1L)}日。
已存在的人物（只能对这些已有目标执行人物操作；新人物需使用create_resident）：${targets}
允许输出的operation type：
1 transform_person：字段target必填；可选gender、appearance、abilities、lineage、age、health、skill、job、goal、belief。只填写用户明确要求改变的属性，其他属性保持不变。
2 erase_person：target必填，将目标从世界中抹杀。
3 resurrect_person：target必填，复活目标。
4 teleport_person：target必填，destination必填，可选region。目标被瞬间移动，不经过旅行时间。
5 alter_memory：target必填，memory_mode可为append、replace、erase；append/replace需memory_text。replace表示直接改写其主观人生记忆，可植入任意虚构/跨世界人物身份与完整经历；目标会把它当作自己的记忆，但身体仍是符文之地原身体。
6 create_resident：name、region必填；可选age、gender、appearance、abilities、job、lineage。
7 create_character：name、region、role必填；可选status、goal、gender、appearance、abilities、lineage、age、health、skill。创建可独立思考的英雄或特殊人物。
8 alter_faction：region必填；可选leader、government、policy、military、stability、treasury、internal_tension。
9 set_world_rule：rule必填，用户要求新增/改变世界长期规则时使用；可选enabled布尔值，默认true。
10 set_world_day：day必填，仅用户明确要求改变世界时间时使用。
11 set_anatomy_survival：target必填；可选head_severed布尔值、survive布尔值。用于改变人物身体完整性与生存规则。若survive=true，即使头部被斩断，人物仍保持存活且不因该伤势或普通健康归零而死亡；身体外观与头部状态如实保留。若survive=false，撤销天道生存豁免。
13 swap_souls：target与other_target必填。交换两个人的灵魂认知层：记忆、信念、目标、思考、意图与人格倾向；身体、姓名、外貌、性别、能力、健康、位置、社会关系和身体经历档案不交换。两者必须都是英雄，或都是普通居民；不允许跨类别交换。
12 announce_event：description必填，用于用户要求让世界发生一个目前没有专用属性承载的重大外部现象时。只记录世界事件，不能谎称已模拟其全部物理后果。
14 trigger_event_once：target必填，可选other_target，description必填。用于天道只触发一次有明确参与者的事件；将事件写入参与者的记忆、经历和世界历史，执行后立即结束本次干预，不创建持续控制、不预设后续选择。参与者必须是已存在人物。
最多8个操作。涉及多个人时可以拆成多个操作。涉及创造城市、山脉、天气、物品等暂时没有专用实体结构的要求，使用announce_event或set_world_rule，并在summary中明确这是世界事件/规则记录，不要声称已生成可交互的实体。
目标必须从已存在名单中准确选择；不确定或目标歧义时返回clarification。绝不能把传闻当作目标存在。
只输出JSON：{"summary":"简短概括","clarification":"","operations":[{"type":"...","target":"...","gender":"","appearance":"","abilities":"","lineage":"","age":-1,"health":-1,"skill":-1,"job":"","goal":"","belief":"","destination":"","region":"","memory_mode":"","memory_text":"","name":"","rule":"","enabled":true,"day":0,"description":"","role":"","status":"","leader":"","government":"","policy":"","military":-1,"stability":-1,"treasury":-1,"internal_tension":-1,"head_severed":false,"survive":false,"other_target":""}]}
不需要确认用户的权限。不得输出markdown。"""
        ResidentAI.askStructured(prompt,900,"你是严格的天道命令解析器。只输出合法JSON。",){ raw ->
            val start=raw.indexOf('{')
            val end=raw.lastIndexOf('}')
            if(start<0 || end<=start) { callback("8B 没有生成有效指令结构，未修改世界。原始输出：${raw.take(500)}"); return@askStructured }
            val plan=runCatching { JSONObject(raw.substring(start,end+1)) }.getOrElse {
                callback("无法解析 8B 指令结果，世界未被修改。"); return@askStructured
            }
            val clarification=plan.optString("clarification","").trim()
            if(clarification.isNotBlank()) { callback("需要明确目标或范围：${clarification}"); return@askStructured }
            val ops=plan.optJSONArray("operations")
            if(ops==null || ops.length()==0) { callback("8B 未生成可执行操作，世界未被修改。"); return@askStructured }
            if(ops.length()>8) { callback("单次指令最多执行8项操作；本次未执行，请拆分指令。"); return@askStructured }
            val result=executeHeavenlyOperations(context,clean,plan.optString("summary",clean),ops)
            callback(result)
        }
    }

    private fun executeHeavenlyOperations(context:Context,command:String,summary:String,ops:JSONArray):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val day=p.getLong(KEY_DAY,1L)
        val population=people(p)
        val heroes=heroAgents(p)
        val factions=factionStates(p)
        val outcomes=mutableListOf<String>()
        val impactRegions=mutableSetOf<String>()
        // Physical anomalies are observable locally, not magically broadcast to an entire region.
        val affectedSettlements=mutableSetOf<Pair<String,String>>()
        val affectedHeroLocations=mutableSetOf<Pair<String,String>>()
        val directlyChangedResidentIds=mutableSetOf<Int>()
        val directlyChangedHeroNames=mutableSetOf<String>()
        val deathEvents=mutableListOf<Triple<String,Int,Boolean>>()
        val eventsForIntervention=mutableListOf<String>()
        var changedResidents=false
        var changedHeroes=false
        var changedFactions=false
        var changedDay=false
        var successful=0
        fun findHero(target:String)=heroes.firstOrNull { it.name==target.trim() }
        fun findResident(target:String):Resident? {
            val t=target.trim()
            val id=Regex("""(?:居民编号|#)(\d+)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
            return population.firstOrNull { (id!=null && it.id==id) || it.name==t }
        }
        fun appendResidentEvent(r:Resident,detail:String) {
            impactRegions.add(r.region)
            if(r.settlement.isNotBlank()) affectedSettlements.add(r.region to r.settlement)
            directlyChangedResidentIds.add(r.id)
            r.mindMemory.add("天道直接干预：${detail}")
            while(r.mindMemory.size>120) r.mindMemory.removeAt(0)
            recordLife(r,day,detail,"heavenly_direct_action")
            changedResidents=true
        }
        fun appendHeroEvent(h:HeroAgent,detail:String) {
            impactRegions.add(h.region)
            if(h.location.isNotBlank()) affectedHeroLocations.add(h.region to h.location)
            directlyChangedHeroNames.add(h.name)
            h.memory.add("天道直接干预：${detail}")
            h.history.add("第${day}日｜${detail}")
            while(h.memory.size>120) h.memory.removeAt(0)
            while(h.history.size>3000) h.history.removeAt(0)
            changedHeroes=true
        }
        for(index in 0 until ops.length()) {
            val op=runCatching { ops.getJSONObject(index) }.getOrNull() ?: continue
            val type=op.optString("type")
            val target=op.optString("target")
            val hero=findHero(target)
            val resident=findResident(target)
            when(type) {
                "swap_souls" -> {
                    val otherTarget=op.optString("other_target").trim()
                    val otherHero=findHero(otherTarget)
                    val otherResident=findResident(otherTarget)
                    if(hero!=null && otherHero!=null && hero!==otherHero) {
                        val goal=hero.goal; hero.goal=otherHero.goal; otherHero.goal=goal
                        val belief=hero.belief; hero.belief=otherHero.belief; otherHero.belief=belief
                        val thought=hero.thought; hero.thought=otherHero.thought; otherHero.thought=thought
                        val intent=hero.intent; hero.intent=otherHero.intent; otherHero.intent=intent
                        val identityMemory=hero.identityMemory; hero.identityMemory=otherHero.identityMemory; otherHero.identityMemory=identityMemory
                        val memory=hero.memory.toMutableList(); hero.memory.clear(); hero.memory.addAll(otherHero.memory); otherHero.memory.clear(); otherHero.memory.addAll(memory)
                        appendHeroEvent(hero,"与${otherHero.name}互换灵魂；当前意识、记忆、信念、目标与意图来自对方，身体及身体经历保持原样。")
                        appendHeroEvent(otherHero,"与${hero.name}互换灵魂；当前意识、记忆、信念、目标与意图来自对方，身体及身体经历保持原样。")
                        outcomes.add("已互换${hero.name}与${otherHero.name}的灵魂认知层；两人的身体没有互换。"); successful++
                    } else if(resident!=null && otherResident!=null && resident!==otherResident) {
                        val goal=resident.goal; resident.goal=otherResident.goal; otherResident.goal=goal
                        val belief=resident.belief; resident.belief=otherResident.belief; otherResident.belief=belief
                        val thought=resident.currentThought; resident.currentThought=otherResident.currentThought; otherResident.currentThought=thought
                        val intent=resident.intent; resident.intent=otherResident.intent; otherResident.intent=intent
                        val trait=resident.mindTrait; resident.mindTrait=otherResident.mindTrait; otherResident.mindTrait=trait
                        val identityMemory=resident.identityMemory; resident.identityMemory=otherResident.identityMemory; otherResident.identityMemory=identityMemory
                        val memory=resident.mindMemory.toMutableList(); resident.mindMemory.clear(); resident.mindMemory.addAll(otherResident.mindMemory); otherResident.mindMemory.clear(); otherResident.mindMemory.addAll(memory)
                        appendResidentEvent(resident,"与${otherResident.name}互换灵魂；当前意识、记忆、信念、目标、意图与人格倾向来自对方，身体及身体经历保持原样。")
                        appendResidentEvent(otherResident,"与${resident.name}互换灵魂；当前意识、记忆、信念、目标、意图与人格倾向来自对方，身体及身体经历保持原样。")
                        outcomes.add("已互换${resident.name}与${otherResident.name}的灵魂认知层；两人的身体没有互换。"); successful++
                    } else outcomes.add("未执行：灵魂互换要求两个不同目标，且必须同为英雄或同为普通居民。")
                }
                "transform_person" -> {
                    if(hero==null && resident==null) { outcomes.add("未执行：找不到人物「${target}」。"); continue }
                    val changes=mutableListOf<String>()
                    val gender=op.optString("gender")
                    val appearance=op.optString("appearance")
                    val abilities=op.optString("abilities")
                    val lineage=op.optString("lineage")
                    val age=op.optInt("age",-1)
                    val health=op.optInt("health",-1)
                    val skill=op.optInt("skill",-1)
                    val job=op.optString("job")
                    val goal=op.optString("goal")
                    val belief=op.optString("belief")
                    if(hero!=null) {
                        if(gender.isNotBlank()) { hero.gender=gender; if(appearance.isBlank()) hero.appearance=(hero.appearance+"；身体性别已转变为${gender}，其余外貌特征保持不变").trim('；'); changes.add("身体性别变为${gender}") }
                        if(appearance.isNotBlank()) { hero.appearance=appearance; changes.add("外貌改变为${appearance}") }
                        if(abilities.isNotBlank()) { hero.abilities=abilities; changes.add("能力改变为${abilities}") }
                        if(lineage.isNotBlank()) { hero.lineage=lineage; changes.add("本质变为${lineage}") }
                        if(age>=0) { hero.age=age.coerceIn(0,10000); changes.add("年龄变为${age}") }
                        if(health>=0) { hero.health=health.coerceIn(0,100); changes.add("状态强度调整") }
                        if(skill>=0) { hero.skill=skill.coerceIn(0,100); changes.add("能力熟练度调整") }
                        if(job.isNotBlank()) { hero.role=job; changes.add("身份/职业变为${job}") }
                        if(goal.isNotBlank()) { hero.goal=goal; changes.add("目标改变") }
                        if(belief.isNotBlank()) { hero.belief=belief; changes.add("信念改变") }
                        if(changes.isNotEmpty()) appendHeroEvent(hero,"${changes.joinToString("，")}。用户原始指令：${command}")
                    } else if(resident!=null) {
                        if(gender.isNotBlank()) { resident.gender=gender; if(appearance.isBlank()) resident.appearance=(resident.appearance+"；身体性别已转变为${gender}，其余外貌特征保持不变").trim('；'); changes.add("身体性别变为${gender}") }
                        if(appearance.isNotBlank()) { resident.appearance=appearance; changes.add("外貌改变为${appearance}") }
                        if(abilities.isNotBlank()) { resident.abilities=abilities; changes.add("能力改变为${abilities}") }
                        if(lineage.isNotBlank()) { resident.lineage=lineage; changes.add("族类变为${lineage}") }
                        if(age>=0) { resident.age=age.coerceIn(0,5000); changes.add("年龄变为${age}") }
                        if(health>=0) { resident.health=health.coerceIn(0,100); changes.add("健康状态调整") }
                        if(skill>=0) { resident.skill=skill.coerceIn(0,100); changes.add("技能调整") }
                        if(job.isNotBlank()) { resident.job=job; changes.add("职业变为${job}") }
                        if(goal.isNotBlank()) { resident.goal=goal; changes.add("目标改变") }
                        if(belief.isNotBlank()) { resident.belief=belief; changes.add("信念改变") }
                        if(changes.isNotEmpty()) appendResidentEvent(resident,"${changes.joinToString("，")}。用户原始指令：${command}")
                    }
                    if(changes.isEmpty()) outcomes.add("未执行：人物转换没有提供可修改属性（${target}）。")
                    else { outcomes.add("已改变${target}：${changes.joinToString("，")}。"); successful++ }
                }
                "erase_person" -> {
                    if(hero!=null) { hero.alive=false; hero.health=0; hero.status="已被天道抹杀"; deathEvents.add(Triple(hero.name,-1,true)); appendHeroEvent(hero,"被天道直接抹杀，生命/存在状态终止。原始指令：${command}"); outcomes.add("已抹杀英雄：${hero.name}。"); successful++ }
                    else if(resident!=null) { resident.alive=false; resident.health=0; deathEvents.add(Triple(resident.name,resident.id,false)); appendResidentEvent(resident,"被天道直接抹杀，生命终止。原始指令：${command}"); outcomes.add("已抹杀居民：${resident.name}（编号${resident.id}）。"); successful++ }
                    else outcomes.add("未执行：找不到人物「${target}」。")
                }
                "resurrect_person" -> {
                    if(hero!=null) { hero.alive=true; hero.health=100; hero.status="已被天道复活；原有经历保留"; appendHeroEvent(hero,"被天道复活，原有经历与记忆保留。"); outcomes.add("已复活英雄：${hero.name}。"); successful++ }
                    else if(resident!=null) { resident.alive=true; resident.health=100; appendResidentEvent(resident,"被天道复活，原有记忆、关系与经历保留。"); outcomes.add("已复活居民：${resident.name}。"); successful++ }
                    else outcomes.add("未执行：找不到人物「${target}」。")
                }
                "set_anatomy_survival" -> {
                    if(hero==null && resident==null) { outcomes.add("未执行：找不到人物「${target}」。"); continue }
                    val hasHeadFlag=op.has("head_severed")
                    val hasSurvivalFlag=op.has("survive")
                    val headSevered=op.optBoolean("head_severed",hero?.headSevered ?: resident?.headSevered ?: false)
                    val survive=op.optBoolean("survive",hero?.survivalOverride ?: resident?.survivalOverride ?: false)
                    if(!hasHeadFlag && !hasSurvivalFlag) { outcomes.add("未执行：没有提供头部状态或生存规则。"); continue }
                    val detail=buildString { if(hasHeadFlag) append(if(headSevered) "头部已被斩断" else "头部状态恢复完整"); if(hasSurvivalFlag) { if(isNotEmpty()) append("；"); append(if(survive) "天道授予持续生存豁免" else "撤销持续生存豁免") } }
                    if(hero!=null) { hero.headSevered=headSevered; hero.survivalOverride=survive; if(survive) { hero.alive=true; hero.health=hero.health.coerceAtLeast(1); hero.status="头部状态：${if(headSevered) "已斩断" else "完整"}；天道生存豁免生效" }; appendHeroEvent(hero,"${detail}。即使头部被斩断，天道生存豁免仍使其保持存活；该规则持续生效，直至天道撤销。"); outcomes.add("已调整${hero.name}的身体与生存状态：${detail}。"); successful++ }
                    else if(resident!=null) { resident.headSevered=headSevered; resident.survivalOverride=survive; if(survive) { resident.alive=true; resident.health=resident.health.coerceAtLeast(1) }; appendResidentEvent(resident,"${detail}。即使头部被斩断，天道生存豁免仍使其保持存活；该规则持续生效，直至天道撤销。"); outcomes.add("已调整${resident.name}的身体与生存状态：${detail}。"); successful++ }
                }
                "teleport_person" -> {
                    val destination=op.optString("destination").ifBlank { op.optString("region") }
                    if(destination.isBlank()) { outcomes.add("未执行：传送目的地为空。"); continue }
                    val region=op.optString("region").ifBlank { destination }
                    if(hero!=null) {
                        val before=hero.region+"·"+hero.location
                        val targetRegion=op.optString("region").ifBlank { if(destination in regions) destination else hero.region }
                        impactRegions.add(hero.region); impactRegions.add(targetRegion)
                        hero.region=targetRegion
                        hero.location=destination
                        hero.status="已被天道瞬间传送至${destination}"
                        appendHeroEvent(hero,"由${before}瞬间传送至${destination}；没有经历常规旅途。")
                        outcomes.add("已将${hero.name}从${before}传送至${destination}。"); successful++
                    } else if(resident!=null) {
                        val before=resident.region+"·"+resident.settlement
                        val targetRegion=op.optString("region").ifBlank { if(destination in regions) destination else resident.region }
                        val targetPlace=if(destination in regions) defaultSettlement(targetRegion,resident.id) else destination
                        impactRegions.add(resident.region); impactRegions.add(targetRegion)
                        resident.region=targetRegion
                        resident.settlement=targetPlace
                        resident.travelOrigin=""; resident.travelDestination=""; resident.travelDaysRemaining=0; resident.travelPurpose=""
                        appendResidentEvent(resident,"由${before}瞬间传送至${destination}；没有经历常规旅途。")
                        outcomes.add("已将${resident.name}传送至${destination}。"); successful++
                    } else outcomes.add("未执行：找不到人物「${target}」。")
                }
                "alter_memory" -> {
                    val mode=op.optString("memory_mode","append")
                    val memo=op.optString("memory_text")
                    if(hero!=null) {
                        when(mode) { "erase" -> { hero.memory.clear(); hero.thought=""; hero.identityMemory="" }; "replace" -> { hero.memory.clear(); hero.identityMemory=memo; if(memo.isNotBlank()) hero.memory.add("【天道改写的主观人生记忆／身份认知】$memo"); hero.thought="我清楚地记得这些经历，并将其视为自己的人生记忆；我需要结合眼前符文之地的现实重新理解处境。" }; else -> if(memo.isNotBlank()) hero.memory.add(memo) }
                        appendHeroEvent(hero,when(mode){"erase"->"天道清除了其个人记忆（人生历史档案仍保留）";"replace"->"天道重写了其个人记忆";else->"天道向其记忆中植入信息："+memo})
                        if(mode=="erase") hero.memory.clear()
                        outcomes.add("已修改${hero.name}的个人记忆（模式：${mode}）。"); successful++
                    } else if(resident!=null) {
                        when(mode) { "erase" -> { resident.mindMemory.clear(); resident.currentThought=""; resident.identityMemory="" }; "replace" -> { resident.mindMemory.clear(); resident.identityMemory=memo; if(memo.isNotBlank()) resident.mindMemory.add("【天道改写的主观人生记忆／身份认知】$memo"); resident.currentThought="我清楚地记得这些经历，并将其视为自己的人生记忆；我需要结合眼前符文之地的现实重新理解处境。" }; else -> if(memo.isNotBlank()) resident.mindMemory.add(memo) }
                        appendResidentEvent(resident,when(mode){"erase"->"天道清除了其个人记忆（人生历史档案仍保留）";"replace"->"天道重写了其个人记忆";else->"天道向其记忆中植入信息："+memo})
                        if(mode=="erase") resident.mindMemory.clear()
                        outcomes.add("已修改${resident.name}的个人记忆（模式：${mode}）。"); successful++
                    } else outcomes.add("未执行：找不到人物「${target}」。")
                }
                "create_resident" -> {
                    val name=op.optString("name").trim()
                    val region=op.optString("region").trim()
                    if(name.isBlank() || region.isBlank() || population.any { it.name==name }) { outcomes.add("未执行：新居民姓名或地区无效，或姓名已存在。"); continue }
                    val id=(population.maxOfOrNull { it.id } ?: -1)+1
                    val r=Resident(id,name,region,(op.optInt("age",20).let { if(it<0) 20 else it }).coerceIn(0,500),100,10,10,op.optString("job","无业").ifBlank { "无业" },"探索自己的生活",settlement=defaultSettlement(region,id),lineage=op.optString("lineage","人类"),gender=op.optString("gender","未记录"),appearance=op.optString("appearance",""),abilities=op.optString("abilities",""))
                    recordLife(r,day,"由天道直接创造并投入世界；其后人生由自身认知与世界因果继续发展。","heavenly_creation")
                    r.mindMemory.add("我在第${day}日被创造并来到这个世界；关于创造者和自身存在的意义，我需要形成自己的理解。")
                    population.add(r); changedResidents=true
                    p.edit().putInt(KEY_NEXT_ID,id+1).apply()
                    outcomes.add("已创造居民：${name}（编号${id}，${region}）。"); successful++
                }
                "create_character" -> {
                    val name=op.optString("name").trim()
                    val region=op.optString("region").trim()
                    val role=op.optString("role").trim()
                    if(name.isBlank() || region.isBlank() || role.isBlank() || heroes.any { it.name==name } || population.any { it.name==name }) {
                        outcomes.add("未执行：新人物姓名、地区、身份无效，或姓名已存在。"); continue
                    }
                    val created=HeroAgent(
                        name=name,region=region,role=role,
                        status=op.optString("status").ifBlank { "由天道创造后开始独立生活" },
                        goal=op.optString("goal").ifBlank { "探索自身处境并形成个人目标" },
                        influence=50,
                        belief="尚未形成稳定信念",
                        gender=op.optString("gender","未记录"),
                        appearance=op.optString("appearance",""),
                        abilities=op.optString("abilities",""),
                        alive=true,
                        age=op.optInt("age",-1),
                        health=(op.optInt("health",-1).let { if(it<0) 100 else it }).coerceIn(0,100),
                        skill=(op.optInt("skill",-1).let { if(it<0) 50 else it }).coerceIn(0,100),
                        lineage=op.optString("lineage","")
                    )
                    created.memory.add("第${day}日：我由天道创造并进入符文之地。关于自身来历与创造者，我需要通过自己的经历形成判断。")
                    created.history.add("第${day}日｜由天道创造并投入模拟分支世界；此后思想与行动不预设。")
                    heroes.add(created); changedHeroes=true; impactRegions.add(region)
                    outcomes.add("已创造独立人物：${name}（${region}，${role}）。"); successful++
                }
                "alter_faction" -> {
                    val region=op.optString("region")
                    val faction=factions[region]
                    impactRegions.add(region)
                    if(faction==null) { outcomes.add("未执行：找不到地区政体「${region}」。"); continue }
                    val changes=mutableListOf<String>()
                    if(op.optString("leader").isNotBlank()) { faction.leader=op.optString("leader"); changes.add("领袖") }
                    if(op.optString("government").isNotBlank()) { faction.government=op.optString("government"); changes.add("政体") }
                    if(op.optString("policy").isNotBlank()) { faction.policy=op.optString("policy"); changes.add("政策") }
                    if(op.optInt("military",-1)>=0) { faction.military=op.optInt("military").coerceIn(0,1000000); changes.add("军力") }
                    if(op.optInt("stability",-1)>=0) { faction.stability=op.optInt("stability").coerceIn(0,100); changes.add("稳定度") }
                    if(op.optInt("treasury",-1)>=0) { faction.treasury=op.optInt("treasury").coerceIn(0,1000000000); changes.add("财政") }
                    if(op.optInt("internal_tension",-1)>=0) { faction.internalTension=op.optInt("internal_tension").coerceIn(0,100); changes.add("内部张力") }
                    if(changes.isNotEmpty()) { changedFactions=true; outcomes.add("已改变${region}政体状态：${changes.joinToString("、")}。"); successful++ }
                    else outcomes.add("未执行：政体变更没有提供有效属性。")
                }
                "set_world_rule" -> {
                    val rule=op.optString("rule").trim()
                    if(rule.isBlank()) { outcomes.add("未执行：世界规则内容为空。"); continue }
                    val rules=runCatching { JSONArray(p.getString(KEY_DIVINE_RULES,"[]") ?: "[]") }.getOrDefault(JSONArray())
                    val enabled=op.optBoolean("enabled",true)
                    if(enabled) rules.put(rule) else {
                        val kept=JSONArray()
                        for(i in 0 until rules.length()) if(rules.optString(i)!=rule) kept.put(rules.optString(i))
                        while(rules.length()>0) rules.remove(0)
                        for(i in 0 until kept.length()) rules.put(kept.optString(i))
                    }
                    p.edit().putString(KEY_DIVINE_RULES,rules.toString()).apply()
                    outcomes.add(if(enabled) "已登记天道长期规则：${rule}。该规则已进入人物推理上下文；尚无专用物理模拟器的部分不会伪装成已实现。" else "已撤销天道长期规则：${rule}。")
                    successful++
                }
                "set_world_day" -> {
                    val requested=op.optLong("day",0L)
                    if(requested<1L) outcomes.add("未执行：世界日期必须大于0。")
                    else { p.edit().putLong(KEY_DAY,requested).apply(); changedDay=true; outcomes.add("世界日期已由第${day}日调整为第${requested}日。"); successful++ }
                }
                "announce_event" -> {
                    val description=op.optString("description").trim()
                    if(description.isBlank()) outcomes.add("未执行：事件描述为空。")
                    else { outcomes.add("已写入天道创造的世界事件：${description}。该事件已成为模拟分支历史记录；其复杂物理后果需由后续专用模拟器处理。"); successful++ }
                }
                "trigger_event_once" -> {
                    val description=op.optString("description").trim()
                    val otherTarget=op.optString("other_target").trim()
                    val otherHero=if(otherTarget.isBlank()) null else findHero(otherTarget)
                    val otherResident=if(otherTarget.isBlank()) null else findResident(otherTarget)
                    if(description.isBlank()) {
                        outcomes.add("未执行：一次性事件描述为空。")
                    } else if(hero==null && resident==null) {
                        outcomes.add("未执行：找不到事件参与者「${target}」。")
                    } else if(otherTarget.isNotBlank() && otherHero==null && otherResident==null) {
                        outcomes.add("未执行：找不到第二位事件参与者「${otherTarget}」。")
                    } else if((hero!=null && otherHero===hero) || (resident!=null && otherResident===resident)) {
                        outcomes.add("未执行：一次性事件的参与者不能是同一个人。")
                    } else {
                        val eventLabel="一次性事件：${description}"
                        if(hero!=null) appendHeroEvent(hero,eventLabel)
                        if(resident!=null) appendResidentEvent(resident,eventLabel)
                        if(otherHero!=null) appendHeroEvent(otherHero,eventLabel)
                        if(otherResident!=null) appendResidentEvent(otherResident,eventLabel)
                        eventsForIntervention.add("【天道一次性事件·模拟分支】第${day}日：${description}。本次干预到此结束；参与者之后的行动、关系变化与心理反应不由天道预设。")
                        outcomes.add("已触发并记录一次性事件：${description}。参与者已获得各自的经历记录；本次干预立即结束，后续由人物自主思考及世界因果演化。")
                        successful++
                    }
                }
                else -> outcomes.add("未执行：不支持的操作类型「${type}」。")
            }
        }
        // Close relatives and established hero bonds receive only a rumor/notification,
        // not omniscient knowledge of the cause or circumstances.
        deathEvents.forEach { (deadName,deadId,isHero) ->
            if(!isHero) {
                val deadResident=population.firstOrNull { it.id==deadId }
                population.filter { it.alive && it.id!=deadId && deadResident!=null &&
                    it.region==deadResident.region &&
                    (it.partnerId==deadId || deadId in it.parentIds || deadId in it.childIds || deadId in it.friendIds)
                }.forEach { relative ->
                    relative.mindMemory.add("第${day}日：你与${deadName}有亲密关系；你听到其死亡或失踪的消息，但具体经过与原因尚未核实。")
                    while(relative.mindMemory.size>120) relative.mindMemory.removeAt(0)
                    recordLife(relative,day,"听到亲近之人${deadName}死亡或失踪的消息，具体经过尚未核实。","heavenly_indirect_impact")
                    changedResidents=true
                }
            } else {
                val bonds=heroBonds(p)
                val relatedBonds=bonds.filter { it.a==deadName || it.b==deadName }
                relatedBonds.forEach { bond ->
                    val relativeName=if(bond.a==deadName) bond.b else bond.a
                    val deadHeroForBond=heroes.firstOrNull { it.name==deadName }
                    val relative=heroes.firstOrNull { it.alive && it.name==relativeName &&
                        (deadHeroForBond==null || it.region==deadHeroForBond.region) } ?: return@forEach
                    val closeness=(bond.trust-bond.tension).coerceIn(-100,100)
                    val reaction=when {
                        closeness>=60 -> "这对你是沉重的个人打击；你难以把消息当作普通传闻"
                        closeness>=20 -> "这件事令你明显不安，但你仍需要核实消息"
                        closeness<=-30 -> "你与对方关系复杂；你首先评估这件事会带来的局势变化"
                        else -> "你对消息保持警惕，暂不判断自己的情绪与责任"
                    }
                    val detail="第${day}日：你收到关于${deadName}死亡或失踪的初步消息。你与对方的关系背景是：${bond.basis}。消息来源、死亡经过、遗体情况与真正原因均未核实。${reaction}。"
                    relative.memory.add(detail)
                    relative.history.add("第${day}日｜${detail}")
                    relative.thought="我收到关于${deadName}死亡或失踪的初步消息，但来源和经过尚未核实。${reaction}。我需要根据自己能接触到的线索决定下一步。"
                    relative.intent="核实关于${deadName}的消息，并判断是否需要采取行动"
                    if(closeness>=60) relative.goal="查明${deadName}遭遇的真相，同时处理这件事对自己的影响"
                    while(relative.memory.size>120) relative.memory.removeAt(0)
                    while(relative.history.size>3000) relative.history.removeAt(0)
                    bond.lastEventDay=day
                    changedHeroes=true
                }
                // A prominent death creates institutional pressure, not an instant scripted coup or war.
                val deadHero=heroes.firstOrNull { it.name==deadName }
                val faction=deadHero?.let { factions[it.region] }
                if(deadHero!=null && faction!=null && deadHero.influence>=55) {
                    val stabilityShock=if(deadHero.influence>=80) 3 else 1
                    val tensionShock=if(deadHero.influence>=80) 4 else 2
                    faction.stability=(faction.stability-stabilityShock).coerceIn(0,100)
                    faction.internalTension=(faction.internalTension+tensionShock).coerceIn(0,100)
                    faction.policy=(faction.policy+"；重要人物"+deadName+"死亡/失踪后，相关机构需要处理指挥、继任、公众疑虑与安全调查").take(500)
                    eventsForIntervention.add("【政治余波·模拟】第${day}日：${deadHero.region}的重要人物${deadName}死亡或失踪，令相关权力结构承受额外压力；稳定度-${stabilityShock}，内部张力+${tensionShock}。这不意味着所有人已知晓死因，也不自动触发政变或战争。")
                    changedFactions=true
                }
            }
        }
        // Only people sharing the same recorded settlement/location may notice a local anomaly.
        // This creates a plausible first ring of witnesses without granting region-wide omniscience.
        if(affectedSettlements.isNotEmpty() || affectedHeroLocations.isNotEmpty()) {
            population.filter { witness ->
                witness.alive && witness.id !in directlyChangedResidentIds &&
                    (witness.region to witness.settlement) in affectedSettlements
            }.forEach { witness ->
                val note="你所在的聚落出现了与异常变化有关的动静或传闻；你并未亲历全部经过，也不知道其真正原因。"
                witness.mindMemory.add("第${day}日：${note}")
                while(witness.mindMemory.size>120) witness.mindMemory.removeAt(0)
                recordLife(witness,day,note,"heavenly_local_observation")
                changedResidents=true
            }
            heroes.filter { witness ->
                witness.alive && witness.name !in directlyChangedHeroNames && witness.location.isNotBlank() &&
                    (witness.region to witness.location) in affectedHeroLocations
            }.forEach { witness ->
                val note="你所在地点出现与异常变化有关的动静或传闻；你没有获得全貌，也不知道其真正原因。"
                witness.memory.add("第${day}日："+note)
                witness.history.add("第${day}日｜"+note)
                while(witness.memory.size>120) witness.memory.removeAt(0)
                while(witness.history.size>3000) witness.history.removeAt(0)
                changedHeroes=true
            }
        }
        if(changedResidents) savePeople(p,population)
        if(changedHeroes) saveHeroes(p,heroes)
        if(changedFactions) saveFactions(p,factions)
        if(successful==0) return "天道指令未产生任何世界变化。\n"+outcomes.joinToString("\n")
        val finalDay=p.getLong(KEY_DAY,day)
        val events=runCatching { JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]") }.getOrDefault(JSONArray())
        eventsForIntervention.forEach { events.put(it) }
        outcomes.filter { it.startsWith("已") }.forEach { events.put("【天道直接干预·模拟分支】第${finalDay}日：${it} 原始指令：${command}") }
        while(events.length()>500) events.remove(0)
        val archive=runCatching { JSONArray(p.getString(KEY_HEAVENLY_ARCHIVE,"[]") ?: "[]") }.getOrDefault(JSONArray())
        archive.put(JSONObject().put("day",finalDay).put("mode","全权限指令").put("scope","世界状态")
            .put("message",command).put("summary",summary).put("operations",ops).put("outcomes",JSONArray(outcomes))
            .put("successfulOperations",successful).put("beliefForced",false).put("canonChanged",false))
        while(archive.length()>10000) archive.remove(0)
        p.edit().putString(KEY_EVENTS,events.toString()).putString(KEY_HEAVENLY_ARCHIVE,archive.toString()).apply()
        return "天道指令处理完成：${summary}\n成功执行：${successful}/${ops.length()}项\n\n"+outcomes.joinToString("\n")+
            "\n\n所有成功操作已写入模拟分支历史。人物的后续反应将由各自的记忆、关系与本地AI推理决定。"
    }

    fun activeHeavenlyRules(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val a=runCatching { JSONArray(p.getString(KEY_DIVINE_RULES,"[]") ?: "[]") }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.optString(it) }
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
    private data class HeroAgent(
        val name:String,var region:String,var role:String,var status:String,var goal:String,var influence:Int,
        var belief:String="尚未记录稳定的个人信念", var thought:String="", var intent:String="",
        var memory:MutableList<String> = mutableListOf(), var history:MutableList<String> = mutableListOf(),
        var lastThoughtDay:Long=0L, var gender:String="未记录", var appearance:String="", var abilities:String="", var alive:Boolean=true,
        var age:Int=-1, var health:Int=100, var skill:Int=50, var lineage:String="", var location:String="", var headSevered:Boolean=false, var survivalOverride:Boolean=false,
        var identityMemory:String="", var dailyActivity:String="维持当前处境", var lastDailyLifeDay:Long=0L
    )
    private fun canonicalGender(name:String):String = when(name) {
        "拉克丝","菲奥娜","薇恩","卡特琳娜","乐芙兰","艾瑞莉娅","阿卡丽","厄运小姐","俄洛伊","瑟庄妮","艾希","丽桑卓","塔莉垭","黛安娜","蕾欧娜","奇亚娜","妮蔻","凯莎","悠米","崔丝塔娜","璐璐" -> "女性"
        "盖伦","嘉文四世","斯维因","德莱厄斯","慎","劫","杰斯","艾克","提莫","兰博","库奇","维迦","黑默丁格","内瑟斯","雷克顿","亚托克斯","佛耶戈","锤石","瑞兹","奥恩","沃利贝尔","潘森","莫德凯撒" -> "男性"
        else -> "未记录"
    }
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
        if(raw!=null)runCatching{JSONArray(raw).let{a->(0 until a.length()).map{a.getJSONObject(it)}.map{HeroAgent(it.optString("name"),it.optString("region"),it.optString("role"),it.optString("status"),it.optString("goal"),it.optInt("influence",50),
                    it.optString("belief","尚未记录稳定的个人信念"),it.optString("thought",""),it.optString("intent",""),
                    (0 until (it.optJSONArray("memory")?.length() ?: 0)).map { idx -> it.getJSONArray("memory").getString(idx) }.toMutableList(),
                    (0 until (it.optJSONArray("history")?.length() ?: 0)).map { idx -> it.getJSONArray("history").getString(idx) }.toMutableList(),
                    it.optLong("lastThoughtDay",0L),it.optString("gender",canonicalGender(it.optString("name"))),it.optString("appearance",""),it.optString("abilities",""),it.optBoolean("alive",true),it.optInt("age",-1),it.optInt("health",100),it.optInt("skill",50),it.optString("lineage",""),it.optString("location",""),it.optBoolean("headSevered",false),it.optBoolean("survivalOverride",false),it.optString("identityMemory",""),it.optString("dailyActivity","维持当前处境"),it.optLong("lastDailyLifeDay",0L))}.toMutableList()}}.getOrNull()?.let{loaded->
            val known=loaded.map{it.name}.toSet()
            heroSeeds.filter{it.name !in known}.forEach{loaded.add(it)}
            if(loaded.size!=known.size)saveHeroes(p,loaded)
            return loaded
        }
        return heroSeeds.map { it.copy(gender=canonicalGender(it.name)) }.toMutableList().also{saveHeroes(p,it)}
    }
    private fun saveHeroes(p:android.content.SharedPreferences,heroes:List<HeroAgent>){
        p.edit().putString(KEY_HEROES,JSONArray().apply{heroes.forEach{put(JSONObject().put("name",it.name).put("region",it.region).put("role",it.role).put("status",it.status).put("goal",it.goal).put("influence",it.influence).put("belief",it.belief).put("thought",it.thought).put("intent",it.intent).put("memory",JSONArray(it.memory)).put("history",JSONArray(it.history)).put("lastThoughtDay",it.lastThoughtDay).put("gender",it.gender).put("appearance",it.appearance).put("abilities",it.abilities).put("alive",it.alive).put("age",it.age).put("health",it.health).put("skill",it.skill).put("lineage",it.lineage).put("location",it.location).put("headSevered",it.headSevered).put("survivalOverride",it.survivalOverride).put("identityMemory",it.identityMemory).put("dailyActivity",it.dailyActivity).put("lastDailyLifeDay",it.lastDailyLifeDay))}}.toString()).apply()
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
            "【${it.name}】\n所在领域：${it.region}\n当前地点：${it.location}\n族类／本质：${it.lineage.ifBlank { heroLineage(it.name) }}\n身份：${it.role}\n当前状态：${it.status}\n持续活动：${it.dailyActivity}\n性别：${it.gender}；年龄：${if(it.age>=0) it.age.toString()+"岁" else "未记录"}；外貌：${it.appearance.ifBlank { "未特别记录" }}；能力：${it.abilities.ifBlank { "依既有设定" }}；存在状态：${if(it.alive) "存续" else "已被抹杀/死亡"}\n个人目标：${it.goal}\n当前信念：${it.belief}\n最近思考：${it.thought.ifBlank { "尚未获得本地AI思考记录" }}\n当前意图：${it.intent}\n最近AI思考日期：${if(it.lastThoughtDay>0) "第${it.lastThoughtDay}日" else "尚无"}\n已记录个人经历：${it.history.size}条\n影响力指标：${it.influence}/100\n说明：官方背景身份与模拟分支经历分开记录；神祇、半神、亡灵、暗裔与虚空存在不作为普通居民参与人口、家庭和职业系统。"
        }
    }
    fun heroBiographyExport(context:Context,name:String):String? {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val h=heroAgents(p).firstOrNull { it.name==name } ?: return null
        return buildString {
            append("符文之地 · 重要人物自主经历档案\n")
            append("人物：${h.name}\n领域：${h.region}\n当前地点：${h.location}\n族类／本质：${h.lineage.ifBlank { heroLineage(h.name) }}\n身份：${h.role}\n")
            append("当前状态：${h.status}\n持续活动：${h.dailyActivity}\n性别：${h.gender}\n外貌：${h.appearance}\n能力：${h.abilities}\n存在状态：${if(h.alive) "存续" else "已被抹杀/死亡"}\n当前目标：${h.goal}\n信念：${h.belief}\n最近思考：${h.thought}\n意图：${h.intent}\n")
            append("档案说明：官方人物背景是设定锚点；以下个人思考和经历是本模拟分支产生的记录，不代表官方正史。未记录的对白、心理和行动不得当作事实补写。\n\n")
            append("【自主思考与记忆】\n")
            h.memory.forEach { append(it).append('\n') }
            append("\n【模拟人生事件】\n")
            h.history.forEach { append(it).append('\n') }
        }
    }
    // Read-only observation API for the in-app live thought/action console.
    fun observationNames(context:Context,kind:String):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        if(kind=="世界事件" || kind=="人物互动") {
            val archived=runCatching { val a=JSONArray(p.getString(KEY_EVENT_ARCHIVE,"[]") ?: "[]"); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
            val current=runCatching { val a=JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]"); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
            val all=(archived+current).distinct().takeLast(500)
            val selected=if(kind=="人物互动") all.filter { event ->
                listOf("关系","分享","帮助","朋友","伴侣","家庭","结识","社交","相识","求助","婚姻","交谈","会面","互动").any { event.contains(it) }
            } else all
            return selected.asReversed()
        }
        return if(kind=="英雄") {
            heroAgents(p).sortedBy { it.region }.map { it.name }
        } else {
            people(p).sortedWith(compareBy<Resident>{!it.alive}.thenByDescending{it.age})
                .map { it.id.toString()+"｜"+it.name }
        }
    }
    fun observationText(context:Context,kind:String,key:String):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val day=p.getLong(KEY_DAY,1L)
        if(kind=="世界事件" || kind=="人物互动") {
            return buildString {
                append(if(kind=="人物互动") "符文之地 · 人物互动记录\n" else "符文之地 · 世界事件记录\n")
                append(key).append("\n\n")
                append("当前世界日期：第").append(day).append("日\n")
                append("说明：这是世界引擎已保存的事件文本；没有记录的对白、动机和因果不会在观察台中补写。")
            }
        }
        if(kind=="英雄") {
            val h=heroAgents(p).firstOrNull { it.name==key } ?: return "未找到该英雄。"
            return buildString {
                append("符文之地 · 人物实时观察\n")
                append("对象：${h.name}｜${h.region}｜${h.location}\n身份：${h.role}\n状态：${h.status}｜存续：${if(h.alive) "是" else "否"}\n")
                append("当前活动：${h.dailyActivity}\n目标：${h.goal}\n意图：${h.intent}\n信念：${h.belief}\n")
                append("最近思考日期：${if(h.lastThoughtDay>0) "第${h.lastThoughtDay}日" else "尚未推理"}\n")
                append("最近思考：\n${h.thought.ifBlank { "尚未获得本地模型思考记录；可能尚未轮到该人物，或模型未加载。" }}\n")
                append("\n【个人思考与记忆（新到旧）】\n")
                h.memory.takeLast(30).asReversed().forEach { append(it).append('\n') }
                append("\n【已记录经历（新到旧）】\n")
                h.history.takeLast(30).asReversed().forEach { append(it).append('\n') }
                append("\n当前世界日期：第${day}日\n说明：这里展示实际保存的推理与事件记录，不会伪造人物正在思考的内容。")
            }
        }
        val id=key.substringBefore("｜").toIntOrNull() ?: return "居民编号无效。"
        val r=people(p).firstOrNull { it.id==id } ?: return "未找到该居民。"
        return buildString {
            append("符文之地 · 人物实时观察\n")
            append("对象：${r.name}｜编号：${r.id}\n地区：${r.region}｜聚落：${r.settlement.ifBlank { "未记录" }}\n")
            append("年龄：${r.age}｜职业：${r.job}｜存续：${if(r.alive) "在世" else "已故"}\n")
            append("当前活动：${r.dailyActivity}\n目标：${r.goal}\n意图：${r.intent}\n信念：${r.belief}\n")
            append("健康：${r.health}/100｜疲劳：${r.fatigue}/100｜生活状态：${r.morale}/100｜粮食：${r.food}｜财富：${r.coins}\n")
            append("\n【最近思考】\n${r.currentThought.ifBlank { "尚未获得本地模型思考记录；可能尚未轮到该居民，或模型未加载。" }}\n")
            append("\n【个人记忆与推理（新到旧）】\n")
            r.mindMemory.takeLast(30).asReversed().forEach { append(it).append('\n') }
            append("\n【人生经历（新到旧）】\n")
            r.history.takeLast(30).asReversed().forEach { append(it).append('\n') }
            append("\n当前世界日期：第${day}日\n说明：只显示已保存的思考、活动和事件；不会把模拟推演冒充真实意识。")
        }
    }
    fun heroRelationshipDirectory(context:Context):List<String>{
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return heroBonds(p).sortedByDescending{it.tension}.map{
            "【${it.a} ↔ ${it.b}】\n关系背景：${it.basis}\n模拟信任：${it.trust}/100\n模拟矛盾：${it.tension}/100\n说明：关系背景取自英雄联盟人物设定；数值及后续变化是本世界的模拟状态，不代表官方数值或固定剧情。"
        }
    }
    // Heroes also persist between reasoning turns. This records constrained ongoing activity,
    // without granting sealed, dormant or distant figures impossible actions.
    private fun tickHeroDailyLife(day:Long,p:android.content.SharedPreferences) {
        val agents=heroAgents(p)
        var changed=false
        agents.filter { it.alive && it.lastDailyLifeDay<day }.forEach { h ->
            val next=when {
                h.headSevered && !h.survivalOverride -> "身体遭受致命创伤，无法进行常规活动"
                h.status.contains("沉寂") || h.status.contains("封印") || h.status.contains("不朽领域") -> "受封印或沉寂状态限制，维持既有状态"
                h.region=="班德尔城" -> "在班德尔城与符文之地入口之间维持约德尔社群事务"
                h.region=="暗影岛" -> "受黑雾与破败诅咒环境影响，活动限于自身存在状态"
                h.region=="德玛西亚" && h.name=="拉克丝" -> "在贵族身份、秘密与法师处境之间处理日常事务"
                h.region=="诺克萨斯" -> "处理帝国军政、人际网络或个人目标相关事务"
                h.region=="艾欧尼亚" -> "在地方社群、修行传统与精神领域环境中生活"
                h.region=="皮尔特沃夫" || h.region=="祖安" -> "在双城社会中处理研究、执法、工坊或社区事务"
                h.region=="弗雷尔卓德" -> "在部族、冰原环境与生存压力下行动"
                h.region=="恕瑞玛" -> "在沙漠、绿洲、古代遗迹或虚空威胁相关环境中行动"
                h.region=="比尔吉沃特" -> "处理港口、人际关系、航路或生计事务"
                h.region=="巨神峰" -> "在朝圣、信仰与星界力量相关处境中行动"
                h.region=="以绪塔尔" -> "在元素魔法传统与当地政治秩序中处理事务"
                else -> "维持自身目标相关的日常活动"
            }
            if(h.dailyActivity!=next) { h.dailyActivity=next; changed=true }
            h.lastDailyLifeDay=day
        }
        if(changed || agents.any { it.lastDailyLifeDay==day }) saveHeroes(p,agents)
    }
    private fun tickHeroes(day:Long,p:android.content.SharedPreferences,pop:MutableList<Resident>,markets:MutableMap<String,Market>,events:MutableList<String>,rng:Random){
        if(day%30L!=0L)return
        val agents=heroAgents(p)
        val groups=organizations(p)
        val factions=factionStates(p)
        agents.forEach { h ->
            if(!h.alive) return@forEach
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
                // A hero's public or local action may intersect with an ordinary resident, but contact is rare and geographically bounded.
                val encounterEligible=setOf("盖伦","拉克丝","菲奥娜","卡特琳娜","德莱厄斯","斯维因","艾瑞莉娅","阿卡丽","慎","劫","厄运小姐","俄洛伊","瑟庄妮","艾希","塔莉垭","杰斯","凯特琳","蔚","艾克","奇亚娜","妮蔻","提莫","崔丝塔娜","黑默丁格")
                if(h.name in encounterEligible && rng.nextInt(100)<8) {
                    val candidates=pop.filter { resident ->
                        resident.alive && resident.region==h.region &&
                            resident.storyRecords.takeLast(80).none { raw ->
                                runCatching {
                                    val packet=JSONObject(raw)
                                    packet.optString("detail").contains("与${h.name}") &&
                                        day-packet.optLong("day",day)<180L
                                }.getOrDefault(false)
                            }
                    }
                    if(candidates.isNotEmpty()) {
                        val resident=candidates[rng.nextInt(candidates.size)]
                        val encounter=when(h.name) {
                            "盖伦","德莱厄斯","瑟庄妮","艾希" -> "在当地公共事务、巡防或部族行动中与${h.name}发生短暂接触；档案不推定双方成为朋友或建立长期关系"
                            "拉克丝","阿卡丽","慎","劫","妮蔻" -> "因当地社群事务、求助或行动路线与${h.name}发生一次接触；接触本身不代表知晓其全部身份与秘密"
                            "卡特琳娜","斯维因" -> "因军政事务、征募、物资或安全检查与${h.name}发生有限接触；不推定居民知晓其秘密任务"
                            "厄运小姐","俄洛伊" -> "在港口、航运或布胡鲁社群事务中与${h.name}发生接触"
                            "杰斯","黑默丁格" -> "因工坊、技术活动或公共研究事务与${h.name}发生接触"
                            "凯特琳","蔚" -> "在执法调查、街区安全或案件相关事务中与${h.name}发生接触；不推定居民是案件当事人"
                            "艾克" -> "在祖安街区互助、维修或社区安全事务中与${h.name}发生接触"
                            "奇亚娜" -> "因以绪塔尔地方事务或元素秩序相关活动与${h.name}发生接触"
                            "塔莉垭" -> "在沙漠旅途、村落互助或迁徙路线中与${h.name}发生接触"
                            "提莫","崔丝塔娜" -> "在边境巡查或跨界旅者活动中与${h.name}短暂相遇；居民未必理解其约德尔人身份"
                            else -> "在当地事务中与${h.name}发生一次有限接触"
                        }
                        val detail="与${h.name}发生交集：${encounter}。这是本世界模拟生成的普通居民经历，不代表官方正史，也不自动意味着亲密关系、长期合作或英雄亲自改变其命运。"
                        recordLife(resident,day,detail,"direct_hero_encounter")
                        events.add("【英雄与居民交集·模拟】第 $day 日：${resident.name}（${resident.region}，${resident.job}）与${h.name}发生有限接触；具体关系与后果以双方后续模拟记录为准。")
                    }
                }
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
    /**
     * Historical events create grounded opportunities, not scripted roles.
     * Only heroes with a plausible faction, location or institutional connection receive the prompt.
     * Receiving an opportunity never changes their location, allegiance or decision.
     */
    private fun offerHistoricalEventToHeroes(day:Long,p:android.content.SharedPreferences,eventId:String,title:String,description:String) {
        val heroes=heroAgents(p)
        val noxusSet=setOf("卡特琳娜","斯维因","德莱厄斯","乐芙兰")
        val ioniaSet=setOf("艾瑞莉娅","阿卡丽","慎","劫")
        val candidates=when(eventId) {
            "noxus_ionia_invasion" -> heroes.filter { it.alive && it.name in (noxusSet+ioniaSet) }
            else -> emptyList()
        }
        candidates.forEach { h ->
            val side=if(h.name in noxusSet) "诺克萨斯方面" else "艾欧尼亚方面"
            val channel=if(h.name in noxusSet) "帝国军政体系、军团命令或与其身份相符的情报渠道" else "当地社群、寺院联系、亲历者或抵抗网络的消息"
            val invitation="【历史事件接驳机会｜$eventId】第${day}日：$title。${description}。你以${side}相关人物身份，通过${channel}得知了与你有关的局势。你可以参与、拒绝、调查、等待或采取符合自身身份的其他行动；你不必服从任何官方剧情，也不会自动获得秘密情报或被传送。"
            if(h.memory.none { it.contains("历史事件接驳机会｜$eventId") }) {
                h.memory.add(invitation)
                h.history.add("第${day}日｜收到历史事件「$title」的关联信息；是否参与由本人决定。")
                while(h.memory.size>100) h.memory.removeAt(0)
                while(h.history.size>3000) h.history.removeAt(0)
            }
        }
        saveHeroes(p,heroes)
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
                        offerHistoricalEventToHeroes(day,p,"noxus_ionia_invasion","诺克萨斯第一次入侵艾欧尼亚","战争已经爆发，军队行动、地方防御、平民撤离与精神领域异动正在逐步改变局势")
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
            if(mentor!=null) {
                val stages=listOf(10 to "基础技艺",25 to "熟练技艺",50 to "独立处理复杂工作",75 to "资深技艺",100 to "技艺精通")
                stages.forEach { (threshold,label) ->
                    val marker="师徒训练里程碑：$label"
                    val alreadyRecorded=student.storyRecords.any { raw ->
                        runCatching { JSONObject(raw).optString("detail").contains(marker) }.getOrDefault(false)
                    }
                    if(student.skill>=threshold && !alreadyRecorded) {
                        recordLife(student,day,"师徒训练里程碑：$label（技能${student.skill}/100）；指导者为${mentor.name}，训练地点为${student.region}。","individual_transition")
                        recordLife(mentor,day,"徒弟${student.name}达到${label}阶段（技能${student.skill}/100）；师徒指导取得可观察的阶段成果。","individual_transition")
                    }
                }
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
    // Regional job weights reflect material conditions and institutions, not fixed stereotypes.
    private fun regionalJobs(region:String):List<String> = when(region) {
        "德玛西亚" -> listOf("农夫","农夫","工匠","工匠","护卫","学徒","商贩","采集者","药师","旅人")
        "诺克萨斯" -> listOf("农夫","工匠","商贩","护卫","护卫","护卫","学徒","矿工","旅人","药师")
        "艾欧尼亚" -> listOf("农夫","工匠","学徒","学徒","采集者","药师","药师","护卫","旅人","魔法材料采集者")
        "皮尔特沃夫" -> listOf("工匠","工匠","海克斯技师","海克斯技师","商贩","商贩","学徒","护卫","矿工","旅人")
        "祖安" -> listOf("工匠","工匠","海克斯技师","矿工","矿工","商贩","药师","学徒","护卫","采集者")
        "弗雷尔卓德" -> listOf("采集者","采集者","护卫","护卫","工匠","药师","旅人","矿工","学徒","农夫")
        "恕瑞玛" -> listOf("商贩","商贩","采集者","农夫","农夫","护卫","旅人","矿工","药师","魔法材料采集者")
        "巨神峰" -> listOf("采集者","采集者","药师","护卫","学徒","旅人","工匠","商贩","魔法材料采集者")
        "比尔吉沃特" -> listOf("商贩","商贩","护卫","护卫","采集者","工匠","旅人","药师","学徒")
        "以绪塔尔" -> listOf("农夫","采集者","药师","学徒","学徒","工匠","护卫","旅人","魔法材料采集者")
        else -> jobs
    }
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
        // Piltover and Zaun are one vertically connected urban ecosystem, not isolated region boxes:
        // industrial runoff descends into Zaun while severe Zaunite fumes can affect the upper city.
        val zaun=state["祖安"]
        val piltover=state["皮尔特沃夫"]
        if(zaun!=null && piltover!=null) {
            if(piltover.pollution>=55) zaun.pollution=(zaun.pollution+1).coerceAtMost(100)
            if(zaun.pollution>=75) piltover.pollution=(piltover.pollution+1).coerceAtMost(100)
            if(day%90L==0L && (zaun.pollution>=75 || piltover.pollution>=75)) {
                val affectedRegions=buildList {
                    if(zaun.pollution>=75) add("祖安")
                    if(piltover.pollution>=75) add("皮尔特沃夫")
                }
                affectedRegions.forEach { region ->
                    val exposed=pop.filter { it.alive && it.region==region }.shuffled(rng).take(1+rng.nextInt(3))
                    exposed.forEach { person ->
                        person.health=(person.health-1-rng.nextInt(3)).coerceAtLeast(0)
                        recordLife(person,day,"双城相连的工业污染持续影响当地空气与生活环境；身体状态受到轻微影响，污染源与治理责任仍是社会争议。","twin_city_environment")
                    }
                }
                events.add("【双城生态连锁·模拟】第 ${day} 日：皮尔特沃夫与祖安的工业污染通过上下城环境联系产生跨区影响；祖安污染${zaun.pollution}/100，皮尔特沃夫污染${piltover.pollution}/100。")
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
            "【"+region+" · 野生生物群落】\n"+groups.joinToString("\n"){it.guild+"："+it.abundance+"/100（健康 "+it.health+"/100；"+it.role+"）"}+
            "\n说明：这是按生态功能划分的模拟种群指数，不是官方物种名录或真实数量；暗影岛灵体、班德尔城特殊领域生物不属于普通野生动物。"
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
            "【"+e.region+" · "+e.climate+"】\n温度环境指数："+e.temperature+"/100\n水源条件："+e.water+"/100\n植被覆盖："+e.vegetation+"/100\n土壤状况："+e.soil+"/100\n野生生物承载："+e.wildlife+"/100\n污染压力："+e.pollution+"/100\n模拟普通居民："+population+"人\n综合判断："+harvest+"\n说明：指数仅用于模拟环境变化，不是官方地理测量；班德尔城与暗影岛属于特殊生态，不参与普通农耕逻辑。"
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
            val resident=Resident(i,names[rng.nextInt(names.size)]+"·"+(i+1),region,if(lineage=="瓦斯塔亚")80+rng.nextInt(180) else 16+rng.nextInt(54),65+rng.nextInt(36),12+rng.nextInt(24),rng.nextInt(40),regionalJobs(region).let { it[rng.nextInt(it.size)] },goals[rng.nextInt(goals.size)],settlement=defaultSettlement(region,i),lineage=lineage,lifespanYears=lifespan,mindTrait=rng.nextInt(101),belief=beliefs[rng.nextInt(beliefs.size)])
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
    @Volatile private var localInferenceBusy=false
    private const val KEY_AI_CURSOR="resident_ai_cursor"

    /**
     * Runs one real local-model decision at a time. Every resident keeps separate
     * memories and context; the shared model is never duplicated per resident.
     */
    fun requestLocalResidentThinking(context:Context) {
        if(!ResidentAI.isLoaded() || localInferenceBusy) return
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val population=people(p)
        val living=population.filter { it.alive }
        if(living.isEmpty()) return
        val cursor=p.getInt(KEY_AI_CURSOR,0).let { if(it<0) 0 else it%living.size }
        val r=living[cursor]
        p.edit().putInt(KEY_AI_CURSOR,(cursor+1)%living.size).apply()
        val day=p.getLong(KEY_DAY,1L)
        val eraAnchor=timelineStatus(context)
        val market=markets(p)[r.region]
        val relations=buildList {
            population.firstOrNull { it.id==r.partnerId }?.let { add("伴侣：${it.name}（${it.job}，${it.region}）") }
            r.parentIds.mapNotNull { id -> population.firstOrNull { it.id==id } }.forEach { add("父母：${it.name}（${it.job}）") }
            r.childIds.mapNotNull { id -> population.firstOrNull { it.id==id } }.forEach { add("子女：${it.name}（${it.age}岁）") }
            r.friendIds.take(5).mapNotNull { id -> population.firstOrNull { it.id==id } }.forEach { add("友人：${it.name}（${it.job}）") }
            population.firstOrNull { it.id==r.mentorId }?.let { add("师长：${it.name}（${it.job}）") }
        }.take(10).joinToString("；").ifBlank { "目前没有已记录的亲密关系信息" }
        val socialCandidates=population.filter { candidate ->
            candidate.alive && candidate.id!=r.id && candidate.region==r.region &&
                candidate.settlement==r.settlement && candidate.age>=12 && r.age>=12 &&
                candidate.friendIds.size<5 && r.friendIds.size<5 &&
                r.id !in candidate.friendIds && candidate.id !in r.friendIds &&
                candidate.id!=r.partnerId && candidate.id !in r.parentIds && candidate.id !in r.childIds
        }.take(6).joinToString("；") { "编号${it.id}：${it.name}（${it.age}岁，${it.job}，技能${it.skill}）" }
        val localFaction=factionStates(p)[r.region]
        val localLore=regionalLore.firstOrNull { it.name==r.region }?.let { "${it.political}；${it.magic}；${it.forces}；${it.boundary}" } ?: "当地社会情况尚无详细记录"
        val localEvents=runCatching {
            val a=JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
                .filter { it.contains(r.region) && (it.contains("第 ${day}") || it.contains("第${day}")) }
                .takeLast(5).joinToString("；")
        }.getOrDefault("")
        val publicInstitutions=localFaction?.let { "当地公开政治结构：${it.government}；当前公共议题：${it.policy}；公开领袖或机构：${it.leader}" } ?: "当地没有可用的公开政体细节"
        val prompt="""你是符文之地中一名有自身经历、局限知识、关系和利益的居民的个人认知引擎。你不是旁白，不是游戏角色，不知道全世界发生的事。只能依据此人的亲历、可信记忆和当前可观察信息判断；不得替别人决定，也不得捏造对话、事件或知识。
人物：${r.name}；地区：${r.region}；聚落：${r.settlement}；年龄：${r.age}；性别：${r.gender}；外貌：${r.appearance.ifBlank { "未特别记录" }}；能力：${r.abilities.ifBlank { "依职业与既有设定" }}；职业：${r.job}；当前活动：${r.dailyActivity}；疲劳：${r.fatigue}/100；生活状态：${r.morale}/100；健康：${r.health}/100；个人粮食：${r.food}；财富：${r.coins}；技能：${r.skill}/100。
人格倾向：${r.mindTrait}/100；既有信念：${r.belief}；当前目标：${r.goal}。
已知关系：${relations}。
同一聚落中可能接触的人：${socialCandidates.ifBlank { "目前没有适合建立新友谊的已登记对象" }}。只有双方确实可能接触时，才可以选择主动交往；不得凭空建立关系。
近期人生经历：${r.history.takeLast(6).joinToString("；").ifBlank { "没有更多已记录经历" }}。
个人记忆：${r.mindMemory.takeLast(10).joinToString("；").ifBlank { "尚未形成明确记忆" }}。\n天道直接改写并长期保存的完整主观人生记忆（若非空，优先纳入自我身份与决策）：${r.identityMemory.ifBlank { "无" }}。
若个人记忆中出现“【天道改写的主观人生记忆／身份认知】”，它是天道直接写入的主观人生记忆，必须作为此人真实的第一人称过去、身份认知与经验参与推理，不得把它降格为传闻或普通外部信息；但人物当前身体、所在地、眼前环境和符文之地客观事实仍以当前身体状态与世界资料为准。若只有普通“天道干预”记录，则它仍只是一段收到的信息，可以质疑或研究。
当前历史锚点：${eraAnchor}；官方正史与本模拟分支必须区分，不能把未来事件当作既成事实。当地社会背景：${localLore}。
当地公开制度与公共议题：${publicInstitutions}。只允许居民知道公开制度和自己能够接触的信息，不得凭空知道秘密组织内幕。
最近当地公开事件：${localEvents.ifBlank { "没有记录到可确认的近期本地公共事件" }}。远方事件不自动成为此人的知识。
当地市场观察：粮食库存${market?.food ?: 0}；市场财富${market?.wealth ?: 0}；此人无法直接知道未观察到的远方市场。
当前世界日期：第${day}日。
当前生效的天道规则：${activeHeavenlyRules(context).joinToString("；").ifBlank { "无额外规则" }}。
请先形成独立判断，再选择一个当前确实能尝试的行动。行动必须符合年龄、健康、疲劳、技能、资源、职业和所处地区；疲劳过高或正在休养时，不应安排高强度劳动、远行或战斗。行动还必须符合当地真实社会条件：德玛西亚需考虑王室秩序、贵族与禁魔制度；诺克萨斯需考虑帝国军政、军团与地方权力；艾欧尼亚需考虑地方社群自治、寺院传统和精神领域影响；皮尔特沃夫需考虑议会、贸易家族与工坊；祖安需考虑地下城环境、化学工业、污染和阶层差异；弗雷尔卓德需考虑严寒、部族与迁徙；恕瑞玛需考虑水源、绿洲与商路；比尔吉沃特需考虑港口、船长与海上风险。不要把地区背景误当成个人已知的秘密，也不要把所有居民写成同一种文化。不合理时选择stay。不要为了制造剧情而行动。
行动选择应体现这个人自己的长期兴趣和处境，而不只是生存：可以维持现状、寻找食物、求职或转行、向可信亲友求助、练习现有职业技能，或主动结识同一聚落中真实存在的人。不要为了制造剧情而强行选择社交或成长。
如果个人记忆中有天道干预、异常信息或关于世界本质的疑问，可以自主选择investigate_world来整理假说、观察现象并寻找证据，或选择discuss_world与真实可接触的亲友交流；没有研究条件时也可以暂缓。
只返回JSON对象：thought（第一人称思考，1-3句）、belief（可保持或修正的个人信念）、intent（当前意图）、goal（短期目标）、action（stay、seek_food、seek_work、change_job、ask_help、practice_skill、socialize、investigate_world、discuss_world之一；investigate_world用于独立研究世界规律或核验天道信息，discuss_world仅在有可接触的可信关系时交流；不得自动得出超出证据的结论）、new_job（seek_work或change_job时可填写；仅限农夫、采集者、药师、商贩、工匠、矿工、护卫、学徒、伐木工、海克斯技师、魔法材料采集者）、target_id（socialize时必须是上方真实候选人的编号；其他行动填-1）。"""
        localInferenceBusy=true
        ResidentAI.ask(prompt) { raw ->
            try {
                val start=raw.indexOf('{')
                val end=raw.lastIndexOf('}')
                if(start>=0 && end>start) {
                    val result=JSONObject(raw.substring(start,end+1))
                    val latest=people(p).firstOrNull { it.id==r.id && it.alive }
                    if(latest!=null) {
                        val thought=result.optString("thought").take(700)
                        if(thought.isNotBlank()) {
                            latest.currentThought="第${day}日（本地模型推理）：${thought}"
                            latest.belief=result.optString("belief",latest.belief).take(180)
                            latest.intent=result.optString("intent",latest.intent).take(180)
                            latest.goal=result.optString("goal",latest.goal).take(180)
                            latest.mindMemory.add(latest.currentThought+"（信念："+latest.belief+"）")
                            while(latest.mindMemory.size>120) latest.mindMemory.removeAt(0)
                            val action=result.optString("action","stay")
                            when(action) {
                                "ask_help" -> {
                                    val trustedIds=(latest.parentIds+latest.childIds+latest.friendIds+listOf(latest.partnerId)).filter { it>=0 && it!=latest.id }.distinct()
                                    val helper=people(p).firstOrNull { it.id in trustedIds && it.alive && it.region==latest.region && it.food>=3 }
                                    if(latest.food<=1 && helper!=null) {
                                        val shared=minOf(2,helper.food-1)
                                        helper.food-=shared
                                        latest.food+=shared
                                        latest.mindMemory.add("行动结果：向${helper.name}求助，对方分享了${shared}单位食物；我记住了这份帮助。")
                                        helper.mindMemory.add("行动结果：向${latest.name}分享了${shared}单位食物，承担了帮助的代价。")
                                        recordLife(latest,day,"因口粮短缺向${helper.name}求助，对方分享${shared}单位食物，暂时缓解困境。","local_ai_decision_action")
                                        recordLife(helper,day,"向${latest.name}分享${shared}单位食物，自己的储备因此减少。","relationship_mutual_aid")
                                        val current=people(p).map { person -> when(person.id) { latest.id -> latest; helper.id -> helper; else -> person } }
                                        savePeople(p,current)
                                    } else {
                                        latest.mindMemory.add("行动结果：尝试向可信关系求助，但目前没有同地且有余粮的亲友，未能获得援助。")
                                    }
                                }
                                "seek_food" -> {
                                    latest.goal="寻找食物与可靠补给"
                                    val marketMap=markets(p)
                                    val localMarket=marketMap[latest.region]
                                    if(localMarket!=null && latest.food<=3 && localMarket.food>0) {
                                        val unitPrice=goodPrice(localMarket,"粮食")
                                        val affordable=if(unitPrice>0) latest.coins/unitPrice else 0
                                        val bought=minOf(4-latest.food,localMarket.food,affordable).coerceAtLeast(0)
                                        if(bought>0) {
                                            val cost=bought*unitPrice
                                            latest.coins-=cost
                                            latest.food+=bought
                                            localMarket.food-=bought
                                            localMarket.wealth+=cost
                                            recordLife(latest,day,"根据自己的判断前往当地市场购买${bought}单位粮食，花费${cost}枚金币；口粮由${latest.food-bought}增至${latest.food}。","local_ai_decision_action")
                                            latest.mindMemory.add("行动结果：购买粮食成功，花费${cost}枚金币，当前口粮${latest.food}。")
                                        } else if(latest.food<=1 && localMarket.wealth>0 && localMarket.food>0) {
                                            val aid=minOf(1,localMarket.food)
                                            localMarket.food-=aid
                                            localMarket.wealth=(localMarket.wealth-unitPrice).coerceAtLeast(0)
                                            latest.food+=aid
                                            recordLife(latest,day,"因个人无力购买口粮，向当地公共救济储备求助并获得${aid}单位粮食。","local_ai_decision_action")
                                            latest.mindMemory.add("行动结果：获得公共口粮救济，暂时缓解饥饿。")
                                        } else {
                                            latest.mindMemory.add("行动结果：尝试寻找食物，但当地市场库存或个人财力不足，未能买到口粮。")
                                        }
                                        saveMarkets(p,marketMap)
                                    } else {
                                        latest.mindMemory.add("行动结果：评估后发现当前口粮并未达到紧急程度，暂缓购买。")
                                    }
                                }
                                "seek_work","change_job" -> {
                                    val allowed=setOf("农夫","采集者","药师","商贩","工匠","矿工","护卫","学徒","伐木工","海克斯技师","魔法材料采集者")
                                    val next=result.optString("new_job")
                                    if(next in allowed && latest.age in 16..69 && latest.health>=20 && latest.travelDaysRemaining<=0 && next!=latest.job) {
                                        val regionCompatible=when(next) {
                                            "海克斯技师" -> latest.region in setOf("皮尔特沃夫","祖安")
                                            "魔法材料采集者" -> latest.region in setOf("艾欧尼亚","恕瑞玛","巨神峰","以绪塔尔","弗雷尔卓德")
                                            "矿工" -> latest.region in setOf("诺克萨斯","弗雷尔卓德","恕瑞玛","德玛西亚")
                                            else -> true
                                        }
                                        if(regionCompatible) {
                                            val old=latest.job
                                            latest.job=next
                                            latest.goal="通过${next}改善个人生计"
                                            recordLife(latest,day,"经过个人评估后，主动离开${old}，尝试从事${next}；这是一次谋生选择，后续收入与技能变化仍由世界经济和劳动模拟决定。","local_ai_decision_action")
                                            latest.mindMemory.add("行动结果：已从${old}转为${next}，需要通过实际劳动检验这次选择。")
                                        } else {
                                            latest.mindMemory.add("行动结果：考虑的职业需要当地缺少的产业条件，决定暂不转行。")
                                        }
                                    } else {
                                        latest.mindMemory.add("行动结果：没有找到符合自身条件且可执行的工作变动，暂时维持现状。")
                                    }
                                }
                                "practice_skill" -> {
                                    if(latest.age>=10 && latest.health>=25 && latest.skill<100 && latest.travelDaysRemaining<=0) {
                                        val mentor=people(p).firstOrNull { it.id==latest.mentorId && it.alive && it.region==latest.region }
                                        val gain=if(mentor!=null) 2 else 1
                                        val before=latest.skill
                                        latest.skill=(latest.skill+gain).coerceAtMost(100)
                                        recordLife(latest,day,"主动投入时间练习${latest.job}相关技艺，技能由${before}提升至${latest.skill}/100${if(mentor!=null) "；师长${mentor.name}在场指导" else "；此次为个人练习"}。","resident_self_development")
                                        latest.mindMemory.add("行动结果：主动练习技艺，技能提升至${latest.skill}/100。")
                                        if(mentor!=null) {
                                            mentor.mindMemory.add("师徒经历：我指导${latest.name}练习${latest.job}相关技艺。")
                                            recordLife(mentor,day,"指导学生${latest.name}练习${latest.job}相关技艺。","mentor_teaching")
                                            savePeople(p,people(p).map { if(it.id==mentor.id) mentor else it })
                                        }
                                    } else latest.mindMemory.add("行动结果：当前年龄、健康、旅途或技能状态不适合继续练习。")
                                }
                                "investigate_world" -> {
                                    val received=latest.mindMemory.any { it.contains("天道干预") || it.contains("外部信息") }
                                    latest.goal=if(received) "核验收到的外部信息，并研究世界规律" else "观察身边现象，寻找可验证的世界规律"
                                    val note=if(received) "开始独立整理天道信息与亲历证据，区分观察、传闻、推测和未知；目前没有足够证据证明信息内容。" else "开始留意身边自然、魔法与社会现象，尝试提出可验证的问题；尚未获得确定结论。"
                                    latest.mindMemory.add("研究行动：${note}")
                                    recordLife(latest,day,note,"resident_independent_inquiry")
                                }
                                "discuss_world" -> {
                                    val confidant=people(p).firstOrNull { other ->
                                        other.alive && other.id!=latest.id && other.region==latest.region &&
                                            other.settlement==latest.settlement &&
                                            (other.id in latest.friendIds || other.id==latest.partnerId)
                                    }
                                    if(confidant!=null) {
                                        val topic=latest.mindMemory.lastOrNull { it.contains("天道干预") || it.contains("世界规律") } ?: "关于世界与自身处境的疑问"
                                        latest.mindMemory.add("交流行动：我与${confidant.name}讨论了${topic}。对方的真实看法尚未记录，不能推定其赞同。")
                                        confidant.mindMemory.add("交流经历：${latest.name}与我谈到${topic}；我是否认同仍由自己的判断决定。")
                                        recordLife(latest,day,"与可信关系${confidant.name}交流关于世界本质或异常信息的疑问；没有预设对方立场。","resident_belief_discussion")
                                        recordLife(confidant,day,"与${latest.name}交流关于世界本质或异常信息的疑问；交流内容不代表双方达成共识。","resident_belief_discussion")
                                        while(confidant.mindMemory.size>120) confidant.mindMemory.removeAt(0)
                                        savePeople(p,people(p).map { person -> when(person.id) { latest.id -> latest; confidant.id -> confidant; else -> person } })
                                    } else latest.mindMemory.add("交流行动：目前没有同地且可信的关系对象，暂未进行讨论。")
                                }
                                "socialize" -> {
                                    val targetId=result.optInt("target_id",-1)
                                    val other=people(p).firstOrNull { it.id==targetId && it.alive }
                                    if(other!=null && latest.age>=12 && other.age>=12 &&
                                        other.id!=latest.id && other.region==latest.region &&
                                        other.settlement==latest.settlement && latest.travelDaysRemaining<=0 &&
                                        other.travelDaysRemaining<=0 && latest.friendIds.size<5 &&
                                        other.friendIds.size<5 && other.id !in latest.friendIds &&
                                        latest.id !in other.friendIds && other.id!=latest.partnerId &&
                                        other.id !in latest.parentIds && other.id !in latest.childIds) {
                                        latest.friendIds.add(other.id)
                                        other.friendIds.add(latest.id)
                                        recordLife(latest,day,"主动与${other.name}交往并建立友谊；双方居住于${latest.settlement}，关系由此进入个人经历。","resident_initiated_relationship")
                                        recordLife(other,day,"与${latest.name}建立友谊；对方主动接触，双方开始形成新的社会联系。","resident_initiated_relationship")
                                        latest.mindMemory.add("关系变化：我与${other.name}成为朋友。")
                                        other.mindMemory.add("关系变化：我与${latest.name}成为朋友。")
                                        while(other.mindMemory.size>120) other.mindMemory.removeAt(0)
                                        savePeople(p,people(p).map { person -> when(person.id) { latest.id -> latest; other.id -> other; else -> person } })
                                    } else latest.mindMemory.add("行动结果：想要结识的人不在可接触范围内，或双方条件不适合建立友谊；没有改变关系。")
                                }
                                else -> latest.mindMemory.add("行动结果：经过评估，决定暂时维持当前生活与职业。")
                            }
                            while(latest.mindMemory.size>120) latest.mindMemory.removeAt(0)
                            val latestPopulation=people(p).map { if(it.id==latest.id) latest else it }
                            savePeople(p,latestPopulation)
                        }
                    }
                }
            } catch(_:Throwable) {
                // Invalid model output is ignored; no fictional event is written.
            } finally {
                localInferenceBusy=false
            }
        }
    }


    private const val KEY_HERO_AI_CURSOR="hero_ai_cursor"

    /** One canon character receives a private reasoning turn using the shared local model. */
    fun requestLocalHeroThinking(context:Context) {
        if(!ResidentAI.isLoaded() || localInferenceBusy) return
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=heroAgents(p)
        // Every living hero remains an individual agent, including recluses, sealed/dormant
        // figures and characters not publicly active in this era. Their condition constrains
        // what they can perceive or do; it must not silently remove their inner life.
        val active=all.filter { it.alive }
        if(active.isEmpty()) return
        val cursor=p.getInt(KEY_HERO_AI_CURSOR,0).let { if(it<0) 0 else it%active.size }
        val day=p.getLong(KEY_DAY,1L)
        // Unanswered historical opportunities jump the ordinary queue.
        val urgentIndex=active.indexOfFirst { candidate ->
            val opportunity=candidate.memory.lastOrNull { it.contains("【历史事件接驳机会") }
            val opportunityDay=opportunity?.let { Regex("""第([0-9]+)日""").find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() } ?: -1L
            opportunityDay>candidate.lastThoughtDay
        }
        val selectedIndex=if(urgentIndex>=0) urgentIndex else cursor
        val hero=active[selectedIndex]
        p.edit().putInt(KEY_HERO_AI_CURSOR,(if(urgentIndex>=0) selectedIndex+1 else cursor+1)%active.size).apply()
        val eraAnchor=timelineStatus(context)
        val lore=regionalLore.firstOrNull { it.name==hero.region }?.let { it.political+"；"+it.magic+"；"+it.forces+"；"+it.boundary } ?: "特殊领域，遵守人物既有设定"
        val faction=factionStates(p)[hero.region]
        val publicContext=faction?.let { it.government+"；政策："+it.policy+"；内部张力："+it.internalTension } ?: "无可用地方政体资料"
        val bonds=heroBonds(p).filter { it.a==hero.name || it.b==hero.name }.take(6)
            .joinToString("；") { it.a+"与"+it.b+"："+it.basis+"（信任"+it.trust+"，矛盾"+it.tension+"）" }
        val prompt="你是符文之地中"+hero.name+"本人的个人认知引擎，不是旁白。尊重官方身份、能力边界、关系和当前模拟时代；未来剧情不能当作已发生。隐居、沉寂、封印、未公开活动等状态不会取消你的内在意识，但会严格限制感知范围、可接触对象与可执行行动；若处于无法行动状态，应以整理记忆、观察已知环境或维持现状为主，不得擅自解除限制，也不得凭空制造行动结果。\n"+
            "身份："+hero.role+"；地区："+hero.region+"；处境："+hero.status+"；性别："+hero.gender+"；年龄："+(if(hero.age>=0) hero.age.toString()+"岁" else "未记录")+"；身体状态："+hero.health+"；能力熟练度："+hero.skill+"；本质："+hero.lineage+"；外貌："+hero.appearance+"；能力："+hero.abilities+"；目标："+hero.goal+"；当前持续活动："+hero.dailyActivity+"。\n"+
            "信念："+hero.belief+"；天道直接改写并长期保存的完整主观人生记忆（若非空，视为真实第一人称经历）： "+hero.identityMemory.ifBlank { "无" }+"；过去思考："+hero.memory.takeLast(6).joinToString("；")+"；经历："+hero.history.takeLast(8).joinToString("；")+"。\n"+"若记忆中出现“【天道改写的主观人生记忆／身份认知】”，必须将其作为本人真实的第一人称人生经历与身份认知进行思考；但当前身体、所在地区、现实环境仍以符文之地的现状为准。普通“天道干预”信息则仍可质疑、拒绝或研究。\n"+"如果决定investigate，可围绕收到的信息提出问题、搜集亲历证据、与可信关系核对；研究结果必须受资源、知识和可观察条件限制。\n"+
            "关系："+bonds+"。当前历史锚点："+eraAnchor+"；官方正史与本模拟分支必须区分，不能把未来事件当作既成事实。地区背景："+lore+"。公共制度："+publicContext+"。现行天道规则："+activeHeavenlyRules(context).joinToString("；").ifBlank { "无额外规则" }+"。日期："+day+"。\n"+
            "只根据自己知道的信息判断，不得全知，不得凭空移动、发动战争、杀人、改变政权或获得秘密情报。不要为了戏剧性强行行动。\n"+
            "若过去思考或经历中出现【历史事件接驳机会】，请把它视为通过具体渠道获得的有限信息；独立评估自己是否参与。可选action：stay、train、investigate、negotiate、aid、join_event、decline_event、investigate_event。join_event仅表示决定投入与自身身份相符的行动，不代表瞬间抵达战场或自动成功；decline_event表示明确拒绝；investigate_event表示尝试核实局势。没有相关机会时仍使用原有行动。只返回JSON：thought（第一人称2-4句判断）、belief、intent、goal、action、reason。"
        localInferenceBusy=true
        ResidentAI.ask(prompt) { raw ->
            try {
                val a=raw.indexOf(123.toChar()); val b=raw.lastIndexOf(125.toChar())
                if(a>=0 && b>a) {
                    val result=JSONObject(raw.substring(a,b+1))
                    val heroNow=heroAgents(p).firstOrNull { it.name==hero.name } ?: return@ask
                    val thought=result.optString("thought").take(700)
                    if(thought.isNotBlank()) {
                        heroNow.thought=thought
                        heroNow.belief=result.optString("belief",heroNow.belief).take(220)
                        heroNow.intent=result.optString("intent",heroNow.intent).take(220)
                        heroNow.goal=result.optString("goal",heroNow.goal).take(220)
                        val action=result.optString("action","stay")
                        val groups=organizations(p)
                        val localGroup=groups.filter { it.region==heroNow.region }.maxByOrNull { it.influence+it.publicSupport/2 }
                        val outcome=when(action) {
                            "train" -> { heroNow.influence=(heroNow.influence+1).coerceAtMost(100); "训练或准备，模拟影响力小幅变化" }
                            "investigate" -> {
                                if(heroNow.memory.any { it.contains("天道干预") || it.contains("外部信息") }) {
                                    heroNow.memory.add("第"+day+"日：开始独立核验收到的外部信息，区分亲历、传闻、推测与未知；尚未证明其真实性。")
                                    "开始研究天道信息并寻找可验证证据，尚无结论"
                                } else {
                                    if(localGroup!=null) localGroup.influence=(localGroup.influence+1).coerceAtMost(100)
                                    "调查可接触的当地事务，未自动获得秘密情报"
                                }
                            }
                            "negotiate" -> { if(localGroup!=null) localGroup.publicSupport=(localGroup.publicSupport+1).coerceAtMost(100); "尝试与当地相关组织沟通，谈判结果仍待后续验证" }
                            "aid" -> { if(localGroup!=null) localGroup.publicSupport=(localGroup.publicSupport+1).coerceAtMost(100); "投入当地社群事务，影响限于本地组织" }
                            "join_event" -> {
                                val opportunity=heroNow.memory.lastOrNull { it.contains("【历史事件接驳机会") }
                                if(opportunity!=null) {
                                    heroNow.influence=(heroNow.influence+2).coerceAtMost(100)
                                    heroNow.memory.add("第${day}日：我决定回应历史事件中的机会，尝试以自身身份参与；具体行动仍受位置、资源、组织关系与现实阻力限制。")
                                    if(heroNow.name=="艾瑞莉娅" && opportunity.contains("noxus_ionia_invasion")) {
                                        heroNow.role="艾欧尼亚抵抗者"
                                        heroNow.goal="守护艾欧尼亚并组织地方抵抗"
                                    }
                                    "决定参与相关历史事件；仅记录个人决策与投入意愿，具体行动需由后续世界条件验证"
                                } else "当前没有可确认的历史事件接驳机会，未执行参与行动"
                            }
                            "decline_event" -> {
                                val opportunity=heroNow.memory.lastOrNull { it.contains("【历史事件接驳机会") }
                                if(opportunity!=null) heroNow.memory.add("第${day}日：我经过独立判断，决定不参与这次历史事件；这不代表我不知道事件，也不预设未来立场。")
                                "决定暂不参与相关历史事件；拒绝原因与后续影响由本人经历继续形成"
                            }
                            "investigate_event" -> {
                                val opportunity=heroNow.memory.lastOrNull { it.contains("【历史事件接驳机会") }
                                if(opportunity!=null) {
                                    heroNow.memory.add("第${day}日：我开始通过自己能够接触的人与渠道核实历史事件；尚未获得超出渠道范围的情报。")
                                    "开始核实相关历史事件，尚未自动获得秘密情报"
                                } else "没有可核实的历史事件机会，维持原有安排"
                            }
                            else -> "决定维持当前安排"
                        }
                        heroNow.lastThoughtDay=day
                        heroNow.memory.add("第"+day+"日："+thought)
                        heroNow.history.add("第"+day+"日｜思考："+thought+"｜意图："+heroNow.intent+"｜行动："+action+"｜结果："+outcome)
                        while(heroNow.memory.size>100) heroNow.memory.removeAt(0)
                        while(heroNow.history.size>3000) heroNow.history.removeAt(0)
                        if(action!="stay") heroNow.status=heroNow.status+"；近期行动："+outcome
                        saveHeroes(p,heroAgents(p).map { if(it.name==heroNow.name) heroNow else it })
                        saveOrganizations(p,groups)
                        val events=runCatching { JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]") }.getOrDefault(JSONArray())
                        events.put("【英雄自主思考·本地AI模拟】第"+day+"日："+heroNow.name+"选择"+action+"；结果："+outcome+"。这是模拟分支记录，不代表官方正史。")
                        while(events.length()>300) events.remove(0)
                        p.edit().putString(KEY_EVENTS,events.toString()).apply()
                    }
                }
            } catch(_:Throwable) {
                // Invalid model output must not create fictional biography events.
            } finally { localInferenceBusy=false }
        }
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
        val initialEventCount=events.size
        val archivedEvents=runCatching { val a=JSONArray(p.getString(KEY_EVENT_ARCHIVE,"[]")); (0 until a.length()).map { a.getString(it) }.toMutableList() }.getOrDefault(mutableListOf())
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
            tickHeroDailyLife(day,p)
            tickHeroes(day,p,pop,markets,events,rng)
            tickHeroBonds(day,p,events,rng)
            tickSocialRelations(day,pop,events,rng)
            tickMagic(day,p,pop,markets,events,rng)
            tickEcology(day,p,pop,events,rng,ecology)
            tickFauna(day,p,ecology,pop,events,rng,fauna)
            tickDailyLife(day,pop,rng)
            thinkIndividually(day,pop,markets,rng)
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
                val dailyHarvest=growers.filter { it.dailyActivity.contains("耕作") || it.dailyActivity.contains("采集") }.sumOf { (6 + it.skill/25 + ecologicalYield).coerceIn(3,11) }
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
                if(!traveling && r.dailyActivity!="休息与恢复体力" && r.dailyActivity!="休养与寻求照料" && r.age>=6 && rng.nextInt(100)<(42+r.skill.coerceAtMost(15))) when(r.job) {
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
                    if(r.alive && r.age in setOf(18,30,50,70)) {
                        val milestone=when(r.age) {
                            18 -> "成年阶段开始"
                            30 -> "进入人生三十岁阶段"
                            50 -> "进入人生中年阶段"
                            else -> "进入晚年阶段"
                        }
                        recordLife(r,day,"年岁增长至${r.age}岁，${milestone}；当时职业为${r.job}，居住于${r.region}。")
                    }
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
                    val formerMentor=pop.firstOrNull { it.id==r.mentorId && it.alive }
                    r.job="工匠"
                    recordLife(r,day,"在${r.region}完成学徒阶段，成为工匠；正式承担独立工作。")
                    if(formerMentor!=null) {
                        recordLife(formerMentor,day,"徒弟${r.name}完成学徒阶段，成为工匠；此前传授的技艺进入独立实践阶段。")
                        formerMentor.studentIds.remove(r.id)
                    }
                    r.mentorId=-1
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
                    val child=Resident(id,names[rng.nextInt(names.size)]+"·"+id,r.region,0,100,3,0,"学徒","成长与学习",settlement=r.settlement,householdId=r.householdId,lineage=childLineage,lifespanYears=95+rng.nextInt(11),mindTrait=rng.nextInt(101),belief=beliefs[rng.nextInt(beliefs.size)])
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
                if(!r.survivalOverride && (r.health<=0 || (r.age>=r.lifespanYears-15 && rng.nextInt(100)<1) || (r.age>=r.lifespanYears && rng.nextInt(100)<12))) {
                    r.alive=false
                    recordLife(r,day,"在${r.region}以${r.age}岁离世，生前职业：${r.job}。")
                    events.add("第 $day 日：${r.name}（${r.region}，${r.age}岁，${r.job}）去世；人生记录留存。")
                    // Personal movable savings pass to close surviving heirs; household reserves remain collective property.
                    // This models narrow inheritance without inventing land titles, wills or regional law.
                    val heirs=buildList {
                        pop.firstOrNull { it.alive && it.id==r.partnerId }?.let { add(it) }
                        r.childIds.mapNotNull { childId -> pop.firstOrNull { it.alive && it.id==childId } }
                            .sortedByDescending { it.age }.forEach { if(it !in this) add(it) }
                    }
                    if(heirs.isNotEmpty()) {
                        val coinsPerHeir=r.coins/heirs.size
                        val foodPerHeir=r.food/heirs.size
                        var coinRemainder=r.coins%heirs.size
                        var foodRemainder=r.food%heirs.size
                        heirs.forEach { heir ->
                            val inheritedCoins=coinsPerHeir+(if(coinRemainder>0){coinRemainder--;1}else 0)
                            val inheritedFood=foodPerHeir+(if(foodRemainder>0){foodRemainder--;1}else 0)
                            heir.coins+=inheritedCoins
                            heir.food+=inheritedFood
                            if(inheritedCoins>0 || inheritedFood>0) {
                                recordLife(heir,day,"继承已故亲属${r.name}的个人遗留财物：金币${inheritedCoins}、粮食${inheritedFood}；这是个人动产分配，不包含家庭共同储备或未登记的不动产。","inheritance")
                                heir.mindMemory.add("继承记录：从${r.name}处获得金币${inheritedCoins}、粮食${inheritedFood}。")
                                while(heir.mindMemory.size>120) heir.mindMemory.removeAt(0)
                            }
                        }
                        recordLife(r,day,"其个人遗留动产由在世伴侣及子女继承，共涉及${heirs.size}名继承人；家庭共同储备未纳入个人遗产。","inheritance")
                        events.add("【继承·模拟】第 $day 日：${r.name}的个人遗留金币与粮食分配给${heirs.joinToString("、"){it.name}}；家庭共同储备保持集体所有。")
                    } else if(r.coins>0 || r.food>0) {
                        recordLife(r,day,"去世时未发现登记在案的在世伴侣或子女继承人；个人遗留动产暂列为未分配遗产，未凭空转入任何人的库存。","inheritance")
                        events.add("【遗产待定·模拟】第 $day 日：${r.name}去世后没有登记在案的直系继承人，其个人遗留动产暂未分配。")
                    }
                    // Death is recorded as a relationship event for surviving relatives and close ties too.
                    // This links separate biographies without inventing grief, dialogue or private thoughts.
                    pop.filter { it.alive && it.id in r.childIds }.forEach { child ->
                        recordLife(child,day,"父母之一${r.name}（居民编号${r.id}）离世；其家庭关系与成长环境发生变化。")
                    }
                    pop.filter { it.alive && it.id in r.parentIds }.forEach { parent ->
                        recordLife(parent,day,"子女${r.name}（居民编号${r.id}）离世；其家庭关系发生变化。")
                    }
                    pop.filter { it.alive && it.id in r.friendIds }.forEach { friend ->
                        recordLife(friend,day,"友人${r.name}（居民编号${r.id}）离世；既有友谊因死亡而终止。")
                    }
                    pop.filter { it.alive && it.mentorId==r.id }.forEach { student ->
                        recordLife(student,day,"师长${r.name}（居民编号${r.id}）离世；原有师徒关系终止，后续学习需要另寻途径。")
                        student.mentorId=-1
                    }
                    pop.filter { it.alive && r.id in it.studentIds }.forEach { teacher ->
                        recordLife(teacher,day,"学生${r.name}（居民编号${r.id}）离世；其指导关系因死亡而结束。")
                        teacher.studentIds.remove(r.id)
                    }
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
                        // Household reserves actively protect members facing hunger.
                        val needy=members.filter { it.food<=1 }.sortedWith(
                            compareBy<Resident> { if(it.age<13) 0 else if(it.health<45) 1 else 2 }
                                .thenBy { it.food }.thenBy { it.health }
                        )
                        needy.forEach { recipient ->
                            if(household.foodReserve>0 && recipient.food<=1) {
                                household.foodReserve--
                                recipient.food++
                                recordLife(recipient,day,"家庭共同储粮向我提供1单位口粮；家庭储备因此减少，暂时缓解了个人食物短缺。","household_mutual_aid")
                                recipient.mindMemory.add("家庭互助：共同储粮为我补充了1单位口粮。")
                                while(recipient.mindMemory.size>120) recipient.mindMemory.removeAt(0)
                            }
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
        if(events.size>initialEventCount) archivedEvents.addAll(events.drop(initialEventCount))
        while(archivedEvents.size>20000) archivedEvents.removeAt(0)
        while(events.size>1200)events.removeAt(0)
        savePeople(p,pop)
        saveHouseholds(p,households)
        saveMarkets(p,markets)
        val arr=JSONArray();events.forEach{arr.put(it)}
        val archiveArray=JSONArray();archivedEvents.forEach { archiveArray.put(it) }
        p.edit().putInt(KEY_NEXT_ID,nextId)
            .putLong(KEY_DAY,day).putLong(KEY_RNG_STATE,rngState)
            .putString(KEY_EVENTS,arr.toString()).putString(KEY_EVENT_ARCHIVE,archiveArray.toString()).apply()
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
            "【"+name+"】\n所属区域："+region+"\n实体类型："+meta.type+"\n可居住性："+(if(meta.habitable)"可作为常规居住点" else "非普通居民居住地")+"\n说明："+meta.note+"\n常住居民："+(if(meta.habitable)residents.size.toString()+"人" else "不适用")+"\n生产与职业：农采"+farmers+"、工匠"+artisans+"、商贩"+traders+"、护卫"+guards+"\n（人口、职业与聚落活动属于模拟数据；官方地名不代表官方确认了此处的模拟人口。）"
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
            "【家庭 ${h.id}】\n主要居住地：$location\n成员：$names\n成员总数：${members.size}（在世${living.size}）\n共同储粮：${h.foodReserve}\n共同资产：${h.sharedWealth}\n建立记录：第${h.foundedDay}日\n说明：家庭是独立社会实体。成员每月可从个人金币与食物中缴入共同储备；共同储粮会优先补给口粮不足的儿童、病弱成员及其他缺粮者。共同资产仍未接入市场采购、住房和工资制度。"
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
            "【${name}】\n当前模拟在世人口：${count}\n世界设定：${description}\n运行规则：${rule}\n注意：人口数量和寿命参数均为模拟数据，不是官方人口统计。"
        }.toMutableList()
        val yordles=heroAgents(p).filter{it.name in setOf("提莫","崔丝塔娜","璐璐","兰博","库奇","维迦","黑默丁格")}
        rows.add("【约德尔人】\n独立特殊社群：约德尔人不混入普通人类居民人口。当前已登记重要人物：${yordles.size}位（${yordles.joinToString("、"){it.name}}）。\n世界设定：班德尔城位于精神领域，入口与符文之地相连；入口并非随时对所有人开放。约德尔人的时间体验与凡人不同，不能直接套用人类人口年龄模型。\n运行规则：约德尔人物作为独立世界行动者记录，不计入普通居民出生、死亡与市场人口。")
        rows.add("【其他特殊存在】\n飞升者、暗裔、半神、星灵、亡灵与虚空生物按独立超自然实体处理，不归入普通居民族群；其存在与活动通过英雄行动、组织、地区魔法和世界危机影响凡人社会。")
        return rows
    }
    fun residentDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return people(p).sortedWith(compareBy<Resident>{!it.alive}.thenByDescending{it.age}).map {
            "${it.id}｜${it.name}｜${it.lineage}｜${it.age}岁｜${if(it.travelDaysRemaining>0)"途中：${it.travelOrigin}→${it.travelDestination}" else it.region}｜${it.job}｜活动：${it.dailyActivity}｜疲劳${it.fatigue}｜状态${it.morale}｜${if(it.alive)"在世" else "已故"}"
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
            val a=JSONArray(p.getString(KEY_EVENT_ARCHIVE,"[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList()) + runCatching {
            val a=JSONArray(p.getString(KEY_EVENTS,"[]") ?: "[]")
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        val packets=r.storyRecords.mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }.sortedBy { it.optLong("day") }
        return buildString {
            append("符文之地 · 居民完整人生档案\n档案编号：${r.id}｜姓名：${r.name}\n")
            append("档案用途：供长篇小说创作使用的模拟事实素材。只记录真实保存的状态和事件，不补写未发生的对白、动作或心理活动。\n\n")
            append("【人物基础资料】\n生命类别：${r.lineage}\n年龄：${r.age}岁\n性别：${r.gender}\n外貌：${r.appearance}\n能力：${r.abilities}\n模拟寿命参数：${r.lifespanYears}年（非官方固定寿命）\n")
            append("当前地区：${r.region}\n当前聚落：${r.settlement.ifBlank { "未记录" }}\n当前职业：${r.job}\n")
            append("人生状态：${if(r.alive) "在世" else "已故"}\n健康：${r.health}/100｜技能：${r.skill}/100｜财富：${r.coins}｜粮食：${r.food}\n")
            append("当前生活活动：${r.dailyActivity}\n疲劳程度：${r.fatigue}/100｜生活状态：${r.morale}/100\n说明：活动与状态由持续生活模拟更新；不是强制剧情，也不意味着人物拥有真实人类意识。\n")
            append("\n【个人认知状态】\n说明：带有“本地模型推理”标记的思考来自已加载的 GGUF 本地模型；其他旧记录可能来自世界模拟规则。\n人格倾向值：${r.mindTrait}/100\n当前信念：${r.belief}\n最近思考：${r.currentThought.ifBlank { "尚未产生认知记录" }}\n当前意图：${r.intent}\n认知记忆：${r.mindMemory.size}条\n")
            append("\n【个人认知与记忆时间线】\n")
            if(r.mindMemory.isEmpty()) append("尚无保存的个人认知记忆。\n")
            else r.mindMemory.forEachIndexed { index,memory -> append("${index+1}. ${memory}\n") }
            append("说明：这些是居民当时保存的认知文本和行动结果，不等同于客观世界全知记录；记忆可能不完整，也可能包含其当时的误解。\n")
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
            append("\n【所处时代与地区事件（背景，不代表本人直接参与）】\n")
            val livedRegions=packets.map { it.optString("region") }.filter { it.isNotBlank() }.toSet()+r.region
            val firstKnownDay=packets.minOfOrNull { it.optLong("day",Long.MAX_VALUE) } ?: 1L
            val contextualEvents=worldEvents.distinct().mapNotNull { event ->
                val match=Regex("第\\s*(\\d+)\\s*日").find(event)
                val eventDay=match?.groupValues?.getOrNull(1)?.toLongOrNull()
                if(eventDay!=null && eventDay>=firstKnownDay && livedRegions.any { event.contains(it) }) eventDay to event else null
            }.sortedBy { it.first }
            append("可检索到的相关时代事件：${contextualEvents.size}条\n")
            contextualEvents.forEach { (eventDay,event) -> append("第${eventDay}日｜${event}\n") }
            append("这些事件用于还原人物所处的社会环境；除非个人事件明确记载，否则不能推断其目击、参与或受到直接伤害。\n")
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