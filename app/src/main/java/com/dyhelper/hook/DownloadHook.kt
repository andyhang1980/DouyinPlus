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
            toast(ctx, "u5DF2u590Du5236")
        }
    }

    fun video(ctx: Context) { dl(ctx, c.getVideoUrl(), ".mp4") }
    fun audio(ctx: Context) { dl(ctx, c.getMusicUrl(), ".mp3") }
    fun image(ctx: Context) {
        val url = c.getVideoUrl() ?: c.getMusicUrl()
        dl(ctx, url, ".jpg")
    }

    private fun dl(ctx: Context, url: String?, ext: String) {
        if (url == null || url.isEmpty()) {
            toast(ctx, "u83B7u53D6u94FEu63A5u5931u8D25")
            return
        }
        toast(ctx, "u5F00u59CBu4E0Bu8F7D...")
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "okhttp/3.10.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 120000
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode != 200) {
                    toast(ctx, "u4E0Bu8F7Du5931u8D25: HTTP " + conn.responseCode)
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
                toast(ctx, "u4E0Bu8F7Du5B8Cu6210: " + file.name)
            } catch (e: Exception) {
                toast(ctx, "u9519u8BEF: " + (e.message ?: "unknown"))
            }
        }.start()
    }

    private fun toast(ctx: Context, msg: String) {
        handler.post { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
    }
}
