package com.dyhelper.hook

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
        val cls = XposedHelpers.findClassIfExists(
            "com.ss.android.ugc.aweme.feed.model.Aweme", loader) ?: return false
        for (m in cls.declaredMethods) {
            if (m.name == "isAd" && m.returnType == Boolean::class.javaPrimitiveType) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        currentAweme = p.thisObject
                        p.result = false
                    }
                })
                HookUtils.log("[Data] Hooked Aweme.isAd")
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
