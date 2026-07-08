package com.dyhelper

import android.content.Context
import android.os.Build
import android.util.Log
import com.dyhelper.adaptive.HookCache
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
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
        private val DOUYIN_VARIANTS = arrayOf(
            "com.ss.android.ugc.aweme.live",
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
        val allPkgs = DOUYIN_PKGS + DOUYIN_VARIANTS
        if (lpparam.packageName !in allPkgs) return

        currentPackage = lpparam.packageName

        log("========================================")
        log("抖音小能手 v1.1 (adaptive) loaded -> ${lpparam.packageName}")
        log("Process: ${lpparam.processName}")
        log("Android SDK: ${Build.VERSION.SDK_INT}")
        log("========================================")

        if (lpparam.processName != lpparam.packageName &&
            lpparam.processName != "${lpparam.packageName}:main") {
            log("Skipping sub-process: ${lpparam.processName}")
            return
        }

        classLoader = lpparam.classLoader

        try {
            val appClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.app.host.HostApplication",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(appClass, "onCreate",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.thisObject as Context
                        currentVersion = try {
                            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "unknown"
                        } catch (_: Exception) { "unknown" }
                        log("抖音版本: $currentVersion")

                        // Initialize adaptive cache with app context
                        HookCache.init(ctx)

                        // Now run hooks
                        initAllHooks(lpparam)
                    }
                })
        } catch (_: Exception) {
            log("WARNING: Could not hook HostApplication.onCreate, using fallback")
            initAllHooks(lpparam)
        }
    }

    private fun initAllHooks(lpparam: XC_LoadPackage.LoadPackageParam) {
        log("Starting hook initialization...")

        try {
            AntiAdsHook.init(lpparam)
            ShareMenuHook.init(lpparam)
        } catch (e: Exception) {
            log("ERROR during hook init: ${e.message}")
            Log.e(TAG, "Hook init error", e)
        }

        log("抖音小能手 hooks initialized!")
    }
}
