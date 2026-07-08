package com.dyhelper.adaptive

import com.dyhelper.MainHook
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Modifier

/**
 * Automatic class discovery across Douyin versions.
 *
 * Scans loaded classes using structural fingerprints that survive obfuscation:
 * - Parent class / interface hierarchy
 * - Method signature patterns (return type + param types)
 * - Field name/type patterns
 * - Package prefix hints
 *
 * This is the core of the adaptive system -- as long as Douyin preserves
 * its class structure, we can find the right targets even with renamed classes.
 */
object ClassScanner {

    /** Matched class name -> Class<?> cache for the current session */
    private val classCache = mutableMapOf<String, Class<*>?>()

    /**
     * Find a single class matching the given [fingerprint].
     * Returns the best match or null if none found.
     */
    fun findClass(fingerprint: ClassFingerprint): Class<*>? {
        val cacheKey = fingerprint.label
        classCache[cacheKey]?.let { return it }

        val candidates = findAllClasses(fingerprint, limit = 1)
        val result = candidates.firstOrNull()
        classCache[cacheKey] = result
        return result
    }

    /**
     * Find all classes matching the [fingerprint], up to [limit].
     */
    fun findAllClasses(fingerprint: ClassFingerprint, limit: Int = Int.MAX_VALUE): List<Class<*>> {
        val loader = MainHook.getModuleClassLoader() ?: return emptyList()
        if (fingerprint.packagePrefixes.isEmpty()) return emptyList()

        val results = mutableListOf<Class<*>>()
        val visited = mutableSetOf<String>()

        for (prefix in fingerprint.packagePrefixes) {
            if (results.size >= limit) break
            try {
                scanPackage(prefix, loader, fingerprint, results, visited, limit)
            } catch (_: Exception) { }
        }

        // Sort by score (higher = better match)
        results.sortByDescending { score(it, fingerprint) }

        MainHook.log("[Adaptive] findClass '${fingerprint.label}' -> ${results.firstOrNull()?.name ?: "NOT FOUND"}")
        return results.take(limit)
    }

    // ---- Internal scanning ----

