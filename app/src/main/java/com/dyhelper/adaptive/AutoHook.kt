package com.dyhelper.adaptive

import com.dyhelper.MainHook
import com.dyhelper.adaptive.ClassScanner.ClassFingerprint
import com.dyhelper.adaptive.ClassScanner.FieldPattern
import com.dyhelper.adaptive.ClassScanner.MethodPattern
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Adaptive auto-hook system -- the unified entry point.
 *
 * Strategy (tried in order):
 * 1. Check HookCache for previously discovered class/method names
 * 2. Try hardcoded candidate class names (from legacy HookHelper)
 * 3. Run ClassScanner to discover classes by structural fingerprint
 *
 * Once discovered, results are cached for future launches.
 */
object AutoHook {

    // ---- Public API ----

    /**
     * Hook a method by structural fingerprint. Fully automatic.
     *
     * @param label Short identifier for logging/caching (e.g., "share_panel")
     * @param targetFingerprint Structural fingerprint of the target class
     * @param methodPattern Signature of the method to hook
     * @param hardcodedClasses Fallback list of known class names for known versions
     * @param callback The hook callback
     * @param priority Hook priority (higher = earlier)
     * @return true if hook was successfully applied
     */
    fun hookMethod(
        label: String,
        targetFingerprint: ClassFingerprint,
        methodPattern: MethodPattern,
        hardcodedClasses: List<String> = emptyList(),
        callback: XC_MethodHook,
        priority: Int = XposedBridge.PRIORITY_DEFAULT
    ): Boolean {
        // Step 1: Try cache
        val cachedClass = HookCache.getClass(label)
        if (cachedClass != null) {
            try {
                val clazz = MainHook.getModuleClassLoader()!!.loadClass(cachedClass)
                if (hookMethodInClass(label, clazz, methodPattern, callback, priority)) return true
            } catch (_: Exception) {
                HookCache.clear()  // Cache is stale, re-scan
            }
        }

        // Check in-session cache
        HookCache.getClassInSession(label)?.let {
            if (hookMethodInClass(label, it, methodPattern, callback, priority)) return true
        }

        // Step 2: Try hardcoded classes
        for (className in hardcodedClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, MainHook.getModuleClassLoader())
            if (clazz != null && hookMethodInClass(label, clazz, methodPattern, callback, priority)) {
                HookCache.putClass(label, clazz.name)
                return true
            }
        }

        // Step 3: ClassScanner discovery
        MainHook.log("[AutoHook] Scanning for '$label'...")
        val discovered = ClassScanner.findClass(targetFingerprint)
        if (discovered != null) {
            if (hookMethodInClass(label, discovered, methodPattern, callback, priority)) {
                HookCache.putClass(label, discovered.name)
                HookCache.putClassInSession(label, discovered)
                return true
            }
        }

