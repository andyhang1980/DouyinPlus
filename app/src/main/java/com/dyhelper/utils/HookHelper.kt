package com.dyhelper.utils

import com.dyhelper.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Hook helper with automatic method signature matching.
 *
 * Two strategies:
 * 1. [hookByMethodName] - Match by class name + method name (fast, most common)
 * 2. [hookBySignature]  - Match by return type + parameter types ONLY, ignoring method name.
 *                         Survives Douyin obfuscation changes.
 */
object HookHelper {

    /**
     * Hook a method by exact class name and method signature.
     * Tries multiple candidate class names if the first fails.
     *
     * Usage:
     *   hookByMethodName(
     *     tag = "ShareMenu",
     *     candidateClasses = listOf(
     *       "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
     *       "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapLayout",  // fallback
     *     ),
     *     returnType = Void.TYPE,
     *     methodName = "onMeasure",
     *     paramTypes = arrayOf(Int::class.java, Int::class.java),
     *     callback = myHook
     *   )
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
     * This survives Douyin version updates where method names get obfuscated differently.
     *
     * Usage:
     *   hookBySignature(
     *     tag = "SplashAd",
     *     candidateClasses = listOf("com.bytedance...SplashAdActivity"),
     *     returnType = Void.TYPE,
     *     paramTypes = arrayOf(Bundle::class.java),
     *     callback = myHook
     *   )
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

    // ---- Internal method matching ----

    /**
     * Find a method by name + return type + param types.
     * Searches declared methods first, then public methods.
     */
    private fun findMethodByName(
        clazz: Class<*>,
        returnType: Class<*>?,
        name: String,
        vararg paramTypes: Class<*>
    ): Method? {
        // Try declared methods first
        for (method in clazz.declaredMethods) {
            if (method.name == name && matchSignature(method, returnType, paramTypes)) {
                method.isAccessible = true
                return method
            }
        }
        // Fallback to public methods
        for (method in clazz.methods) {
            if (method.name == name && matchSignature(method, returnType, paramTypes)) {
                return method
            }
        }
        return null
    }

    /**
     * Find a method by return type + param types, ignoring the method name.
     * Useful when Douyin changes method names between versions.
     */
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

    /**
     * Check if a method matches the given return type and parameter types.
     * Null entries in paramTypes act as wildcards.
     */
    private fun matchSignature(
        method: Method,
        returnType: Class<*>?,
        paramTypes: Array<out Class<*>>
    ): Boolean {
        // Check return type
        if (returnType != null && returnType != method.returnType) return false

        // Check parameter count
        val actualParams = method.parameterTypes
        if (paramTypes.size != actualParams.size) return false

        // Check each parameter (null = wildcard)
        for (i in paramTypes.indices) {
            if (paramTypes[i] != null && paramTypes[i] != actualParams[i]) return false
        }
        return true
    }

    /**
     * Hook all constructors of a class. Tries multiple candidate class names.
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
}
