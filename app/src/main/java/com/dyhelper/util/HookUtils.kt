package com.dyhelper.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

object HookUtils {
    fun log(msg: String) { XposedBridge.log("[DH] " + msg) }

    /** Walk up class hierarchy to find and get a declared field value */
    fun getField(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: NoSuchFieldException) { cls = cls.superclass }
        }
        throw NoSuchFieldException(name)
    }

    /** Walk up class hierarchy to find and invoke a no-arg declared method */
    fun callMethod(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            try {
                val m = cls.getDeclaredMethod(name)
                m.isAccessible = true
                return m.invoke(obj)
            } catch (_: NoSuchMethodException) { cls = cls.superclass }
        }
        throw NoSuchMethodException(name)
    }

    /** Safe findClass that returns null instead of throwing */
    fun findClass(loader: ClassLoader, name: String): Class<*>? {
        return try { Class.forName(name, false, loader) } catch (_: Exception) { null }
    }

    /** Hook a single method by class+name (uses hookAllMethods, LSPosed-safe) */
    fun hookOneMethod(cls: Class<*>, methodName: String, callback: XC_MethodHook) {
        XposedBridge.hookAllMethods(cls, methodName, callback)
    }

    /** Try to find and hook a method across class hierarchy */
    fun tryHookMethod(cls: Class<*>, methodName: String, callback: XC_MethodHook): Boolean {
        return try {
            XposedBridge.hookAllMethods(cls, methodName, callback)
            true
        } catch (t: Throwable) {
            log("tryHookMethod $methodName on " + cls.name + " err: " + t.message)
            false
        }
    }
}
