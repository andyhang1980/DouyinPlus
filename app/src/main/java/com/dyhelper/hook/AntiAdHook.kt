package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        val adNames = listOf(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            "com.ss.android.ugc.aweme.commercialize.splash.show.SplashAdActivity"
        )
        for (name in adNames) {
            val cls = ClassFinder.findClass(loader, name) ?: continue
            if (hookOnCreate(cls, skipAd = true)) { ok = true; break }
        }

        if (!ok) {
            val adPackages = listOf(
                "com.bytedance.ies.ugc.aweme.commercialize",
                "com.ss.android.ugc.aweme.commercialize"
            )
            outer@ for (pkg in adPackages) {
                for (cls in ClassFinder.scanClasses(loader, pkg)) {
                    if (cls.simpleName.contains("SplashAd") || cls.simpleName.contains("AdActivity")) {
                        if (hookOnCreate(cls, skipAd = true)) {
                            HookUtils.log("[Ad] Auto: " + cls.name)
                            ok = true; break@outer
                        }
                    }
                }
            }
        }

        val splashNames = listOf("com.ss.android.ugc.aweme.splash.SplashActivity")
        for (name in splashNames) {
            val cls = ClassFinder.findClass(loader, name) ?: continue
            if (hookSplash(cls)) { ok = true; break }
        }

        if (!ok) {
            for (cls in ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.splash")) {
                if (cls.simpleName.contains("Splash") && cls.simpleName.endsWith("Activity")) {
                    if (hookSplash(cls)) {
                        HookUtils.log("[Ad] Auto: " + cls.name)
                        ok = true; break
                    }
                }
            }
        }

        if (!ok) {
            val sc = HookUtils.findClass(loader, "com.ss.android.ugc.aweme.splash.SplashActivity")
            if (sc != null && hookSplash(sc)) ok = true
        }

        return ok
    }

    private fun hookOnCreate(cls: Class<*>, skipAd: Boolean): Boolean {
        HookUtils.hookOneMethod(cls, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                (p.thisObject as? android.app.Activity)?.finish()
            }
        })
        HookUtils.log("[Ad] hooked: " + cls.name)
        return true
    }

    private fun hookSplash(cls: Class<*>): Boolean {
        HookUtils.hookOneMethod(cls, "onCreate", object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                try { HookUtils.callMethod(p.thisObject, "goMainActivity") }
                catch (_: Exception) { (p.thisObject as? android.app.Activity)?.finish() }
            }
        })
        HookUtils.log("[Ad] Splash: " + cls.name)
        return true
    }
}