        MainHook.log("[AutoHook] FAILED: could not hook '$label'")
        return false
    }

    /**
     * Hook multiple methods in a single class, discovered automatically.
     */
    fun hookMethods(
        label: String,
        targetFingerprint: ClassFingerprint,
        methodCallbacks: List<Pair<MethodPattern, XC_MethodHook>>,
        hardcodedClasses: List<String> = emptyList(),
        priority: Int = XposedBridge.PRIORITY_DEFAULT
    ): Boolean {
        // Step 1: Try cache + in-session
        var clazz = tryGetClass(label, targetFingerprint, hardcodedClasses)
        if (clazz == null) {
            MainHook.log("[AutoHook] FAILED: could not find class for '$label'")
            return false
        }

        var allHooked = true
        for ((pattern, callback) in methodCallbacks) {
            if (!hookMethodInClass("${label}.methods", clazz, pattern, callback, priority)) {
                allHooked = false
            }
        }
        return allHooked
    }

    /**
     * Hook all constructors of a class, discovered automatically.
     */
    fun hookAllConstructors(
        label: String,
        targetFingerprint: ClassFingerprint,
        hardcodedClasses: List<String> = emptyList(),
        callback: XC_MethodHook
    ): Boolean {
        var clazz = tryGetClass(label, targetFingerprint, hardcodedClasses)
        if (clazz == null) {
            MainHook.log("[AutoHook] FAILED: could not find class for '$label'")
            return false
        }

        XposedBridge.hookAllConstructors(clazz, callback)
        MainHook.log("[AutoHook] Hooked constructors: ${clazz.name}")
        return true
    }

    /**
     * Directly hook a method on a class obtained by ClassScanner.
     * Useful when you need the Class object for more complex operations.
     */
    fun findAndHook(
        label: String,
        targetFingerprint: ClassFingerprint,
        hardcodedClasses: List<String> = emptyList(),
        onClassFound: (Class<*>) -> Unit
    ): Boolean {
        var clazz = tryGetClass(label, targetFingerprint, hardcodedClasses)
        if (clazz == null) {
            MainHook.log("[AutoHook] FAILED: could not find class for '$label'")
            return false
        }
        onClassFound(clazz)
        return true
    }

    // ---- Internal helpers ----

    private fun tryGetClass(
        label: String,
        fingerprint: ClassFingerprint,
        hardcodedClasses: List<String>
    ): Class<*>? {
        // Try cache
        val cachedClass = HookCache.getClass(label)
        if (cachedClass != null) {
            try {
                return MainHook.getModuleClassLoader()!!.loadClass(cachedClass)
            } catch (_: Exception) {
                HookCache.clear()
            }
        }

        // Try in-session
        HookCache.getClassInSession(label)?.let { return it }

        // Try hardcoded
        for (className in hardcodedClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, MainHook.getModuleClassLoader())
            if (clazz != null) {
                HookCache.putClass(label, clazz.name)
                HookCache.putClassInSession(label, clazz)
                return clazz
            }
        }

        // Scan
        val discovered = ClassScanner.findClass(fingerprint)
        if (discovered != null) {
            HookCache.putClass(label, discovered.name)
            HookCache.putClassInSession(label, discovered)
        }
        return discovered
    }

    private fun hookMethodInClass(
        label: String,
        clazz: Class<*>,
        pattern: MethodPattern,
        callback: XC_MethodHook,
        priority: Int
    ): Boolean {
        // Try cached method name first
        val patternsLabel = "${pattern.returnTypeName}(${pattern.paramTypeNames.joinToString(",")})"
        val cachedMethodName = HookCache.getMethod(clazz.name, patternsLabel)

        val method = if (cachedMethodName != null) {
            findMethodByName(clazz, pattern, cachedMethodName)
        } else {
            findMethodBySignature(clazz, pattern)
        }

        if (method != null) {
            XposedBridge.hookMethod(method, priority, callback)
            MainHook.log("[AutoHook] $label -> ${clazz.simpleName}.${method.name}")
            HookCache.putMethod(clazz.name, patternsLabel, method.name)
            return true
        }

        return false
    }

    private fun findMethodByName(clazz: Class<*>, pattern: MethodPattern, name: String): Method? {
        return try {
            val method = clazz.getDeclaredMethod(name, *resolveTypesFromNames(clazz.classLoader, pattern.paramTypeNames))
            method.isAccessible = true
            method
        } catch (_: NoSuchMethodException) { null }
    }

    private fun findMethodBySignature(clazz: Class<*>, pattern: MethodPattern): Method? {
        for (method in clazz.declaredMethods) {
            if (pattern.matches(method)) {
                method.isAccessible = true
                return method
            }
        }
        return null
    }

    private fun resolveTypesFromNames(loader: ClassLoader?, names: List<String>): Array<Class<*>> {
        return names.map { name ->
            when (name) {
                "void" -> Void.TYPE
                "boolean" -> Boolean::class.javaPrimitiveType!!
                "byte" -> Byte::class.javaPrimitiveType!!
                "short" -> Short::class.javaPrimitiveType!!
                "int" -> Integer.TYPE
                "long" -> Long::class.javaPrimitiveType!!
                "float" -> Float::class.javaPrimitiveType!!
                "double" -> Double::class.javaPrimitiveType!!
                "char" -> Character.TYPE
                "*" -> Any::class.java
                else -> {
                    try { Class.forName(name, false, loader) }
                    catch (_: ClassNotFoundException) { Any::class.java }
                }
            }
        }.toTypedArray()
    }
}
