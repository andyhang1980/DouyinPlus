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
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {
    companion object { var classLoader: ClassLoader? = null; var appContext: Context? = null; private var inited = false; const val VERSION = "3.1.5-dy395" }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.ss.android.ugc.aweme" && pkg != "com.ss.android.ugc.aweme.lite") return
        classLoader = lpparam.classLoader
        HookUtils.log("=== DH v$VERSION $pkg ===")

        // Diagnostic: use compile-time class references
        HookUtils.log("Bridge class: " + XposedBridge::class.java.name)
        HookUtils.log("XCMH class: " + XC_MethodHook::class.java.name)
        try {
            val bridgeClass = XposedBridge::class.java
            for (m in bridgeClass.declaredMethods) {
                if (m.name.contains("hook") || m.name == "log") {
                    val params = m.parameterTypes.joinToString(", ") { it.name }
                    HookUtils.log("  " + m.name + "(" + params + ") -> " + m.returnType.name)
                }
            }
            HookUtils.log("=== end bridge dump ===")
        } catch (t: Throwable) {
            HookUtils.log("dump err: " + t.message)
        }

        // Try direct hookMethod again (not reflection)
        try {
            val methods = Application::class.java.declaredMethods.filter { it.name == "attach" }
            for (m in methods) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (inited) return; inited = true
                        val ctx = param.args[0] as Context; appContext = ctx; classLoader = ctx.classLoader; initAll(ctx)
                    }
                })
                HookUtils.log("hookMethod success on attach(${m.parameterTypes.joinToString { it.simpleName }})")
                return
            }
            HookUtils.log("No attach methods found")
        } catch (t: Throwable) {
            HookUtils.log("hookMethod err: " + t.message)
            // Log full stack
            HookUtils.log("  cause: " + (t.cause?.message ?: "none"))
        }
    }

    private fun initAll(ctx: Context) {
        val loader = classLoader ?: run { HookUtils.log("no classLoader"); return }
        HookUtils.log("initAll: " + loader.javaClass.name)
        val cap = DataCaptureHook(); val dl = DownloadHook(cap)
        val hooks = listOf<BaseHook>(ShareMenuHook(copyFn={c->dl.copyLink(c)},videoFn={c->dl.video(c)},audioFn={c->dl.audio(c)},imageFn={c->dl.image(c)},checkImage={cap.isImage()}),cap,AntiAdHook())
        var ok=0; var names=""
        for (h in hooks) { try { if (h.init(loader)) { ok++; if (names.isNotEmpty()) names+=" "; names+=h.name() } } catch (t:Throwable) { HookUtils.log(h.name()+" err: "+t.message) } }
        val msg = if (ok==3) "\u81EA\u52A8\u9002\u914D\u5B8C\u6210 $ok/3 $names" else "\u81EA\u52A8\u9002\u914D: $ok/3 $names"
        Handler(Looper.getMainLooper()).postDelayed({Toast.makeText(ctx,msg,Toast.LENGTH_LONG).apply{setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP,0,200);show()}},2000)
        HookUtils.log("=== $ok/3 : $names ===")
    }
}