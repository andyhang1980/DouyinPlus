package com.dyhelper.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadHook(private val c: DataCaptureHook) {

    fun copyLink(ctx: Context) {
        val desc = c.getDesc()
        val url = c.getVideoUrl() ?: ""
        val text = if (desc.isNotEmpty()) desc + "\n" + url else url
        if (text.isNotEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("desc", text))
            Toast.makeText(ctx, "已复制!", Toast.LENGTH_SHORT).show()
        }
    }

    fun video(ctx: Context) { dl(ctx, c.getVideoUrl(), ".mp4") }
    fun audio(ctx: Context) { dl(ctx, c.getMusicUrl(), ".mp3") }
    fun image(ctx: Context) { dl(ctx, null, ".jpg") }

    private fun dl(ctx: Context, url: String?, ext: String) {
        if (url == null) { Toast.makeText(ctx, "获取链接失败", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(ctx, "开始下载...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 120000; conn.connect()
                if (conn.responseCode != 200) return@Thread
                val name = "dy_" + System.currentTimeMillis() + ext
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DH")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                conn.inputStream.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
                conn.disconnect()
                Toast.makeText(ctx, "下载完成: " + file.name, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "错误: " + e.message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
