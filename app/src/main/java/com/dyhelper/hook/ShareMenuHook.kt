package com.dyhelper.hook

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.Void

class ShareMenuHook(
    private val onCopy: (Context) -> Unit,
    private val onVideo: (Context) -> Unit,
    private val onAudio: (Context) -> Unit,
    private val onImage: (Context) -> Unit,
    private val isImage: () -> Boolean
) : BaseHook {

    override fun name() = "Menu"
    override fun init(loader: ClassLoader): Boolean {
        return h(loader, "Menu", "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
            Void.TYPE, "onMeasure",
            arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val panel = p.thisObject as? LinearLayout ?: return
                    if (panel.childCount < 1) return
                    if (panel.findViewWithTag<View>(888888) != null) return
                    val sn = panel.getChildAt(0).javaClass.simpleName
                    if (sn.contains("MeasureOnce") || sn.contains("Linear")) return
                    addMenu(panel, panel.context)
                }
            })
    }

    private fun addMenu(container: ViewGroup, ctx: Context) {
        val img = isImage()
        val items = ArrayList<MI>()
        items.add(MI("Copy", Runnable { onCopy(ctx) }))
        items.add(MI(if (img) "Image" else "Video",
            Runnable { if (img) onImage(ctx) else onVideo(ctx) }))
        items.add(MI("Audio", Runnable { onAudio(ctx) }))

        val rv = RecyclerView(ctx)
        rv.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rv.setPadding(13, 0, 13, 0)
        rv.adapter = MA(items)
        rv.tag = 888888
        container.addView(rv)

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100; pb.visibility = View.GONE
        container.addView(pb)
    }

    data class MI(val label: String, val action: Runnable)

    class MA(private val items: List<MI>) : RecyclerView.Adapter<MA.VH>() {
        class VH(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val tv = TextView(p.context)
            tv.textSize = 12f; tv.setTextColor(Color.parseColor("#CCCCCC"))
            tv.gravity = Gravity.CENTER; tv.setPadding(20, 12, 20, 12)
            return VH(tv)
        }
        override fun onBindViewHolder(vh: VH, pos: Int) {
            (vh.itemView as TextView).text = items[pos].label
            vh.itemView.setOnClickListener { items[pos].action.run() }
        }
        override fun getItemCount(): Int = items.size
    }

    private fun h(loader: ClassLoader, tag: String, cls: String,
                  rt: Class<*>?, mn: String, pts: Array<Class<*>>,
                  cb: XC_MethodHook): Boolean {
        val c = XposedHelpers.findClassIfExists(cls, loader) ?: run {
            HookUtils.log("[" + tag + "] Class not found"); return false
        }
        for (m in c.declaredMethods) {
            if (m.name != mn) continue
            if (rt != null && m.returnType != rt) continue
            val mp = m.parameterTypes
            if (mp.size != pts.size) continue
            var ok = true
            var i = 0
            while (i < pts.size) { if (pts[i] != null && pts[i] != mp[i]) { ok = false; break }; i++ }
            if (!ok) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, cb)
            return true
        }
        return false
    }
}
