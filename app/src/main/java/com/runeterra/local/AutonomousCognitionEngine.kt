package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline cognition bridge.
 * It converts actor-specific policy learning and verified outcomes into bounded decisions.
 * It does not claim human-like consciousness; the world engine remains authoritative.
 */
object AutonomousCognitionEngine {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("runeterra_autonomous_skills", Context.MODE_PRIVATE)
    private fun key(actor: String) = actor.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)

    fun prepare(context: Context, actor: String): String {
        val p = prefs(context)
        val skills = runCatching { JSONArray(p.getString("${key(actor)}_skills", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val learned = (0 until skills.length()).mapNotNull { skills.optJSONObject(it)?.optString("name")?.takeIf { it.isNotBlank() } }
        return "【自主技能库】\n已沉淀技能：${learned.takeLast(12).joinToString("、").ifBlank { "尚无经多次验证的稳定技能" }}\n技能只在真实行动结果反复支持后形成；技能是建议，不得覆盖当前事实、角色意愿或天道规则。"
    }

    fun recordOutcome(context: Context, actor: String, day: Long, action: String, outcome: String) {
        if (action.isBlank() || outcome.isBlank()) return
        val positive = listOf("成功", "获得", "完成", "改善", "学会", "帮助", "保护", "恢复", "建立友谊", "获救")
        val negative = listOf("失败", "未能", "受伤", "损失", "被拒绝", "无法", "死亡", "被捕")
        val good = positive.any(outcome::contains) && negative.none(outcome::contains)
        if (!good) return
        val p = prefs(context)
        val k = key(actor)
        val skills = runCatching { JSONArray(p.getString("${k}_skills", "[]") ?: "[]") }.getOrDefault(JSONArray())
        var found: JSONObject? = null
        for (i in 0 until skills.length()) {
            val item = skills.optJSONObject(i) ?: continue
            if (item.optString("action") == action.trim().lowercase()) { found = item; break }
        }
        val item = found ?: JSONObject().put("action", action.trim().lowercase()).put("name", skillName(action)).put("successes", 0).also { skills.put(it) }
        item.put("successes", item.optInt("successes") + 1).put("lastDay", day).put("lastEvidence", outcome.take(260))
        if (item.optInt("successes") >= 3) item.put("validated", true)
        while (skills.length() > 32) skills.remove(0)
        p.edit().putString("${k}_skills", skills.toString()).apply()
    }

    private fun skillName(action: String): String = when (action.trim().lowercase()) {
        "seek_food" -> "寻找食物与补给"
        "seek_work" -> "谋生与工作"
        "change_job" -> "职业调整"
        "ask_help", "aid" -> "求助与互助"
        "practice_skill", "train" -> "训练与技能提升"
        "socialize", "negotiate" -> "建立关系与沟通"
        "investigate_world", "investigate", "investigate_event" -> "调查与信息核实"
        "join_event" -> "参与公共事件"
        "take_risk" -> "承担风险争取机会"
        "explore_unknown" -> "探索未知区域"
        "provoke" -> "挑衅与正面对抗"
        else -> "谨慎等待与观察"
    }

    fun tryDecide(context: Context, actor: String, actorName: String, prompt: String): String? {
        val rawAction = PersonalLearningNetwork.autonomousChoice(context, actor, prompt) ?: return null
        val action = normalizeAction(rawAction, actor.startsWith("hero:"))
        return buildResponse(actor, actorName, prompt, action, "根据本人已积累的行动经验，选择当前最有把握的策略。", "personal_policy")
    }

    fun offlineFallback(context: Context, actor: String, actorName: String, prompt: String, diagnostic: String = ""): String {
        val hero = actor.startsWith("hero:")
        val ruleAction = if (hero) chooseHeroAction(prompt) else chooseResidentAction(prompt)
        val learned = PersonalLearningNetwork.autonomousChoice(context, actor, prompt)
        fun number(label: String): Int? = Regex("$label\\s*(\\d+)").find(prompt)?.groupValues?.getOrNull(1)?.toIntOrNull()
        // Hard survival layer: the learned policy may advise, but it may never override
        // imminent starvation, critical health or active danger. A starving resident
        // does not provoke a neighbour because a network said so. (Deliberate,
        // reasoned self-endangerment by the character's own online decision is a
        // different path and is not blocked here.)
        val survivalAction: String? = if (!hero) {
            val food = number("口粮"); val health = number("健康")
            when {
                health != null && health < 35 -> "stay"
                (food != null && food <= 1) || listOf("饥饿", "缺少食物", "口粮不足").any(prompt::contains) -> "seek_food"
                ruleAction == "ask_help" -> "ask_help"
                else -> null
            }
        } else if (ruleAction == "aid") "aid" else null
        // Boredom break: accumulated boredom can replace a MONOTONOUS rule action —
        // not only "stay". A hero whose prompt mentions training every day gets "train"
        // from the rules, and v1's stay-only replacement let that loop run forever.
        // Survival actions and deliberate refusals are never replaced.
        var boredomBreak = false
        val breakAction: String? = if (survivalAction == null && learned == null) {
            val boredom = MotivationEngine.boredomLevel(context, actor)
            val lastAct = MotivationEngine.lastAction(context, actor)
            val protectedActions = setOf("seek_food", "ask_help", "aid", "decline_event")
            val replaceable = ruleAction !in protectedActions && (ruleAction == "stay" || (lastAct.isNotBlank() && ruleAction == lastAct))
            if (boredom >= 55 && replaceable) {
                // The seed varies by world day and by a persisted per-actor call
                // sequence, so re-asking with an identical prompt does not replay one
                // frozen random sequence (v1 seeded on prompt+actor only, which made
                // every same-prompt call decide identically, even across days).
                val p = prefs(context); val k = key(actor)
                val seq = p.getInt("${k}_fbseq", 0) + 1
                p.edit().putInt("${k}_fbseq", seq).apply()
                val day = Regex("第\\s*(\\d+)\\s*日").find(prompt)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val rng = java.util.Random(day * 1000003L + actor.hashCode().toLong() * 31L + seq * 7919L)
                val chance = ((boredom - 40) / 60.0).coerceIn(0.25, 0.85)
                if (rng.nextDouble() < chance) {
                    boredomBreak = true
                    val risk = MotivationEngine.riskTolerance(context, actor)
                    val pool = if (hero) listOf("explore_unknown", "investigate", "take_risk") else listOf("explore_unknown", "investigate_world", "take_risk")
                    if (risk >= 60 && rng.nextDouble() < 0.45) "take_risk" else pool[rng.nextInt(2)]
                } else null
            } else null
        } else null
        val selected = survivalAction ?: learned?.let { normalizeAction(it, hero) } ?: breakAction ?: ruleAction
        val reason = when {
            survivalAction != null -> "当前处境涉及基本生存与安全，先处理生存需求，其余倾向暂缓。${if (diagnostic.isNotBlank()) " 云端诊断：${diagnostic.take(240)}" else ""}"
            learned != null -> "结合本人过往行动反馈选择已学习的策略。${if (diagnostic.isNotBlank()) " 云端诊断：${diagnostic.take(240)}" else ""}"
            boredomBreak -> "同样的日子重复太久，无聊与停滞感累积到了临界点，决定主动打破惯例，去做一件不一样的事，哪怕结果不确定。${if (diagnostic.isNotBlank()) " 云端诊断：${diagnostic.take(240)}" else ""}"
            else -> "外部模型不可用，依据当前可见处境、基本需求和已知目标作出有限的本地判断。${if (diagnostic.isNotBlank()) " 云端诊断：${diagnostic.take(240)}" else ""}"
        }
        return buildResponse(actor, actorName, prompt, selected, reason, if (learned != null && survivalAction == null) "personal_policy_offline" else "bounded_offline_rules")
    }

    private fun normalizeAction(action: String, hero: Boolean): String {
        val allowed = if (hero) setOf("stay", "train", "investigate", "negotiate", "aid", "join_event", "decline_event", "investigate_event", "wait", "take_risk", "explore_unknown", "provoke")
            else setOf("stay", "seek_food", "seek_work", "change_job", "ask_help", "practice_skill", "socialize", "investigate_world", "discuss_world", "take_risk", "explore_unknown", "provoke")
        return if (action in allowed) action else "stay"
    }

    private fun chooseResidentAction(prompt: String): String {
        fun number(label: String): Int? = Regex("$label\\s*(\\d+)").find(prompt)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val food = number("口粮")
        val health = number("健康")
        val fatigue = number("疲劳")
        if ((health != null && health < 35) || (fatigue != null && fatigue >= 85)) return "stay"
        if ((food != null && food <= 1) || listOf("饥饿", "缺少食物", "口粮不足").any(prompt::contains)) return "seek_food"
        if (listOf("受到攻击", "遭到袭击", "危险迫近", "受伤", "求援").any(prompt::contains)) return "ask_help"
        if (listOf("训练", "学习技艺", "提升技能", "学徒").any(prompt::contains)) return "practice_skill"
        if (listOf("结识", "朋友", "友谊", "关系改善", "社交").any(prompt::contains)) return "socialize"
        if (listOf("调查", "真相", "探索", "研究", "查明").any(prompt::contains)) return "investigate_world"
        if (listOf("工作", "谋生", "收入", "雇佣", "职业机会").any(prompt::contains)) return "seek_work"
        return "stay"
    }

    private fun chooseHeroAction(prompt: String): String {
        if (listOf("平民遇险", "保护村民", "救援", "有人受伤", "袭击平民").any(prompt::contains)) return "aid"
        // Do not match the generic word "拒绝": the character prompt itself explains that
        // refusal is an available option, which previously made every fallback turn decline.
        if (listOf("明确拒绝邀请", "拒绝这项邀请", "拒绝当前提议", "拒绝加入行动", "我不愿参与这次事件").any(prompt::contains)) return "decline_event"
        if (listOf("训练", "修炼", "提升能力", "磨练").any(prompt::contains)) return "train"
        if (listOf("谈判", "交涉", "结盟", "外交", "沟通").any(prompt::contains)) return "negotiate"
        if (listOf("调查", "真相", "线索", "探索").any(prompt::contains)) return "investigate"
        if (listOf("战斗", "冲突", "战事", "危机").any(prompt::contains)) return "join_event"
        if (listOf("神秘事件", "异常现象", "历史遗迹").any(prompt::contains)) return "investigate_event"
        return "stay"
    }

    private fun buildResponse(actor: String, actorName: String, prompt: String, action: String, reason: String, source: String): String {
        val hero = actor.startsWith("hero:")
        val thought = when (action) {
            "seek_food" -> "眼下先解决食物问题，维持体力比贸然追求其他目标更重要。"
            "seek_work" -> "我需要寻找可靠的谋生机会，让自己的生活和目标有现实支撑。"
            "ask_help", "aid" -> "眼前的处境不能只靠逞强解决，我应当寻求援助或先保护需要帮助的人。"
            "practice_skill", "train" -> "我应该把时间投入训练与实践，让能力真正得到提升。"
            "socialize", "negotiate" -> "我需要认真考虑与他人的关系，通过交流确认彼此的立场。"
            "investigate_world", "investigate", "investigate_event" -> "目前掌握的信息还不充分，我应先调查并核实线索，避免轻率判断。"
            "join_event" -> "这件事可能影响周围的人，我需要先评估风险，再决定如何参与。"
            "decline_event" -> "我决定不参加这次提议；这只是当前处境下的选择，之后若出现新证据或条件变化，我会重新评估。"
            "take_risk" -> "机会难得，代价也在可承受范围内。与其继续原地踏步，我愿意押上一部分筹码赌一次结果。"
            "explore_unknown" -> "熟悉的范围里已经没有什么新东西了。我想去看看未知的地方，哪怕只是为了确认那里到底有什么。"
            "provoke" -> "有些事退让解决不了。我决定正面挑明立场，哪怕会因此树敌或付出代价。"
            else -> "目前没有足够依据贸然行动，我先观察眼前变化，维持当前安排。"
        }
        val belief = Regex("信念[:：]([^；。\\n]+)").find(prompt)?.groupValues?.getOrNull(1)?.take(100).orEmpty()
        val goal = Regex("(?:目标|自主目标)[:：]([^；。\\n]+)").find(prompt)?.groupValues?.getOrNull(1)?.take(100).orEmpty()
        val intent = when (action) {
            "stay" -> "观察并维持当前安排"
            "wait" -> "等待时机，暂不表态"
            "seek_food" -> "寻找食物与补给"
            "seek_work" -> "寻找谋生机会"
            "ask_help", "aid" -> "寻求援助并处理眼前危险"
            "practice_skill", "train" -> "训练并提升能力"
            "socialize", "negotiate" -> "与相关人物交流"
            "investigate_world", "investigate", "investigate_event" -> "调查并核实信息"
            "join_event" -> "评估后参与当前事件"
            "decline_event" -> "拒绝当前提议"
            "take_risk" -> "承担风险争取一次机会"
            "explore_unknown" -> "离开熟悉范围探索未知"
            "provoke" -> "正面对抗并挑明立场"
            else -> "处理当前最重要的事情"
        }
        val out = JSONObject().put("thought", thought).put("belief", belief.ifBlank { "依据亲身经历与当前处境形成判断" })
            .put("intent", intent).put("goal", goal.ifBlank { intent }).put("action", action).put("reason", reason)
            .put("decision_source", source).put("decision_confidence", if (source == "personal_policy") 0.68 else 0.35)
        if (!hero) out.put("new_job", "").put("target_id", -1)
        return out.toString()
    }
}
