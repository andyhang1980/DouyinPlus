import de.robv.android.xposed.XposedBridge
package com.douyinplus.hooks

import com.douyinplus.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

/**
 * Anti-Ads Hook: Remove ads from Douyin.
 *
 * Strategy (multi-layered):
 * 1. Hook Aweme.isAd() to return false for all videos
 * 2. Hook feed data loading to filter ad items
 * 3. Hook ad-related UI to prevent display
 */
object AntiAdsHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[AntiAds] Initializing...")

        // Layer 1: Try to hook Aweme.isAd() or equivalent
        hookAwemeIsAd(lpparam)

        // Layer 2: Try to hook feed data loading and filter ads
        hookFeedDataLoading(lpparam)

        // Layer 3: Try to hook the ad video controller
        hookAdVideoController(lpparam)

        MainHook.log("[AntiAds] Hooks registered")
    }

    /**
     * Layer 1: Make every Aweme return isAd = false.
     *
     * Known targets:
     * - com.ss.android.ugc.aweme.feed.model.Aweme.isAd() -> boolean
     * - com.ss.android.ugc.aweme.feed.model.Aweme.getAwemeType() -> int
     */
    private fun hookAwemeIsAd(lpparam: XC_LoadPackage.LoadPackageParam) {
        val possibleMethods = listOf(
            "isAd" to "()Z",
            "getAwemeType" to "()I",
            "isAds" to "()Z",
        )

        for ((methodName, signature) in possibleMethods) {
            try {
                val awemeClass = XposedHelpers.findClass(
                    "com.ss.android.ugc.aweme.feed.model.Aweme",
                    lpparam.classLoader
                )
                awemeClass.declaredMethods
                    .firstOrNull { it.name == methodName }
                    ?.let { method ->
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                when (method.returnType) {
                                    Boolean::class.javaPrimitiveType -> param.result = false
                                    Int::class.javaPrimitiveType -> {
                                        // aweme_type == 2 or specific values indicate ads
                                        val current = param.result as? Int ?: 0
                                        if (current in 1..99) {
                                            param.result = 0 // force non-ad type
                                        }
                                    }
                                }
                            }
                        })
                        MainHook.log("[AntiAds] Hooked: Aweme.$methodName")
                    }
            } catch (e: Exception) {
                // Silently try next candidate
            }
        }
    }

    /**
     * Layer 2: Filter ad items from feed data lists.
     *
     * Targets:
     * - Any ArrayList or List returned by feed API methods
     * - Hook on methods that return List<Aweme>
     */
    private fun hookFeedDataLoading(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Try to find feed-related API response classes
            val feedResponseCandidates = listOf(
                "com.ss.android.ugc.aweme.feed.model.FeedItemList",
                "com.ss.android.ugc.aweme.feed.model.BaseFeedObjectList",
                "com.ss.android.ugc.aweme.feed.model.AwemeList",
            )

            for (className in feedResponseCandidates) {
                try {
                    val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                    // Hook all methods that return List<Aweme>
                    for (method in clazz.declaredMethods) {
                        if (method.returnType == List::class.java ||
                            method.returnType == java.util.ArrayList::class.java
                        ) {
                            XposedBridge.hookMethod(method, createListFilterHook())
                            MainHook.log("[AntiAds] Hooked feed list: $className.${method.name}")
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            MainHook.log("[AntiAds] Feed loading hook setup failed: ${e.message}")
        }
    }

    private fun createListFilterHook(): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val list = param.result as? MutableList<*> ?: return
                if (list.isEmpty()) return

                val iterator = list.listIterator()
                while (iterator.hasNext()) {
                    val item = iterator.next() ?: continue
                    try {
                        // Try to call isAd() via reflection
                        val isAd = try {
                            item.javaClass.getMethod("isAd").invoke(item) as? Boolean ?: false
                        } catch (_: Exception) {
                            // Try field check
                            try {
                                val field = item.javaClass.getDeclaredField("isAd")
                                field.isAccessible = true
                                field.getBoolean(item)
                            } catch (_: Exception) {
                                false
                            }
                        }

                        if (isAd) {
                            iterator.remove()
                        }
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /**
     * Layer 3: Prevent ad video controller from playing ad content.
     */
    private fun hookAdVideoController(lpparam: XC_LoadPackage.LoadPackageParam) {
        val adControllerCandidates = listOf(
            "com.ss.android.ugc.aweme.feed.ad.AdVideoViewController",
            "com.ss.android.ugc.aweme.feed.ad.AdViewController",
            "com.bytedance.ad.AdVideoController",
        )

        for (className in adControllerCandidates) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                // Hook show/display/play methods
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("show", true) ||
                        method.name.contains("display", true) ||
                        method.name.contains("play", true)
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                                MainHook.log("[AntiAds] Blocked ad display: ${method.name}")
                            }
                        })
                    }
                }
                MainHook.log("[AntiAds] Hooked ad controller: $className")
            } catch (_: Exception) { }
        }
    }
}

