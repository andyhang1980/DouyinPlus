package com.dyhelper.hook

import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

class AntiAdHook : BaseHook {
    override fun name() = "Ad"
    override fun init(loader: ClassLoader): Boolean { var ok=false
        for (n in listOf("com.bytedance.ies.ugc.aweme.commercialize.splash.show.SplashAdActivity","com.ss.android.ugc.aweme.commercialize.splash.show.SplashAdActivity")){val c=ClassFinder.findClass(loader,n);if(c!=null&&hookFinish(c)){ok=true;break}}
        if(!ok){outer@for(p in listOf("com.bytedance.ies.ugc.aweme.commercialize","com.ss.android.ugc.aweme.commercialize")){for(c in ClassFinder.scanClasses(loader,p)){if(c.simpleName.contains("SplashAd")||c.simpleName.contains("AdActivity")){if(hookFinish(c)){HookUtils.log("[Ad] Auto: "+c.name);ok=true;break@outer}}}}}
        for(n in listOf("com.ss.android.ugc.aweme.splash.SplashActivity")){val c=ClassFinder.findClass(loader,n);if(c!=null&&hookSplash(c)){ok=true;break}}
        if(!ok){for(c in ClassFinder.scanClasses(loader,"com.ss.android.ugc.aweme.splash")){if(c.simpleName.contains("Splash")&&c.simpleName.endsWith("Activity")){if(hookSplash(c)){HookUtils.log("[Ad] Auto: "+c.name);ok=true;break}}}}
        if(!ok){val sc=HookUtils.findClass(loader,"com.ss.android.ugc.aweme.splash.SplashActivity");if(sc!=null&&hookSplash(sc))ok=true}
        return ok }
    private fun hookFinish(cls:Class<*>):Boolean { try{for(m in cls.declaredMethods){if(m.name=="onCreate"&&m.parameterTypes.size==1){m.isAccessible=true;XposedBridge.hookMethod(m,object:XC_MethodHook(){override fun afterHookedMethod(p:MethodHookParam){(p.thisObject as? android.app.Activity)?.finish()}});HookUtils.log("[Ad] "+cls.name);return true}}}catch(t:Throwable){HookUtils.log("[Ad] err: "+t.message)};return false }
    private fun hookSplash(cls:Class<*>):Boolean { try{for(m in cls.declaredMethods){if(m.name=="onCreate"&&m.parameterTypes.size==1){m.isAccessible=true;XposedBridge.hookMethod(m,object:XC_MethodHook(){override fun afterHookedMethod(p:MethodHookParam){try{HookUtils.callMethod(p.thisObject,"goMainActivity")}catch(_:Exception){(p.thisObject as? android.app.Activity)?.finish()}}});HookUtils.log("[Ad] Splash: "+cls.name);return true}}}catch(t:Throwable){HookUtils.log("[Ad] spl err: "+t.message)};return false }
}