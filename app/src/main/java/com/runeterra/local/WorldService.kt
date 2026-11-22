package com.runeterra.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

class WorldService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private var ticks = 0L
    private var slowModeUntil = 0L

    private val tick = object : Runnable {
        override fun run() {
            val snapshot = WorldEngine.load(this@WorldService)
            if (!snapshot.running) return

            val now = SystemClock.elapsedRealtime()
            val clockPrefs = getSharedPreferences(CLOCK_PREFS, MODE_PRIVATE)
            val latestEvent = snapshot.events.lastOrNull().orEmpty()
            val previousEvent = clockPrefs.getString(KEY_LAST_EVENT, "").orEmpty()
            if (latestEvent.isNotBlank() && latestEvent != previousEvent) {
                if (URGENT_EVENT_WORDS.any { latestEvent.contains(it) }) {
                    slowModeUntil = now + URGENT_SLOW_MODE_MS
                }
                clockPrefs.edit().putString(KEY_LAST_EVENT, latestEvent).apply()
            }

            // Base speed: one simulated hour per real second. During a newly detected
            // crisis, the clock slows to one simulated hour per five real seconds.
            val minutesToAdvance = if (now < slowModeUntil) SLOW_MINUTES_PER_TICK else MINUTES_PER_TICK
            var accumulatedMinutes = clockPrefs.getInt(KEY_ACCUMULATED_MINUTES, 0) + minutesToAdvance
            while (accumulatedMinutes >= MINUTES_PER_DAY) {
                WorldEngine.advance(this@WorldService, 1)
                accumulatedMinutes -= MINUTES_PER_DAY
            }
            clockPrefs.edit().putInt(KEY_ACCUMULATED_MINUTES, accumulatedMinutes).apply()

            // One shared local model: dispatch exactly one queued cognition turn whenever
            // the previous inference has completed. Busy turns do not consume the next slot.
            WorldEngine.requestNextLocalThinking(this@WorldService)
            ticks++

            if (ticks % NOTIFICATION_INTERVAL_TICKS == 0L) {
                updateNotification(WorldEngine.load(this@WorldService).day, accumulatedMinutes, now < slowModeUntil)
            }
            worker.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        workerThread = HandlerThread("RuneterraWorldSimulation").apply { start() }
        worker = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                worker.removeCallbacks(tick)
                WorldEngine.setRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, notification(WorldEngine.load(this).day, currentMinutes(), false))
        WorldEngine.setRunning(this, true)
        worker.removeCallbacks(tick)
        worker.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        if (::worker.isInitialized) worker.removeCallbacksAndMessages(null)
        if (::workerThread.isInitialized) workerThread.quitSafely()
        WorldEngine.setRunning(this, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun currentMinutes(): Int =
        getSharedPreferences(CLOCK_PREFS, MODE_PRIVATE).getInt(KEY_ACCUMULATED_MINUTES, 0)

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "世界运行状态", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(day: Long, minutes: Int, slow: Boolean): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val hour = (minutes / 60).coerceIn(0, 23)
        val minute = (minutes % 60).coerceIn(0, 59)
        val speed = if (slow) "重大事件减速中" else "现实 1 秒约推进 1 小时"
        return builder.setContentTitle("符文之地正在运行")
            .setContentText("第 ${day} 日 ${"%02d".format(hour)}:${"%02d".format(minute)} · ${speed}")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(day: Long, minutes: Int, slow: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(day, minutes, slow))
    }

    companion object {
        const val ACTION_STOP = "com.runeterra.local.STOP"
        private const val CHANNEL_ID = "runeterra_world"
        private const val NOTIFICATION_ID = 701
        private const val TICK_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_INTERVAL_TICKS = 5L
        private const val MINUTES_PER_TICK = 60
        private const val SLOW_MINUTES_PER_TICK = 12
        private const val MINUTES_PER_DAY = 24 * 60
        private const val URGENT_SLOW_MODE_MS = 60_000L
        private const val CLOCK_PREFS = "runeterra_simulation_clock"
        private const val KEY_ACCUMULATED_MINUTES = "accumulated_minutes"
        private const val KEY_LAST_EVENT = "last_seen_event"
        private val URGENT_EVENT_WORDS = listOf("战争", "入侵", "战线", "袭击", "刺杀", "死亡", "去世", "政变", "叛乱", "围城", "战斗", "灾害", "失踪", "黑雾", "虚空")
    }
}
