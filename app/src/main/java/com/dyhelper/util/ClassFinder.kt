package com.dyhelper.util

import java.util.concurrent.ConcurrentHashMap

object ClassFinder {

    private val cache = ConcurrentHashMap<String, Class<*>>()

    fun scanClasses(loader: ClassLoader, pkgFilter: String): List<Class<*>> {
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
                        if (cn.startsWith(pkgFilter)) {
                            try { classes.add(Class.forName(cn, false, loader)) }
                            catch (_: Throwable) {}
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            HookUtils.log("ClassFinder scan failed: " + t.message)
        }
        return classes
    }

    fun findClass(loader: ClassLoader, className: String): Class<*>? {
        cache[className]?.let { return it }
        return try {
            val clazz = Class.forName(className, false, loader)
            cache[className] = clazz
            clazz
        } catch (_: ClassNotFoundException) { null }
    }

    fun clearCache() { cache.clear() }
}
