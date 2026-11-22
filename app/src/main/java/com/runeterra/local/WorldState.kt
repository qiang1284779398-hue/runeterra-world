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
     var jobBeforeTravel: String = ""
) {
    fun json() = JSONObject().put("id",id).put("name",name).put("region",region).put("age",age)
        .put("health",health).put("food",food).put("coins",coins).put("job",job).put("goal",goal)
        .put("skill",skill).put("partnerId",partnerId).put("familyCount",familyCount).put("alive",alive)
        .put("history",JSONArray(history)).put("parentIds",JSONArray(parentIds)).put("childIds",JSONArray(childIds))
        .put("friendIds",JSONArray(friendIds)).put("mentorId",mentorId).put("studentIds",JSONArray(studentIds)).put("settlement",settlement).put("travelOrigin",travelOrigin).put("travelDestination",travelDestination).put("travelDaysRemaining",travelDaysRemaining).put("travelPurpose",travelPurpose).put("jobBeforeTravel",jobBeforeTravel)
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
            j.optString("settlement",""),j.optString("travelOrigin",""),j.optString("travelDestination",""),j.optInt("travelDaysRemaining",0),j.optString("travelPurpose",""),j.optString("jobBeforeTravel",""))
    }
}

object WorldEngine {
    private const val PREFS="runeterra_world"
    private fun recordLife(r:Resident,day:Long,detail:String){ r.history.add("第${day}日：${detail}") }
    private const val KEY_DAY="day"
    private const val KEY_EVENTS="events"
    private const val KEY_RUNNING="running"
    private const val KEY_PEOPLE="people"
    private const val KEY_NEXT_ID="next_id"
    private const val KEY_MARKETS="markets"
    private const val KEY_MAGIC="magic_regions"
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
            val change=rng.nextInt(11)-5
            val newLevel=(level+change).coerceIn(0,100)
            levels[region]=newLevel
            if(newLevel>=80 && rng.nextInt(100)<12){
                val residents=pop.filter{it.alive&&it.region==region}.shuffled(rng).take(1+rng.nextInt(3))
                residents.forEach{it.health=(it.health+rng.nextInt(8)-3).coerceIn(0,100)}
                markets[region]?.let{it.food=(it.food-rng.nextInt(20)).coerceAtLeast(0)}
                events.add("【魔法与符文·模拟】第 $day 日：${region}出现强烈魔法波动，影响${residents.size}名居民，区域魔法强度${levels[region]}。")
            }else if(newLevel<=15 && rng.nextInt(100)<10){
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
                        "盖伦"->{target.health=(target.health+3).coerceAtMost(100);h.status="巡逻";recordLife(target,day,"在${h.name}巡逻时获得保护。")}
                        "拉克丝"->{target.health=(target.health+2).coerceAtMost(100);h.status="秘密援助";recordLife(target,day,"在${h.name}的秘密援助下得到帮助。")}
                        "艾瑞莉娅"->{target.health=(target.health+2).coerceAtMost(100);h.status="抵抗行动";recordLife(target,day,"受到${h.name}领导的抵抗行动影响。")}
                        "厄运小姐"->{target.coins+=2;h.status="港口行动";recordLife(target,day,"参与${h.name}主导的港口行动，获得报酬。")}
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
        "德玛西亚" to FactionLore("王国君主制；王位继承处于政治动荡","王室与各大贵族家族","法师社群、改革派贵族与反禁魔者","王位继承、贵族认可与魔法管制共同影响王国秩序","王子嘉文四世（继承尚未获贵族一致认可）"),
        "诺克萨斯" to FactionLore("扩张型帝国；崔法利议会主导","崔法利议会与诺克萨斯军方","地方总督、旧贵族及反扩张势力","以力量与功绩晋升，持续争夺边境与影响力","崔法利议会"),
        "艾欧尼亚" to FactionLore("多地灵性传统并存；无统一中央政权","地方长老、寺院与抵抗组织","亲诺克萨斯合作者、地方割据者与路线分歧派","守护土地与精神传统，同时处理战争创伤和自治分歧","地方议会与长老"),
        "皮尔特沃夫" to FactionLore("城邦议会与精英家族影响","议会、贸易家族与工坊巨头","底层工人、改革派与反垄断团体","推动海克斯科技、贸易和城市秩序，承受阶层压力","皮尔特沃夫议会"),
        "祖安" to FactionLore("地下城邦；工坊、帮派与企业势力并立","化学男爵及大型工坊势力","工人社区、独立发明家与反企业组织","争夺资源、空气与生存空间，科技繁荣伴随污染和剥削","化学男爵议会"),
        "弗雷尔卓德" to FactionLore("部族联盟与氏族政治","各部族首领及联盟","敌对部族、流亡者与争夺资源的氏族","在严酷环境中争夺生存资源、盟约与部族领导权","部族首领议会"),
        "恕瑞玛" to FactionLore("帝国复兴与绿洲城邦并存","太阳圆盘信仰者、复兴势力与地方统治者","独立城邦、反帝国势力与掠夺集团","争夺古代遗产、水源、商路与帝国合法性","复兴势力代表"),
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
        saveFactions(p,states)
    }
    private const val KEY_ORGANIZATIONS="organizations_state"
    private data class Organization(
        val id:String,val name:String,val region:String,val kind:String,val agenda:String,
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
        Organization("noxus_trifarix","崔法利议会","诺克萨斯","最高统治机构","以力量、远见与狡诈的权力原则统治帝国",mutableListOf("noxus_black_rose"),mutableListOf("noxus_legions"),"三人议会",90,95,65),
        Organization("noxus_legions","诺克萨斯军团","诺克萨斯","军事组织","扩张帝国疆域并维持军事控制",mutableListOf(),mutableListOf("noxus_trifarix"),"军团统帅",82,90,70),
        Organization("noxus_black_rose","黑色玫瑰","诺克萨斯","秘密结社","通过秘密网络与古老力量影响权力格局",mutableListOf("noxus_trifarix"),mutableListOf(),"秘密议会",76,82,20),
        Organization("ionia_kinkou","均衡教派","艾欧尼亚","宗教与武术组织","维护精神领域与物质领域之间的均衡",mutableListOf("ionia_shadow_order"),mutableListOf(),"教派长老",62,45,60),
        Organization("ionia_shadow_order","影流","艾欧尼亚","武装组织","以激进手段守护艾欧尼亚并追求力量",mutableListOf("ionia_kinkou"),mutableListOf(),"影流领袖",58,58,35),
        Organization("freljord_avarosan","阿瓦罗萨部族联盟","弗雷尔卓德","部族联盟","团结部族并争取和平与生存空间",mutableListOf("freljord_winter_claw"),mutableListOf(),"部族联盟领袖",64,50,65),
        Organization("freljord_winter_claw","凛冬之爪","弗雷尔卓德","部族联盟","以强硬的生存法则与征战维系部族",mutableListOf("freljord_avarosan"),mutableListOf(),"战母",61,55,54),
        Organization("zaun_chem_barons","祖安化学男爵势力","祖安","商业与帮派联盟","控制工业、炼金资源与地下贸易网络",mutableListOf("zaun_firelights"),mutableListOf(),"化学男爵议会",75,88,32),
        Organization("zaun_firelights","萤火","祖安","地下抵抗组织","保护社区并反抗压迫与环境破坏",mutableListOf("zaun_chem_barons"),mutableListOf(),"社区行动者",42,28,68),
        Organization("piltover_council","皮尔特沃夫议会与贸易家族","皮尔特沃夫","政治与商业联盟","维护城邦贸易、海克斯科技与精英秩序",mutableListOf(),mutableListOf(),"议会代表",78,92,62),
        Organization("bilgewater_captains","比尔吉沃特船长与港口势力","比尔吉沃特","港口联盟","争夺港口、航线、悬赏与海上贸易",mutableListOf(),mutableListOf(),"港口头目",60,62,45),
        Organization("shurima_sun_disc","恕瑞玛复兴势力","恕瑞玛","帝国复兴运动","争夺古代遗产、绿洲与帝国合法性",mutableListOf(),mutableListOf(),"复兴派代表",58,52,48),
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
    private data class PairTension(val a:String,val b:String,var tension:Int,var war:Boolean=false,var aWins:Int=0,var bWins:Int=0)
    private val factionRegions=mapOf("德玛西亚" to "德玛西亚","诺克萨斯" to "诺克萨斯","艾欧尼亚" to "艾欧尼亚","皮尔特沃夫" to "皮尔特沃夫","祖安" to "祖安","弗雷尔卓德" to "弗雷尔卓德","恕瑞玛" to "恕瑞玛","巨神峰" to "巨神峰","比尔吉沃特" to "比尔吉沃特","以绪塔尔" to "以绪塔尔")
    private fun diplomacy(p:android.content.SharedPreferences):MutableList<PairTension> {
        val raw=p.getString(KEY_DIPLO,null)
        if(raw!=null) runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { PairTension(it.optString("a"),it.optString("b"),it.optInt("tension",10),it.optBoolean("war",false),it.optInt("aWins",0),it.optInt("bWins",0)) }.toMutableList() } }.getOrNull()?.let { if(it.isNotEmpty()) {
            val corrected=it.filterNot { relation -> setOf(relation.a,relation.b)==setOf("皮尔特沃夫","祖安") }.toMutableList()
            if(corrected.size!=it.size) saveDiplomacy(p,corrected)
            return corrected
        } }
        return mutableListOf(PairTension("诺克萨斯","艾欧尼亚",72),PairTension("诺克萨斯","德玛西亚",58),PairTension("弗雷尔卓德","诺克萨斯",32),PairTension("恕瑞玛","诺克萨斯",28),PairTension("比尔吉沃特","诺克萨斯",24)).also { saveDiplomacy(p,it) }
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
                        victims.forEach { r -> r.health=(r.health-5-rng.nextInt(12)).coerceAtLeast(0); recordLife(r,day,"战争波及${region}，在冲突中受伤。") }
                        markets[region]?.let { it.food=(it.food-15-rng.nextInt(30)).coerceAtLeast(0) }
                        if(victims.isNotEmpty()) events.add("【战争损害】第 $day 日：${region}遭冲突波及，${victims.size}名居民受伤，粮食储备下降。")
                    }
                }
                saveFactions(p,factions)
            }
        }
        saveDiplomacy(p,relations)
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
        lorePulses.filter { it.day==day && !triggered.contains(it.id) }.forEach { pulse ->
            val market=markets[pulse.region]
            if(market!=null) market.food=(market.food-pulse.foodLoss).coerceAtLeast(0)
            val affected=pop.filter { it.alive && it.region==pulse.region }
            affected.forEach { r -> r.health=(r.health-pulse.healthLoss).coerceAtLeast(0); recordLife(r,day,"经历历史事件「${pulse.title}」的冲击，身处${pulse.region}。") }
            val moved=affected.filter { rng.nextInt(100)<18 }
            moved.forEach { r ->
                val dest=residentRegions[(residentRegions.indexOf(pulse.region).coerceAtLeast(0)+1)%residentRegions.size]
                r.region=dest
                recordLife(r,day,"因「${pulse.title}」引发的动荡，从${pulse.region}迁往${dest}。")
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
            residents.forEach { r -> r.health=(r.health+stage.healthDelta).coerceIn(0,100); recordLife(r,day,"受到历史事件「${stage.title}」影响，身处${stage.region}。") }
            val displaced=residents.filter { rng.nextInt(100)<stage.migrationChance }
            displaced.forEach { r ->
                val destination=residentRegions[(residentRegions.indexOf(stage.region).coerceAtLeast(0)+1)%residentRegions.size]
                r.region=destination
                recordLife(r,day,"因「${stage.title}」从${stage.region}迁往${destination}。")
            }
            events.add("【历史事件链·模拟】第 ${day} 日 ${stage.title}；${stage.region}影响${residents.size}人，迁徙${displaced.size}人。")
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
        "暗影岛" -> -2
        "班德尔城" -> 0
        else -> 0
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
            for(i in 0 until a.length()) { val j=a.getJSONObject(i); map[j.optString("region")]=Market(j.optInt("food",300),j.optInt("wealth",1000)) }
            if(map.isNotEmpty()) {
                regions.filter { it !in map }.forEach { region -> map[region]=Market(300+Random(region.hashCode().toLong()).nextInt(250),1000) }
                saveMarkets(p,map)
                return map
            }
        }
        val fresh=regions.associateWith { Market(300+Random(it.hashCode().toLong()).nextInt(250),1000) }.toMutableMap()
        saveMarkets(p,fresh); return fresh
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
        val rng=Random(24681357L)
        val list=(0 until 240).map { i ->
            val region=residentRegions[i%residentRegions.size]
            val resident=Resident(i,names[rng.nextInt(names.size)]+"·"+(i+1),region,16+rng.nextInt(54),65+rng.nextInt(36),12+rng.nextInt(24),rng.nextInt(40),jobs[rng.nextInt(jobs.size)],goals[rng.nextInt(goals.size)],settlement=defaultSettlement(region,i))
            recordLife(resident,1,"世界模拟档案建立；当时${resident.age}岁，居住于${resident.region}的${resident.settlement}，职业为${resident.job}。")
            resident
        }.toMutableList()
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
        var nextId=pop.maxOfOrNull { it.id }?.plus(1) ?: p.getInt(KEY_NEXT_ID,240)
        repeat(days.coerceAtLeast(1)) {
            day++
            val rng=Random(day*7919L+17L)
            applyLorePulse(day,p,pop,markets,events,rng)
            applyLoreChainStage(day,p,pop,markets,events,rng)
            tickDiplomacy(day,p,pop,markets,events,rng)
            tickOrganizations(day,p,pop,events,rng)
            tickFactionGovernance(day,p,pop,markets,events,rng)
            tickHeroes(day,p,pop,markets,events,rng)
            tickSocialRelations(day,pop,events,rng)
            tickMagic(day,p,pop,markets,events,rng)
            val newborns=mutableListOf<Resident>()
            pop.filter { it.alive }.forEach { r ->
                r.food=(r.food-1).coerceAtLeast(0)
                if(r.food==0) r.health=(r.health-2).coerceAtLeast(0)
                if(tickTravel(r,day,events,rng)) return@forEach
                if(rng.nextInt(100)<(42+r.skill.coerceAtMost(15))) when(r.job) {
                    "农夫","采集者" -> {r.food+=(2+rng.nextInt(3)+foodBonus(r.region)).coerceAtLeast(1);r.coins++;markets[r.region]?.let { it.food+=(12+rng.nextInt(9)+foodBonus(r.region)).coerceAtLeast(1);it.wealth++ };r.skill=(r.skill+1).coerceAtMost(100)}
                    "商贩" -> if(r.food>1){r.food--;r.coins+=2;markets[r.region]?.let { if(it.food>0)it.food--;it.wealth+=2 };r.skill=(r.skill+1).coerceAtMost(100)}else r.food++
                    "工匠" -> {r.coins+=2+craftBonus(r.region);markets[r.region]?.let { it.wealth+=2+craftBonus(r.region) };r.skill=(r.skill+1).coerceAtMost(100)}
                    "护卫" -> {r.coins++;if(rng.nextInt(100)<4)r.health=(r.health-3).coerceAtLeast(0)}
                    else -> {r.coins++;if(rng.nextInt(100)<20)r.food++}
                }
                if(r.food<=2) {
                    val market=markets[r.region]
                    if(market!=null && market.food>0 && r.coins>0) {
                        val bought=minOf(3-r.food,market.food,r.coins)
                        if(bought>0) { market.food-=bought; r.food+=bought; r.coins-=bought; market.wealth+=bought }
                    }
                }
                if(day%360L==0L) r.age++
                if(day%90L==0L && r.alive && r.skill>=10 && r.job=="学徒") {
                    r.job="工匠"
                    recordLife(r,day,"在${r.region}完成学徒阶段，成为工匠。")
                    events.add("第 $day 日：${r.name}在${r.region}完成学徒阶段，成为工匠。")
                }
                if(day%90L==0L && r.alive && r.skill>=25 && r.job=="旅人") {
                    r.job="护卫"
                    recordLife(r,day,"在${r.region}积累经验，转为护卫。")
                    events.add("第 $day 日：${r.name}在${r.region}积累经验，转为护卫。")
                }
                if(day%90L==0L && r.alive && r.skill>=50 && r.job=="采集者") {
                    r.job="商贩"
                    recordLife(r,day,"在${r.region}凭经验开始经营贸易。")
                    events.add("第 $day 日：${r.name}在${r.region}凭经验开始经营贸易。")
                }
                if(day%30L==0L && r.alive && r.partnerId<0 && r.age in 20..35 && rng.nextInt(1000)<2) {
                    val candidate=pop.firstOrNull { it.alive && it.id!=r.id && it.region==r.region && it.partnerId<0 && it.age in 20..35 }
                    if(candidate!=null) { r.partnerId=candidate.id;candidate.partnerId=r.id
                        recordLife(r,day,"与${candidate.name}建立伴侣关系。");recordLife(candidate,day,"与${r.name}建立伴侣关系。")
                        events.add("第 $day 日：${r.region}的${r.name}与${candidate.name}建立伴侣关系。") }
                }
                if(day%30L==0L && r.partnerId>=0 && r.id<r.partnerId && r.age in 20..38 && r.familyCount<3 && rng.nextInt(1000)<3) {
                    r.familyCount++
                    val mate=pop.firstOrNull { it.id==r.partnerId && it.alive }
                    if(mate!=null) mate.familyCount=r.familyCount
                    val id=nextId++
                    val child=Resident(id,names[rng.nextInt(names.size)]+"·"+id,r.region,0,100,3,0,"学徒","成长与学习",settlement=r.settlement)
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
                    if(next!=oldRegion) beginTravel(r,next,day,events,"粮食短缺与生计压力")
                }
                if(r.health<=0 || (r.age>78 && rng.nextInt(100)<1) || (r.age>95 && rng.nextInt(100)<12)) {
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
            }
            pop.addAll(newborns)
            regions.forEach { region ->
                val market=markets[region]
                if(market!=null) market.food=market.food.coerceAtLeast(0)
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
    fun residentDirectory(context:Context):List<String> {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return people(p).sortedWith(compareBy<Resident>{!it.alive}.thenByDescending{it.age}).map {
            "${it.id}｜${it.name}｜${it.age}岁｜${it.region}｜${it.job}｜${if(it.alive)"在世" else "已故"}"
        }
    }
    fun residentHistory(context:Context,id:Int):String {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val all=people(p)
        val r=all.firstOrNull{it.id==id} ?: return "未找到该居民档案。"
        val partner=all.firstOrNull{it.id==r.partnerId}
        return buildString {
            append("人物档案：${r.name}\n")
            append("居民编号：${r.id}\n年龄：${r.age}岁\n地区：${r.region}\n聚落：${r.settlement}\n职业：${r.job}\n")
            append("健康：${r.health}/100\n技能：${r.skill}\n财富：${r.coins}\n")
            append("人生状态：${if(r.alive)"在世" else "已故"}\n")
            append("伴侣：${partner?.name ?: if(r.partnerId>=0)"关系对象档案不可用" else "暂无记录"}\n")
            val parents=all.filter { it.id in r.parentIds }
            val children=all.filter { it.id in r.childIds }
            val friends=all.filter { it.id in r.friendIds }
            val mentor=all.firstOrNull{it.id==r.mentorId}
            val students=all.filter{it.id in r.studentIds}
            append("父母：${parents.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n")
            append("子女：${children.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n")
            append("朋友：${friends.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n")
            append("师长：${mentor?.name ?: "暂无记录"}\n")
            append("学生：${students.joinToString("、") { it.name }.ifBlank { "暂无记录" }}\n")
            val memberships=organizations(p).filter { r.id in it.members }.map { it.name }
            append("所属组织：${memberships.joinToString("、").ifBlank { "暂无记录" }}\n")
            append("家庭子女计数：${r.familyCount}\n\n人生时间线\n")
            if(r.history.isEmpty()) append("暂时没有人生事件记录。")
            else r.history.forEach { append("• ").append(it).append("\n") }
        }
    }
    fun setRunning(context: Context,running:Boolean) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY_RUNNING,running).apply()
    }
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().putLong(KEY_DAY,1L).apply()
    }
}