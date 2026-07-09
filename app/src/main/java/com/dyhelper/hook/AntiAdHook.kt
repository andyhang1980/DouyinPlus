package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class AntiAdHook : BaseHook {

    override fun name() = "去广告"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // SplashAdActivity.onCreate (same as Bear u0/s.java class a)
        if (hookBySig(loader, "Ad",
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            Void.TYPE, "onCreate", Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? android.app.Activity)?.finish()
                }
            })) ok = true

        // SplashActivity.onCreate (same as Bear u0/s.java class b)
        if (hookBySig(loader, "Splash",
            "com.ss.android.ugc.aweme.splash.SplashActivity",
            Void.TYPE, "onCreate", Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try { XposedHelpers.callMethod(param.thisObject, "goMainActivity") }
                    catch (_: Exception) {
                        (param.thisObject as? android.app.Activity)?.finish()
                    }
                }
            })) ok = true

        return ok
    }

    private fun hookBySig(
        loader: ClassLoader, tag: String, className: String,
        returnType: Class<*>?, methodName: String,
        vararg paramTypes: Class<*>,
        callback: XC_MethodHook
    ): Boolean {
        val clazz = XposedHelpers.findClassIfExists(className, loader)
        if (clazz == null) {
            HookUtils.log("[" + tag + "] Class not found: " + className)
            return false
        }
        for (m in clazz.declaredMethods) {
            if (m.name != methodName) continue
            if (returnType != null && m.returnType != returnType) continue
            val params = m.parameterTypes
            if (params.size != paramTypes.size) continue
            var match = true
            var i = 0
            while (i < paramTypes.size) {
                if (paramTypes[i] != null && paramTypes[i] != params[i]) {
                    match = false
                    break
                }
                i++
            }
            if (!match) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, callback)
            HookUtils.log("[" + tag + "] Hooked: " + clazz.simpleName + "." + m.name)
            return true
        }
        HookUtils.log("[" + tag + "] Method not found: " + methodName)
        return false
    }
}
