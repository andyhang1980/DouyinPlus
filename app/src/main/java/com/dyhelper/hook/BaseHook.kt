package com.dyhelper.hook

interface BaseHook {
    fun init(classLoader: ClassLoader): Boolean
    fun name(): String
}
