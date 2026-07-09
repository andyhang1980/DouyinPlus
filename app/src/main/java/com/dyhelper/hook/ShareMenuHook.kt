package com.dyhelper.hook

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    override fun name() = "Menu"

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
        pb.max = 100
        pb.visibility = View.GONE
        container.addView(pb)
    }

    private fun collectRVs(view: View): List<RecyclerView> {
        val result = mutableListOf<RecyclerView>()
        if (view is RecyclerView) {
            result.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                result.addAll(collectRVs(view.getChildAt(i)))
            }
        }
        return result
    }

    private fun tryInject(decor: View, source: String): Boolean {
        val rvs = collectRVs(decor)
        for (rv in rvs) {
            val lm = rv.layoutManager as? LinearLayoutManager ?: continue
            if (lm.orientation != LinearLayoutManager.HORIZONTAL) continue
            val ad = rv.adapter ?: continue
            if (ad.itemCount < 2 || ad.itemCount > 30) continue
            val adName = ad.javaClass.name.lowercase()
            if (!adName.contains("share") && !adName.contains("presenter") &&
                !adName.contains("model") && !source.lowercase().contains("share") &&
                !source.lowercase().contains("panel") && !source.lowercase().contains("bottom")) continue
            val parent = rv.parent as? ViewGroup ?: continue
            if (parent.findViewWithTag<View>(888888) != null) continue
            HookUtils.log("[Menu] Injected into: " + source + " / " + adName)
            addMenu(parent, rv.context)
            return true
        }
        return false
    }

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // Strategy 1: Hook RecyclerView.setAdapter
        try {
            val rvClass = RecyclerView::class.java
            for (m in rvClass.declaredMethods) {
                if (m.name == "setAdapter" && m.parameterTypes.size == 1) {
                    m.isAccessible = true
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val rv = p.thisObject as RecyclerView
                            tryInject(rv, "RV")
                        }
                    })
                    HookUtils.log("[Menu] RV hook installed")
                    ok = true
                    break
                }
            }
        } catch (t: Throwable) {
            HookUtils.log("[Menu] RV hook err: " + t.message)
        }

        // Strategy 2: Hook Dialog.show
        try {
            val showMethod = Dialog::class.java.getDeclaredMethod("show")
            showMethod.isAccessible = true
            XposedBridge.hookMethod(showMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val dialog = p.thisObject as Dialog
                    val w = dialog.window ?: return
                    val decor = w.decorView ?: return
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        tryInject(decor, dialog.javaClass.name)
                    }, 500)
                }
            })
            HookUtils.log("[Menu] Dialog hook installed")
            ok = true
        } catch (t: Throwable) {
            HookUtils.log("[Menu] Dialog hook err: " + t.message)
        }

        return ok
    }
}
