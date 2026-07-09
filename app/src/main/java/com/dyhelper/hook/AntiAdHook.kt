package com.dyhelper.hook

import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false
        val adNames = listOf("com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity", "com.ss.android.ugc.aweme.commercialize.splash.show.SplashAdActivity")
        for (name in adNames) { val cls = ClassFinder.findClass(loader, name); if (cls != null && hookFinish(cls)) { ok = true; break } }
        if (!ok) {
            val adPkgs = listOf("com.bytedance.ies.ugc.aweme.commercialize", "com.ss.android.ugc.aweme.commercialize")
            outer@ for (pkg in adPkgs) { for (cls in ClassFinder.scanClasses(loader, pkg)) { if (cls.simpleName.contains("SplashAd") || cls.simpleName.contains("AdActivity")) { if (hookFinish(cls)) { HookUtils.log("[Ad] Auto: " + cls.name); ok = true; break@outer } } } }
        }
        val splashNames = listOf("com.ss.android.ugc.aweme.splash.SplashActivity")
        for (name in splashNames) { val cls = ClassFinder.findClass(loader, name); if (cls != null && hookSplash(cls)) { ok = true; break } }
        if (!ok) { for (cls in ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.splash")) { if (cls.simpleName.contains("Splash") && cls.simpleName.endsWith("Activity")) { if (hookSplash(cls)) { HookUtils.log("[Ad] Auto: " + cls.name); ok = true; break } } } }
        if (!ok) { val sc = HookUtils.findClass(loader, "com.ss.android.ugc.aweme.splash.SplashActivity"); if (sc != null && hookSplash(sc)) ok = true }
        return ok
    }

    private fun hookFinish(cls: Class<*>): Boolean {
        HookUtils.hookViaReflection(cls, "onCreate", object : HookUtils.RB() {
            override fun after(obj: Any, args: Array<out Any?>, result: Any?): Any? { (obj as? android.app.Activity)?.finish(); return null }
        })
        HookUtils.log("[Ad] hooked: " + cls.name); return true
    }

    private fun hookSplash(cls: Class<*>): Boolean {
        HookUtils.hookViaReflection(cls, "onCreate", object : HookUtils.RB() {
            override fun after(obj: Any, args: Array<out Any?>, result: Any?): Any? {
                try { HookUtils.callMethod(obj, "goMainActivity") } catch (_: Exception) { (obj as? android.app.Activity)?.finish() }; return null
            }
        })
        HookUtils.log("[Ad] Splash: " + cls.name); return true
    }
}