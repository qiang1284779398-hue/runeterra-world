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
        else -> "谨慎等待与观察"
    }

    fun tryDecide(context: Context, actor: String, actorName: String, prompt: String): String? {
        val rawAction = PersonalLearningNetwork.autonomousChoice(context, actor, prompt) ?: return null
        val action = normalizeAction(rawAction, actor.startsWith("hero:"))
        return buildResponse(actor, actorName, prompt, action, "根据本人已积累的行动经验，选择当前最有把握的策略。", "personal_policy")
    }

    fun offlineFallback(context: Context, actor: String, actorName: String, prompt: String): String {
        val hero = actor.startsWith("hero:")
        val action = if (hero) chooseHeroAction(prompt) else chooseResidentAction(prompt)
        val learned = PersonalLearningNetwork.autonomousChoice(context, actor, prompt)
        val selected = learned?.let { normalizeAction(it, hero) } ?: action
        val reason = if (learned != null) "结合本人过往行动反馈选择已学习的策略。" else "外部模型不可用，依据当前可见处境、基本需求和已知目标作出有限的本地判断。"
        return buildResponse(actor, actorName, prompt, selected, reason, if (learned != null) "personal_policy_offline" else "bounded_offline_rules")
    }

    private fun normalizeAction(action: String, hero: Boolean): String {
        val allowed = if (hero) setOf("stay", "train", "investigate", "negotiate", "aid", "join_event", "decline_event", "investigate_event")
            else setOf("stay", "seek_food", "seek_work", "change_job", "ask_help", "practice_skill", "socialize", "investigate_world", "discuss_world")
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
            else -> "目前没有足够依据贸然行动，我先观察眼前变化，维持当前安排。"
        }
        val belief = Regex("信念[:：]([^；。\\n]+)").find(prompt)?.groupValues?.getOrNull(1)?.take(100).orEmpty()
        val goal = Regex("(?:目标|自主目标)[:：]([^；。\\n]+)").find(prompt)?.groupValues?.getOrNull(1)?.take(100).orEmpty()
        val intent = when (action) {
            "stay" -> "观察并维持当前安排"
            "seek_food" -> "寻找食物与补给"
            "seek_work" -> "寻找谋生机会"
            "ask_help", "aid" -> "寻求援助并处理眼前危险"
            "practice_skill", "train" -> "训练并提升能力"
            "socialize", "negotiate" -> "与相关人物交流"
            "investigate_world", "investigate", "investigate_event" -> "调查并核实信息"
            "join_event" -> "评估后参与当前事件"
            "decline_event" -> "拒绝当前提议"
            else -> "处理当前最重要的事情"
        }
        val out = JSONObject().put("thought", thought).put("belief", belief.ifBlank { "依据亲身经历与当前处境形成判断" })
            .put("intent", intent).put("goal", goal.ifBlank { intent }).put("action", action).put("reason", reason)
            .put("decision_source", source).put("decision_confidence", if (source == "personal_policy") 0.68 else 0.35)
        if (!hero) out.put("new_job", "").put("target_id", -1)
        return out.toString()
    }
}
