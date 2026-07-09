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

    override fun name() = "Menu"

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // Hook 1: WrapSizeLinearLayout.onMeasure (primary, from bear)
        val wrapNames = listOf(
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout"
        )
        for (name in wrapNames) {
            if (tryHookWrap(name, loader)) { ok = true; break }
        }
        if (!ok) {
            // Auto-scan for WrapSizeLinearLayout-like class
            val scan = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.sharer")
            for (cls in scan) {
                if (cls.simpleName.contains("Wrap") && cls.simpleName.contains("Linear") &&
                    tryHookWrap2(cls)) {
                    HookUtils.log("[Menu] Auto: " + cls.name)
                    ok = true; break
                }
            }
        }

        // Hook 2: PanelBuilder$buildPanel$1.onCreateView (secondary)
        val D = '\$'
        val pbNames = listOf(
            "com.ss.android.ugc.aweme.sharer.panelmodel.PanelBuilder" + D + "buildPanel" + D + "1",
            "com.ss.android.ugc.aweme.share.business.live.inroommorepanel.InRoomMorePanelShareLinkPanelBuilder" + D + "buildPanel" + D + "1"
        )
        for (name in pbNames) {
            if (tryHookPanel(name, loader)) { ok = true; break }
        }
        if (!ok) {
            val scan = ClassFinder.scanClasses(loader, "com.ss.android.ugc.aweme.share")
            for (cls in scan) {
                if (cls.simpleName.contains("buildPanel") && tryHookPanel2(cls)) {
                    HookUtils.log("[Menu] Auto: " + cls.name)
                    ok = true; break
                }
            }
        }

        return ok
    }

    private fun tryHookWrap(className: String, loader: ClassLoader): Boolean {
        val cls = ClassFinder.findClass(loader, className) ?: return false
        return tryHookWrap2(cls)
    }

    private fun tryHookWrap2(cls: Class<*>): Boolean {
        val intType = Int::class.javaPrimitiveType
        for (m in cls.declaredMethods) {
            if (m.name == "onMeasure" && m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == intType && m.parameterTypes[1] == intType) {
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
                HookUtils.log("[Menu] onMeasure hooked: " + cls.name)
                return true
            }
        }
        return false
    }

    private fun tryHookPanel(className: String, loader: ClassLoader): Boolean {
        val cls = ClassFinder.findClass(loader, className) ?: return false
        return tryHookPanel2(cls)
    }

    private fun tryHookPanel2(cls: Class<*>): Boolean {
        val ctxClass = Context::class.java
        val vgClass = ViewGroup::class.java
        for (m in cls.declaredMethods) {
            if (m.name == "onCreateView" && m.parameterTypes.size == 2 &&
                m.parameterTypes[0] == ctxClass && m.parameterTypes[1] == vgClass) {
                m.isAccessible = true
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val vg = p.result as? ViewGroup ?: return
                        val cn = vg.javaClass.name
                        if (!cn.contains("Measure") && !cn.contains("keyboard") && !cn.contains("Recycler")) return
                        addMenu(vg, vg.context)
                    }
                })
                HookUtils.log("[Menu] onCreateView hooked: " + cls.name)
                return true
            }
        }
        return false
    }

    private fun addMenu(container: ViewGroup, ctx: Context) {
        val img = checkImage()
        val items = ArrayList<MI>()
        items.add(MI("u590du5236u94FEu63A5", Runnable { copyFn(ctx) }))
        items.add(MI(if (img) "u56FEu7247u4E0Bu8F7D" else "u89C6u9891u4E0Bu8F7D", Runnable {
            if (img) imageFn(ctx) else videoFn(ctx)
        }))
        items.add(MI("u97F3u9891u4E0Bu8F7D", Runnable { audioFn(ctx) }))

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
