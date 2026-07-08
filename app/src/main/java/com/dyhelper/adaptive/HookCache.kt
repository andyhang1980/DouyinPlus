package com.dyhelper.adaptive

import android.content.Context
import android.content.SharedPreferences
import com.dyhelper.MainHook
import de.robv.android.xposed.XposedHelpers

/**
 * Persistent cache for adaptive hook results.
 *
 * Once we successfully discover a class/method signature for a given
 * Douyin version, we cache it to avoid re-scanning on every launch.
 *
 * Cache key: fingerprint label + Douyin version code
 * Cache value: discovered class name
 */
object HookCache {

    private const val PREFS_NAME = "dyhelper_adaptive_cache"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    /** Storage for current-session method-to-name mappings (not persisted) */
    private val methodNameCache = mutableMapOf<String, MutableMap<String, String>>()

    /**
     * Initialize with the Application context, called from MainHook after
     * the HostApplication is ready.
     */
    fun init(context: Context) {
        appContext = context
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        MainHook.log("[Cache] Initialized")
    }

    private fun ensurePrefs(): SharedPreferences? {
        if (prefs != null) return prefs
        // Try to get context from the running app
        try {
            val appClass = XposedHelpers.findClassIfExists(
                "com.ss.android.ugc.aweme.app.host.HostApplication",
                MainHook.getModuleClassLoader()
            )
            if (appClass != null) {
                // Try instance method first
                for (method in appClass.declaredMethods) {
                    if (method.name == "getApplication" && method.parameterTypes.isEmpty()) {
                        val ctx = method.invoke(null) as? Context
                        if (ctx != null) {
                            appContext = ctx
                            prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            return prefs
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        // Fallback: try ActivityThread
        try {
            val atClass = Class.forName("android.app.ActivityThread")
            val atMethod = atClass.getMethod("currentApplication")
            val ctx = atMethod.invoke(null) as? Context
            if (ctx != null) {
                appContext = ctx
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                return prefs
            }
        } catch (_: Exception) { }
        return prefs
    }

    /** Get the douyin version code for cache key isolation */
    private fun versionCode(): String {
        return try {
            if (appContext != null) {
                val pkgInfo = appContext!!.packageManager.getPackageInfo(appContext!!.packageName, 0)
                "${pkgInfo.versionName}_${pkgInfo.longVersionCode}"
            } else {
                MainHook.currentVersion
            }
        } catch (_: Exception) { MainHook.currentVersion }
    }

    // ---- Class cache ----

    fun getClass(label: String): String? {
        val p = ensurePrefs() ?: return null
        val key = "${label}_v${versionCode()}"
        val value = p.getString(key, null)
        return value?.takeIf { it.isNotEmpty() }
    }

    fun putClass(label: String, className: String) {
        val p = ensurePrefs() ?: return
        val key = "${label}_v${versionCode()}"
        p.edit().putString(key, className).apply()
        MainHook.log("[Cache] Saved $label -> $className")
    }

    // ---- Method cache (in-memory only) ----

    fun getMethod(className: String, patternLabel: String): String? {
        return methodNameCache[className]?.get(patternLabel)
    }

    fun putMethod(className: String, patternLabel: String, methodName: String) {
        methodNameCache.getOrPut(className) { mutableMapOf() }[patternLabel] = methodName
    }

    fun clear() {
        ensurePrefs()?.edit()?.clear()?.apply()
        methodNameCache.clear()
        classCache_internal.clear()
    }

    // ---- In-session class cache ----

    private val classCache_internal = mutableMapOf<String, Class<*>?>()

    fun getClassInSession(label: String): Class<*>? = classCache_internal[label]
    fun putClassInSession(label: String, clazz: Class<*>?) {
        classCache_internal[label] = clazz
    }
}
