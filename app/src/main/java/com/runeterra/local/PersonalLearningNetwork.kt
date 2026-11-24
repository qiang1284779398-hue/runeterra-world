package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.tanh

/**
 * Small on-device policy network.
 * A deterministic shared prior is combined with actor-isolated trainable residual weights.
 * Training uses only outcomes committed by the world engine, never model self-reports alone.
 */
object PersonalLearningNetwork {
    private const val INPUT = 16
    private const val HIDDEN = 12
    // 12 slots: the original 9 tendencies plus take_risk / explore_unknown / provoke.
    // Saved models with 9 rows still load: read() fills missing rows from the seeded fallback.
    private const val ACTIONS = 12
    private const val MAX_REPLAY = 48
    private const val LR = 0.018f
    // Policy learning may join decisions after a handful of verified outcomes;
    // the old 24-update gate left most actors on fixed rules for their whole lives.
    private const val MIN_UPDATES_FOR_CHOICE = 4
    private val actionNames = listOf(
        "stay", "seek_food", "seek_work", "change_job", "ask_help",
        "practice_skill", "socialize", "investigate_world", "discuss_world",
        "take_risk", "explore_unknown", "provoke"
    )
    private val heroActions = listOf(
        "stay", "train", "investigate", "negotiate", "aid",
        "join_event", "decline_event", "investigate_event", "wait",
        "take_risk", "explore_unknown", "provoke"
    )

