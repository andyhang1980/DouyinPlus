package com.dyhelper.hook

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.os.Handler
import android.os.Looper
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

        // Strategy 1: Try known class names (bear/compatible pattern)
        val wrapNames = listOf(
            "com.ss.android.ugc.aweme.sharer.panelmodel.view.WrapSizeLinearLayout",
            "com.ss.android.ugc.aweme.feed.quick.presenter.WrapSizeLinearLayout"
        )
        for (name in wrapNames) {
            if (tryHookWrap(name, loader)) { ok = true; break }
        }

        // Strategy 2: Auto-scan for Wrap-like layouts
        if (!ok) {
            val scanPkgs = listOf(
                "com.ss.android.ugc.aweme.sharer",
                "com.ss.android.ugc.aweme.share",
                "com.ss.android.ugc.aweme.sharefeed",
                "com.ss.android.ugc.aweme.feed.quick",
                "com.bytedance.ies.ugc.aweme.share"
            )
            outer@ for (pkg in scanPkgs) {
                val scan = ClassFinder.scanClasses(loader, pkg)
                for (cls in scan) {
                    if (cls.simpleName.contains("Wrap") && cls.simpleName.contains("Layout")) {
                        if (tryHookWrap2(cls)) {
                            HookUtils.log("[Menu] Auto: " + cls.name)
                            ok = true; break@outer
                        }
                    }
                }
            }
        }

        // Strategy 3: Hook RecyclerView.setAdapter to detect share panel
        if (!ok) {
            try {
                XposedHelpers.findAndHookMethod(
                    RecyclerView::class.java, "setAdapter",
                    RecyclerView.Adapter::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val rv = p.thisObject as RecyclerView
                            val adapter = p.args[0] as? RecyclerView.Adapter<*> ?: return
                            val lm = rv.layoutManager as? LinearLayoutManager ?: return
                            if (lm.orientation != LinearLayoutManager.HORIZONTAL) return
                            if (adapter.itemCount < 2 || adapter.itemCount > 30) return
                            val adName = adapter.javaClass.name.lowercase()
                            if (!adName.contains("share") && !adName.contains("presenter") &&
                                !adName.contains("model")) return
                            val parent = rv.parent as? ViewGroup ?: return
                            if (parent.findViewWithTag<View>(888888) != null) return
                            HookUtils.log("[Menu] RV detected: " + adName)
                            addMenu(parent, rv.context)
                        }
                    })
                HookUtils.log("[Menu] RecyclerView hook installed")
                ok = true
            } catch (t: Throwable) {
                HookUtils.log("[Menu] RV hook err: " + t.message)
            }
        }

        // Strategy 4: Hook Dialog.show for share dialogs
        if (!ok) {
            try {
                XposedHelpers.findAndHookMethod(
                    Dialog::class.java, "show",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val dialog = p.thisObject as Dialog
                            val w = dialog.window ?: return
                            val decor = w.decorView ?: return
                            Handler(Looper.getMainLooper()).postDelayed({
                                findAndInject(decor, dialog.javaClass.name)
                            }, 300)
                        }
                    })
                HookUtils.log("[Menu] Dialog hook installed")
                ok = true
            } catch (t: Throwable) {
                HookUtils.log("[Menu] Dialog hook err: " + t.message)
            }
        }

        return ok
    }

    private fun findAndInject(root: View, tag: String) {
        val rvs = collectRecyclerViews(root)
        for (rv in rvs) {
            val lm = rv.layoutManager as? LinearLayoutManager ?: continue
            if (lm.orientation != LinearLayoutManager.HORIZONTAL) continue
            val ad = rv.adapter ?: continue
            if (ad.itemCount < 2 || ad.itemCount > 30) continue
            val adName = ad.javaClass.name.lowercase()
            if (adName.contains("share") || adName.contains("presenter") ||
                adName.contains("model") || tag.lowercase().contains("share") ||
                tag.lowercase().contains("panel") || tag.lowercase().contains("bottom")) {
                val parent = rv.parent as? ViewGroup ?: continue
                if (parent.findViewWithTag<View>(888888) != null) continue
                HookUtils.log("[Menu] Dialog+RV: " + tag + " / " + adName)
                addMenu(parent, rv.context)
                return
            }
        }
    }

    private fun collectRecyclerViews(view: View): List<RecyclerView> {
        val result = mutableListOf<RecyclerView>()
        if (view is RecyclerView) {
            result.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(collectRecyclerViews(view.getChildAt(i)))
            }
        }
        return result
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
                HookUtils.log("[Menu] onMeasure: " + cls.name)
                return true
            }
        }
        return false
    }

    private fun addMenu(container: ViewGroup, ctx: Context) {
        val img = checkImage()
        val items = ArrayList<MI>()
        items.add(MI("u590Du5236u94FEu63A5", Runnable { copyFn(ctx) }))
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

