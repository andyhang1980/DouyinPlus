package com.douyinplus.hooks

import com.douyinplus.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Anti-Update Hook: Prevent Douyin from checking for and downloading updates.
 *
 * Strategy:
 * 1. Hook the update check API to return "no update available"
 * 2. Hook the update download manager to block downloads
 * 3. Hook update notification dialog to prevent display
 * 4. Hook PackageManager to hide update APK downloads
 */
object AntiUpdateHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[AntiUpdate] Initializing...")

        // Layer 1: Block update check API
        hookUpdateCheckApi(lpparam)

        // Layer 2: Block update download
        hookUpdateDownload(lpparam)

        // Layer 3: Block update dialog
        hookUpdateDialog(lpparam)

        MainHook.log("[AntiUpdate] Hooks registered")
    }

    /**
     * Layer 1: Intercept update check API responses.
     *
     * Common update-related classes in Douyin:
     * - com.ss.android.ugc.aweme.update.UpdateManager
     * - com.ss.android.ugc.aweme.app.api.UpdateApi
     * - com.bytedance.common.utility.AppUpdateManager
     */
    private fun hookUpdateCheckApi(lpparam: XC_LoadPackage.LoadPackageParam) {
        val updateClasses = listOf(
            "com.ss.android.ugc.aweme.update.UpdateManager",
            "com.ss.android.ugc.aweme.app.api.UpdateApi",
            "com.bytedance.common.utility.collection.UpdateManager",
        )

        for (className in updateClasses) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)

                // Hook all "check" related methods
                for (method in clazz.declaredMethods) {
                    if (method.name.contains("check", true) ||
                        method.name.contains("update", true) ||
                        method.name.contains("version", true)
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null // Block the call
                                MainHook.log("[AntiUpdate] Blocked: ${clazz.simpleName}.${method.name}")
                            }
                        })
                    }
                }
                MainHook.log("[AntiUpdate] Hooked update checker: $className")
            } catch (_: Exception) { }
        }
    }

    /**
     * Layer 2: Block update APK download.
     *
     * Douyin uses DownloadManager or custom download service.
     * Hook the download initiation to prevent APK downloads.
     */
    private fun hookUpdateDownload(lpparam: XC_LoadPackage.LoadPackageParam) {
        val downloadClasses = listOf(
            "com.ss.android.ugc.aweme.update.DownloadManager",
            "com.ss.android.ugc.aweme.download.DownloadService",
            "com.bytedance.frameworks.baselib.network.download.DownloadManager",
        )

        for (className in downloadClasses) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)

                for (method in clazz.declaredMethods) {
                    if (method.name.contains("download", true) ||
                        method.name.contains("start", true) ||
                        method.name.contains("enqueue", true)
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                // Check if this download is for an APK update
                                val url = param.args.firstOrNull()?.toString() ?: ""
                                if (url.contains(".apk") || url.contains("update") ||
                                    url.contains("upgrade")
                                ) {
                                    param.result = null
                                    MainHook.log("[AntiUpdate] Blocked APK download: ${url.take(80)}")
                                }
                            }
                        })
                    }
                }
                MainHook.log("[AntiUpdate] Hooked download manager: $className")
            } catch (_: Exception) { }
        }

        // Block Android DownloadManager for .apk files
        try {
            val dmClass = XposedHelpers.findClass(
                "android.app.DownloadManager",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                dmClass,
                "enqueue",
                android.app.DownloadManager.Request::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val request = param.args[0] as? android.app.DownloadManager.Request
                        if (request != null) {
                            try {
                                val uriField = request.javaClass.getDeclaredField("mUri")
                                uriField.isAccessible = true
                                val uri = uriField.get(request)?.toString() ?: ""
                                if (uri.contains(".apk") && uri.contains("douyin")) {
                                    param.result = -1L // Block
                                    MainHook.log("[AntiUpdate] Blocked DownloadManager APK enqueue")
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
            )
            MainHook.log("[AntiUpdate] Hooked Android DownloadManager")
        } catch (_: Exception) { }
    }

    /**
     * Layer 3: Block update notification dialogs.
     */
    private fun hookUpdateDialog(lpparam: XC_LoadPackage.LoadPackageParam) {
        val dialogClasses = listOf(
            "com.ss.android.ugc.aweme.update.UpdateDialog",
            "com.ss.android.ugc.aweme.main.UpdateDialogActivity",
            "com.bytedance.common.utility.collection.UpdateDialog",
        )

        for (className in dialogClasses) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)

                // Hook show/display methods
                for (method in clazz.declaredMethods) {
                    if (method.name == "show" ||
                        method.name == "display" ||
                        method.name == "onCreate"
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = null
                                MainHook.log("[AntiUpdate] Blocked update dialog: ${clazz.simpleName}.${method.name}")
                            }
                        })
                    }
                }
                MainHook.log("[AntiUpdate] Hooked dialog: $className")
            } catch (_: Exception) { }
        }

        // Also try to hook SharedPreferences to modify update-related keys
        try {
            val spClass = XposedHelpers.findClass(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                spClass,
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        // Block update-related preference checks
                        if (key.contains("update", true) ||
                            key.contains("upgrade", true) ||
                            key.contains("version", true)
                        ) {
                            val defaultValue = param.args[1] as? Boolean ?: false
                            if (defaultValue) {
                                param.result = false
                            }
                        }
                    }
                }
            )
            MainHook.log("[AntiUpdate] Hooked SharedPreferences for update flags")
        } catch (_: Exception) { }
    }
}
