package com.dyhelper.util

import de.robv.android.xposed.XposedBridge

object HookUtils {
    fun log(msg: String) { XposedBridge.log("[DH] " + msg) }
    fun getField(obj: Any, name: String): Any? { var cls: Class<*>? = obj.javaClass; while (cls != null) { try { val f = cls.getDeclaredField(name); f.isAccessible = true; return f.get(obj) } catch (_: NoSuchFieldException) { cls = cls.superclass } }; throw NoSuchFieldException(name) }
    fun callMethod(obj: Any, name: String): Any? { var cls: Class<*>? = obj.javaClass; while (cls != null) { try { val m = cls.getDeclaredMethod(name); m.isAccessible = true; return m.invoke(obj) } catch (_: NoSuchMethodException) { cls = cls.superclass } }; throw NoSuchMethodException(name) }
    fun findClass(loader: ClassLoader, name: String): Class<*>? = try { Class.forName(name, false, loader) } catch (_: Exception) { null }
}