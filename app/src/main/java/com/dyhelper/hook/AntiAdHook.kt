package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // SplashAdActivity
        val cls1 = XposedHelpers.findClassIfExists(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity", loader)
        if (cls1 != null) {
            for (m in cls1.declaredMethods) {
                if (m.name == "onCreate" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Bundle::class.java) {
                    m.isAccessible = true
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            (p.thisObject as? android.app.Activity)?.finish()
                        }
                    })
                    HookUtils.log("[Ad] SplashAdActivity hooked")
                    ok = true
                    break
                }
            }
        }

        // SplashActivity - skip to main
        val cls2 = XposedHelpers.findClassIfExists(
            "com.ss.android.ugc.aweme.splash.SplashActivity", loader)
        if (cls2 != null) {
            for (m in cls2.declaredMethods) {
                if (m.name == "onCreate" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Bundle::class.java) {
                    m.isAccessible = true
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            try { XposedHelpers.callMethod(p.thisObject, "goMainActivity") }
                            catch (_: Exception) {
                                (p.thisObject as? android.app.Activity)?.finish()
                            }
                        }
                    })
                    HookUtils.log("[Ad] SplashActivity hooked")
                    ok = true
                    break
                }
            }
        }

        return ok
    }
}
