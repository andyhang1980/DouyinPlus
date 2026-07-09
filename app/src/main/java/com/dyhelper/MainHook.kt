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
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    companion object {
        var classLoader: ClassLoader? = null
        var appContext: Context? = null
        private var inited = false
        const val VERSION = "3.2.0"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.ss.android.ugc.aweme" && pkg != "com.ss.android.ugc.aweme.lite") return
        classLoader = lpparam.classLoader
        HookUtils.log("=== DH v$VERSION $pkg ===")
        HookUtils.log("Bridge: " + XposedBridge::class.java.name)

        try {
            val appClass = XposedHelpers.findClass("android.app.Application", lpparam.classLoader)
            if (HookUtils.hookAllMethods(appClass, "attach", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inited) return; inited = true
                    val ctx = param.args[0] as Context; appContext = ctx; classLoader = ctx.classLoader; initAll(ctx)
                }
            })) {
                HookUtils.log("Application.attach hooked via hookAllMethods")
            } else {
                HookUtils.log("Application.attach FAILED")
            }
        } catch (t: Throwable) {
            HookUtils.log("App hook err: " + t.message)
        }
    }

    private fun initAll(ctx: Context) {
        val loader = classLoader ?: run { HookUtils.log("no classLoader"); return }
        HookUtils.log("initAll: " + loader.javaClass.name)
        val cap = DataCaptureHook(); val dl = DownloadHook(cap)
        val hooks = listOf<BaseHook>(
            ShareMenuHook(copyFn={c->dl.copyLink(c)},videoFn={c->dl.video(c)},audioFn={c->dl.audio(c)},imageFn={c->dl.image(c)},checkImage={cap.isImage()}),
            cap,
            AntiAdHook()
        )
        var ok=0; var names=""
        for (h in hooks) {
            try {
                if (h.init(loader)) { ok++; if (names.isNotEmpty()) names+=" "; names+=h.name() }
            } catch (t:Throwable) { HookUtils.log(h.name()+" err: "+t.message) }
        }
        val msg = if (ok==3) "\u81EA\u52A8\u9002\u914D\u5B8C\u6210 $ok/3 $names" else "\u81EA\u52A8\u9002\u914D: $ok/3 $names"
        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(ctx,msg,Toast.LENGTH_LONG).apply{setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP,0,200);show()}
        },2000)
        HookUtils.log("=== $ok/3 : $names ===")
    }
}