    private data class Model(
        val w1: Array<FloatArray>, val b1: FloatArray,
        val w2: Array<FloatArray>, val b2: FloatArray,
        val valueW: FloatArray, var valueB: Float = 0f,
        var updates: Int = 0, var baseline: Float = 0f
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("runeterra_policy_learning", Context.MODE_PRIVATE)
    private fun safeKey(actor: String) = actor.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
    private fun seed(actor: String): Long = actor.fold(1125899906842597L) { a, ch -> a * 31 + ch.code }
    private fun initial(actor: String): Model {
        var x = seed(actor)
        fun next(): Float { x = x * 6364136223846793005L + 1442695040888963407L; return (((x ushr 33).toInt() / Int.MAX_VALUE.toFloat()) * 0.12f) }
        return Model(Array(HIDDEN) { FloatArray(INPUT) { next() } }, FloatArray(HIDDEN),
            Array(ACTIONS) { FloatArray(HIDDEN) { next() } }, FloatArray(ACTIONS), FloatArray(HIDDEN) { next() })
    }
    private fun read(c: Context, actor: String): Model {
        val raw = runCatching { JSONObject(prefs(c).getString("${safeKey(actor)}_model", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (!raw.has("w1")) return initial(actor)
        fun matrix(name: String, rows: Int, cols: Int, fallback: Array<FloatArray>): Array<FloatArray> {
            val a = raw.optJSONArray(name) ?: return fallback
            return Array(rows) { i -> val row = a.optJSONArray(i); FloatArray(cols) { j -> row?.optDouble(j, fallback[i][j].toDouble())?.toFloat() ?: fallback[i][j] } }
        }
        fun vector(name: String, size: Int, fallback: FloatArray): FloatArray {
            val a = raw.optJSONArray(name) ?: return fallback
            return FloatArray(size) { i -> a.optDouble(i, fallback[i].toDouble()).toFloat() }
        }
        val base = initial(actor)
        return Model(matrix("w1", HIDDEN, INPUT, base.w1), vector("b1", HIDDEN, base.b1),
            matrix("w2", ACTIONS, HIDDEN, base.w2), vector("b2", ACTIONS, base.b2),
            vector("valueW", HIDDEN, base.valueW), raw.optDouble("valueB", 0.0).toFloat(), raw.optInt("updates", 0), raw.optDouble("baseline", 0.0).toFloat())
    }
    private fun encodeModel(m: Model): String {
        fun matrix(v: Array<FloatArray>) = JSONArray().also { a -> v.forEach { row -> a.put(JSONArray().also { r -> row.forEach { r.put(it.toDouble()) } }) } }
        fun vector(v: FloatArray) = JSONArray().also { a -> v.forEach { a.put(it.toDouble()) } }
        return JSONObject().put("w1", matrix(m.w1)).put("b1", vector(m.b1))
            .put("w2", matrix(m.w2)).put("b2", vector(m.b2))
            .put("valueW", vector(m.valueW)).put("valueB", m.valueB.toDouble())
            .put("updates", m.updates).put("baseline", m.baseline).toString()
    }
    private fun save(c: Context, actor: String, m: Model) {
        prefs(c).edit().putString("${safeKey(actor)}_model", encodeModel(m)).apply()
    }
    private fun finite(m: Model): Boolean =
        m.w1.all { row -> row.all { it.isFinite() && kotlin.math.abs(it) < 8f } } &&
        m.w2.all { row -> row.all { it.isFinite() && kotlin.math.abs(it) < 8f } } &&
        m.valueW.all { it.isFinite() && kotlin.math.abs(it) < 8f } &&
        m.b1.all { it.isFinite() } && m.b2.all { it.isFinite() } && m.valueB.isFinite()
    private fun encode(text: String): FloatArray {
        val f = FloatArray(INPUT)
        val normalized = text.lowercase()
        normalized.forEachIndexed { i, ch ->
            val h = (ch.code * 31 + i * 17) % INPUT
            f[h] += if ((ch.code + i) % 2 == 0) 1f else -1f
        }
        val max = f.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1f) ?: 1f
        for (i in f.indices) f[i] = (f[i] / max).coerceIn(-1f, 1f)
        return f
    }
    private fun forward(m: Model, x: FloatArray): Pair<FloatArray, FloatArray> {
        val h = FloatArray(HIDDEN) { j -> tanh(m.b1[j] + (0 until INPUT).sumOf { (m.w1[j][it] * x[it]).toDouble() }.toFloat()) }
        val logits = FloatArray(ACTIONS) { a -> m.b2[a] + (0 until HIDDEN).sumOf { (m.w2[a][it] * h[it]).toDouble() }.toFloat() }
        val mx = logits.maxOrNull() ?: 0f
        val ex = FloatArray(ACTIONS) { exp((logits[it] - mx).coerceIn(-20f, 20f).toDouble()).toFloat() }
        val sum = ex.sum().coerceAtLeast(0.0001f)
        return h to FloatArray(ACTIONS) { ex[it] / sum }
    }
    private fun actionIndex(action: String, actor: String): Int {
        val normalized = action.trim().lowercase()
        if (actor.startsWith("hero:")) return when (normalized) {
            "stay" -> 0
            "train", "practice_skill" -> 1
            "investigate", "investigate_world" -> 2
            "negotiate", "discuss_world" -> 3
            "aid", "ask_help" -> 4
            "join_event" -> 5
            "decline_event" -> 6
            "investigate_event" -> 7
            "wait" -> 8
            "take_risk" -> 9
            "explore_unknown", "explore" -> 10
            "provoke", "challenge" -> 11
            else -> 0
        }
        return when (normalized) {
            "stay", "wait", "decline_event" -> 0
            "seek_food" -> 1
            "seek_work" -> 2
            "change_job" -> 3
            "ask_help", "aid" -> 4
            "practice_skill", "train" -> 5
            "socialize", "negotiate" -> 6
            "investigate_world", "investigate", "investigate_event" -> 7
            "discuss_world", "join_event" -> 8
            "take_risk" -> 9
            "explore_unknown", "explore" -> 10
            "provoke", "challenge" -> 11
            else -> 0
        }
    }
    private fun sharedPrior(x: FloatArray): FloatArray {
        // Frozen shared base policy: same prior for every actor; personal residuals learn independently.
        return FloatArray(ACTIONS) { a -> (((a + 1) * 0.013f) * x[(a * 3) % INPUT]) }
    }
    private fun guidance(c: Context, actor: String, prompt: String): String {
        val x = encode(prompt)
        val m = read(c, actor)
        val (hidden, probs) = forward(m, x)
        val prior = sharedPrior(x)
        val scores = FloatArray(ACTIONS) { probs[it] + prior[it] }
        val top = scores.indices.sortedByDescending { scores[it] }.take(3)
        prefs(c).edit().putString("${safeKey(actor)}_last_state", JSONArray().also { a -> x.forEach { a.put(it.toDouble()) } }.toString()).apply()
        val labels = if (actor.startsWith("hero:")) heroActions else actionNames
        return "【角色专属策略网络】共享基础策略已提供通用先验；本角色独立网络已训练${m.updates}次。候选行动倾向：${top.joinToString("、") { labels[it] }}。这些只是供本人权衡的建议，不是强制命令；必须结合角色目标、事实和天道规则自行决定。"
    }
    fun prepare(c: Context, actor: String, prompt: String): String = guidance(c, actor, prompt)
    fun recordOutcome(c: Context, actor: String, day: Long, action: String, outcome: String): String {
        val p = prefs(c)
        val key = safeKey(actor)
        val stateJson = runCatching { JSONArray(p.getString("${key}_last_state", "[]") ?: "[]") }.getOrDefault(JSONArray())
        if (stateJson.length() != INPUT) return "未训练：缺少本轮有效状态特征"
        val x = FloatArray(INPUT) { stateJson.optDouble(it, 0.0).toFloat() }
        val text = outcome.lowercase()
        val negative = listOf("失败", "未能", "受伤", "损失", "被拒绝", "没有改变", "不在可接触", "无法", "死亡")
        val positive = listOf("成功", "获得", "建立友谊", "完成", "改善", "学会", "帮助", "保护", "恢复")
        val reward = when {
            negative.any { text.contains(it) } && positive.any { text.contains(it) } -> 0.15f
            negative.any { text.contains(it) } -> -0.45f
            positive.any { text.contains(it) } -> 0.65f
            else -> 0.08f
        }
        val idx = actionIndex(action, actor)
        val replay = runCatching { JSONArray(p.getString("${key}_replay", "[]") ?: "[]") }.getOrDefault(JSONArray())
        // Exploration bonus: the first verified attempts at an action tendency this actor
        // has never tried earn a small novelty bonus, so the policy does not collapse onto
        // the first safe routine that happened to pay off.
        val triedBefore = (0 until replay.length()).any { replay.optJSONObject(it)?.optInt("action", -1) == idx }
        val noveltyBonus = if (!triedBefore && idx != 0) 0.12f else 0f
        val finalReward = (reward + noveltyBonus).coerceIn(-1f, 1f)
        replay.put(JSONObject().put("day", day).put("x", JSONArray().also { a -> x.forEach { a.put(it.toDouble()) } }).put("action", idx).put("reward", finalReward.toDouble()))
        while (replay.length() > MAX_REPLAY) replay.remove(0)
        val model = read(c, actor)
        val previous = encodeModel(model)
        train(model, x, idx, finalReward)
        // Small replay update over recent real outcomes to reduce forgetting.
        if (replay.length() % 4 == 0) {
            val sample = replay.optJSONObject((replay.length() - 1 - 3).coerceAtLeast(0))
            if (sample != null) {
                val sx = sample.optJSONArray("x")
                if (sx != null && sx.length() == INPUT) train(model, FloatArray(INPUT) { sx.optDouble(it).toFloat() }, sample.optInt("action"), sample.optDouble("reward").toFloat() * 0.35f)
            }
        }
        model.updates++
        model.baseline = model.baseline * 0.9f + finalReward * 0.1f
        if (finite(model)) {
            p.edit().putString("${key}_previous_model", previous).apply()
            save(c, actor, model)
        } else {
            p.edit().putString("${key}_model", previous).apply()
        }
        p.edit().putString("${key}_replay", replay.toString())
            .putString("${key}_last_learning", JSONObject().put("day", day).put("action", action).put("reward", finalReward.toDouble()).put("updates", model.updates).toString()).apply()
        return "专属网络已更新：第${model.updates}次；奖励${"%.2f".format(java.util.Locale.US, finalReward)}；经验池${replay.length()}条"
    }
    private fun train(m: Model, x: FloatArray, action: Int, reward: Float) {
        val (h, probs) = forward(m, x)
        val predictedValue = m.valueB + (0 until HIDDEN).sumOf { (m.valueW[it] * h[it]).toDouble() }.toFloat()
        val advantage = (reward - predictedValue).coerceIn(-1f, 1f)
        val valueError = (reward - predictedValue).coerceIn(-1f, 1f)
        m.valueB += LR * valueError
        for (j in 0 until HIDDEN) m.valueW[j] += LR * valueError * h[j]
        val grad = FloatArray(ACTIONS) { a -> ((if (a == action) 1f else 0f) - probs[a]) * advantage }
        val oldW2 = Array(ACTIONS) { m.w2[it].clone() }
        val dh = FloatArray(HIDDEN) { j ->
            (0 until ACTIONS).sumOf { (grad[it] * oldW2[it][j]).toDouble() }.toFloat() * (1f - h[j] * h[j])
        }
        for (a in 0 until ACTIONS) {
            m.b2[a] += LR * grad[a]
            for (j in 0 until HIDDEN) m.w2[a][j] += LR * grad[a] * h[j]
        }
        for (j in 0 until HIDDEN) {
            m.b1[j] += LR * dh[j] * 0.2f
            for (i in 0 until INPUT) m.w1[j][i] += LR * dh[j] * x[i] * 0.2f
        }
    }
    /** Returns a learned action only after enough actor-specific verified feedback exists. */
    fun autonomousChoice(c: Context, actor: String, prompt: String): String? {
        val model = read(c, actor)
        if (model.updates < MIN_UPDATES_FOR_CHOICE) return null
        val (_, probabilities) = forward(model, encode(prompt))
        val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
        val best = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.let { probabilities[it] } ?: 0f
        if (probabilities[best] < 0.14f || probabilities[best] - second < 0.018f) return null
        val labels = if (actor.startsWith("hero:")) heroActions else actionNames
        return labels.getOrNull(best)
    }

    fun report(c: Context, actor: String): String {
        val key = safeKey(actor)
        val m = read(c, actor)
        val replay = runCatching { JSONArray(prefs(c).getString("${key}_replay", "[]") ?: "[]") }.getOrDefault(JSONArray())
        val last = runCatching { JSONObject(prefs(c).getString("${key}_last_learning", "{}") ?: "{}") }.getOrDefault(JSONObject())
        return "策略网络：共享基础先验 + 角色专属可训练MLP\n价值网络：已启用（预测行动长期回报）\n角色参数隔离：是\n独立训练次数：${m.updates}\n离线自主决策：${if (m.updates >= MIN_UPDATES_FOR_CHOICE) "达到候选门槛，仍需通过上下文置信度检查" else "继续积累真实行动反馈（至少${MIN_UPDATES_FOR_CHOICE}次更新）"}\n经验回放：${replay.length()}条\n最近奖励：${last.optDouble("reward", 0.0)}\n参数版本：${m.updates}\n参数安全检查：有限值与权重范围校验"
    }
}
