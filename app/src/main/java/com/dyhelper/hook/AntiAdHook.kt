package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        val adNames = listOf(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            "com.ss.android.ugc.aweme.commercialize.splash.show.SplashAdActivity"
        )
        for (name in adNames) {
            val cls = ClassFinder.findClass(loader, name)
            if (cls != null && hookCreate(cls, true)) {
                HookUtils.log("[Ad] Found: " + name)
                ok = true
                break
            }
        }

        val splashNames = listOf(
            "com.ss.android.ugc.aweme.splash.SplashActivity"
        )
        for (name in splashNames) {
            val cls = ClassFinder.findClass(loader, name)
            if (cls != null && hookCreate(cls, false)) {
                HookUtils.log("[Ad] Found: " + name)
                ok = true
                break
            }
        }

        return ok
    }

    private fun hookCreate(cls: Class<*>, isAd: Boolean): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "onCreate" && m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == Bundle::class.java) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        if (isAd) {
                            (p.thisObject as? android.app.Activity)?.finish()
                        } else {
                            try { XposedHelpers.callMethod(p.thisObject, "goMainActivity") }
                            catch (_: Exception) {
                                (p.thisObject as? android.app.Activity)?.finish()
                            }
                        }
                    }
                })
                return true
            }
        }
        return false
    }
}
