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

class ShareMenuHook(
    private val onCopy: (Context) -> Unit,
    private val onVideo: (Context) -> Unit,
    private val onAudio: (Context) -> Unit,
    private val onImage: (Context) -> Unit,
    private val isImage: () -> Boolean
) : BaseHook {

    override fun name() = "Menu"

    override fun init(loader: ClassLoader): Boolean {
        return hookOne(loader, "Menu",
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
            Void.TYPE, "onMeasure",
            Integer.TYPE, Integer.TYPE,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val panel = param.thisObject as? LinearLayout ?: return
                    if (panel.childCount < 1) return
                    if (panel.findViewWithTag<View>(888888) != null) return
                    val sn = panel.getChildAt(0).javaClass.simpleName
                    if (sn.contains("MeasureOnce") || sn.contains("Linear")) return
                    injectMenu(panel, panel.context)
                }
            })
    }

    private fun injectMenu(container: ViewGroup, ctx: Context) {
        val img = isImage()
        val items = ArrayList<MenuItem>()
        items.add(MenuItem("Copy", Runnable { onCopy(ctx) }))
        items.add(MenuItem(if (img) "Image" else "Video",
            Runnable { if (img) onImage(ctx) else onVideo(ctx) }))
        items.add(MenuItem("Audio", Runnable { onAudio(ctx) }))

        val rv = RecyclerView(ctx)
        rv.layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        rv.setPadding(13, 0, 13, 0)
        rv.adapter = MenuAdapter(items)
        rv.tag = 888888
        container.addView(rv)

        val pb = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.visibility = View.GONE
        container.addView(pb)
    }

    private fun hookOne(
        loader: ClassLoader, tag: String, className: String,
        returnType: Class<*>?, methodName: String,
        vararg paramTypes: Class<*>,
        callback: XC_MethodHook
    ): Boolean {
        val clazz = XposedHelpers.findClassIfExists(className, loader)
        if (clazz == null) { HookUtils.log("[" + tag + "] Class not found"); return false }
        for (m in clazz.declaredMethods) {
            if (m.name != methodName) continue
            if (returnType != null && m.returnType != returnType) continue
            val params = m.parameterTypes
            if (params.size != paramTypes.size) continue
            var match = true
            var i = 0
            while (i < paramTypes.size) {
                if (paramTypes[i] != null && paramTypes[i] != params[i]) { match = false; break }
                i++
            }
            if (!match) continue
            m.isAccessible = true
            XposedBridge.hookMethod(m, callback)
            return true
        }
        return false
    }

    data class MenuItem(val label: String, val action: Runnable)
    class MenuAdapter(private val items: List<MenuItem>) :
        RecyclerView.Adapter<MenuAdapter.VH>() {
        class VH(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int): VH {
            val tv = TextView(parent.context)
            tv.textSize = 12f
            tv.setTextColor(Color.parseColor("#CCCCCC"))
            tv.gravity = Gravity.CENTER
            tv.setPadding(20, 12, 20, 12)
            return VH(tv)
        }
        override fun onBindViewHolder(vh: VH, pos: Int) {
            (vh.itemView as TextView).text = items[pos].label
            vh.itemView.setOnClickListener { items[pos].action.run() }
        }
        override fun getItemCount(): Int = items.size
    }
}
