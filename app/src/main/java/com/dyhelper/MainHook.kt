package com.dyhelper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
        const val TAG = "DouyinHelper"
        val PKGS = arrayOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
        )
        var classLoader: ClassLoader? = null
        var currentAweme: Any? = null

        fun log(msg: String) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }

        fun toast(ctx: Context, msg: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in PKGS) return
        classLoader = lpparam.classLoader
        log("LOADED: " + lpparam.packageName)

        /* ============ 1. Share Menu Hook ============ */
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
                lpparam.classLoader, "onMeasure",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val panel = param.thisObject as? ViewGroup ?: return
                        if (panel.findViewWithTag<View>(999888) != null) return
                        if (panel.childCount < 1) return

                        // Check for MeasureLinearLayout to avoid early injection
                        val first = panel.getChildAt(0)
                        if (first.javaClass.name.contains("MeasureLinearLayout")) return

                        val ctx = panel.context
                        val items = listOf(
                            "????" to { copyLink(ctx) },
                            "????" to { download(ctx, 1) },
                            "????" to { download(ctx, 0) }
                        )

                        val container = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(12, 4, 12, 8)
                            tag = 999888
                        }

                        for ((label, action) in items) {
                            val btn = TextView(ctx).apply {
                                text = label
                                setTextColor(Color.parseColor("#CCCCCC"))
                                textSize = 12f
                                gravity = Gravity.CENTER
                                setPadding(16, 8, 16, 8)
                                setOnClickListener { action() }
                            }
                            container.addView(btn)
                        }
                        panel.addView(container)
                        log("Share menu injected")
                    }
                })
            log("ShareMenu hook OK")
        } catch (e: Exception) {
            log("ShareMenu hook FAILED: " + e.message)
        }

        /* ============ 2. Splash Ad Hook ============ */
        try {
            XposedHelpers.findAndHookMethod(
                "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
                lpparam.classLoader, "onCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? android.app.Activity)?.finish()
                        log("Blocked splash ad")
                    }
                })
            log("SplashAd hook OK")
        } catch (e: Exception) {
            log("SplashAd hook FAILED: " + e.message)
        }

        /* ============ 3. Main Splash skip ============ */
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.android.ugc.aweme.splash.SplashActivity",
                lpparam.classLoader, "onCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            XposedHelpers.callMethod(param.thisObject, "goMainActivity")
                        } catch (_: Exception) {
                            (param.thisObject as? android.app.Activity)?.finish()
                        }
                        log("Skipped main splash")
                    }
                })
            log("Splash hook OK")
        } catch (e: Exception) {
            log("Splash hook FAILED: " + e.message)
        }

        /* ============ 4. Aweme model - capture data + block ads ============ */
        try {
            val awemeCls = lpparam.classLoader.loadClass(
                "com.ss.android.ugc.aweme.feed.model.Aweme")
            // Hook isAd()
            for (m in awemeCls.declaredMethods) {
                if (m.name == "isAd" && m.parameterTypes.isEmpty() &&
                    m.returnType == Boolean::class.javaPrimitiveType) {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            currentAweme = param.thisObject
                            param.result = false
                        }
                    })
                    log("Hooked Aweme.isAd()")
                    break
                }
            }
        } catch (e: Exception) {
            log("Aweme hook FAILED: " + e.message)
        }

        /* ============ 5. IFeedViewHolder hook for video data ============ */
        try {
            val ifeedCls = XposedHelpers.findClassIfExists(
                "com.ss.android.ugc.aweme.feed.adapter.IFeedViewHolder",
                lpparam.classLoader)
            if (ifeedCls != null) {
                // Hook onPageSelected-like methods via VideoViewHolder
                XposedHelpers.findAndHookMethod(
                    "com.ss.android.ugc.aweme.feed.adapter.VideoViewHolder",
                    lpparam.classLoader, "onPageSelected",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // This fires when a video is shown - good trigger point
                        }
                    })
                log("VideoViewHolder hook OK")
            }
        } catch (e: Exception) {
            log("VideoViewHolder hook: " + e.message)
        }

        /* ============ 6. Image watermark removal ============ */
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.ugc.aweme.ImageUrlStruct",
                lpparam.classLoader, "equals", Object::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        log("ImageUrlStruct.equals called")
                    }
                })
        } catch (_: Exception) {}

        /* ============ 7. Detail Activity hook ============ */
        try {
            XposedHelpers.findAndHookMethod(
                "com.ss.android.ugc.aweme.detail.ui.DetailActivity",
                lpparam.classLoader, "onCreate", Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        log("DetailActivity opened")
                    }
                })
        } catch (_: Exception) {}

        log("All hooks initialized!")
    }

    /* ---- Utility methods ---- */

    private fun copyLink(ctx: Context) {
        try {
            val aweme = currentAweme ?: return
            val desc = XposedHelpers.getObjectField(aweme, "desc") as? String ?: ""
            val url = getVideoUrl(aweme)
            val text = if (desc.isNotEmpty()) desc + "\n" + url else url
            if (text.isNotEmpty()) {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("desc", text))
                toast(ctx, "?????!")
            }
        } catch (e: Exception) {
            toast(ctx, "????")
        }
    }

    private fun download(ctx: Context, type: Int) {
        try {
            val aweme = currentAweme ?: return
            val url = when (type) {
                0 -> getMusicUrl(aweme)
                else -> getVideoUrl(aweme)
            }
            if (url.isNullOrEmpty()) {
                toast(ctx, "????????")
                return
            }
            toast(ctx, "????...")
            Thread {
                downloadFile(url, type)
            }.start()
        } catch (e: Exception) {
            toast(ctx, "????")
        }
    }

    private fun getVideoUrl(aweme: Any): String? {
        return try {
            XposedHelpers.callMethod(aweme, "getFirstPlayAddr") as? String
        } catch (_: Exception) { null }
    }

    private fun getMusicUrl(aweme: Any): String? {
        return try {
            val music = XposedHelpers.getObjectField(aweme, "music")
            val playUrl = XposedHelpers.getObjectField(music, "playUrl")
            val urlList = XposedHelpers.callMethod(playUrl, "getUrlList") as? List<*>
            urlList?.firstOrNull()?.toString()
        } catch (_: Exception) { null }
    }

    private fun downloadFile(url: String, type: Int) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.connect()

            if (conn.responseCode != 200) {
                log("Download HTTP " + conn.responseCode)
                return
            }

            val ext = if (type == 0) ".mp3" else ".mp4"
            val name = "dy_" + System.currentTimeMillis() + ext
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "DouyinHelper"
            ).apply { if (!exists()) mkdirs() }

            val file = File(dir, name)
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buf = ByteArray(8192)
                    var len: Int
                    var total = 0L
                    while (input.read(buf).also { len = it } != -1) {
                        output.write(buf, 0, len)
                        total += len
                    }
                    log("Downloaded: " + file.absolutePath + " (" + total + " bytes)")
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            log("Download error: " + e.message)
        }
    }
}
