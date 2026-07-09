package com.dyhelper.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dyhelper.util.HookUtils
import com.dyhelper.util.MediaDownloader

class DownloadHook(private val capture: DataCaptureHook) {

    fun copyLink(ctx: Context) {
        val desc = capture.getDesc()
        val url = capture.getVideoUrl() ?: ""
        val text = if (desc.isNotEmpty()) desc + "\n" + url else url
        if (text.isNotEmpty()) {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("desc", text))
            HookUtils.showToast(ctx, "已复制!")
        }
    }

    fun downloadVideo(ctx: Context) {
        download(ctx, 1)
    }

    fun downloadAudio(ctx: Context) {
        download(ctx, 0)
    }

    fun downloadImage(ctx: Context) {
        download(ctx, 2)
    }

    private fun download(ctx: Context, type: Int) {
        val url = when (type) {
            0 -> capture.getMusicUrl()
            2 -> null
            else -> capture.getVideoUrl()
        }
        if (url == null) {
            HookUtils.showToast(ctx, "获取链接失败")
            return
        }
        HookUtils.showToast(ctx, "开始下载...")
        val ext = if (type == 0) ".mp3" else ".mp4"
        Thread {
            val file = MediaDownloader.download(url, ext)
            if (file != null) {
                HookUtils.showToast(ctx, "下载完成: " + file.name)
            }
        }.start()
    }
}
