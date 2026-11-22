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
        val lore = Button(this).apply { text = "符文之地 / 官方世界观设定"; setOnClickListener { showRegionalLoreDirectory() } }
        val heroes = Button(this).apply { text = "英雄 / 半神 / 超自然存在档案"; setOnClickListener { showHeroDirectory() } }
        val lineages = Button(this).apply { text = "生命体系 / 种族与族群档案"; setOnClickListener { showLineageDirectory() } }
        val ecology = Button(this).apply { text = "自然生态 / 气候 / 水源 / 植被档案"; setOnClickListener { showEcologyDirectory() } }
        val heroRelations = Button(this).apply { text = "英雄人物 / 关系网络档案"; setOnClickListener { showHeroRelationshipDirectory() } }
        val settlements = Button(this).apply { text = "城市 / 村庄 / 聚落档案"; setOnClickListener { showSettlementDirectory() } }
        val organizations = Button(this).apply { text = "组织 / 家族 / 教派 / 军团档案"; setOnClickListener { showOrganizationDirectory() } }
        val caravans = Button(this).apply { text = "商队 / 贸易 / 地区市场"; setOnClickListener { showCaravanDirectory() } }
        val households = Button(this).apply { text = "家庭 / 家庭成员 / 共同资产档案"; setOnClickListener { showHouseholdDirectory() } }
        val biographies = Button(this).apply {
            text = "居民档案 / 人生传记"
            setOnClickListener { showResidentDirectory() }
        }
        log = TextView(this).apply { textSize = 14f; setPadding(0, 20, 0, 0) }
        listOf(title, status, start, pause, step, lore, heroes, lineages, ecology, heroRelations, factions, settlements, organizations, caravans, households, biographies, reset, log).forEach {
            root.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(root)
        refresh()
    }

    private fun showHeroDirectory() {
        val entries=WorldEngine.heroDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 英雄与特殊存在")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("人物与特殊存在档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showEcologyDirectory() {
        val entries=WorldEngine.ecologyDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 自然生态与环境")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("区域生态档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showLineageDirectory() {
        val entries=WorldEngine.lineageDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 生命与族群体系")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("生命与族群档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showHeroRelationshipDirectory() {
        val entries=WorldEngine.heroRelationshipDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 英雄人物关系网络")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("人物关系档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showRegionalLoreDirectory() {
        val entries=WorldEngine.regionalLoreDirectory()
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 世界观设定锚点")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("地区与超自然设定")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showHouseholdDirectory() {
        val entries=WorldEngine.householdDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 家庭与共同生活")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("家庭档案")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun showCaravanDirectory() {
        val entries=WorldEngine.caravanDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 商队与地区市场")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("商贸与市场")
                    .setMessage(entries[which])
                    .setPositiveButton("返回",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
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
