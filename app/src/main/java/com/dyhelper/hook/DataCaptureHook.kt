package com.dyhelper.hook

import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class DataCaptureHook : BaseHook {
    companion object {
        var currentAweme: Any? = null
    }

    override fun name() = "Data"

    override fun init(loader: ClassLoader): Boolean {
        val candidates = listOf(
            "com.ss.android.ugc.aweme.feed.model.Aweme",
            "com.ss.android.ugc.aweme.feed.model.AwemeBase",
            "com.ss.android.ugc.aweme.feed.model.BaseFeedItem",
            "com.bytedance.ies.ugc.aweme.feed.model.Aweme"
        )
        for (name in candidates) {
            val cls = ClassFinder.findClass(loader, name)
            if (cls != null && hookIsAd(cls)) {
                HookUtils.log("[Data] Found: " + name)
                return true
            }
        }
        val scanPkgs = listOf(
            "com.ss.android.ugc.aweme.feed.model",
            "com.bytedance.ies.ugc.aweme.feed.model",
            "com.ss.android.ugc.aweme.model"
        )
        for (pkg in scanPkgs) {
            val classes = ClassFinder.scanClasses(loader, pkg)
            for (cls in classes) {
                if (hookIsAd(cls)) {
                    HookUtils.log("[Data] Auto: " + cls.name)
                    return true
                }
            }
        }
        val broad = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.feed")
        for (cls in broad) {
            if (hookIsAd(cls)) {
                HookUtils.log("[Data] Broad: " + cls.name)
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
                HookUtils.log("[Data] isAd hooked: " + cls.name)
                return true
            }
        }
        return false
    }

    fun isImage(): Boolean {
        val aweme = currentAweme ?: return false
        try {
            val at = HookUtils.getField(aweme, "awemeType") as? Int
            if (at != null) return at == 68
        } catch (_: Exception) {}
        try {
            val img = HookUtils.callMethod(aweme, "isImage") as? Boolean
            if (img != null) return img
        } catch (_: Exception) {}
        try {
            val at2 = HookUtils.callMethod(aweme, "getAwemeType") as? Int
            if (at2 != null) return at2 == 68
        } catch (_: Exception) {}
        return false
    }

    fun getVideoUrl(): String? {
        val aweme = currentAweme ?: return null
        for (methodName in listOf("getFirstPlayAddr", "getVideoPlayAddr", "getOriginPlayAddr", "getDownloadAddr")) {
            try {
                val url = HookUtils.callMethod(aweme, methodName) as? String
                if (!url.isNullOrEmpty()) return extractUrl(url)
            } catch (_: Exception) {}
        }
        try {
            val video = HookUtils.getField(aweme, "video")
            val playAddr = HookUtils.getField(video, "playAddr")
            val urlList = HookUtils.callMethod(playAddr, "getUrlList") as? List<*>
            val first = urlList?.firstOrNull()?.toString()
            if (first != null) return first
        } catch (_: Exception) {}
        return null
    }

    fun getMusicUrl(): String? {
        try {
            val aweme = currentAweme ?: return null
            val m = HookUtils.getField(aweme, "music")
            val pu = HookUtils.getField(m, "playUrl")
            val ul = HookUtils.callMethod(pu, "getUrlList") as? List<*>
            return ul?.firstOrNull()?.toString()
        } catch (_: Exception) { return null }
    }

    fun getDesc(): String {
        return try {
            HookUtils.getField(currentAweme!!, "desc") as? String ?: ""
        } catch (_: Exception) { "" }
    }

    private fun extractUrl(raw: String): String {
        if (raw.startsWith("http")) return raw
        try {
            if (raw.contains("\"url_list\"")) {
                val start = raw.indexOf("\"http")
                if (start >= 0) {
                    val end = raw.indexOf("\"", start + 1)
                    if (end > start) return raw.substring(start, end)
                }
            }
        } catch (_: Exception) {}
        return raw
    }
}
