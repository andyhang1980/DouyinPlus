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
        return h(loader, "Aweme", "com.ss.android.ugc.aweme.feed.model.Aweme",
            Boolean::class.javaPrimitiveType, "isAd", emptyArray(),
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    currentAweme = p.thisObject
                    p.result = false
                }
            })
    }

    fun isImageAweme(): Boolean {
        return try { (XposedHelpers.getObjectField(currentAweme, "awemeType") as? Int) == 68 }
        catch (_: Exception) { false }
    }
    fun getVideoUrl(): String? {
        return try { XposedHelpers.callMethod(currentAweme, "getFirstPlayAddr") as? String }
        catch (_: Exception) { null }
    }
    fun getMusicUrl(): String? {
        return try {
            val m = XposedHelpers.getObjectField(currentAweme, "music")
            val pu = XposedHelpers.getObjectField(m, "playUrl")
            val ul = XposedHelpers.callMethod(pu, "getUrlList") as? List<*>
            ul?.firstOrNull()?.toString()
        } catch (_: Exception) { null }
    }
    fun getDesc(): String {
        return try { XposedHelpers.getObjectField(currentAweme, "desc") as? String ?: "" }
        catch (_: Exception) { "" }
    }

    private fun h(loader: ClassLoader, tag: String, cls: String,
                  rt: Class<*>?, mn: String, pts: Array<Class<*>>,
                  cb: XC_MethodHook): Boolean {
        val c = XposedHelpers.findClassIfExists(cls, loader) ?: run {
            HookUtils.log("[" + tag + "] Class not found"); return false
        }
        for (m in c.declaredMethods) {
            if (m.name != mn) continue
            if (rt != null && m.returnType != rt) continue
            val mp = m.parameterTypes
            if (mp.size != pts.size) continue
            var ok = true
            var i = 0
            while (i < pts.size) { if (pts[i] != null && pts[i] != mp[i]) { ok = false; break }; i++ }
            if (!ok) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, cb)
            return true
        }
        return false
    }
}
