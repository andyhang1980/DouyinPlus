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
        HookUtils.log("=== DH v3.0 " + pkg + " ===")

        XposedHelpers.findAndHookMethod(
            Application::class.java, "attach", Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inited) return
                    inited = true
                    val ctx = param.args[0] as Context
                    classLoader = ctx.classLoader
                    initAll(ctx)
                }
            })
    }

    private fun initAll(ctx: Context) {
        val loader = classLoader ?: return
        val capture = DataCaptureHook()
        val download = DownloadHook(capture)

        val hooks = listOf<BaseHook>(
            ShareMenuHook(
                onCopy = { c -> download.copyLink(c) },
                onVideo = { c -> download.downloadVideo(c) },
                onAudio = { c -> download.downloadAudio(c) },
                onImage = { c -> download.downloadImage(c) },
                isImage = { capture.isImageAweme() }
            ),
            AntiAdHook(),
            capture
        )

        var success = 0
        for (hook in hooks) {
            try { if (hook.init(loader)) success++ }
            catch (t: Throwable) { HookUtils.log(hook.name() + " failed: " + t.message) }
        }

        val total = hooks.size
        val msg = "DH: " + success + "/" + total + " hooks OK"
        Handler(Looper.getMainLooper()).postDelayed({
            val t = Toast.makeText(ctx, msg, Toast.LENGTH_LONG)
            t.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
            t.show()
        }, 2000)
        HookUtils.log("=== " + success + "/" + total + " hooks ===")
    }
}
