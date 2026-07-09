package com.dyhelper.util

import de.robv.android.xposed.XposedBridge

object HookUtils {
    fun log(msg: String) { XposedBridge.log("[DH] " + msg) }
}
