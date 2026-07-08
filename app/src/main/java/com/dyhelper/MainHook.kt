package com.dyhelper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "DouyinHelper"
        val PKGS = arrayOf(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite"
        )
        var classLoader: ClassLoader? = null

        fun log(msg: String) {
            Log.d(TAG, msg)
            XposedBridge.log("[$TAG] $msg")
        }

        fun toast(ctx: Context, msg: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, "[DouyinHelper] $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in PKGS) return

        classLoader = lpparam.classLoader
        log("LOADED -> ${lpparam.packageName} | process: ${lpparam.processName}")

        // ==== STEP 1: Basic Toast test - verify module loads ====
        try {
            // Hook Application to get context for Toast
            XposedHelpers.findAndHookMethod(
                "android.app.Application", lpparam.classLoader, "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val ctx = param.thisObject as Context
                        if (ctx.packageName in PKGS) {
                            log("Application created: ${ctx.packageName}")
                            toast(ctx, "Module loaded! v1.3-test")
                            initHooks(lpparam, ctx)
                        }
                    }
                })
        } catch (e: Exception) {
            log("ERROR hooking Application: ${e.message}")
        }
    }

    private fun initHooks(lpparam: XC_LoadPackage.LoadPackageParam, ctx: Context) {
        log("=== initHooks ===")

        // ==== Anti-Ads: Hook isAd() on Aweme model ====
        try {
            // Try multiple known class names for Aweme
            val awemeClass = findAnyClass(listOf(
                "com.ss.android.ugc.aweme.feed.model.Aweme",
                "com.ss.android.ugc.aweme.feed.model.AwemeRaw",
                "com.ss.android.ugc.aweme.feed.model.BaseFeedData"
            ), lpparam.classLoader)

            if (awemeClass != null) {
                // Find and hook isAd method
                var hooked = false
                for (method in awemeClass.declaredMethods) {
                    if (method.name == "isAd" && method.parameterTypes.isEmpty() &&
                        (method.returnType == Boolean::class.java || method.returnType == Boolean::class.javaPrimitiveType)) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                param.result = false
                            }
                        })
                        log("Hooked: ${awemeClass.name}.isAd()")
                        hooked = true
                        break
                    }
                }
                if (hooked) toast(ctx, "Anti-Ads hooked!")
                else log("isAd() not found on ${awemeClass.name}")
            } else {
                log("Aweme class not found")
            }
        } catch (e: Exception) {
            log("AntiAds error: ${e.message}")
        }

        // ==== Share Menu: Find and hook share panel ====
        try {
            // Try to find share panel LinearLayout by its characteristics
            val panelClass = findClassByParent(
                "android.widget.LinearLayout",
                lpparam.classLoader,
                "sharer"
            )
            if (panelClass != null) {
                log("Found share panel: ${panelClass.name}")
                // Hook onMeasure to inject our UI
                XposedHelpers.findAndHookMethod(panelClass, "onMeasure",
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val view = param.thisObject as? android.view.ViewGroup ?: return
                            if (view.findViewWithTag<android.view.View>(888888) != null) return
                            if (view.childCount < 1) return

                            // Inject a simple TextView to prove it works
                            val tv = android.widget.TextView(view.context).apply {
                                text = "DouyinHelper"
                                setTextColor(0xFFFF2D55.toInt())
                                textSize = 14f
                                setPadding(20, 10, 20, 10)
                                tag = 888888
                            }
                            view.addView(tv)
                            toast(ctx, "Share menu injected!")
                            log("Injected share menu item")
                        }
                    })
            } else {
                log("Share panel not found")
            }
        } catch (e: Exception) {
            log("ShareMenu error: ${e.message}")
        }

        log("=== initHooks done ===")
    }

    // ---- Helpers ----

    private fun findAnyClass(names: List<String>, cl: ClassLoader): Class<*>? {
        for (name in names) {
            try { return cl.loadClass(name) } catch (_: Exception) {}
        }
        return null
    }

    private fun findClassByParent(parentName: String, cl: ClassLoader, hint: String): Class<*>? {
        // Try known names first
        val candidates = listOf(
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
            "com.ss.android.ugc.aweme.share.base.ui.BaseSharePanel",
            "com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel"
        )
        for (name in candidates) {
            try { return cl.loadClass(name) } catch (_: Exception) {}
        }

        // Walk all loaded classes looking for one that extends LinearLayout
        // and has "sharer" or "panel" or "share" in its name
        try {
            val pathListField = XposedHelpers.findField(
                Class.forName("dalvik.system.BaseDexClassLoader"), "pathList"
            )
            val pathList = pathListField.get(cl)
            val dexElementsField = XposedHelpers.findField(pathList.javaClass, "dexElements")
            val dexElements = dexElementsField.get(pathList) as? Array<*>

            dexElements?.forEach { element ->
                try {
                    val dexFileField = XposedHelpers.findField(element!!.javaClass, "dexFile")
                    val dexFile = dexFileField.get(element)
                    val entries = XposedHelpers.callMethod(dexFile, "entries") as? java.util.Enumeration<String>

                    entries?.let {
                        while (it.hasMoreElements()) {
                            val name = it.nextElement()
                            val lower = name.lowercase()
                            if (!lower.contains(hint) && !lower.contains("share")) continue
                            try {
                                val c = cl.loadClass(name)
                                var sup: Class<*>? = c.superclass
                                while (sup != null) {
                                    if (sup.name == parentName) return c
                                    sup = sup.superclass
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return null
    }
}
