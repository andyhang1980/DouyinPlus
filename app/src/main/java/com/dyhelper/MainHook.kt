package com.dyhelper

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import com.dyhelper.hook.AntiAdHook
import com.dyhelper.hook.BaseHook
import com.dyhelper.hook.DataCaptureHook
import com.dyhelper.hook.DownloadHook
import com.dyhelper.hook.ShareMenuHook
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        var classLoader: ClassLoader? = null
        private var inited = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.ss.android.ugc.aweme" && pkg != "com.ss.android.ugc.aweme.lite") return

        classLoader = lpparam.classLoader
        HookUtils.log("=== DouyinHelper v3.0 " + pkg + " ===")

        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inited) return
                    inited = true
                    val ctx = param.args[0] as Context
                    DataCaptureHook.appContext = ctx
                    classLoader = ctx.classLoader
                    initAll(ctx)
                }
            })
    }

    private fun initAll(ctx: Context) {
        val loader = classLoader ?: return
        val capture = DataCaptureHook()

        // Build download handler
        val download = DownloadHook(capture)

        // Build hooks list (same as Bear, modular like dou-plus)
        val hooks = listOf<BaseHook>(
            ShareMenuHook(
                onCopyLink = { c -> download.copyLink(c) },
                onDownloadVideo = { c -> download.downloadVideo(c) },
                onDownloadAudio = { c -> download.downloadAudio(c) },
                onDownloadImage = { c -> download.downloadImage(c) },
                isImageCheck = { capture.isImageAweme() }
            ),
            AntiAdHook(),
            capture  // DataCaptureHook itself hooks isAd
        )

        var success = 0
        var results = ""
        for (hook in hooks) {
            try {
                if (hook.init(loader)) {
                    success++
                    if (results.isNotEmpty()) results += " "
                    results += hook.name()
                }
            } catch (t: Throwable) {
                HookUtils.log(hook.name() + " init failed: " + t.message)
            }
        }

        val total = hooks.size
        val msg = if (success == total) "自动适配完成 " + total + "/" + total
                  else "自动适配: " + success + "/" + total + " " + results

        Handler(Looper.getMainLooper()).postDelayed({
            val t = Toast.makeText(ctx, msg, Toast.LENGTH_LONG)
            t.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
            t.show()
        }, 2000)

        HookUtils.log("=== " + success + "/" + total + " hooks: " + results + " ===")
    }
}
