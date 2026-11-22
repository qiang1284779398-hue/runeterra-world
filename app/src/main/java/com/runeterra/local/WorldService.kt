package com.runeterra.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class WorldService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val state = WorldEngine.advance(this@WorldService)
            updateNotification(state.day)
            handler.postDelayed(this, 5_000L) // 初始节奏：现实 5 秒推进 1 个世界日
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handler.removeCallbacks(tick)
                WorldEngine.setRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, notification(WorldEngine.load(this).day))
        WorldEngine.setRunning(this, true)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
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
        return builder.setContentTitle("符文之地正在运行")
            .setContentText("世界第 $day 日 · 本机模拟中")
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
    }
}
