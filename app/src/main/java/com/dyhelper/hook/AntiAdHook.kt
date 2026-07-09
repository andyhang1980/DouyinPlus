package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // Try known SplashAdActivity class names
        val adNames = listOf(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            "com.ss.android.ugc.aweme.commercialize.splash.show.SplashAdActivity"
        )
        for (name in adNames) {
            if (hookAdActivity(name, loader)) { ok = true; break }
        }

        // Auto-scan for splash ad activities in commercialize packages
        if (!ok) {
            val adPackages = listOf(
                "com.bytedance.ies.ugc.aweme.commercialize",
                "com.ss.android.ugc.aweme.commercialize"
            )
            outer@ for (pkg in adPackages) {
                val scan = ClassFinder.scanClasses(loader, pkg)
                for (cls in scan) {
                    if (cls.simpleName.contains("SplashAd") || cls.simpleName.contains("AdActivity")) {
                        if (hookAdClass(cls, true)) {
                            HookUtils.log("[Ad] Auto: " + cls.name)
                            ok = true; break@outer
                        }
                    }
                }
            }
        }

        // Try known SplashActivity class name
        val splashNames = listOf(
            "com.ss.android.ugc.aweme.splash.SplashActivity"
        )
        for (name in splashNames) {
            if (hookSplashActivity(name, loader)) { ok = true; break }
        }

        // Auto-scan for splash activity
        if (!ok) {
            val scan = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.splash")
            for (cls in scan) {
                if (cls.simpleName.contains("Splash") && cls.simpleName.endsWith("Activity")) {
                    if (hookSplashClass(cls)) {
                        HookUtils.log("[Ad] Auto: " + cls.name)
                        ok = true; break
                    }
                }
            }
        }

        // Fallback: hook SplashActivity via reflection (no XposedHelpers)
        if (!ok) {
            try {
                val sc = HookUtils.findClass(loader, "com.ss.android.ugc.aweme.splash.SplashActivity")
                if (sc != null && hookSplashClass(sc)) ok = true
            } catch (_: Exception) {}
        }

        return ok
    }

    private fun hookAdActivity(name: String, loader: ClassLoader): Boolean {
        val cls = ClassFinder.findClass(loader, name) ?: return false
        return hookAdClass(cls, true)
    }

    private fun hookAdClass(cls: Class<*>, isAd: Boolean): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "onCreate" && m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == Bundle::class.java) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        (p.thisObject as? android.app.Activity)?.finish()
                    }
                })
                HookUtils.log("[Ad] AdActivity hooked: " + cls.name)
                return true
            }
        }
        return false
    }

    private fun hookSplashActivity(name: String, loader: ClassLoader): Boolean {
        val cls = ClassFinder.findClass(loader, name) ?: return false
        return hookSplashClass(cls)
    }

    private fun hookSplashClass(cls: Class<*>): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "onCreate" && m.parameterTypes.size == 1 &&
                m.parameterTypes[0] == Bundle::class.java) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        try {
                            HookUtils.callMethod(p.thisObject, "goMainActivity")
                        } catch (_: Exception) {
                            (p.thisObject as? android.app.Activity)?.finish()
                        }
                    }
                })
                HookUtils.log("[Ad] SplashActivity hooked: " + cls.name)
                return true
            }
        }
        return false
    }
}
