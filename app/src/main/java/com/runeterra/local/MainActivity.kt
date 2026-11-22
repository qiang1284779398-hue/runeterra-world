package com.runeterra.local

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private var pendingBiographyExportId: Int = -1
    private val requestExportBiography = 4107

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
        val timeline = Button(this).apply { text = "历史时间线 / 时代档案"; setOnClickListener { showTimelineDirectory() } }
        val heavenly = Button(this).apply { text = "天道系统 / 观察与干预"; setOnClickListener { showHeavenlySystem() } }
        val heroes = Button(this).apply { text = "英雄 / 半神 / 超自然存在档案"; setOnClickListener { showHeroDirectory() } }
        val lineages = Button(this).apply { text = "生命体系 / 种族与族群档案"; setOnClickListener { showLineageDirectory() } }
        val ecology = Button(this).apply { text = "自然生态 / 气候 / 水源 / 植被档案"; setOnClickListener { showEcologyDirectory() } }
        val fauna = Button(this).apply { text = "野生生物 / 种群 / 生态群落档案"; setOnClickListener { showFaunaDirectory() } }
        val heroRelations = Button(this).apply { text = "英雄人物 / 关系网络档案"; setOnClickListener { showHeroRelationshipDirectory() } }
        val settlements = Button(this).apply { text = "城市 / 村庄 / 聚落档案"; setOnClickListener { showSettlementDirectory() } }
        val organizations = Button(this).apply { text = "组织 / 家族 / 教派 / 军团档案"; setOnClickListener { showOrganizationDirectory() } }
        val caravans = Button(this).apply { text = "商队 / 贸易 / 地区市场"; setOnClickListener { showCaravanDirectory() } }
        val households = Button(this).apply { text = "家庭 / 家庭成员 / 共同资产档案"; setOnClickListener { showHouseholdDirectory() } }
        val localModel = Button(this).apply { text = "导入并加载 8B 本地 AI 模型（GGUF）"; setOnClickListener { pickLocalModel() } }
        val unloadAI = Button(this).apply {
            text = "卸载本地模型（释放运行内存）"
            setOnClickListener {
                ResidentAI.unload()
                Toast.makeText(this@MainActivity,"已请求卸载模型；推理结束后释放内存。",Toast.LENGTH_LONG).show()
                refresh()
            }
        }
        val testAI = Button(this).apply { text = "测试本地 AI 推理"; setOnClickListener { testLocalAI() } }
        val biographies = Button(this).apply {
            text = "居民传记导出 / 查看居民人生（点击进入）"
            setOnClickListener { showResidentDirectory() }
        }
        log = TextView(this).apply { textSize = 14f; setPadding(0, 20, 0, 0) }
        listOf(title, status, localModel, unloadAI, testAI, biographies, start, pause, step, heavenly, timeline, lore, heroes, lineages, ecology, fauna, heroRelations, factions, settlements, organizations, caravans, households, reset, log).forEach {
            root.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(root)
        refresh()
    }

    private fun pickLocalModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 8101)
    }

    private fun importLocalModel(uri: Uri) {
        Toast.makeText(this,"正在复制模型到本地存储，请保持应用打开…",Toast.LENGTH_LONG).show()
        Thread {
            try {
                val modelsDir=File(filesDir,"models").apply { mkdirs() }
                val modelFile=File(modelsDir,"resident-model.gguf")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output, 1024*1024) }
                } ?: throw IllegalStateException("无法读取所选文件")
                ResidentAI.load(this,modelFile.absolutePath) { error ->
                    runOnUiThread {
                        Toast.makeText(this, if(error==null) "本地模型加载成功" else "模型加载失败：$error",Toast.LENGTH_LONG).show()
                    }
                }
            } catch(e:Throwable) {
                runOnUiThread { Toast.makeText(this,"模型导入失败：${e.message}",Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun testLocalAI() {
        if(!ResidentAI.isLoaded()) {
            AlertDialog.Builder(this).setTitle("本地 AI 尚未加载")
                .setMessage("请先选择 GGUF 模型文件并等待加载完成。")
                .setPositiveButton("知道了",null).show()
            return
        }
        val input=EditText(this).apply { hint="输入测试问题" }
        AlertDialog.Builder(this).setTitle("本地模型推理测试").setView(input)
            .setPositiveButton("发送") { _,_ ->
                val question=input.text.toString().trim()
                if(question.isNotEmpty()) {
                    Toast.makeText(this,"正在本机生成回答…",Toast.LENGTH_SHORT).show()
                    ResidentAI.ask(question) { answer ->
                        runOnUiThread { AlertDialog.Builder(this).setTitle("本地模型回答").setMessage(answer).setPositiveButton("关闭",null).show() }
                    }
                }
            }.setNegativeButton("取消",null).show()
    }


    private fun showHeavenlySystem() {
        val statusLines=WorldEngine.heavenlyStatus(this)
        val choices=arrayOf("天道直接下令（全权限自然语言）","向全世界发布神谕","向指定居民传达","向指定英雄传达","查看天道干预记录","查看天道系统状态")
        AlertDialog.Builder(this)
            .setTitle("天道系统 · 外部观察与干预")
            .setItems(choices) { _, which ->
                when(which) {
                    0 -> composeDivineCommand()
                    1 -> chooseHeavenlyMode("全世界",-1,"")
                    2 -> {
                        val residents=WorldEngine.residentDirectory(this).filter { it.contains("｜在世") }
                        AlertDialog.Builder(this).setTitle("选择接收神谕的居民")
                            .setItems(residents.toTypedArray()) { _, index ->
                                val id=residents[index].substringBefore("｜").toIntOrNull()
                                val name=residents[index].substringAfter("｜").substringBefore("｜")
                                if(id!=null) chooseHeavenlyMode("指定居民",id,name)
                            }.setNegativeButton("取消",null).show()
                    }
                    3 -> {
                        val heroes=WorldEngine.heroDirectory(this).mapNotNull { row ->
                            row.substringAfter("【","").substringBefore("】").takeIf { it.isNotBlank() }
                        }.distinct()
                        AlertDialog.Builder(this).setTitle("选择接收神谕的英雄")
                            .setItems(heroes.toTypedArray()) { _, index -> chooseHeavenlyMode("指定英雄",-1,heroes[index]) }
                            .setNegativeButton("取消",null).show()
                    }
                    4 -> {
                        val history=WorldEngine.heavenlyInterventionHistory(this)
                        AlertDialog.Builder(this).setTitle("天道干预历史")
                            .setMessage(history.joinToString("\n\n").ifBlank { "尚无天道干预记录。" })
                            .setPositiveButton("关闭",null).show()
                    }
                    5 -> AlertDialog.Builder(this).setTitle("天道系统状态")
                        .setMessage(statusLines.joinToString("\n\n"))
                        .setPositiveButton("关闭",null).show()
                }
            }
            .setNegativeButton("关闭",null)
            .show()
    }


    private fun composeDivineCommand() {
        val input=EditText(this).apply {
            hint="例如：让盖伦变成女性，但保留他的记忆、人格和能力。\n抹杀某某。\n把拉克丝瞬间传送到艾欧尼亚。\n创造一名生活在皮尔特沃夫的居民。"
            minLines=5
            maxLines=10
            setSingleLine(false)
            setPadding(28,20,28,20)
        }
        AlertDialog.Builder(this)
            .setTitle("天道 · 全权限直接指令")
            .setMessage("用自然语言描述你要改变的世界。8B负责理解并生成操作，世界引擎负责验证和执行。没有对应实体模拟器的复杂现象会明确标注为事件/规则记录，不会假装已经完成全部物理后果。")
            .setView(input)
            .setPositiveButton("解析并执行") { _, _ ->
                val command=input.text.toString().trim()
                if(command.isBlank()) return@setPositiveButton
                Toast.makeText(this,"8B 正在解析天道指令，请稍候…",Toast.LENGTH_LONG).show()
                WorldEngine.interpretHeavenlyCommand(this,command) { result ->
                    runOnUiThread {
                        AlertDialog.Builder(this)
                            .setTitle("天道指令执行报告")
                            .setMessage(result)
                            .setPositiveButton("确定") { _, _ -> refresh() }
                            .show()
                    }
                }
            }
            .setNegativeButton("取消",null)
            .show()
    }

    private fun chooseHeavenlyMode(scope:String,targetId:Int,targetName:String) {
        val modes=arrayOf("神谕（直接传达文字）","梦境启示（以梦境记忆传达）","显现（明确感知到外部存在）")
        AlertDialog.Builder(this).setTitle("选择天道显现方式")
            .setItems(modes) { _, which ->
                val mode=when(which) { 1 -> "梦境启示"; 2 -> "显现"; else -> "神谕" }
                composeHeavenlyMessage(scope,targetId,targetName,mode)
            }.setNegativeButton("取消",null).show()
    }

    private fun composeHeavenlyMessage(scope:String,targetId:Int,targetName:String,mode:String) {
        val input=EditText(this).apply {
            hint="输入你要传达的信息，例如：你们所处的世界并非唯一真实的世界……"
            minLines=4
            maxLines=8
            setSingleLine(false)
            setPadding(28,20,28,20)
        }
        AlertDialog.Builder(this).setTitle("天道干预 · "+mode)
            .setMessage(if(scope=="全世界") "信息将写入所有在世居民与已登记英雄的个人记忆。" else "接收对象："+targetName)
            .setView(input)
            .setPositiveButton("传达") { _, _ ->
                val result=WorldEngine.heavenlyIntervene(this,scope,targetId,targetName,mode,input.text.toString())
                AlertDialog.Builder(this).setTitle("天道干预结果").setMessage(result)
                    .setPositiveButton("确定",null).show()
                refresh()
            }
            .setNegativeButton("取消",null)
            .show()
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

    private fun showFaunaDirectory() {
        val entries=WorldEngine.faunaDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 野生生物与生态群落")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("野生生物群落档案")
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

    private fun showTimelineDirectory() {
        val entries=WorldEngine.timelineDirectory(this)
        AlertDialog.Builder(this)
            .setTitle("符文之地 · 历史时间线")
            .setItems(entries.toTypedArray()) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("时代与历史锚点")
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
                val residentName=entries[which].substringAfter("｜").substringBefore("｜")
                AlertDialog.Builder(this)
                    .setTitle("居民 ${id} 号 · ${residentName}")
                    .setItems(arrayOf("查看人生传记", "导出传记 TXT")) { _, action ->
                        if(action==0) {
                            AlertDialog.Builder(this)
                                .setTitle("人生传记档案")
                                .setMessage(WorldEngine.residentHistory(this,id))
                                .setPositiveButton("返回",null)
                                .show()
                        } else {
                            beginBiographyExport(id)
                        }
                    }
                    .setNegativeButton("取消",null)
                    .show()
            }
            .setNegativeButton("关闭",null)
            .show()
    }

    private fun beginBiographyExport(id:Int) {
        pendingBiographyExportId=id
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/plain"
            putExtra(Intent.EXTRA_TITLE,"居民${id}号-人生传记.txt")
        }
        try {
            startActivityForResult(intent,requestExportBiography)
        } catch (e:Exception) {
            pendingBiographyExportId=-1
            Toast.makeText(this,"无法打开系统文件保存窗口：${e.message ?: "未知错误"}",Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the current Activity implementation")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==8101) {
            if(resultCode==RESULT_OK && data?.data!=null) importLocalModel(data.data!!)
            return
        }
        if(requestCode!=requestExportBiography) return
        val id=pendingBiographyExportId
        pendingBiographyExportId=-1
        if(resultCode!=RESULT_OK || data?.data==null) {
            Toast.makeText(this,"已取消传记导出。",Toast.LENGTH_SHORT).show()
            return
        }
        val biography=WorldEngine.residentBiographyExport(this,id)
        if(biography==null) {
            Toast.makeText(this,"居民档案不存在，无法导出。",Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.openOutputStream(data.data!!,"wt")?.use { stream ->
                stream.write(biography.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: throw IllegalStateException("无法写入目标文件")
            Toast.makeText(this,"传记已导出为 TXT 文件。",Toast.LENGTH_LONG).show()
        } catch(e:Exception) {
            Toast.makeText(this,"导出失败：${e.message ?: "写入文件时发生错误"}",Toast.LENGTH_LONG).show()
        }
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
        status.text = "历史时代：${WorldEngine.timelineStatus(this)}\n世界时间：第 ${s.day} 日\n居民样本：${s.residents} 人\n运行状态：${if (s.running) "运行中" else "已暂停"}\n模拟速度：现实 1 秒约推进 200 个世界日\n本地 AI：${if (ResidentAI.isLoaded()) "8B 模型已加载" else if (ResidentAI.isLoading()) "模型加载中" else "尚未加载"}"
        log.text = "最近世界记录\n" + (s.events.takeLast(12).asReversed().joinToString("\n") { "• $it" }.ifBlank { "尚无事件。启动世界后将逐步产生记录。" })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
}
