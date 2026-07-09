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
import com.dyhelper.util.ClassFinder
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.Void

class ShareMenuHook(
    private val copyFn: (Context) -> Unit,
    private val videoFn: (Context) -> Unit,
    private val audioFn: (Context) -> Unit,
    private val imageFn: (Context) -> Unit,
    private val checkImage: () -> Boolean
) : BaseHook {

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

    override fun name() = "__分享菜单__"

    override fun init(loader: ClassLoader): Boolean {
        val intType = Int::class.javaPrimitiveType
        var ok = false

        // Hook 1: WrapSizeLinearLayout.onMeasure (try known name)
        val cls1 = ClassFinder.findClass(loader,
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout")
        if (cls1 != null && hookOnMeasure(cls1, intType)) {
            HookUtils.log("[Menu] WrapSizeLinearLayout hooked")
            ok = true
        }

        // Hook 2: PanelBuilder inner class
        val pbClass = "com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder${'$'}buildPanel${'$'}1"
        val cls2 = ClassFinder.findClass(loader, pbClass)
        if (cls2 != null && hookOnCreateView(cls2)) {
            HookUtils.log("[Menu] PanelBuilder onCreateView hooked")
            ok = true
        }

        // Auto-scan share panel classes if direct lookup failed
        if (!ok) {
            val classes = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.sharer")
            for (cls in classes) {
                if (hookOnMeasure(cls, intType)) {
                    HookUtils.log("[Menu] Auto-found share panel: " + cls.name)
                    ok = true
                }
            }
        }

        return ok
    }

    private fun hookOnMeasure(cls: Class<*>, intType: Class<*>): Boolean {
        for (m in cls.declaredMethods) {
            if (m.name == "onMeasure" && m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == intType &&
                m.parameterTypes[1] == intType) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val panel = p.thisObject as? LinearLayout ?: return
                        if (panel.childCount < 1) return
                        if (panel.findViewWithTag<View>(888888) != null) return
                        val sn = panel.getChildAt(0).javaClass.simpleName
                        if (sn.contains("MeasureOnce") || sn.contains("Linear")) return
                        addMenu(panel, panel.context)
                    }
                })
                return true
            }
        }
        return false
    }

    private fun hookOnCreateView(cls: Class<*>): Boolean {
        val ctxClass = Context::class.java
        val vgClass = ViewGroup::class.java
        for (m in cls.declaredMethods) {
            if (m.name == "onCreateView" &&
                m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == ctxClass &&
                m.parameterTypes[1] == vgClass) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val vg = p.result as? ViewGroup ?: return
                        if (!vg.javaClass.name.contains("MeasureLinearLayout")) return
                        addMenu(vg, vg.context)
                    }
                })
                return true
            }
        }
        return false
    }

    private fun addMenu(container: ViewGroup, ctx: Context) {
        val img = checkImage()
        val items = ArrayList<MI>()
        items.add(MI("__复制链接__", Runnable { copyFn(ctx) }))
        items.add(MI(if (img) "__图片下载__" else "__视频下载__", Runnable {
            if (img) imageFn(ctx) else videoFn(ctx)
        }))
        items.add(MI("__音频下载__", Runnable { audioFn(ctx) }))

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
}
