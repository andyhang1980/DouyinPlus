package com.dyhelper.hook

import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class DataCaptureHook : BaseHook {

    companion object {
        var currentAweme: Any? = null
        var appContext: android.content.Context? = null
    }

    override fun name() = "数据捕获"

    override fun init(loader: ClassLoader): Boolean {
        // Aweme.isAd (same as Bear c1/a.java d())
        return hookBySig(loader, "Aweme",
            "com.ss.android.ugc.aweme.feed.model.Aweme",
            Boolean::class.javaPrimitiveType, "isAd",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    currentAweme = param.thisObject
                    param.result = false
                }
            })
    }

    fun isImageAweme(): Boolean {
        return try {
            (XposedHelpers.getObjectField(currentAweme, "awemeType") as? Int) == 68
        } catch (_: Exception) { false }
    }

    fun getVideoUrl(): String? {
        return try {
            XposedHelpers.callMethod(currentAweme, "getFirstPlayAddr") as? String
        } catch (_: Exception) { null }
    }

    fun getMusicUrl(): String? {
        return try {
            val music = XposedHelpers.getObjectField(currentAweme, "music")
            val playUrl = XposedHelpers.getObjectField(music, "playUrl")
            val urlList = XposedHelpers.callMethod(playUrl, "getUrlList") as? List<*>
            urlList?.firstOrNull()?.toString()
        } catch (_: Exception) { null }
    }

    fun getDesc(): String {
        return try {
            XposedHelpers.getObjectField(currentAweme, "desc") as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private fun hookBySig(
        loader: ClassLoader, tag: String, className: String,
        returnType: Class<*>?, methodName: String,
        vararg paramTypes: Class<*>,
        callback: XC_MethodHook
    ): Boolean {
        val clazz = XposedHelpers.findClassIfExists(className, loader)
        if (clazz == null) {
            HookUtils.log("[" + tag + "] Class not found: " + className)
            return false
        }
        for (m in clazz.declaredMethods) {
            if (m.name != methodName) continue
            if (returnType != null && m.returnType != returnType) continue
            val params = m.parameterTypes
            if (params.size != paramTypes.size) continue
            var match = true
            var i = 0
            while (i < paramTypes.size) {
                if (paramTypes[i] != null && paramTypes[i] != params[i]) {
                    match = false
                    break
                }
                i++
            }
            if (!match) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, callback)
            HookUtils.log("[" + tag + "] Hooked: " + clazz.simpleName + "." + m.name)
            return true
        }
        HookUtils.log("[" + tag + "] Method not found: " + methodName)
        return false
    }
}
