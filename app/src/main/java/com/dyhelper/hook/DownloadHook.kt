package com.dyhelper.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadHook(private val c: DataCaptureHook) {

    private val handler = Handler(Looper.getMainLooper())

    fun copyLink(ctx: Context) {
        val desc = c.getDesc()
        val url = c.getVideoUrl() ?: ""
        val text = if (desc.isNotEmpty()) desc + "\n" + url else url
        if (text.isNotEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("desc", text))
            toast(ctx, "\u5DF2\u590D\u5236\u94FE\u63A5")
        } else {
            toast(ctx, "\u672A\u83B7\u53D6\u5230\u94FE\u63A5")
        }
    }

    fun video(ctx: Context) { dl(ctx, c.getVideoUrl(), ".mp4", "\u89C6\u9891") }
    fun audio(ctx: Context) { dl(ctx, c.getMusicUrl(), ".mp3", "\u97F3\u9891") }
    fun image(ctx: Context) {
        val url = c.getVideoUrl() ?: c.getMusicUrl()
        dl(ctx, url, ".jpg", "\u56FE\u7247")
    }

    private fun dl(ctx: Context, url: String?, ext: String, label: String) {
        if (url == null || url.isEmpty()) {
            toast(ctx, "\u83B7\u53D6" + label + "\u94FE\u63A5\u5931\u8D25")
            return
        }
        toast(ctx, "\u5F00\u59CB\u4E0B\u8F7D" + label + "...")
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "okhttp/3.10.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 120000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode != 200) {
                    toast(ctx, "\u4E0B\u8F7D\u5931\u8D25: HTTP " + conn.responseCode)
                    return@Thread
                }
                val name = "dy_" + System.currentTimeMillis() + ext
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "DH"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
                toast(ctx, label + "\u4E0B\u8F7D\u5B8C\u6210: " + file.name)
            } catch (e: Exception) {
                toast(ctx, "\u9519\u8BEF: " + (e.message ?: "unknown"))
            }
        }.start()
    }

    private fun toast(ctx: Context, msg: String) {
        handler.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}