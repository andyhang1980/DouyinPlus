package com.dyhelper.hooks

import com.dyhelper.MainHook
import com.dyhelper.data.AwemeData
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Anti-Ads Hook: Blocks Douyin ads.
 *
 * Layer 1: Block splash ad activities (开屏广告)
 * Layer 2: Hook Aweme.isAd() to capture current Aweme and filter ad cards
 */
object AntiAdsHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[AntiAds] Initializing...")

        // Layer 1: Splash ad activities
        hookSplashAd(lpparam)

        // Layer 2: Capture Aweme data and detect ads
        hookAwemeCapture(lpparam)

        MainHook.log("[AntiAds] Hooks registered")
    }

    /** Hook splash ad activities */
    private fun hookSplashAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        val splashClasses = listOf(
            "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
            "com.ss.android.ugc.aweme.splash.SplashActivity",
        )

        for (className in splashClasses) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                XposedHelpers.findAndHookMethod(clazz, "onCreate",
                    android.os.Bundle::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val activity = param.thisObject as? android.app.Activity ?: return
                            if (className.contains("SplashAdActivity")) {
                                activity.finish() // Close ad activity
                            } else {
                                // Skip to main
                                XposedHelpers.callMethod(activity, "goMainActivity", null)
                            }
                            MainHook.log("[AntiAds] Blocked splash: ${clazz.simpleName}")
                        }
                    })
                MainHook.log("[AntiAds] Hooked splash: $className")
            } catch (_: Exception) { }
        }
    }

    /** Hook Aweme.isAd() to capture Aweme data */
    private fun hookAwemeCapture(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val awemeClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.feed.model.Aweme",
                lpparam.classLoader
            )

            // Hook isAd() - captures the current Aweme
            XposedHelpers.findAndHookMethod(awemeClass, "isAd",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        AwemeData.currentAweme = param.thisObject
                        // Force return false to hide ads
                        param.result = false
                    }
                })
            MainHook.log("[AntiAds] Hooked Aweme.isAd()")
        } catch (_: Exception) { }

        // Also try to get Aweme from feed adapter binding
        try {
            val vhClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.feed.adapter.VideoViewHolder",
                lpparam.classLoader
            )
            // Hook onBind or similar to capture aweme
            for (method in vhClass.declaredMethods) {
                if (method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].name.contains("Aweme")) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            AwemeData.currentAweme = param.args[0]
                        }
                    })
                    MainHook.log("[AntiAds] Hooked VideoViewHolder.${method.name}")
                    break
                }
            }
        } catch (_: Exception) { }
    }
}
