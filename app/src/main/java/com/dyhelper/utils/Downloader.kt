package com.dyhelper.utils

import android.os.Environment
import android.widget.ProgressBar
import com.dyhelper.MainHook
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Downloader {

    fun download(url: String, type: Int, progressBar: ProgressBar?) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != 200) {
                MainHook.log("[Download] HTTP ${connection.responseCode}")
                return
            }

            val contentLength = connection.contentLength
            val ext = when (type) { 0 -> ".mp3"; 2 -> ".jpg"; else -> ".mp4" }
            val fileName = "dy_${System.currentTimeMillis()}$ext"

            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "DouyinHelper"
            ).apply { if (!exists()) mkdirs() }

            val file = File(dir, fileName)

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var total = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        total += bytesRead
                        if (contentLength > 0 && progressBar != null) {
                            val pct = (total * 100 / contentLength).toInt()
                            progressBar.post { progressBar.progress = pct }
                        }
                    }
                    MainHook.log("[Download] Saved: ${file.absolutePath} ($total bytes)")
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            MainHook.log("[Download] Error: ${e.message}")
        }
    }
}
