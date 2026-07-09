package com.dyhelper.hook

import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class DataCaptureHook : BaseHook {
    companion object {
        var currentAweme: Any? = null
    }

    override fun name() = "Data"

    override fun init(loader: ClassLoader): Boolean {
        val candidates = listOf(
            "com.ss.android.ugc.aweme.feed.model.Aweme",
            "com.ss.android.ugc.aweme.feed.model.AwemeBase"
        )
        for (name in candidates) {
            val cls = ClassFinder.findClass(loader, name)
            if (cls != null && hookIsAd(cls)) {
                HookUtils.log("[Data] Found: " + name)
                return true
            }
        }
        // Auto-scan
        val classes = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.feed.model")
        for (cls in classes) {
            if (hookIsAd(cls)) {
                HookUtils.log("[Data] Auto: " + cls.name)
                return true
            }
        }
        return false
    }

    private fun hookIsAd(cls: Class<*>): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "isAd" && m.returnType == Boolean::class.javaPrimitiveType &&
                m.parameterTypes.isEmpty()) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        currentAweme = p.thisObject
                        p.result = false
                    }
                })
                return true
            }
        }
        return false
    }

    fun isImage(): Boolean = try {
        (XposedHelpers.getObjectField(currentAweme, "awemeType") as? Int) == 68
    } catch (_: Exception) { false }

    fun getVideoUrl(): String? = try {
        XposedHelpers.callMethod(currentAweme, "getFirstPlayAddr") as? String
    } catch (_: Exception) { null }

    fun getMusicUrl(): String? = try {
        val m = XposedHelpers.getObjectField(currentAweme, "music")
        val pu = XposedHelpers.getObjectField(m, "playUrl")
        val ul = XposedHelpers.callMethod(pu, "getUrlList") as? List<*>
        ul?.firstOrNull()?.toString()
    } catch (_: Exception) { null }

    fun getDesc(): String = try {
        XposedHelpers.getObjectField(currentAweme, "desc") as? String ?: ""
    } catch (_: Exception) { "" }
}
