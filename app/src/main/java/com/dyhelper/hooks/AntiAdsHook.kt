package com.dyhelper.hooks

import com.dyhelper.MainHook
import com.dyhelper.data.AwemeData
import com.dyhelper.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

object AntiAdsHook {

    // ==== Add candidate class names here for new Douyin versions ====
    private val SPLASH_CLASSES = listOf(
        "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
    )
    private val MAIN_SPLASH_CLASSES = listOf(
        "com.ss.android.ugc.aweme.splash.SplashActivity",
    )
    private val AWEME_CLASSES = listOf(
        "com.ss.android.ugc.aweme.feed.model.Aweme",
    )

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[AntiAds] Initializing...")

        // Splash ad activity -> finish immediately
        HookHelper.hookByMethodName(
            tag = "AntiAds", candidateClasses = SPLASH_CLASSES,
            returnType = Void.TYPE, methodName = "onCreate",
            paramTypes = arrayOf(android.os.Bundle::class.java),
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? android.app.Activity)?.finish()
                    MainHook.log("[AntiAds] Blocked splash ad")
                }
            }
        )

        // Main splash -> skip to main
        HookHelper.hookByMethodName(
            tag = "AntiAds", candidateClasses = MAIN_SPLASH_CLASSES,
            returnType = Void.TYPE, methodName = "onCreate",
            paramTypes = arrayOf(android.os.Bundle::class.java),
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    de.robv.android.xposed.XposedHelpers.callMethod(param.thisObject, "goMainActivity", null)
                    MainHook.log("[AntiAds] Skipped splash")
                }
            }
        )

        // Aweme.isAd() -> capture data + block ads
        HookHelper.hookByMethodName(
            tag = "AntiAds", candidateClasses = AWEME_CLASSES,
            returnType = Boolean::class.java, methodName = "isAd",
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    AwemeData.currentAweme = param.thisObject
                    param.result = false
                }
            }
        )
    }
}
