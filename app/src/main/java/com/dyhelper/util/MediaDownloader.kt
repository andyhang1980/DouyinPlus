package com.dyhelper.util

import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MediaDownloader {

    fun download(url: String, ext: String, onProgress: ((Int) -> Unit)? = null): File? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 120000
            conn.connect()
            if (conn.responseCode != 200) {
                HookUtils.log("Download HTTP " + conn.responseCode)
                conn.disconnect()
                return null
            }
            val total = conn.contentLength
            val name = "dy_" + System.currentTimeMillis() + ext
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DouyinHelper")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, name)
            val input = conn.inputStream
            val output = FileOutputStream(file)
            val buf = ByteArray(8192)
            var read = 0
            var totalRead = 0L
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                totalRead += read
                if (total > 0 && onProgress != null) {
                    onProgress((totalRead * 100 / total).toInt())
                }
            }
            output.close()
            input.close()
            conn.disconnect()
            HookUtils.log("Downloaded: " + file.absolutePath)
            file
        } catch (e: Exception) {
            HookUtils.log("Download err: " + e.message)
            null
        }
    }
}
