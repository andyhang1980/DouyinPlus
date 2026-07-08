package com.dyhelper

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.dyhelper.adaptive.HookCache
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.dyhelper.hooks.AntiAdsHook
import com.dyhelper.hooks.ShareMenuHook

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "DouyinHelper"
        val DOUYIN_PKGS = arrayOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
        )

        var classLoader: ClassLoader? = null
        var currentVersion: String = "unknown"
        var currentPackage: String = ""

        fun getModuleClassLoader(): ClassLoader? = classLoader

        fun log(msg: String) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in DOUYIN_PKGS) return

        currentPackage = lpparam.packageName
        classLoader = lpparam.classLoader

        log("========================================")
        log("DouyinHelper v1.2 loaded -> ${lpparam.packageName}")
        log("Process: ${lpparam.processName}")
        log("========================================")

        // DO NOT filter by process name - Douyin versions vary
        // Run hooks immediately, don't wait for Application
        try {
            initAllHooks(lpparam)
        } catch (e: Exception) {
            log("ERROR in initAllHooks: ${e.message}")
            Log.e(TAG, "initAllHooks error", e)
        }

        // Also try to hook Application for cache init and version detection
        try {
            val appClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.app.host.HostApplication",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(appClass, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val ctx = param.thisObject as Context
                            currentVersion = ctx.packageManager
                                .getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
                            log("Douyin version: $currentVersion")
                            HookCache.init(ctx)
                        } catch (_: Exception) {}
                    }
                })
        } catch (_: Exception) {
            log("Note: HostApplication not hooked (non-critical)")
        }
    }

    private fun initAllHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Initializing hooks...")
        AntiAdsHook.init(lpparam)
        ShareMenuHook.init(lpparam)
        log("All hooks initialized!")
    }
}
