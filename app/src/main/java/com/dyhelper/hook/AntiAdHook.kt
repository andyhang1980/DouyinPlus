package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.Void

class AntiAdHook : BaseHook {
    override fun name() = "Ad"
    override fun init(loader: ClassLoader): Boolean {
        var ok = false
        if (h(loader, "SplashAd", "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            Void.TYPE, "onCreate", arrayOf(Bundle::class.java),
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    (p.thisObject as? android.app.Activity)?.finish()
                }
            })) ok = true
        if (h(loader, "Splash", "com.ss.android.ugc.aweme.splash.SplashActivity",
            Void.TYPE, "onCreate", arrayOf(Bundle::class.java),
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try { XposedHelpers.callMethod(p.thisObject, "goMainActivity") }
                    catch (_: Exception) { (p.thisObject as? android.app.Activity)?.finish() }
                }
            })) ok = true
        return ok
    }

    private fun h(loader: ClassLoader, tag: String, cls: String,
                  rt: Class<*>?, mn: String, pts: Array<Class<*>>,
                  cb: XC_MethodHook): Boolean {
        val c = XposedHelpers.findClassIfExists(cls, loader) ?: run {
            HookUtils.log("[" + tag + "] Class not found: " + cls); return false
        }
        for (m in c.declaredMethods) {
            if (m.name != mn) continue
            if (rt != null && m.returnType != rt) continue
            val mp = m.parameterTypes
            if (mp.size != pts.size) continue
            var ok = true
            var i = 0
            while (i < pts.size) { if (pts[i] != null && pts[i] != mp[i]) { ok = false; break }; i++ }
            if (!ok) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, cb)
            HookUtils.log("[" + tag + "] Hooked: " + c.simpleName + "." + m.name)
            return true
        }
        return false
    }
}
