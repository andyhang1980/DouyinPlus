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
        // Known class names from bear + older versions
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
        // Auto-scan feed.model package
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
        // Broader scan in feed package
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

    fun isImage(): Boolean = try {
        val aweme = currentAweme ?: return false
        // Try awemeType field
        try {
            val at = XposedHelpers.getObjectField(aweme, "awemeType") as? Int
            if (at != null) return at == 68
        } catch (_: Exception) {}
        // Try isImage method
        try {
            val img = XposedHelpers.callMethod(aweme, "isImage") as? Boolean
            if (img != null) return img
        } catch (_: Exception) {}
        // Try getAwemeType
        try {
            val at2 = XposedHelpers.callMethod(aweme, "getAwemeType") as? Int
            if (at2 != null) return at2 == 68
        } catch (_: Exception) {}
        false
    } catch (_: Exception) { false }

    fun getVideoUrl(): String? = try {
        val aweme = currentAweme ?: return null
        // Try multiple URL extraction methods
        for (methodName in listOf("getFirstPlayAddr", "getVideoPlayAddr", "getOriginPlayAddr", "getDownloadAddr")) {
            try {
                val url = XposedHelpers.callMethod(aweme, methodName) as? String
                if (!url.isNullOrEmpty()) {
                    // Extract actual URL from JSON if needed
                    return extractUrl(url)
                }
            } catch (_: Exception) {}
        }
        // Try video.playAddr.urlList
        try {
            val video = XposedHelpers.getObjectField(aweme, "video")
            val playAddr = XposedHelpers.getObjectField(video, "playAddr")
            val urlList = XposedHelpers.callMethod(playAddr, "getUrlList") as? List<*>
            urlList?.firstOrNull()?.toString()
        } catch (_: Exception) { null }
    } catch (_: Exception) { null }

    fun getMusicUrl(): String? = try {
        val aweme = currentAweme ?: return null
        val m = XposedHelpers.getObjectField(aweme, "music")
        val pu = XposedHelpers.getObjectField(m, "playUrl")
        val ul = XposedHelpers.callMethod(pu, "getUrlList") as? List<*>
        ul?.firstOrNull()?.toString()
    } catch (_: Exception) { null }

    fun getDesc(): String = try {
        XposedHelpers.getObjectField(currentAweme, "desc") as? String ?: ""
    } catch (_: Exception) { "" }

    private fun extractUrl(raw: String): String {
        // Handle URLList format: sometimes the method returns a URLList object
        if (raw.startsWith("http")) return raw
        // Try to parse as JSON URLList
        try { if (raw.contains("\"url_list\"")) {
            val start = raw.indexOf("\"http")
            if (start >= 0) {
                val end = raw.indexOf("\"", start + 1)
                if (end > start) return raw.substring(start, end)
            }
        }} catch (_: Exception) {}
        raw
    }
}
