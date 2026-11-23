package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native on-device replacements for the five backend-side agent capabilities.
 * Storage is actor-scoped SharedPreferences so no Python service or account is required.
 */
object NativeAgentRuntime {
    private const val PREFS = "runeterra_native_agents"
    private const val MAX_MEMORY = 120
    private const val MAX_GOALS = 40
    private const val MAX_PLAN_STEPS = 24

    private fun store(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun actorKey(actor: String) = actor.replace(Regex("[^A-Za-z0-9_-]"), "_").take(72).ifBlank { "unknown" }
    private fun read(c: Context, key: String) = runCatching {
        JSONObject(store(c).getString(key, "{}") ?: "{}")
    }.getOrDefault(JSONObject())
    private fun array(o: JSONObject, key: String) = o.optJSONArray(key) ?: JSONArray()
    private fun clean(s: String, max: Int = 1200) = s.trim().replace(Regex("\\s+"), " ").take(max)

    /** Letta-like persistent memory: durable profile, semantic facts, goals and episodic events. */
    private fun memoryPrompt(c: Context, actor: String): String {
        val s = read(c, "${actorKey(actor)}_memory")
        val facts = array(s, "facts")
        val episodes = array(s, "episodes")
        val goals = array(s, "goals")
        val factText = (0 until facts.length()).mapNotNull { facts.optJSONObject(it)?.optString("text") }
            .takeLast(16).joinToString("；").ifBlank { "暂无稳定事实" }
        val episodeText = (maxOf(0, episodes.length() - 10) until episodes.length())
            .mapNotNull { episodes.optJSONObject(it) }
            .joinToString("\n") { "第${it.optLong("day")}日：${it.optString("summary")}" }
            .ifBlank { "暂无已记录经历" }
        val goalText = (0 until goals.length()).mapNotNull { goals.optJSONObject(it) }
            .filter { it.optString("status", "active") == "active" }
            .takeLast(8).joinToString("；") { it.optString("title") + "（优先级" + it.optInt("priority", 50) + "）" }
            .ifBlank { "尚无持续目标" }
        return """【原生长期记忆｜本机保存】
稳定事实：$factText
近期亲历：$episodeText
持续目标：$goalText
事实必须区分亲历、推测和他人转述；新信息与旧记忆冲突时先核实，不得擅自覆盖重要记忆。"""
    }

    /** LangGraph-like native task graph with durable nodes, dependencies and retry state. */
    private fun planPrompt(c: Context, actor: String): String {
        val s = read(c, "${actorKey(actor)}_plan")
        val nodes = array(s, "nodes")
        val pending = (0 until nodes.length()).mapNotNull { nodes.optJSONObject(it) }
            .filter { it.optString("status", "pending") in listOf("pending", "blocked", "retry") }
            .take(10)
        val plan = pending.joinToString("\n") {
            "- [${it.optString("status")}] ${it.optString("title")}；前置：${it.optString("depends_on", "无")}；尝试：${it.optInt("attempts", 0)}"
        }.ifBlank { "当前没有待执行的多步骤计划。" }
        return """【原生行动图 / 计划执行】
当前待办节点：
$plan
每次行动要对应一个可验证节点；前置条件未满足时不得假装完成。遇阻时记录原因、调整后续节点或暂停计划，不要无限重复同一行动。"""
    }

    /** MASEval-like context: recent cooperation, requests and unresolved commitments in this world. */
    private fun socialPrompt(c: Context, actor: String): String {
        val s = read(c, "${actorKey(actor)}_social")
        val events = array(s, "events")
        val worldEvents = array(read(c, "world_social"), "events")
        val personalRecent = (maxOf(0, events.length() - 5) until events.length()).mapNotNull { events.optJSONObject(it) }
        val sharedRecent = (maxOf(0, worldEvents.length() - 8) until worldEvents.length()).mapNotNull { worldEvents.optJSONObject(it) }
        val recent = (personalRecent + sharedRecent).takeLast(12)
            .joinToString("\n") { "- ${it.optString("actor", actor)}：${it.optString("summary")}" }
            .ifBlank { "暂无已记录的协作事件" }
        return """【原生多智能体协作记录】
$recent
涉及他人时应明确对方身份、请求内容、承诺与实际分工；不能把其他居民的行动当作自己的亲历。"""
    }

    fun enrich(c: Context, actor: String, day: Long, prompt: String): String {
        val k = actorKey(actor)
        val s = read(c, "${k}_runtime")
        s.put("lastPromptDay", day).put("lastPrompt", clean(prompt, 2500))
        store(c).edit().putString("${k}_runtime", s.toString()).apply()
        return prompt + "\n\n" + memoryPrompt(c, actor) + "\n\n" + planPrompt(c, actor) + "\n\n" + socialPrompt(c, actor) + """
\n\n【原生智能体输出协议】
在原任务JSON字段之外，补充 next_step（下一项可执行步骤，没有则空字符串）、completed_step（本轮确实完成的步骤）、step_result（success/failed/blocked/none）、collaboration（与其他人物的具体协作、请求、承诺或分工；没有则空字符串）。不得把计划说成已完成；失败必须写明原因。"""
    }

    /** DeepEval-like deterministic checks are performed locally on the model's actual JSON output. */
    private fun evaluate(raw: String, obj: JSONObject?): JSONObject {
        val thought = obj?.optString("thought").orEmpty()
        val action = obj?.optString("action").orEmpty()
        val reason = obj?.optString("reason", obj?.optString("decision_reason", "")).orEmpty()
        val checks = JSONObject()
            .put("valid_json", obj != null)
            .put("has_thought", thought.isNotBlank())
            .put("has_action", action.isNotBlank())
            .put("has_reason", reason.isNotBlank())
            .put("boilerplate", raw.contains("请再告诉我你的问题") || raw.contains("刚才没有正常生成内容"))
            .put("thought_length", thought.length)
        val score = (if (obj != null) 30 else 0) +
            (if (thought.length >= 35) 25 else if (thought.isNotBlank()) 12 else 0) +
            (if (action.isNotBlank()) 20 else 0) +
            (if (reason.length >= 20) 15 else if (reason.isNotBlank()) 7 else 0) +
            (if (!checks.optBoolean("boilerplate")) 10 else 0)
        return JSONObject().put("score", score).put("checks", checks).put("rawLength", raw.length)
    }

    private fun parseObject(raw: String): JSONObject? {
        for (start in raw.indices) {
            if (raw[start] != '{') continue
            var depth = 0
            var quoted = false
            var escaped = false
            for (i in start until raw.length) {
                val ch = raw[i]
                if (quoted) {
                    if (escaped) escaped = false else if (ch == '\\') escaped = true else if (ch == '"') quoted = false
                } else when (ch) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return runCatching { JSONObject(raw.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun appendBounded(a: JSONArray, value: JSONObject, max: Int) {
        a.put(value)
        while (a.length() > max) a.remove(0)
    }

    fun record(c: Context, actor: String, day: Long, raw: String) {
        val k = actorKey(actor)
        val obj = parseObject(raw)
        val memory = read(c, "${k}_memory")
        val episodes = array(memory, "episodes")
        val thought = clean(obj?.optString("thought").orEmpty())
        val action = clean(obj?.optString("action").orEmpty(), 300)
        val outcome = clean(obj?.optString("outcome").orEmpty(), 500)
        if (thought.isNotBlank() || action.isNotBlank()) {
            appendBounded(episodes, JSONObject().put("day", day).put("summary", clean("思考：$thought；行动：$action；结果：$outcome", 900)), MAX_MEMORY)
        }
        val facts = array(memory, "facts")
        val newFacts = obj?.optJSONArray("self_facts")
        if (newFacts != null) for (i in 0 until newFacts.length()) {
            val fact = clean(newFacts.optString(i), 300)
            if (fact.isNotBlank() && (0 until facts.length()).none { facts.optJSONObject(it)?.optString("text") == fact }) {
                appendBounded(facts, JSONObject().put("text", fact).put("day", day), 60)
            }
        }
        val goals = array(memory, "goals")
        val newGoal = clean(obj?.optString("new_goal").orEmpty(), 300)
        if (newGoal.isNotBlank() && (0 until goals.length()).none { goals.optJSONObject(it)?.optString("title") == newGoal && it >= goals.length() - 5 }) {
            appendBounded(goals, JSONObject().put("title", newGoal).put("day", day)
                .put("priority", obj?.optInt("goal_priority", 50)?.coerceIn(0, 100) ?: 50).put("status", "active"), MAX_GOALS)
        }
        val goalStatus = obj?.optString("goal_status").orEmpty()
        val goalTitle = clean(obj?.optString("long_term_goal").orEmpty(), 300)
        if (goalStatus in listOf("completed", "paused", "abandoned") && goalTitle.isNotBlank()) {
            for (i in 0 until goals.length()) {
                val g = goals.optJSONObject(i) ?: continue
                if (g.optString("title") == goalTitle) g.put("status", if (goalStatus == "abandoned") "abandoned" else goalStatus)
            }
        }
        memory.put("episodes", episodes).put("facts", facts).put("goals", goals).put("updatedDay", day)
        store(c).edit().putString("${k}_memory", memory.toString()).apply()

        val plan = read(c, "${k}_plan")
        val nodes = array(plan, "nodes")
        val step = clean(obj?.optString("next_step").orEmpty(), 300)
        if (step.isNotBlank()) {
            val existing = (0 until nodes.length()).any { nodes.optJSONObject(it)?.optString("title") == step && nodes.optJSONObject(it)?.optString("status") == "pending" }
            if (!existing) appendBounded(nodes, JSONObject().put("title", step).put("status", "pending").put("attempts", 0).put("createdDay", day), MAX_PLAN_STEPS)
        }
        val completed = clean(obj?.optString("completed_step").orEmpty(), 300)
        if (completed.isNotBlank()) for (i in 0 until nodes.length()) {
            val n = nodes.optJSONObject(i) ?: continue
            if (n.optString("title") == completed) n.put("status", "completed").put("completedDay", day)
        }
        val stepResult = obj?.optString("step_result").orEmpty().lowercase()
        if (step.isNotBlank() && stepResult in listOf("failed", "blocked")) {
            for (i in 0 until nodes.length()) {
                val n = nodes.optJSONObject(i) ?: continue
                if (n.optString("title") == step && n.optString("status") != "completed") {
                    val attempts = n.optInt("attempts", 0) + 1
                    n.put("attempts", attempts)
                        .put("status", if (attempts >= 3 || stepResult == "blocked") "blocked" else "retry")
                        .put("lastFailure", clean(obj?.optString("step_failure").orEmpty(), 400))
                        .put("updatedDay", day)
                }
            }
        }
        plan.put("nodes", nodes).put("updatedDay", day)
        store(c).edit().putString("${k}_plan", plan.toString()).apply()

        val social = read(c, "${k}_social")
        val events = array(social, "events")
        val collaboration = clean(obj?.optString("collaboration").orEmpty(), 500)
        if (collaboration.isNotBlank()) {
            appendBounded(events, JSONObject().put("day", day).put("actor", actor).put("summary", collaboration), 40)
            val world = read(c, "world_social")
            val shared = array(world, "events")
            appendBounded(shared, JSONObject().put("day", day).put("actor", actor).put("summary", collaboration), 200)
            world.put("events", shared)
            store(c).edit().putString("world_social", world.toString()).apply()
        }
        social.put("events", events)
        store(c).edit().putString("${k}_social", social.toString()).apply()

        val evaluation = evaluate(raw, obj)
        store(c).edit().putString("${k}_evaluation", evaluation.toString()).apply()
    }

    fun recordWorldEvent(c: Context, actor: String, day: Long, action: String, outcome: String) {
        val k = actorKey(actor)
        val memory = read(c, "${k}_memory")
        val episodes = array(memory, "episodes")
        appendBounded(episodes, JSONObject().put("day", day)
            .put("summary", clean("已验证行动：$action；世界结果：$outcome", 900)), MAX_MEMORY)
        memory.put("episodes", episodes).put("updatedDay", day)
        val social = read(c, "${k}_social")
        val events = array(social, "events")
        val worldSummary = clean("世界引擎确认：$action；结果：$outcome", 700)
        appendBounded(events, JSONObject().put("day", day).put("actor", actor).put("summary", worldSummary), 40)
        social.put("events", events)
        val world = read(c, "world_social")
        val shared = array(world, "events")
        appendBounded(shared, JSONObject().put("day", day).put("actor", actor).put("summary", worldSummary), 200)
        world.put("events", shared)
        store(c).edit()
            .putString("${k}_memory", memory.toString())
            .putString("${k}_social", social.toString())
            .putString("world_social", world.toString())
            .apply()
    }

    fun report(c: Context, actor: String): String {
        val k = actorKey(actor)
        val memory = read(c, "${k}_memory")
        val plan = read(c, "${k}_plan")
        val evaluation = read(c, "${k}_evaluation")
        return "原生智能体模块状态\n记忆经历：${array(memory, "episodes").length()} 条\n稳定事实：${array(memory, "facts").length()} 条\n长期目标：${array(memory, "goals").length()} 条\n行动图节点：${array(plan, "nodes").length()} 个\n最近一次本地评测：${evaluation.optInt("score", -1)} / 100"
    }
}
