package com.dyhelper.hooks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dyhelper.MainHook
import com.dyhelper.adaptive.ClassScanner.ClassFingerprint
import com.dyhelper.adaptive.ClassScanner.MethodPattern
import com.dyhelper.data.AwemeData
import com.dyhelper.utils.Downloader
import com.dyhelper.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Share menu hook: inject custom options into Douyin's share panel.
 *
 * Uses the adaptive system for maximum version tolerance.
 * When Douyin updates and changes class names, the module auto-discovers
 * the share panel classes by their structural fingerprint.
 */
object ShareMenuHook {

    private var progressBar: ProgressBar? = null
    private val TAG_VIEW = 888888

    // ==== Hardcoded class names (fallback for known versions) ====
    private val SHARE_PANEL_CLASSES = listOf(
        "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
    )
    private val PANEL_BUILDER_CLASSES = listOf(
        "com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder\$buildPanel\$1",
    )

    // ==== Structural fingerprints (for auto-discovery) ====
    //
    // Key identifying features of Douyin share panel:
    // - Extends LinearLayout
    // - Has onMeasure(int,int) method
    // - Located in the sharer.panelmodel.view package
    // - Extends "WrapSizeLinearLayout" or similar
    private val SHARE_PANEL_FP = ClassFingerprint(
        label = "SharePanelLayout",
        packagePrefixes = listOf("com.ss.android.ugc.aweme.sharer.panelmodel.view"),
        parentClassName = "android.widget.LinearLayout",
        methodPatterns = listOf(
            MethodPattern("void", listOf("int", "int"))   // onMeasure(int,int)
        )
    )

    // PanelBuilder inner class: used in some Douyin versions
    // - Implements a callback/interface pattern
    // - Has onCreateView method returning View
    private val PANEL_BUILDER_FP = ClassFingerprint(
        label = "PanelBuilder",
        packagePrefixes = listOf("com.ss.android.ugc.aweme.sharer.panelmodel"),
        methodPatterns = listOf(
            MethodPattern("android.view.View", listOf("android.content.Context", "android.view.ViewGroup"))
        )
    )

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[ShareMenu] Initializing (adaptive mode)...")

        // Hook share panel -> inject menu items
        HookHelper.tryAdaptive(
            tag = "ShareMenu:Panel",
            fingerprint = SHARE_PANEL_FP,
            methodPattern = MethodPattern("void", listOf("int", "int")),
            hardcodedClasses = SHARE_PANEL_CLASSES,
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val linearLayout = param.thisObject as? LinearLayout ?: return
                    injectMenuItems(linearLayout)
                }
            }
        )

        // Hook panel builder -> inject into another share UI variant
        HookHelper.tryAdaptive(
            tag = "ShareMenu:PanelBuilder",
            fingerprint = PANEL_BUILDER_FP,
            methodPattern = MethodPattern("android.view.View",
                listOf("android.content.Context", "android.view.ViewGroup")),
            hardcodedClasses = PANEL_BUILDER_CLASSES,
            callback = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val viewGroup = param.result as? ViewGroup ?: return
                    if (viewGroup.javaClass.name.contains("MeasureLinearLayout")) {
                        injectMenuItems(viewGroup)
                    }
                }
            }
        )
    }

    private fun injectMenuItems(container: ViewGroup) {
        if (container.childCount < 1) return
        if (container.findViewWithTag<View>(TAG_VIEW) != null) return

        val firstChild = container.getChildAt(0)
        val clsName = firstChild.javaClass.simpleName
        if (clsName.contains("MeasureOnce") || clsName.contains("Linear")) return

        val ctx = container.context
        val isImage = AwemeData.getType(AwemeData.currentAweme) == 68
        val downloadLabel = if (isImage) "图片下载" else "视频下载"

        val items = listOf(
            MenuItem("复制链接") { onCopyLink(ctx, isImage) },
            MenuItem(downloadLabel) { onDownload(ctx, if (isImage) 2 else 1) },
            MenuItem("音频下载") { onDownload(ctx, 0) },
        )

        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            setPadding(13, 0, 13, 0)
            adapter = MenuAdapter(items)
            tag = TAG_VIEW
        }
        container.addView(rv)

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        progressBar = pb
        container.addView(pb)
    }

    private fun onCopyLink(ctx: Context, isImage: Boolean) {
        val desc = AwemeData.getDesc(AwemeData.currentAweme) ?: ""
        val url = if (isImage) AwemeData.getUrl(2, AwemeData.currentAweme)
                  else AwemeData.getUrl(1, AwemeData.currentAweme) ?: ""
        val text = if (desc.isNotEmpty()) "$desc\n$url" else url
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("desc", text))
        Toast.makeText(ctx, "已复制链接!", Toast.LENGTH_SHORT).show()
    }

    private fun onDownload(ctx: Context, type: Int) {
        val url = AwemeData.getUrl(type, AwemeData.currentAweme)
        if (url == null) { Toast.makeText(ctx, "暂不支持下载", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(ctx, "开始下载...", Toast.LENGTH_SHORT).show()
        Thread { Downloader.download(url, type, progressBar) }.start()
    }

    data class MenuItem(val label: String, val onClick: () -> Unit)

    class MenuAdapter(private val items: List<MenuItem>) : RecyclerView.Adapter<MenuAdapter.VH>() {
        class VH(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            TextView(parent.context).apply {
                textSize = 12f; setTextColor(Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER; setPadding(20, 12, 20, 12)
            }
        )
        override fun onBindViewHolder(holder: VH, pos: Int) {
            holder.itemView.apply {
                (this as TextView).text = items[pos].label
                setOnClickListener { items[pos].onClick() }
            }
        }
        override fun getItemCount() = items.size
    }
}
