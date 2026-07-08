package com.dyhelper.hooks

import com.dyhelper.MainHook
import com.dyhelper.adaptive.ClassScanner.ClassFingerprint
import com.dyhelper.adaptive.ClassScanner.FieldPattern
import com.dyhelper.adaptive.ClassScanner.MethodPattern
import com.dyhelper.data.AwemeData
import com.dyhelper.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Anti-ads hook: blocks splash ads, feed ads, and auto-update.
 *
 * Uses the adaptive system:
 * - Tries cached/hardcoded class names first
 * - Falls back to ClassScanner discovery by structural fingerprint
 * - Survives Douyin version updates automatically
 */
object AntiAdsHook {

    // ==== Hardcoded class names (fallback for known versions) ====
    private val SPLASH_CLASSES = listOf(
        "com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity",
    )
    private val MAIN_SPLASH_CLASSES = listOf(
        "com.ss.android.ugc.aweme.splash.SplashActivity",
    )
    private val AWEME_CLASSES = listOf(
        "com.ss.android.ugc.aweme.feed.model.Aweme",
    )

    // ==== Structural fingerprints (for auto-discovery on new versions) ====
    private val SPLASH_AD_FP = ClassFingerprint(
        label = "SplashAdActivity",
        packagePrefixes = listOf("com.bytedance.", "com.ss.android."),
        parentClassName = "android.app.Activity",
        concreteParent = true,
        methodPatterns = listOf(
            MethodPattern("void", listOf("android.os.Bundle"))
        )
    )

    private val MAIN_SPLASH_FP = ClassFingerprint(
        label = "SplashActivity",
        packagePrefixes = listOf("com.ss.android.ugc.aweme.splash"),
        parentClassName = "android.app.Activity",
        methodPatterns = listOf(
            MethodPattern("void", listOf("android.os.Bundle"))
        ),
        // Has goMainActivity method -- key differentiator
        excludeMethodPatterns = listOf(
            MethodPattern("void", listOf("android.os.Bundle"))
        )
    )

    private val AWEME_FP = ClassFingerprint(
        label = "Aweme",
        packagePrefixes = listOf("com.ss.android.ugc.aweme.feed.model"),
        fieldPatterns = listOf(
            FieldPattern("desc", "java.lang.String"),
            FieldPattern("awemeType", "int"),
        ),
        methodPatterns = listOf(
            MethodPattern("boolean", emptyList()),   // isAd()
        )
    )

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[AntiAds] Initializing (adaptive mode)...")

        // 1. Splash ad activity -> finish immediately
        HookHelper.tryAdaptive(
            tag = "AntiAds:SplashAd",
            fingerprint = SPLASH_AD_FP,
            methodPattern = MethodPattern("void", listOf("android.os.Bundle")),
            hardcodedClasses = SPLASH_CLASSES,
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? android.app.Activity)?.finish()
                    MainHook.log("[AntiAds] Blocked splash ad")
                }
            }
        )

        // 2. Main splash -> skip to main activity
        HookHelper.tryAdaptive(
            tag = "AntiAds:MainSplash",
            fingerprint = MAIN_SPLASH_FP,
            methodPattern = MethodPattern("void", listOf("android.os.Bundle")),
            hardcodedClasses = MAIN_SPLASH_CLASSES,
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        de.robv.android.xposed.XposedHelpers.callMethod(
                            param.thisObject, "goMainActivity", null
                        )
                        MainHook.log("[AntiAds] Skipped splash")
                    } catch (e: Exception) {
                        // goMainActivity might not exist; try finish
                        (param.thisObject as? android.app.Activity)?.finish()
                    }
                }
            }
        )

        // 3. Aweme.isAd() -> capture data + block ads
        HookHelper.tryAdaptive(
            tag = "AntiAds:Aweme",
            fingerprint = AWEME_FP,
            methodPattern = MethodPattern("boolean", emptyList()),
            hardcodedClasses = AWEME_CLASSES,
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    AwemeData.currentAweme = param.thisObject
                    param.result = false
                }
            }
        )
    }
}
