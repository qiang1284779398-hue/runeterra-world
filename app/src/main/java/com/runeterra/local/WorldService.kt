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

class WorldService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private var ticks = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!WorldEngine.load(this@WorldService).running) return
            // 目标模拟速度：现实每秒推进 200 个世界日。
            val state = WorldEngine.advance(this@WorldService, DAYS_PER_SECOND)
            ticks++
            if (ticks % NOTIFICATION_INTERVAL_TICKS == 0L) {
                updateNotification(state.day)
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
        startForeground(NOTIFICATION_ID, notification(WorldEngine.load(this).day))
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

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "世界运行状态", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(day: Long): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("符文之地正在高速运行")
            .setContentText("世界第 $day 日 · 现实 1 秒约推进 200 天")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(day: Long) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(day))
    }

    companion object {
        const val ACTION_STOP = "com.runeterra.local.STOP"
        private const val CHANNEL_ID = "runeterra_world"
        private const val NOTIFICATION_ID = 701
        private const val DAYS_PER_SECOND = 200
        private const val TICK_INTERVAL_MS = 1_000L
        private const val NOTIFICATION_INTERVAL_TICKS = 5L
    }
}