    private fun scanPackage(
        prefix: String,
        loader: ClassLoader,
        fp: ClassFingerprint,
        results: MutableList<Class<*>>,
        visited: MutableSet<String>,
        limit: Int
    ) {
        try {
            // Use DexFile for dalvik/art class enumeration
            val pathClassLoader = loader as? dalvik.system.PathClassLoader
            if (pathClassLoader != null) {
                // Try get all dex elements
                val pathListField = XposedHelpers.findField(
                    dalvik.system.PathClassLoader::class.java, "pathList"
                )
                val pathList = pathListField.get(loader)
                val dexElementsField = XposedHelpers.findField(pathList.javaClass, "dexElements")
                val dexElements = dexElementsField.get(pathList) as? Array<*>

                if (dexElements != null) {
                    for (element in dexElements) {
                        if (results.size >= limit) break
                        try {
                            val dexFileField = XposedHelpers.findField(
                                element!!.javaClass, "dexFile"
                            )
                            val dexFile = dexFileField.get(element)
                            val entries = XposedHelpers.callMethod(
                                dexFile, "entries"
                            ) as? java.util.Enumeration<String>

                            entries?.let {
                                while (it.hasMoreElements() && results.size < limit) {
                                    val className = it.nextElement()
                                    if (!className.startsWith(prefix)) continue
                                    if (className in visited) continue
                                    visited.add(className)

                                    try {
                                        val clazz = loader.loadClass(className)
                                        if (matches(clazz, fp)) {
                                            results.add(clazz)
                                        }
                                    } catch (_: Throwable) { }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            } else {
                // Fallback: try DexFile directly
                try {
                    val dexFileClass = Class.forName("dalvik.system.DexFile")
                    val loadDex = dexFileClass.getMethod("loadDex", String::class.java, String::class.java, Int::class.javaPrimitiveType)
                    // Not reliable as fallback, skip
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    // ---- Match scoring ----

    private fun matches(clazz: Class<*>, fp: ClassFingerprint): Boolean {
        // Must pass parent class check
        if (fp.parentClassName != null) {
            val parent = findInHierarchy(clazz, fp.parentClassName) ?: return false
            if (fp.concreteParent && Modifier.isAbstract(parent.modifiers)) return false
        }

        // Must pass interface check
        for (iface in fp.interfaces) {
            if (!implementsInterface(clazz, iface)) return false
        }

        // Must have required fields
        for (fieldPattern in fp.fieldPatterns) {
            if (!hasField(clazz, fieldPattern)) return false
        }

        // Must have required methods
        for (methodPattern in fp.methodPatterns) {
            if (!hasMethod(clazz, methodPattern)) return false
        }

        // Must NOT have excluded methods (for disambiguation)
        for (excl in fp.excludeMethodPatterns) {
            if (hasMethod(clazz, excl)) return false
        }

        return true
    }

    private fun score(clazz: Class<*>, fp: ClassFingerprint): Int {
        var score = 0
        // Prefer classes deeper in the hierarchy (more specific)
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            score++
            current = current.superclass
        }
        // Prefer non-abstract classes
        if (!Modifier.isAbstract(clazz.modifiers)) score += 10
        return score
    }

    // ---- Hierarchy helpers ----

    private fun findInHierarchy(clazz: Class<*>, parentName: String): Class<*>? {
        var current: Class<*>? = clazz
        while (current != null) {
            if (current.name == parentName) return current
            current = current.superclass
        }
        return null
    }

    private fun implementsInterface(clazz: Class<*>, interfaceName: String): Boolean {
        for (iface in clazz.interfaces) {
            if (iface.name == interfaceName) return true
        }
        var current = clazz.superclass
        while (current != null) {
            for (iface in current.interfaces) {
                if (iface.name == interfaceName) return true
            }
            current = current.superclass
        }
        return false
    }

    // ---- Field/Method check ----

    private fun hasField(clazz: Class<*>, pattern: FieldPattern): Boolean {
        return try {
            val field = clazz.getDeclaredField(pattern.name)
            field.type.name == pattern.typeName
        } catch (_: NoSuchFieldException) { false }
    }

    private fun hasMethod(clazz: Class<*>, pattern: MethodPattern): Boolean {
        for (method in clazz.declaredMethods) {
            if (pattern.matches(method)) return true
        }
        return false
    }

    // ---- Data classes ----

    /**
     * Describes what a target class looks like structurally.
     * All fields except [label] and [packagePrefixes] are optional filters.
     */
    data class ClassFingerprint(
        /** Human-readable label for logging */
        val label: String,
        /** Package prefixes to search (e.g., "com.ss.android", "com.bytedance") */
        val packagePrefixes: List<String>,
        /** Required parent class (full name) */
        val parentClassName: String? = null,
        /** If true, the parent itself must be a concrete class (not abstract) */
        val concreteParent: Boolean = false,
        /** Required interfaces */
        val interfaces: List<String> = emptyList(),
        /** Fields that must exist on the class */
        val fieldPatterns: List<FieldPattern> = emptyList(),
        /** Methods that must exist (matched by signature, not name) */
        val methodPatterns: List<MethodPattern> = emptyList(),
        /** Methods that must NOT exist (for disambiguation) */
        val excludeMethodPatterns: List<MethodPattern> = emptyList(),
    )

    data class FieldPattern(
        val name: String,
        val typeName: String,  // e.g., "java.lang.String", "boolean", "int"
    )

    data class MethodPattern(
        val returnTypeName: String,  // e.g., "void", "boolean", "java.lang.String", "*" for wildcard
        val paramTypeNames: List<String> = emptyList(),
        val isStatic: Boolean? = null,  // null = don't care
    ) {
        fun matches(method: java.lang.reflect.Method): Boolean {
            if (isStatic != null && isStatic != Modifier.isStatic(method.modifiers)) return false

            if (returnTypeName != "*" && returnTypeName != method.returnType.name) return false

            val params = method.parameterTypes
            if (paramTypeNames.size != params.size) return false

            for (i in paramTypeNames.indices) {
                if (paramTypeNames[i] != "*" && paramTypeNames[i] != params[i].name) return false
            }
            return true
        }
    }
}
