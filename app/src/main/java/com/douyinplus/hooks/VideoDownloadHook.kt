package com.douyinplus.hooks

import android.app.AlertDialog
import android.content.Context
import android.os.AsyncTask
import android.widget.Toast
import com.douyinplus.MainHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.net.HttpURLConnection
import java.net.URL

/**
 * Video Download Hook: Add "Download Without Watermark" option to the share/long-press menu.
 *
 * Strategy:
 * 1. Hook the share/action dialog to inject "Download" button
 * 2. Extract video URL from the current Aweme object
 * 3. Download with a clean URL (strip watermark params)
 * 4. Save to DouyinPlus/Downloads folder
 */
object VideoDownloadHook {

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[VideoDownload] Initializing...")

        // Hook the share dialog / action sheet
        hookShareDialog(lpparam)

        // Hook detail page for download option
        hookDetailPage(lpparam)

        MainHook.log("[VideoDownload] Hooks registered")
    }

    /**
     * Inject a "Download" button into the share/action dialog.
     *
     * Common Douyin classes for share dialog:
     * - com.ss.android.ugc.aweme.detail.ui.DetailActivity (video detail page)
     * - com.ss.android.ugc.aweme.share.ShareDialog
     * - com.ss.android.ugc.aweme.shortvideo.ShortVideoContext
     */
    private fun hookShareDialog(lpparam: XC_LoadPackage.LoadPackageParam) {
        val shareClassCandidates = listOf(
            "com.ss.android.ugc.aweme.detail.ui.DetailActivity",
            "com.ss.android.ugc.aweme.share.ShareDialog",
            "com.ss.android.ugc.aweme.share.base.BaseShareActivity",
        )

        for (className in shareClassCandidates) {
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)
                // Try to hook onCreate or show methods
                for (method in clazz.declaredMethods) {
                    if ((method.name == "onCreate" || method.name.contains("show")) &&
                        method.parameterTypes.isNotEmpty()
                    ) {
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val activity = param.thisObject as? android.app.Activity ?: return
                                injectDownloadButton(activity)
                            }
                        })
                    }
                }
                MainHook.log("[VideoDownload] Hooked share dialog: $className")
            } catch (_: Exception) { }
        }

        // Also try hooking the long-press menu on feed items
        try {
            val feedAdapter = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.feed.adapter.FeedAdapter",
                lpparam.classLoader
            )
            for (method in feedAdapter.declaredMethods) {
                if (method.name.contains("LongPress") || method.name.contains("longClick")) {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // We'll handle this later with context menu injection
                        }
                    })
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Hook the video detail page to add download functionality.
     */
    private fun hookDetailPage(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val detailActivityClass = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.detail.ui.DetailActivity",
                lpparam.classLoader
            )

            // Hook onCreate to add download FAB or button
            XposedHelpers.findAndHookMethod(
                detailActivityClass,
                "onCreate",
                android.os.Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? android.app.Activity ?: return
                        injectDownloadButton(activity)
                    }
                }
            )
            MainHook.log("[VideoDownload] Hooked DetailActivity.onCreate")
        } catch (e: Exception) {
            MainHook.log("[VideoDownload] DetailActivity hook failed: ${e.message}")
        }
    }

    /**
     * Inject a "Download" FAB or menu item into the given Activity.
     */
    private fun injectDownloadButton(activity: android.app.Activity) {
        try {
            // Show a toast + dialog with download option
            activity.runOnUiThread {
                try {
                    // Get current Aweme from the activity
                    val aweme = extractAwemeFromActivity(activity)
                    if (aweme == null) {
                        MainHook.log("[VideoDownload] No Aweme found in activity")
                        return@runOnUiThread
                    }

                    // Extract video URL
                    val videoUrl = extractVideoUrl(aweme)
                    if (videoUrl == null) {
                        Toast.makeText(activity, "无法获取视频地址", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }

                    // Show download confirmation
                    AlertDialog.Builder(activity)
                        .setTitle("下载无水印视频")
                        .setMessage("确定下载当前视频？\n将保存到 Downloads/DouyinPlus/")
                        .setPositiveButton("下载") { _, _ ->
                            downloadVideo(activity, videoUrl)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } catch (e: Exception) {
                    MainHook.log("[VideoDownload] Inject error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            MainHook.log("[VideoDownload] UI inject error: ${e.message}")
        }
    }

    /**
     * Extract the current Aweme object from an Activity.
     */
    private fun extractAwemeFromActivity(activity: android.app.Activity): Any? {
        // Try common field names
        val fieldNames = listOf("aweme", "mAweme", "awemeData", "mAwemeData", "videoData")
        for (name in fieldNames) {
            try {
                val intent = activity.intent
                if (intent != null && intent.hasExtra(name)) {
                    return intent.getSerializableExtra(name)
                }
            } catch (_: Exception) { }
        }

        // Try activity fields via reflection
        for (field in activity.javaClass.declaredFields) {
            if (field.name.contains("aweme", true) ||
                field.type.name.contains("Aweme")
            ) {
                try {
                    field.isAccessible = true
                    return field.get(activity)
                } catch (_: Exception) { }
            }
        }

        return null
    }

    /**
     * Extract video URL (without watermark) from Aweme object.
     *
     * Douyin video URL structure:
     * - Watermarked: https://aweme.snssdk.com/aweme/v1/play/?video_id=xxx&...
     * - Clean: https://v16-webapp.douyinvod.com/xxx or similar
     *
     * The clean URL can often be obtained by:
     * 1. Removing watermark-related query params
     * 2. Replacing the CDN domain
     * 3. Using play_addr_h264 or bit_rate play addresses
     */
    private fun extractVideoUrl(aweme: Any): String? {
        try {
            // Try to get video object: aweme.getVideo() or aweme.video
            val videoObj = try {
                aweme.javaClass.getMethod("getVideo").invoke(aweme)
            } catch (_: Exception) {
                aweme.javaClass.getDeclaredField("video").apply { isAccessible = true }.get(aweme)
            } ?: return null

            // Try to get play address
            val playAddr = try {
                videoObj.javaClass.getMethod("getPlayAddr").invoke(videoObj)
            } catch (_: Exception) {
                videoObj.javaClass.getDeclaredField("playAddr").apply { isAccessible = true }.get(videoObj)
            } ?: return null

            // Get URL list
            val urlList = try {
                playAddr.javaClass.getMethod("getUrlList").invoke(playAddr) as? List<*>
            } catch (_: Exception) {
                val field = playAddr.javaClass.getDeclaredField("urlList")
                field.isAccessible = true
                field.get(playAddr) as? List<*>
            } ?: return null

            // Pick the best quality URL
            val rawUrl = urlList.firstOrNull()?.toString() ?: return null

            // Try to get clean URL (without watermark)
            return cleanVideoUrl(rawUrl, aweme)
        } catch (e: Exception) {
            MainHook.log("[VideoDownload] extractVideoUrl error: ${e.message}")
            return null
        }
    }

    /**
     * Clean the video URL by removing watermark parameters.
     *
     * Common watermark approaches by Douyin:
     * - URL parameter: watermark=1
     * - Different CDN domain for clean version
     * - play_addr_h264 for alternate encoding without watermark
     */
    private fun cleanVideoUrl(url: String, aweme: Any): String {
        var cleanUrl = url

        // Remove watermark from query params
        cleanUrl = cleanUrl.replace(Regex("[?&]watermark=\\d+"), "")
        cleanUrl = cleanUrl.replace(Regex("[?&]wm=\\d+"), "")

        // Try to get the bit_rate or h264 address (often watermark-free)
        try {
            val videoObj = aweme.javaClass.getMethod("getVideo").invoke(aweme)
            // Try bit_rate addresses
            val bitRateField = videoObj.javaClass
                .declaredFields
                .firstOrNull { it.name.contains("bitRate") || it.name.contains("bit_rate") }
            if (bitRateField != null) {
                bitRateField.isAccessible = true
                val bitRateList = bitRateField.get(videoObj) as? List<*>
                if (bitRateList != null && bitRateList.isNotEmpty()) {
                    val bestQuality = bitRateList.lastOrNull()
                    if (bestQuality != null) {
                        val playAddrField = bestQuality.javaClass
                            .declaredFields
                            .firstOrNull { it.name.contains("playAddr") }
                        if (playAddrField != null) {
                            playAddrField.isAccessible = true
                            val addr = playAddrField.get(bestQuality)
                            val urlListField = addr?.javaClass
                                ?.declaredFields
                                ?.firstOrNull { it.name == "urlList" || it.name.contains("UrlList") }
                            if (urlListField != null) {
                                urlListField.isAccessible = true
                                val urls = urlListField.get(addr) as? List<*>
                                urls?.firstOrNull()?.toString()?.let { cleanUrl = it }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        return cleanUrl
    }

    /**
     * Download the video file.
     */
    private fun downloadVideo(context: Context, videoUrl: String) {
        MainHook.log("[VideoDownload] Starting download: $videoUrl")
        Toast.makeText(context, "开始下载...", Toast.LENGTH_SHORT).show()

        object : AsyncTask<String, Int, String>() {
            override fun doInBackground(vararg params: String?): String {
                val url = params[0] ?: return "URL is null"
                return try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.connect()

                    if (connection.responseCode != 200) {
                        return "HTTP ${connection.responseCode}"
                    }

                    val fileName = "douyin_${System.currentTimeMillis()}.mp4"
                    val file = File(MainHook.downloadDir, fileName)

                    connection.inputStream.use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var total = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                total += bytesRead
                            }
                            MainHook.log("[VideoDownload] Downloaded $total bytes")
                        }
                    }
                    connection.disconnect()

                    "下载完成: ${file.absolutePath}"
                } catch (e: Exception) {
                    "下载失败: ${e.message}"
                }
            }

            override fun onPostExecute(result: String) {
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                MainHook.log("[VideoDownload] $result")
            }
        }.execute(videoUrl)
    }
}
