package com.dyhelper

import android.content.Context
import android.util.Log
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
            "com.ss.android.ugc.aweme",      // 抖音
            "com.ss.android.ugc.aweme.lite"  // 抖音极速版
        )
        var classLoader: ClassLoader? = null
    var moduleRes: Any? = null

        fun getModuleClassLoader(): ClassLoader? = classLoader

    fun log(msg: String) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in DOUYIN_PKGS) return

        log("========================================")
        log("抖音小能手 loaded -> ${lpparam.packageName}")
        log("Process: ${lpparam.processName}")

        // Capture application context and classloader
        try {
            val appClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.app.host.HostApplication",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(appClass, "onCreate",
                object : de.robv.android.xposed.XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        classLoader = lpparam.classLoader
                    val ctx = param.thisObject as Context
                        log("抖音版本: " + ctx.packageManager
                            .getPackageInfo(ctx.packageName, 0).versionName)
                    }
                })
        } catch (_: Exception) { }

        try {
            AntiAdsHook.init(lpparam)
            ShareMenuHook.init(lpparam)
        } catch (e: Exception) {
            log("ERROR: ${e.message}")
            Log.e(TAG, "Hook init error", e)
        }

        log("抖音小能手 hooks initialized!")
    }
}

