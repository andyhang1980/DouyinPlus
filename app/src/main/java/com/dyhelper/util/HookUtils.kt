package com.dyhelper.util

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

object HookUtils {
    fun log(msg: String) { XposedBridge.log("[DH] " + msg) }
    fun getField(obj: Any, name: String): Any? { var cls: Class<*>? = obj.javaClass; while (cls != null) { try { val f = cls.getDeclaredField(name); f.isAccessible = true; return f.get(obj) } catch (_: NoSuchFieldException) { cls = cls.superclass } }; throw NoSuchFieldException(name) }
    fun callMethod(obj: Any, name: String): Any? { var cls: Class<*>? = obj.javaClass; while (cls != null) { try { val m = cls.getDeclaredMethod(name); m.isAccessible = true; return m.invoke(obj) } catch (_: NoSuchMethodException) { cls = cls.superclass } }; throw NoSuchMethodException(name) }
    fun findClass(loader: ClassLoader, name: String): Class<*>? = try { Class.forName(name, false, loader) } catch (_: Exception) { null }

    /** Use reflection to call XposedBridge.hookAllMethods which IS available on LSPosed runtime proxy */
    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: XC_MethodHook): Boolean {
        return try {
            val bridgeClass = XposedBridge::class.java
            val m = bridgeClass.getDeclaredMethod("hookAllMethods", Class::class.java, String::class.java, XC_MethodHook::class.java)
            m.invoke(null, clazz, methodName, callback)
            true
        } catch (e: NoSuchMethodException) {
            log("hookAllMethods not on bridge, fallback to hookMethod")
            var ok = false
            for (method in clazz.declaredMethods) {
                if (method.name == methodName && method.parameterTypes.size == 0) {
                    try { XposedBridge.hookMethod(method, callback); ok = true } catch (t: Throwable) { log("hookMethod fallback err: " + t.message) }
                }
            }
            ok
        } catch (t: Throwable) {
            log("hookAllMethods err: " + t.message); false
        }
    }
}
