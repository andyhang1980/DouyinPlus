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
        val cap = DataCaptureHook()
        val dl = DownloadHook(cap)

        val hooks = listOf<BaseHook>(
            ShareMenuHook(
                copyFn = { c -> dl.copyLink(c) },
                videoFn = { c -> dl.video(c) },
                audioFn = { c -> dl.audio(c) },
                imageFn = { c -> dl.image(c) },
                checkImage = { cap.isImage() }
            ),
            AntiAdHook(),
            cap
        )

        var ok = 0
        var names = ""
        for (h in hooks) {
            try {
                if (h.init(loader)) {
                    ok++
                    if (names.isNotEmpty()) names += " "
                    names += h.name()
                }
            } catch (t: Throwable) {
                HookUtils.log(h.name() + " err: " + t.message)
            }
        }

        val total = hooks.size
        val msg = if (ok == total) "自动适配完成 " + ok + "/" + total
                  else "自动适配: " + ok + "/" + total + " " + names

        Handler(Looper.getMainLooper()).postDelayed({
            val t = Toast.makeText(ctx, msg, Toast.LENGTH_LONG)
            t.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
            t.show()
        }, 2000)

        HookUtils.log("=== " + ok + "/" + total + " : " + names + " ===")
    }
}
