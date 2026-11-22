package com.runeterra.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WorldSnapshot(
    val day: Long,
    val residents: Int,
    val events: List<String>,
    val running: Boolean
)

object WorldEngine {
    private const val PREFS = "runeterra_world"
    private const val KEY_DAY = "day"
    private const val KEY_EVENTS = "events"
    private const val KEY_RUNNING = "running"

    fun load(context: Context): WorldSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(KEY_EVENTS, "[]") ?: "[]"
        val events = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
        return WorldSnapshot(p.getLong(KEY_DAY, 1L), 240, events, p.getBoolean(KEY_RUNNING, false))
    }

    fun advance(context: Context, days: Int = 1): WorldSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = load(context)
        val nextDay = old.day + days.coerceAtLeast(1)
        val events = old.events.toMutableList()
        if (nextDay % 30L == 0L) events.add("第 $nextDay 日：月度经济结算完成，居民继续工作、交易与维系关系。")
        if (nextDay % 90L == 0L) events.add("第 $nextDay 日：季节更替，地区资源与生活条件进入新周期。")
        if (nextDay % 360L == 0L) events.add("第 $nextDay 日：新年到来，居民年龄与人生阶段更新。")
        if (events.isEmpty() || nextDay % 7L == 0L) events.add("第 $nextDay 日：符文之地的居民在各自地区追逐目标。")
        while (events.size > 300) events.removeAt(0)
        val json = JSONArray()
        events.forEach { json.put(it) }
        p.edit().putLong(KEY_DAY, nextDay).putString(KEY_EVENTS, json.toString()).apply()
        return WorldSnapshot(nextDay, 240, events, p.getBoolean(KEY_RUNNING, false))
    }

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, running).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putLong(KEY_DAY, 1L).apply()
    }
}
