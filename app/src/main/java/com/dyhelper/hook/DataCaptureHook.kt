package com.dyhelper.hook

import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class DataCaptureHook : BaseHook {
    companion object { var currentAweme: Any? = null }
    override fun name() = "Data"

    override fun init(loader: ClassLoader): Boolean {
        val candidates = listOf("com.ss.android.ugc.aweme.feed.model.Aweme","com.ss.android.ugc.aweme.feed.model.AwemeBase","com.ss.android.ugc.aweme.feed.model.BaseFeedItem","com.bytedance.ies.ugc.aweme.feed.model.Aweme")
        for (name in candidates) { val cls = ClassFinder.findClass(loader, name); if (cls != null && hookIsAd(cls)) { HookUtils.log("[Data] Found: " + name); return true } }
        for (pkg in listOf("com.ss.android.ugc.aweme.feed.model","com.bytedance.ies.ugc.aweme.feed.model","com.ss.android.ugc.aweme.model")) { for (cls in ClassFinder.scanClasses(loader, pkg)) { if (hookIsAd(cls)) { HookUtils.log("[Data] Auto: " + cls.name); return true } } }
        for (cls in ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.feed")) { if (hookIsAd(cls)) { HookUtils.log("[Data] Broad: " + cls.name); return true } }
        return false
    }

    private fun hookIsAd(cls: Class<*>): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "isAd" && m.returnType == Boolean::class.javaPrimitiveType && m.parameterTypes.isEmpty()) {
                m.isAccessible = true
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) { currentAweme = p.thisObject; p.result = false }
                    })
                    HookUtils.log("[Data] isAd hooked: " + cls.name); return true
                } catch (t: Throwable) { HookUtils.log("[Data] hook err: " + t.message) }
            }
        }
        return false
    }

    fun isImage(): Boolean { val a=currentAweme?:return false;try{val at=HookUtils.getField(a,"awemeType") as? Int;if(at!=null)return at==68}catch(_:Exception){};try{val img=HookUtils.callMethod(a,"isImage") as? Boolean;if(img!=null)return img}catch(_:Exception){};try{val at2=HookUtils.callMethod(a,"getAwemeType") as? Int;if(at2!=null)return at2==68}catch(_:Exception){};return false }
    fun getVideoUrl(): String? { val a=currentAweme?:return null;for(mn in listOf("getFirstPlayAddr","getVideoPlayAddr","getOriginPlayAddr","getDownloadAddr")){try{val u=HookUtils.callMethod(a,mn) as? String;if(!u.isNullOrEmpty())return extractUrl(u)}catch(_:Exception){}};try{val v=HookUtils.getField(a,"video")!!;val pa=HookUtils.getField(v,"playAddr")!!;val ul=HookUtils.callMethod(pa,"getUrlList") as? List<*>;return ul?.firstOrNull()?.toString()}catch(_:Exception){return null} }
    fun getMusicUrl(): String? { try{val a=currentAweme?:return null;val m=HookUtils.getField(a,"music")!!;val pu=HookUtils.getField(m,"playUrl")!!;val ul=HookUtils.callMethod(pu,"getUrlList") as? List<*>;return ul?.firstOrNull()?.toString()}catch(_:Exception){return null} }
    fun getDesc() = try{HookUtils.getField(currentAweme!!,"desc") as? String?:""}catch(_:Exception){""}
    private fun extractUrl(raw: String): String { if(raw.startsWith("http"))return raw;try{if(raw.contains("\"url_list\"")){val s=raw.indexOf("\"http");if(s>=0){val e=raw.indexOf("\"",s+1);if(e>s)return raw.substring(s,e)}}}catch(_:Exception){};return raw }
}