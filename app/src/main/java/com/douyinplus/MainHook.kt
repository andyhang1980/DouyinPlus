package com.douyinplus

import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.douyinplus.hooks.AntiAdsHook
import com.douyinplus.hooks.AntiUpdateHook
import com.douyinplus.hooks.VideoDownloadHook
import java.io.File

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "DouyinPlus"
        const val DOUYIN_PKG = "com.ss.android.ugc.aweme"

        // Preferences for toggle switches
        val prefs: XSharedPreferences by lazy {
            val p = XSharedPreferences("com.douyinplus", "douyinplus_prefs")
            p.makeWorldReadable()
            p
        }

        fun isEnabled(key: String, default: Boolean = true): Boolean {
            return prefs.getBoolean(key, default)
        }

        fun log(msg: String) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }

        // Download directory
        val downloadDir: File by lazy {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DouyinPlus").apply {
                if (!exists()) mkdirs()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Only hook Douyin
        if (lpparam.packageName != DOUYIN_PKG) return

        // Load preferences (try to make world readable first)
        prefs.reload()

        log("========================================")
        log("DouyinPlus loaded into ${lpparam.packageName}")
        log("Process: ${lpparam.processName}")
        log("========================================")

        try {
            // Initialize each hook module
            if (isEnabled("anti_ads", true)) {
                AntiAdsHook.init(lpparam)
            }
            if (isEnabled("anti_update", true)) {
                AntiUpdateHook.init(lpparam)
            }
            if (isEnabled("video_download", true)) {
                VideoDownloadHook.init(lpparam)
            }
        } catch (e: Exception) {
            log("ERROR during hook initialization: ${e.message}")
            Log.e(TAG, "Hook init error", e)
        }

        log("DouyinPlus hooks initialized successfully")
    }
}
