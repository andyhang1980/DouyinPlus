package com.dyhelper.utils

import com.dyhelper.MainHook
import com.dyhelper.adaptive.ClassScanner.ClassFingerprint
import com.dyhelper.adaptive.ClassScanner.MethodPattern
import com.dyhelper.adaptive.AutoHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Hook helper with automatic method signature matching.
 *
 * Three strategies (tried in order by tryAdaptive first, then by the legacy methods):
 * 1. [tryAdaptive]      - Auto-discover class by structural fingerprint (survives obfuscation)
 * 2. [hookByMethodName] - Match by class name + method name (fast, most common)
 * 3. [hookBySignature]  - Match by return type + parameter types ONLY, ignoring method name.
 *
 * Prefer [tryAdaptive] for new hooks -- it handles version churn automatically.
 */
object HookHelper {

    // ========================================================================
    //  Adaptive API (NEW) -- Use this for maximum version tolerance
    // ========================================================================

    /**
     * Try adaptive hooking first, then fall back to hardcoded class names.
     *
     * This is the recommended method for new hooks. It will:
     * 1. Check cache for a previously discovered class
     * 2. Try each hardcoded class name
     * 3. Scan the classloader using [fingerprint] to find the right class
     * 4. Cache the result so next launch is instant
     *
     * @param tag Short label for logging (e.g. "SharePanel")
     * @param fingerprint Structural fingerprint of the target class
     * @param methodPattern Signature of the method to hook
     * @param hardcodedClasses Fallback class names for known versions
     * @param callback The hook callback
     * @return true if hook was applied
     */
    fun tryAdaptive(
        tag: String,
        fingerprint: ClassFingerprint,
        methodPattern: MethodPattern,
        hardcodedClasses: List<String>,
        callback: XC_MethodHook
    ): Boolean {
        return AutoHook.hookMethod(
            label = tag,
            targetFingerprint = fingerprint,
            methodPattern = methodPattern,
            hardcodedClasses = hardcodedClasses,
            callback = callback
        )
    }

    /**
     * Try adaptive hooking for ALL constructors of a class.
     */
    fun tryAdaptiveConstructors(
        tag: String,
        fingerprint: ClassFingerprint,
        hardcodedClasses: List<String>,
        callback: XC_MethodHook
    ): Boolean {
        return AutoHook.hookAllConstructors(
            label = tag,
            targetFingerprint = fingerprint,
            hardcodedClasses = hardcodedClasses,
            callback = callback
        )
    }

    /**
     * Try adaptive hooking with a custom onClassFound handler.
     * Use this when you need the Class object for complex hooking logic.
     */
    fun tryAdaptiveFindClass(
        tag: String,
        fingerprint: ClassFingerprint,
        hardcodedClasses: List<String>,
        onClassFound: (Class<*>) -> Unit
    ): Boolean {
        return AutoHook.findAndHook(
            label = tag,
            targetFingerprint = fingerprint,
            hardcodedClasses = hardcodedClasses,
            onClassFound = onClassFound
        )
    }

    // ========================================================================
    //  Legacy API -- Still works, kept for backward compatibility
    // ========================================================================

    /**
     * Hook a method by exact class name and method signature.
     * Tries multiple candidate class names if the first fails.
     */
    fun hookByMethodName(
        tag: String,
        candidateClasses: List<String>,
        returnType: Class<*>? = null,
        methodName: String,
        paramTypes: Array<Class<*>> = emptyArray(),
        callback: XC_MethodHook
    ): Boolean {
        for (className in candidateClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, MainHook.getModuleClassLoader())
            if (clazz == null) continue

            val method = findMethodByName(clazz, returnType, methodName, *paramTypes)
            if (method != null) {
                XposedBridge.hookMethod(method, callback)
                MainHook.log("[$tag] Hooked: $className.$methodName")
                return true
            }
        }
        MainHook.log("[$tag] FAILED: no class found for $methodName among ${candidateClasses.joinToString()}")
        return false
    }

    /**
     * Hook a method by its signature (return type + parameter types), IGNORING the method name.
     */
    fun hookBySignature(
        tag: String,
        candidateClasses: List<String>,
        returnType: Class<*>? = null,
        paramTypes: Array<Class<*>>,
        callback: XC_MethodHook
    ): Boolean {
        for (className in candidateClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, MainHook.getModuleClassLoader())
            if (clazz == null) continue

            val method = findMethodByParams(clazz, returnType, *paramTypes)
            if (method != null) {
                XposedBridge.hookMethod(method, callback)
                MainHook.log("[$tag] Hooked by signature: $className.${method.name}")
                return true
            }
        }
        MainHook.log("[$tag] FAILED: no matching signature in ${candidateClasses.joinToString()}")
        return false
    }

    /**
     * Hook all constructors of a class.
     */
    fun hookConstructors(
        tag: String,
        candidateClasses: List<String>,
        callback: XC_MethodHook
    ): Boolean {
        for (className in candidateClasses) {
            val clazz = XposedHelpers.findClassIfExists(className, MainHook.getModuleClassLoader())
            if (clazz != null) {
                XposedBridge.hookAllConstructors(clazz, callback)
                MainHook.log("[$tag] Hooked constructors: $className")
                return true
            }
        }
        return false
    }

    // ---- Internal method matching ----

    private fun findMethodByName(
        clazz: Class<*>,
        returnType: Class<*>?,
        name: String,
        vararg paramTypes: Class<*>
    ): Method? {
        for (method in clazz.declaredMethods) {
            if (method.name == name && matchSignature(method, returnType, paramTypes)) {
                method.isAccessible = true
                return method
            }
        }
        for (method in clazz.methods) {
            if (method.name == name && matchSignature(method, returnType, paramTypes)) {
                return method
            }
        }
        return null
    }

    private fun findMethodByParams(
        clazz: Class<*>,
        returnType: Class<*>?,
        vararg paramTypes: Class<*>
    ): Method? {
        for (method in clazz.declaredMethods) {
            if (matchSignature(method, returnType, paramTypes)) {
                method.isAccessible = true
                return method
            }
        }
        for (method in clazz.methods) {
            if (matchSignature(method, returnType, paramTypes)) {
                return method
            }
        }
        return null
    }

    private fun matchSignature(
        method: Method,
        returnType: Class<*>?,
        paramTypes: Array<out Class<*>>
    ): Boolean {
        if (returnType != null && returnType != method.returnType) return false
        val actualParams = method.parameterTypes
        if (paramTypes.size != actualParams.size) return false
        for (i in paramTypes.indices) {
            if (paramTypes[i] != null && paramTypes[i] != actualParams[i]) return false
        }
        return true
    }
}
