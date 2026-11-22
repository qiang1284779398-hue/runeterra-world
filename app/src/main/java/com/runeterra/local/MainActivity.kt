package com.runeterra.local

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 24)
            gravity = Gravity.TOP
        }
        val title = TextView(this).apply { text = "符文之地 · 世界模拟器"; textSize = 25f }
        status = TextView(this).apply { textSize = 17f; setPadding(0, 24, 0, 20) }
        val start = Button(this).apply { text = "开始世界运行"; setOnClickListener { startWorld() } }
        val pause = Button(this).apply { text = "暂停世界"; setOnClickListener { stopWorld() } }
        val step = Button(this).apply {
            text = "单独推进 1 天"
            setOnClickListener { WorldEngine.advance(this@MainActivity); refresh() }
        }
        val reset = Button(this).apply {
            text = "重置世界"
            setOnClickListener { WorldEngine.reset(this@MainActivity); refresh() }
        }
        val factions = Button(this).apply { text = "各地区势力 / 政体档案"; setOnClickListener { showFactionDirectory() } }
        val settlements = Button(this).apply { text = "城市 / 村庄 / 聚落档案"; setOnClickListener { showSettlementDirectory() } }
        val organizations = Button(this).apply { text = "组织 / 家族 / 教派 / 军团档案"; setOnClickListener { showOrganizationDirectory() } }
        val biographies = Button(this).apply {
            text = "居民档案 / 人生传记"
            setOnClickListener { showResidentDirectory() }
        }
        log = TextView(this).apply { textSize = 14f; setPadding(0, 20, 0, 0) }
        listOf(title, status, start, pause, step, factions, settlements, organizations, biographies, reset, log).forEach {
            root.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(root)
        refresh()
    }

    private fun showOrganizationDirectory() {
        val entries=WorldEngine.organizationDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 组织与社会势力")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("组织档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showSettlementDirectory() {
        val entries=WorldEngine.settlementDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 城市与聚落")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("聚落档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showFactionDirectory() {
        val entries=WorldEngine.factionDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 势力与政体")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("势力档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showResidentDirectory() {
        val entries=WorldEngine.residentDirectory(this)
        if(entries.isEmpty()) {
            AlertDialog.Builder(this).setMessage("当前没有居民档案。").setPositiveButton("确定",null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("居民档案（点击查看人生）")
            .setItems(entries.toTypedArray()) { _, which ->
                val id=entries[which].substringBefore("｜").toIntOrNull() ?: return@setItems
                AlertDialog.Builder(this)
                    .setTitle("人生传记档案")
                    .setMessage(WorldEngine.residentHistory(this,id))
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun startWorld() {
        val intent = Intent(this, WorldService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        refresh()
    }

    private fun stopWorld() {
        startService(Intent(this, WorldService::class.java).setAction(WorldService.ACTION_STOP))
        refresh()
    }

    private fun refresh() {
        val s = WorldEngine.load(this)
        status.text = "世界时间：第 ${s.day} 日\n居民样本：${s.residents} 人\n运行状态：${if (s.running) "运行中" else "已暂停"}\n模拟速度：现实 5 秒 = 世界 1 天"
        log.text = "最近世界记录\n" + (s.events.takeLast(12).asReversed().joinToString("\n") { "• $it" }.ifBlank { "尚无事件。启动世界后将逐步产生记录。" })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
}
