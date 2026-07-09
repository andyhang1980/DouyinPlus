package com.dyhelper.hook

import android.os.Bundle
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class AntiAdHook : BaseHook {
    override fun name() = "Ad"

    override fun init(loader: ClassLoader): Boolean {
        val cls = XposedHelpers.findClassIfExists(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity", loader) ?: return false
        for (m in cls.declaredMethods) {
            if (m.name == "onCreate" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Bundle::class.java) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        (p.thisObject as? android.app.Activity)?.finish()
                    }
                })
                HookUtils.log("[Ad] Hooked")
                return true
            }
        }
        return false
    }
}
