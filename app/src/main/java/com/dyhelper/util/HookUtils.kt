package com.dyhelper.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.XposedBridge

object HookUtils {

    private const val TAG = "DH"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun log(msg: String) {
        XposedBridge.log("[" + TAG + "] " + msg)
    }

    fun safeHook(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            log("Hook error: " + t.message)
        }
    }

    fun showToast(ctx: Context, msg: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(ctx, msg, duration).show()
            } else {
                mainHandler.post { Toast.makeText(ctx, msg, duration).show() }
            }
        } catch (t: Throwable) {
            log("Toast failed: " + t.message)
        }
    }

    fun showToastLong(ctx: Context, msg: String) {
        showToast(ctx, msg, Toast.LENGTH_LONG)
    }

    fun getField(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        return try {
            val f = obj.javaClass.getDeclaredField(fieldName)
            f.isAccessible = true
            f.get(obj)
        } catch (_: Throwable) { null }
    }

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        if (obj == null) return null
        return try {
            val types = arrayOfNulls<Class<*>>(args.size)
            for (i in args.indices) {
                types[i] = args[i]?.javaClass ?: Any::class.java
            }
            val m = obj.javaClass.getDeclaredMethod(methodName, *types)
            m.isAccessible = true
            m.invoke(obj, *args)
        } catch (_: Throwable) { null }
    }

    fun classExists(loader: ClassLoader, name: String): Boolean {
        return try {
            Class.forName(name, false, loader)
            true
        } catch (_: ClassNotFoundException) { false }
    }
}
