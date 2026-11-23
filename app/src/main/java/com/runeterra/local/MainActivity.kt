package com.runeterra.local

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Toast
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.os.Handler
import android.os.Looper
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
        val aiDiagnostics = Button(this).apply { text = "查看 / 复制 AI 推理诊断日志"; setOnClickListener { showAiDiagnostics() } }
        val deepSeek = Button(this).apply {
            text = "DeepSeek 云端 AI / API 设置与连接测试"
            setOnClickListener { startActivity(Intent(this@MainActivity, DeepSeekActivity::class.java)) }
        }
        val zhipu = Button(this).apply {
            text = "智谱 GLM 云端 AI / API 设置与连接测试"
            setOnClickListener { startActivity(Intent(this@MainActivity, ZhipuActivity::class.java)) }
        }
        val worldAiProvider = Button(this).apply {
            val providerPrefs = getSharedPreferences("zhipu_secure", MODE_PRIVATE)
            fun refreshProviderLabel() {
                text = if (providerPrefs.getString("world_ai_provider", "zhipu") == "chatgpt")
                    "世界人物 AI 方案：ChatGPT 网页会话"
                else "世界人物 AI 方案：智谱 GLM"
            }
            refreshProviderLabel()
            setOnClickListener {
                val current = if (providerPrefs.getString("world_ai_provider", "zhipu") == "chatgpt") 1 else 0
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("选择世界人物 AI 推理方案")
                    .setSingleChoiceItems(arrayOf("智谱 GLM（API，后台直连）", "ChatGPT（网页会话，开始世界时自动连接）"), current) { dialog, which ->
                        providerPrefs.edit().putString("world_ai_provider", if (which == 1) "chatgpt" else "zhipu")
                            .putBoolean("world_ai_provider_selected", true).apply()
                        refreshProviderLabel()
                        Toast.makeText(this@MainActivity, if (which == 1)
                            "已切换 ChatGPT。开始世界时会自动打开网页桥接；首次需要登录一次。" else "已切换智谱 GLM。", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        val gptWebTest = Button(this).apply {
            text = "ChatGPT 网页 AI 登录 / 世界推理桥接"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ChatGPTWebActivity::class.java))
            }
        }
        val gptWebLog = Button(this).apply {
            text = "查看 GPT 网页桥接日志"
            setOnClickListener {
                val report = ChatGPTAccessibilityService.readLog(this@MainActivity)
                val view = TextView(this@MainActivity).apply {
                    text = report; textSize = 13f; setTextIsSelectable(true); setPadding(24, 18, 24, 18)
                }
                val scroll = ScrollView(this@MainActivity).apply { addView(view) }
                AlertDialog.Builder(this@MainActivity).setTitle("GPT 网页桥接日志").setView(scroll)
                    .setNeutralButton("复制日志") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("GPT网页桥接日志", report))
                        Toast.makeText(this@MainActivity, "日志已复制", Toast.LENGTH_SHORT).show()
                    }.setPositiveButton("关闭", null).show()
            }
        }
        val observation = Button(this).apply { text = "人物思考 / 行动实时观察窗口"; setOnClickListener { showObservationConsole() } }
        val biographies = Button(this).apply {
            text = "居民传记导出 / 查看居民人生（点击进入）"
            setOnClickListener { showResidentDirectory() }
        }
        log = TextView(this).apply { textSize = 14f; setPadding(0, 20, 0, 0) }
        // Runeterra-inspired dark fantasy UI: midnight blue, hextech teal and Demacian gold.
        val night = Color.rgb(10, 17, 31)
        val panel = Color.rgb(19, 31, 49)
        val gold = Color.rgb(220, 183, 105)
        val pale = Color.rgb(229, 235, 241)
        val teal = Color.rgb(74, 190, 180)
        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
        fun pill(fill: Int, stroke: Int = Color.TRANSPARENT) = GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(13).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
        root.setPadding(dp(18), dp(24), dp(18), dp(28))
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(14, 28, 48), night, Color.rgb(8, 13, 24)))
        title.apply {
            text = "符文之地"; textSize = 30f; setTextColor(gold)
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(2))
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        }
        status.apply {
            setTextColor(pale); textSize = 14f
            background = pill(panel, Color.rgb(55, 91, 113))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val allButtons = listOf(start, pause, step, heavenly, observation, biographies,
            worldAiProvider, gptWebTest, gptWebLog, localModel, unloadAI, testAI, aiDiagnostics,
            deepSeek, zhipu, timeline, lore, heroes, lineages, ecology, fauna, heroRelations,
            factions, settlements, organizations, caravans, households, reset)
        allButtons.forEach { button ->
            button.isAllCaps = false; button.textSize = 14f
            button.setTextColor(if (button === start) Color.rgb(18, 29, 43) else pale)
            button.background = pill(if (button === start) gold else panel,
                if (button === start) Color.rgb(245, 218, 157) else Color.rgb(52, 79, 103))
            button.minHeight = dp(52); button.setPadding(dp(16), dp(10), dp(16), dp(10))
            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }
        fun section(label: String) {
            val heading = TextView(this).apply {
                text = label; textSize = 12f; setTextColor(teal); letterSpacing = 0.12f
                setPadding(dp(4), dp(22), dp(4), dp(9))
            }
            root.addView(heading, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun addViews(vararg views: View) {
            views.forEach { root.addView(it, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        }
        addViews(title)
        val subtitle = TextView(this).apply {
            text = "RUNETERRA  ·  LIVING WORLD SIMULATION"
            textSize = 10f; letterSpacing = 0.18f; setTextColor(Color.rgb(123, 155, 174))
            setPadding(dp(5), 0, 0, dp(18))
        }
        addViews(subtitle, status)
        section("世界控制  /  WORLD CONTROL")
        addViews(start, pause, step, heavenly)
        section("观察与人物  /  OBSERVATORY")
        addViews(observation, biographies)
        section("世界之心  /  AI CORE")
        addViews(worldAiProvider, gptWebTest, gptWebLog, zhipu, deepSeek)
        section("符文档案  /  LORE & ATLAS")
        addViews(timeline, lore, heroes, heroRelations, lineages, factions, settlements,
            organizations, caravans, households, ecology, fauna)
        section("本地引擎  /  LOCAL ENGINE")
        addViews(localModel, unloadAI, testAI, aiDiagnostics)
        section("维护  /  MAINTENANCE")
        addViews(reset)
        log.setTextColor(Color.rgb(143, 164, 181)); log.textSize = 12f
        log.setPadding(dp(4), dp(18), dp(4), dp(20))
        addViews(log)
        window.statusBarColor = Color.rgb(9, 17, 30)
        window.navigationBarColor = Color.rgb(8, 13, 24)
        val mainScroll = ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(mainScroll)
        refresh()

    }

    private fun showObservationConsole() {
        var kind="英雄"
        var selectedKey=""
        val handler=Handler(Looper.getMainLooper())
        val panel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(24,12,24,8)
        }
        val hint=TextView(this).apply {
            text=WorldEngine.localAiStatus(this@MainActivity)+"\n\n切换英雄、居民、世界事件或人物互动。状态每 2 秒刷新；正文不会自动重绘，点击“手动刷新当前记录”查看最新内容。"
            textSize=13f
            setPadding(0,0,0,12)
        }
        val switches=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val heroesButton=Button(this).apply { text="英雄"; isAllCaps=false }
        val residentsButton=Button(this).apply { text="居民"; isAllCaps=false }
        switches.addView(heroesButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        switches.addView(residentsButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val eventSwitches=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val worldEventsButton=Button(this).apply { text="世界事件"; isAllCaps=false }
        val interactionsButton=Button(this).apply { text="人物互动"; isAllCaps=false }
        eventSwitches.addView(worldEventsButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        eventSwitches.addView(interactionsButton,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        val spinner=Spinner(this)
        val refreshButton=Button(this).apply {
            text="手动刷新当前记录"
            isAllCaps=false
        }
        val output=TextView(this).apply {
            textSize=14f
            setTextIsSelectable(true)
            setPadding(8,12,8,20)
        }
        val scroll=ScrollView(this).apply {
            addView(output,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        panel.addView(hint)
        panel.addView(switches)
        panel.addView(eventSwitches)
        panel.addView(spinner,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        panel.addView(refreshButton,ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        panel.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val dialog=AlertDialog.Builder(this)
            .setTitle("符文之地 · 人物与世界观察台")
            .setView(panel)
            .setPositiveButton("关闭",null)
            .create()

        fun showSelected() {
            val nextText = if(selectedKey.isBlank()) {
                if(kind=="世界事件" || kind=="人物互动") "当前筛选暂无已保存记录。" else "当前类别没有可观察的人物。"
            } else {
                WorldEngine.observationText(this,kind,selectedKey)
            }
            // 内容更新时始终保留用户当前阅读位置；绝不自动跳到顶部或底部。
            val previousY = scroll.scrollY
            output.text = nextText
            val observer = output.viewTreeObserver
            if(observer.isAlive) {
                val restorePosition = object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if(output.viewTreeObserver.isAlive) {
                            output.viewTreeObserver.removeOnPreDrawListener(this)
                        }
                        val child = scroll.getChildAt(0)
                        if(child != null) {
                            val maxY = (child.height - scroll.height).coerceAtLeast(0)
                            scroll.scrollTo(0, previousY.coerceIn(0,maxY))
                        }
                        return true
                    }
                }
                observer.addOnPreDrawListener(restorePosition)
            }
        }
        refreshButton.setOnClickListener { showSelected() }

        fun loadKind(nextKind:String) {
            kind=nextKind
            val names=WorldEngine.observationNames(this,kind)
            val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,names).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.adapter=adapter
            selectedKey=names.firstOrNull().orEmpty()
            showSelected()
        }
        spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long) {
                selectedKey=parent?.getItemAtPosition(position)?.toString().orEmpty()
                showSelected()
            }
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?) { selectedKey="" }
        }
        heroesButton.setOnClickListener { loadKind("英雄") }
        residentsButton.setOnClickListener { loadKind("居民") }
        worldEventsButton.setOnClickListener { loadKind("世界事件") }
        interactionsButton.setOnClickListener { loadKind("人物互动") }
        loadKind("英雄")
        val refreshTask=object:Runnable {
            override fun run() {
                if(dialog.isShowing) {
                    hint.text=WorldEngine.localAiStatus(this@MainActivity)+"\n\n切换英雄、居民、世界事件或人物互动。状态每 2 秒刷新；正文不会自动重绘，点击“手动刷新当前记录”查看最新内容。"
                    if(kind=="世界事件" || kind=="人物互动") {
                        val latest=WorldEngine.observationNames(this@MainActivity,kind)
                        if(latest.isNotEmpty() && latest.firstOrNull()!=selectedKey) {
                            val previous=selectedKey
                            val adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,latest).apply {
                                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            }
                            spinner.adapter=adapter
                            val position=latest.indexOf(previous).let { if(it>=0) it else 0 }
                            selectedKey=latest[position]
                            spinner.setSelection(position)
                        }
                    }
                    // 正文绝不随 2 秒状态轮询而重绘，避免阅读时滚动位置跳动。
                    // 用户需要最新内容时，点击“手动刷新当前记录”。
                    handler.postDelayed(this,2000L)
                }
            }
        }
        dialog.setOnShowListener { handler.postDelayed(refreshTask,2000L) }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels*0.94f).toInt(),
            (resources.displayMetrics.heightPixels*0.82f).toInt()
        )
    }

    private fun showAiDiagnostics() {
        val report = ResidentAI.diagnosticReport(this)
        val content = TextView(this).apply {
            text = report
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(24, 18, 24, 18)
        }
        val scroll = ScrollView(this).apply {
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        AlertDialog.Builder(this)
            .setTitle("本地 AI 诊断日志")
            .setView(scroll)
            .setNeutralButton("复制全部日志") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI诊断日志", report))
                Toast.makeText(this, "完整诊断日志已复制，可以直接粘贴发送", Toast.LENGTH_LONG).show()
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun pickLocalModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 8101)
    }

    private fun importLocalModel(uri: Uri) {
        Toast.makeText(this,"正在安全导入模型；旧模型会先保留，请保持应用打开…",Toast.LENGTH_LONG).show()
        Thread {
            try {
                val modelsDir=File(filesDir,"models").apply { mkdirs() }
                val modelFile=File(modelsDir,"resident-model.gguf")
                val tempFile=File(modelsDir,"resident-model.gguf.importing")
                tempFile.delete()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output, 1024*1024); output.fd.sync() }
                } ?: throw IllegalStateException("无法读取所选文件")
                if(tempFile.length()<1024*1024) throw IllegalStateException("文件过小，不像有效的 GGUF 模型")
                val magic=ByteArray(4)
                java.io.RandomAccessFile(tempFile,"r").use { it.readFully(magic) }
                if(String(magic,Charsets.US_ASCII)!="GGUF") throw IllegalStateException("文件头不是 GGUF；原模型未被覆盖")
                val staged=tempFile
                runOnUiThread {
                    val replaceAndLoad = {
                        try {
                            val backup=File(modelsDir,"resident-model.gguf.previous")
                            if(backup.exists()) backup.delete()
                            if(modelFile.exists() && !modelFile.renameTo(backup)) throw IllegalStateException("无法备份旧模型；已取消覆盖")
                            if(!staged.renameTo(modelFile)) {
                                if(backup.exists()) backup.renameTo(modelFile)
                                throw IllegalStateException("无法安装新模型；已尝试恢复旧模型")
                            }
                            Toast.makeText(this,"模型文件已安全替换，正在加载并校验…",Toast.LENGTH_LONG).show()
                            ResidentAI.load(this,modelFile.absolutePath) { error ->
                                runOnUiThread {
                                    if(error==null) {
                                        backup.delete()
                                        Toast.makeText(this,"模型导入并加载成功",Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this,"新模型加载失败：$error。旧模型备份保留在应用内部，请勿删除。",Toast.LENGTH_LONG).show()
                                    }
                                    refresh()
                                }
                            }
                        } catch(e:Throwable) {
                            Toast.makeText(this,"模型替换失败：${e.message}",Toast.LENGTH_LONG).show()
                        }
                    }
                    if(ResidentAI.isLoaded()) {
                        ResidentAI.unload { ok, result ->
                            runOnUiThread {
                                if(ok) replaceAndLoad()
                                else Toast.makeText(this,"旧模型释放失败，未覆盖文件：$result",Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        replaceAndLoad()
                    }
                }
            } catch(e:Throwable) {
                runOnUiThread { Toast.makeText(this,"模型导入失败：${e.message}；原模型未被覆盖。",Toast.LENGTH_LONG).show() }
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
                        runOnUiThread {
                            AlertDialog.Builder(this)
                                .setTitle("本地模型回答")
                                .setMessage(answer)
                                .setNeutralButton("复制回答") { _, _ ->
                                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("本地模型回答", answer))
                                    Toast.makeText(this, "回答已复制", Toast.LENGTH_SHORT).show()
                                }
                                .setPositiveButton("关闭", null)
                                .show()
                        }
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
            .setMessage("用自然语言描述你要改变的世界。本地模型负责理解并生成操作，世界引擎负责验证和执行。没有对应实体模拟器的复杂现象会明确标注为事件/规则记录，不会假装已经完成全部物理后果。")
            .setView(input)
            .setPositiveButton("解析并执行") { _, _ ->
                val command=input.text.toString().trim()
                if(command.isBlank()) return@setPositiveButton
                Toast.makeText(this,"本地模型正在解析天道指令，请稍候…",Toast.LENGTH_LONG).show()
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
        val provider = getSharedPreferences("zhipu_secure", MODE_PRIVATE)
            .getString("world_ai_provider", "chatgpt")
        if (provider == "chatgpt" && !ChatGPTWorldBridge.isAttached()) {
            // The user action is in the foreground, so opening the signed-in WebView is allowed.
            // Its persisted WebView session is reused; no API key or backend setup is involved.
            startActivity(Intent(this, ChatGPTWebActivity::class.java))
        }
    }

    private fun stopWorld() {
        startService(Intent(this, WorldService::class.java).setAction(WorldService.ACTION_STOP))
        refresh()
    }

    private fun refresh() {
        val s = WorldEngine.load(this)
        val localModelState = if (ResidentAI.isLoaded()) "已加载" else if (ResidentAI.isLoading()) "加载中" else "未加载（A方案不依赖本地模型）"
        status.text = "历史时代：${WorldEngine.timelineStatus(this)}\n世界时间：第 ${s.day} 日\n居民样本：${s.residents} 人\n运行状态：${if (s.running) "运行中" else "已暂停"}\n模拟速度：现实 1 秒约推进 1 个世界小时（重大事件期间自动减速）\n本地模型：$localModelState\n世界人物AI：${WorldEngine.localAiStatus(this)}"
        log.text = "最近世界记录\n" + (s.events.takeLast(12).asReversed().joinToString("\n") { "• $it" }.ifBlank { "尚无事件。启动世界后将逐步产生记录。" })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refresh() }
}
