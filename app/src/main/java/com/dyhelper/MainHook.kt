package com.dyhelper

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainHook : IXposedHookLoadPackage {

    companion object {
        var classLoader: ClassLoader? = null
        var currentAweme: Any? = null
        var appContext: Context? = null
        private var inited = false

        fun log(msg: String) {
            XposedBridge.log("[DH] " + msg)
        }

        fun toast(msg: String) {
            Handler(Looper.getMainLooper()).post {
                appContext?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.ss.android.ugc.aweme" && pkg != "com.ss.android.ugc.aweme.lite") return

        classLoader = lpparam.classLoader
        log("=== DH v2.2 " + pkg + " ===")

        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inited) return
                    inited = true
                    appContext = param.args[0] as Context
                    classLoader = appContext!!.classLoader
                    initHooks()
                }
            })
    }

    private fun hookCls(
        tag: String, className: String, returnType: Class<*>?,
        methodName: String, vararg paramTypes: Class<*>,
        callback: XC_MethodHook
    ): Boolean {
        val clazz = XposedHelpers.findClassIfExists(className, classLoader)
        if (clazz == null) {
            log("[" + tag + "] Class NOT found: " + className)
            return false
        }
        for (m in clazz.declaredMethods) {
            if (m.name != methodName) continue
            if (returnType != null && m.returnType != returnType) continue
            val params = m.parameterTypes
            if (params.size != paramTypes.size) continue
            var match = true
            var idx = 0
            while (idx < paramTypes.size) {
                if (paramTypes[idx] != null && paramTypes[idx] != params[idx]) {
                    match = false
                    break
                }
                idx++
            }
            if (!match) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, callback)
            log("[" + tag + "] Hooked: " + clazz.simpleName + "." + m.name)
            return true
        }
        log("[" + tag + "] Method NOT found: " + methodName + " in " + className)
        return false
    }

    private fun initHooks() {
        var ok = 0
        val D = '$'

        if (hookCls("Menu",
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
            Void.TYPE, "onMeasure",
            Integer.TYPE, Integer.TYPE,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val panel = param.thisObject as? LinearLayout ?: return
                    if (panel.childCount < 1) return
                    if (panel.findViewWithTag<View>(888888) != null) return
                    val simpleName = panel.getChildAt(0).javaClass.simpleName
                    if (simpleName.contains("MeasureOnce") || simpleName.contains("Linear")) return
                    addMenuItems(panel, panel.context)
                }
            })) ok++

        if (hookCls("Menu2",
            "com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder" + D + "buildPanel" + D + "1",
            android.view.View::class.java, "onCreateView",
            Context::class.java, ViewGroup::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val vg = param.result as? ViewGroup ?: return
                    if (!vg.javaClass.name.contains("common.keyboard.MeasureLinearLayout")) return
                    addMenuItems(vg, vg.context)
                }
            })) ok++

        if (hookCls("Ad",
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            Void.TYPE, "onCreate", Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? android.app.Activity)?.finish()
                }
            })) ok++

        if (hookCls("Splash",
            "com.ss.android.ugc.aweme.splash.SplashActivity",
            Void.TYPE, "onCreate", Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try { XposedHelpers.callMethod(param.thisObject, "goMainActivity") }
                    catch (_: Exception) { (param.thisObject as? android.app.Activity)?.finish() }
                }
            })) ok++

        if (hookCls("Aweme",
            "com.ss.android.ugc.aweme.feed.model.Aweme",
            Boolean::class.javaPrimitiveType, "isAd",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    currentAweme = param.thisObject
                    param.result = false
                }
            })) ok++

        val msg = if (ok == 5) "自动适配 OK" else "自动适配: " + ok + "/5"
        Handler(Looper.getMainLooper()).postDelayed({
            appContext?.let {
                val t = Toast.makeText(it, msg, Toast.LENGTH_LONG)
                t.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
                t.show()
            }
        }, 2000)

        log("=== " + ok + "/5 hooks ===")
    }

    private fun addMenuItems(container: ViewGroup, ctx: Context) {
        val isImage = isImageAweme()
        val items = ArrayList<MenuItem>()
        items.add(MenuItem("复制链接", Runnable { copyLink(ctx) }))
        items.add(MenuItem(if (isImage) "图片下载" else "视频下载", Runnable { download(ctx, if (isImage) 2 else 1) }))
        items.add(MenuItem("音频下载", Runnable { download(ctx, 0) }))

        val rv = RecyclerView(ctx)
        rv.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rv.setPadding(13, 0, 13, 0)
        rv.adapter = MenuAdapter(items)
        rv.tag = 888888
        container.addView(rv)

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.visibility = View.GONE
        container.addView(pb)
    }

    data class MenuItem(val label: String, val action: Runnable)

    class MenuAdapter(private val items: List<MenuItem>) : RecyclerView.Adapter<MenuAdapter.VH>() {
        class VH(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val tv = TextView(parent.context)
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#CCCCCC"))
            tv.gravity = Gravity.CENTER
            tv.setPadding(20, 12, 20, 12)
            return VH(tv)
        }
        override fun onBindViewHolder(vh: VH, pos: Int) {
            (vh.itemView as TextView).text = items[pos].label
            vh.itemView.setOnClickListener { items[pos].action.run() }
        }
        override fun getItemCount(): Int = items.size
    }

    private fun isImageAweme(): Boolean {
        return try {
            (XposedHelpers.getObjectField(currentAweme, "awemeType") as? Int) == 68
        } catch (_: Exception) { false }
    }

    private fun getVideoUrl(): String? {
        return try { XposedHelpers.callMethod(currentAweme, "getFirstPlayAddr") as? String }
        catch (_: Exception) { null }
    }

    private fun getMusicUrl(): String? {
        return try {
            val music = XposedHelpers.getObjectField(currentAweme, "music")
            val playUrl = XposedHelpers.getObjectField(music, "playUrl")
            val urlList = XposedHelpers.callMethod(playUrl, "getUrlList") as? List<*>
            urlList?.firstOrNull()?.toString()
        } catch (_: Exception) { null }
    }

    private fun copyLink(ctx: Context) {
        val desc = try { XposedHelpers.getObjectField(currentAweme, "desc") as? String ?: "" }
        catch (_: Exception) { "" }
        val url = getVideoUrl() ?: ""
        val text = if (desc.isNotEmpty()) desc + "\n" + url else url
        if (text.isNotEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("desc", text))
            toast("已复制!")
        }
    }

    private fun download(ctx: Context, type: Int) {
        val url = when (type) {
            0 -> getMusicUrl()
            2 -> null
            else -> getVideoUrl()
        }
        if (url == null) { toast("获取链接失败"); return }
        toast("开始下载...")
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.connect()
                if (conn.responseCode != 200) { log("Download HTTP " + conn.responseCode); return@Thread }
                val ext = if (type == 0) ".mp3" else ".mp4"
                val name = "dy_" + System.currentTimeMillis() + ext
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DouyinHelper")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                conn.inputStream.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
                log("Downloaded: " + file)
                conn.disconnect()
            } catch (e: Exception) { log("Download err: " + e.message) }
        }.start()
    }
}
