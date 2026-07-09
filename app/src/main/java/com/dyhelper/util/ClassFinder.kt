package com.dyhelper.util

import java.util.concurrent.ConcurrentHashMap

object ClassFinder {

    private val cache = ConcurrentHashMap<String, Class<*>>()

    fun findClass(loader: ClassLoader, candidates: List<String>): Class<*>? {
        for (name in candidates) {
            val cached = cache[name]
            if (cached != null) return cached
            try {
                val clazz = Class.forName(name, false, loader)
                cache[name] = clazz
                return clazz
            } catch (_: ClassNotFoundException) {}
        }
        return null
    }

    fun findByMethodSig(
        loader: ClassLoader, pkg: String,
        sigs: List<String>
    ): List<Class<*>> {
        val results = mutableListOf<Class<*>>()
        val parsed = sigs.map { s ->
            val parts = s.split(":")
            Triple(parts.getOrElse(0){""}, parts.getOrElse(1){""},
                   parts.getOrElse(2){""}.split(",").filter{it.isNotEmpty()})
        }
        val classes = getClassesInPackage(loader, pkg)
        for (clazz in classes) {
            try {
                var ok = true
                for ((mn, rt, pts) in parsed) {
                    val has = clazz.declaredMethods.any { m ->
                        m.name == mn &&
                        (rt.isEmpty() || m.returnType.name.contains(rt)) &&
                        m.parameterTypes.size == pts.size
                    }
                    if (!has) { ok = false; break }
                }
                if (ok) results.add(clazz)
            } catch (_: Throwable) {}
        }
        return results
    }

    private fun getClassesInPackage(loader: ClassLoader, pkg: String): List<Class<*>> {
        val classes = mutableListOf<Class<*>>()
        try {
            val superClass = loader.javaClass.superclass ?: loader.javaClass
            val pathListField = superClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(loader) ?: return classes

            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val dexElements = dexElementsField.get(pathList) as? Array<*> ?: return classes

            for (elem in dexElements) {
                if (elem == null) continue
                try {
                    val dexFileField = elem.javaClass.getDeclaredField("dexFile")
                    dexFileField.isAccessible = true
                    val dexFile = dexFileField.get(elem) ?: continue
                    val entriesMethod = dexFile.javaClass.getMethod("entries")
                    val entries = entriesMethod.invoke(dexFile) as? java.util.Enumeration<String> ?: continue
                    while (entries.hasMoreElements()) {
                        val cn = entries.nextElement()
                        if (cn.startsWith(pkg) && !cn.contains('$')) {
                            try { classes.add(Class.forName(cn, false, loader)) }
                            catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            HookUtils.log("ClassFinder DexFile failed: " + t.message)
        }
        return classes
    }

    fun clearCache() { cache.clear() }
}
