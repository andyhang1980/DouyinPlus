package com.dyhelper.hooks

import android.app.AlertDialog
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
import com.dyhelper.data.AwemeData
import com.dyhelper.MainHook
import com.dyhelper.utils.Downloader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Share menu hook: inject custom options into Douyin's share panel.
 *
 * Hook targets (from Douyin):
 * - com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout.onMeasure(int, int)
 * - com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder$buildPanel$1.onCreateView(Context, ViewGroup)
 *
 * Injected items: copy link, download video/image, download music
 */
object ShareMenuHook {

    private var progressBar: ProgressBar? = null
    private val TAG_VIEW = 888888

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        MainHook.log("[ShareMenu] Initializing...")

        // Hook 1: WrapSizeLinearLayout.onMeasure
        try {
            val wrapLayout = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(wrapLayout, "onMeasure", Int::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val linearLayout = param.thisObject as? LinearLayout ?: return
                        injectMenuItems(linearLayout, true)
                    }
                })
            MainHook.log("[ShareMenu] Hooked WrapSizeLinearLayout.onMeasure")
        } catch (e: Exception) {
            MainHook.log("[ShareMenu] WrapSizeLinearLayout hook failed: ${e.message}")
        }

        // Hook 2: PanelBuilder$buildPanel$1.onCreateView
        try {
            val panelBuilder = XposedHelpers.findClass(
                "com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder\$buildPanel\$1",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(panelBuilder, "onCreateView",
                android.content.Context::class.java, ViewGroup::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val viewGroup = param.result as? ViewGroup ?: return
                        if (viewGroup.javaClass.name.contains("MeasureLinearLayout")) {
                            injectMenuItems(viewGroup, false)
                        }
                    }
                })
            MainHook.log("[ShareMenu] Hooked PanelBuilder.onCreateView")
        } catch (e: Exception) {
            MainHook.log("[ShareMenu] PanelBuilder hook failed: ${e.message}")
        }
    }

    private fun injectMenuItems(container: ViewGroup, isHorizontal: Boolean) {
        if (container.childCount < 1) return
        if (container.findViewWithTag<View>(TAG_VIEW) != null) return // already injected

        val firstChild = container.getChildAt(0)
        val className = firstChild.javaClass.simpleName
        if (className.contains("MeasureOnce") || className.contains("Linear")) return // already customized

        val ctx = container.context

        // Create menu row
        val menuView = createMenuView(ctx, isHorizontal)
        menuView.tag = TAG_VIEW
        container.addView(menuView)

        // Create progress bar (hidden initially)
        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        progressBar = pb
        container.addView(pb)
    }

    private fun createMenuView(context: Context, isHorizontal: Boolean): View {
        val isImage = AwemeData.getType(AwemeData.currentAweme) == 68
        val downloadLabel = if (isImage) "图片下载" else "视频下载"

        val items = listOf(
            MenuItem("复制链接") { onCopyLink(context) },
            MenuItem(downloadLabel) { onDownload(context, if (isImage) 2 else 1) },
            MenuItem("音频下载") { onDownload(context, 0) },
        )

        val recyclerView = RecyclerView(context)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.setPadding(13, 0, 13, 0)
        recyclerView.adapter = MenuAdapter(items)
        return recyclerView
    }

    private fun onCopyLink(context: Context) {
        val desc = AwemeData.getDesc(AwemeData.currentAweme) ?: ""
        val url = when {
            AwemeData.getType(AwemeData.currentAweme) == 68 ->
                AwemeData.getUrl(2, AwemeData.currentAweme)
            else -> AwemeData.getUrl(1, AwemeData.currentAweme)
        } ?: ""
        val text = if (desc.isNotEmpty()) "$desc\n$url" else url

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("desc", text))
        Toast.makeText(context, "已复制链接!", Toast.LENGTH_SHORT).show()
    }

    private fun onDownload(context: Context, type: Int) {
        val url = AwemeData.getUrl(type, AwemeData.currentAweme)
        if (url == null) {
            Toast.makeText(context, "暂不支持下载此内容", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = arrayOf("音乐", "视频", "图片")
        val label = labels.getOrElse(type) { "文件" }
        Toast.makeText(context, "开始下载${label}...", Toast.LENGTH_SHORT).show()

        // Download in background
        Thread {
            Downloader.download(url, type, progressBar)
        }.start()
    }

    // ---- RecyclerView adapter for menu items ----

    data class MenuItem(val label: String, val onClick: () -> Unit)

    class MenuAdapter(private val items: List<MenuItem>) :
        RecyclerView.Adapter<MenuAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER
                setPadding(20, 12, 20, 12)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.textView.text = item.label
            holder.textView.setOnClickListener { item.onClick() }
        }

        override fun getItemCount(): Int = items.size
    }
}
