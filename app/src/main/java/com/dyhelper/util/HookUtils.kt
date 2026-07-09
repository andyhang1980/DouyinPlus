package com.dyhelper.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

object HookUtils {

    /** Simple reflection-based hook callback */
    abstract class RB {
        open fun before(obj: Any, args: Array<out Any?>) {}
        /** Return the value to set as param.result, or null to leave unchanged */
        open fun after(obj: Any, args: Array<out Any?>, result: Any?): Any? = null
    }

    fun log(msg: String) { XposedBridge.log("[DH] " + msg) }

    fun getField(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            try { val f = cls.getDeclaredField(name); f.isAccessible = true; return f.get(obj) }
            catch (_: NoSuchFieldException) { cls = cls.superclass }
        }
        throw NoSuchFieldException(name)
    }

    fun callMethod(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            try { val m = cls.getDeclaredMethod(name); m.isAccessible = true; return m.invoke(obj) }
            catch (_: NoSuchMethodException) { cls = cls.superclass }
        }
        throw NoSuchMethodException(name)
    }

    fun findClass(loader: ClassLoader, name: String): Class<*>? {
        return try { Class.forName(name, false, loader) } catch (_: Exception) { null }
    }

    /** Hook a method using reflection to bypass LSPosed type-mapping issues */
    fun hookViaReflection(targetClass: Class<*>, methodName: String, callback: RB): Boolean {
        try {
            val xposedBridgeClass = Class.forName("de.robv.android.xposed.XposedBridge")
            val hookMethodRef = xposedBridgeClass.declaredMethods.firstOrNull {
                it.name == "hookMethod" && it.parameterCount == 2
            } ?: run { log("hookMethod(2) not found"); return false }

            val xcHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    callback.before(param.thisObject, param.args)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val newResult = callback.after(param.thisObject, param.args, param.result)
                    if (newResult != null) param.result = newResult
                }
            }

            var hooked = 0
            for (m in targetClass.declaredMethods) {
                if (m.name == methodName) {
                    m.isAccessible = true
                    hookMethodRef.isAccessible = true
                    hookMethodRef.invoke(null, m, xcHook)
                    hooked++
                }
            }
            if (hooked > 0) { log("hookViaReflection: $methodName on " + targetClass.simpleName + " ($hooked methods)") }
            return hooked > 0
        } catch (t: Throwable) {
            log("hookViaReflection($methodName) err: " + t.message)
            return false
        }
    }

    fun dumpBridgeMethods() {
        try {
            val bridgeClass = Class.forName("de.robv.android.xposed.XposedBridge")
            val methods = bridgeClass.declaredMethods.sortedBy { it.name }
            log("=== XposedBridge methods (" + methods.size + ") ===")
            for (m in methods) {
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                log("  " + m.name + "(" + params + ")")
            }
        } catch (t: Throwable) {
            log("dumpBridgeMethods err: " + t.message)
        }
    }
}