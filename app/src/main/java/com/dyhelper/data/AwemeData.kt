package com.dyhelper.data

import de.robv.android.xposed.XposedHelpers

/**
 * Aweme data extraction utility.
 * Extracts download URLs from Douyin's Aweme model.
 */
object AwemeData {
    var currentAweme: Any? = null

    /** Extract description text */
    fun getDesc(aweme: Any?): String? {
        return try {
            XposedHelpers.getObjectField(aweme, "desc") as? String
        } catch (e: Exception) { null }
    }

    /** Get media type: 68 = image, others = video */
    fun getType(aweme: Any?): Int {
        return try {
            (XposedHelpers.getObjectField(aweme, "awemeType") as? Int) ?: 0
        } catch (e: Exception) { 0 }
    }

    /**
     * Get download URL by type:
     * 0 = music, 1 = video (no watermark), 2 = image
     */
    fun getUrl(type: Int, aweme: Any?): String? {
        if (aweme == null) return null
        return try {
            when (type) {
                1 -> XposedHelpers.callMethod(aweme, "getFirstPlayAddr") as? String
                0 -> {
                    val music = XposedHelpers.getObjectField(aweme, "music")
                    val playUrl = XposedHelpers.getObjectField(music, "playUrl")
                    val urlList = XposedHelpers.callMethod(playUrl, "getUrlList") as? List<*>
                    urlList?.firstOrNull()?.toString()
                }
                2 -> {
                    val images = XposedHelpers.getObjectField(aweme, "images") as? List<*>
                    if (images.isNullOrEmpty()) return null
                    var pos = (XposedHelpers.getObjectField(aweme, "photosCurPos") as? Int) ?: 0
                    if (pos < 0) pos = 0
                    if (pos >= images.size) pos = images.size - 1
                    val urlList = XposedHelpers.getObjectField(images[pos], "urlList") as? List<*>
                    urlList?.firstOrNull()?.toString()
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /** Check if current is an ad */
    fun isAd(aweme: Any?): Boolean {
        return try {
            XposedHelpers.callMethod(aweme, "isAd") as? Boolean ?: false
        } catch (e: Exception) { false }
    }
}
