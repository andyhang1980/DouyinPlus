package com.dyhelper.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import com.dyhelper.util.HookUtils
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadHook(private val capture: DataCaptureHook) {

    fun copyLink(ctx: Context) {
        val desc = capture.getDesc()
        val url = capture.getVideoUrl() ?: ""
        val text = if (desc.isNotEmpty()) desc + "\n" + url else url
        if (text.isNotEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("desc", text))
            Toast.makeText(ctx, "Copied!", Toast.LENGTH_SHORT).show()
        }
    }
    fun downloadVideo(ctx: Context) { dl(ctx, capture.getVideoUrl(), ".mp4") }
    fun downloadAudio(ctx: Context) { dl(ctx, capture.getMusicUrl(), ".mp3") }
    fun downloadImage(ctx: Context) { dl(ctx, null, ".jpg") }

    private fun dl(ctx: Context, url: String?, ext: String) {
        if (url == null) { Toast.makeText(ctx, "No URL", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(ctx, "Downloading...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 120000; conn.connect()
                if (conn.responseCode != 200) return@Thread
                val name = "dy_" + System.currentTimeMillis() + ext
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DouyinHelper")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                conn.inputStream.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
                conn.disconnect()
                HookUtils.log("Downloaded: " + file.name)
            } catch (e: Exception) { HookUtils.log("Download err: " + e.message) }
        }.start()
    }
}
