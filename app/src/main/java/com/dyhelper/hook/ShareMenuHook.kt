package com.dyhelper.hook

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook

class ShareMenuHook(
    private val copyFn: (Context) -> Unit,
    private val videoFn: (Context) -> Unit,
    private val audioFn: (Context) -> Unit,
    private val imageFn: (Context) -> Unit,
    private val checkImage: () -> Boolean
) : BaseHook {

    data class MI(val label: String, val action: Runnable)

    class MA(items: List<MI>) : RecyclerView.Adapter<MA.VH>() {
        private val data = items.toList()
        class VH(view: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val tv = TextView(p.context).apply {
                textSize = 12f; setTextColor(Color.parseColor("#CCCCCC"))
                gravity = Gravity.CENTER; setPadding(20, 12, 20, 12)
            }
            return VH(tv)
        }
        override fun onBindViewHolder(vh: VH, pos: Int) {
            (vh.itemView as TextView).text = data[pos].label
            vh.itemView.setOnClickListener { data[pos].action.run() }
        }
        override fun getItemCount() = data.size
    }

    override fun name() = "Menu"

    private fun injectMenu(parent: ViewGroup, ctx: Context) {
        if (parent.findViewWithTag<View>(888888) != null) return
        val img = checkImage()
        val items = listOf(
            MI("\u590D\u5236\u94FE\u63A5", Runnable { copyFn(ctx) }),
            MI(if (img) "\u56FE\u7247\u4E0B\u8F7D" else "\u89C6\u9891\u4E0B\u8F7D", Runnable {
                if (img) imageFn(ctx) else videoFn(ctx)
            }),
            MI("\u97F3\u9891\u4E0B\u8F7D", Runnable { audioFn(ctx) })
        )
        val rv = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            adapter = MA(items)
            tag = 888888
            setPadding(13, 0, 13, 0)
        }
        parent.addView(rv, 0)
        HookUtils.log("[Menu] Injected into " + parent.javaClass.simpleName)
    }

    private fun findAndInject(root: View): Boolean {
        if (root is ViewGroup && root.javaClass.name.contains("ActionBar")) {
            injectMenu(root, root.context)
            return true
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (findAndInject(root.getChildAt(i))) return true
            }
        }
        return false
    }

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        // Strategy 1: Hook Dialog.show for share/social panels
        HookUtils.hookOneMethod(Dialog::class.java, "show", object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                val cn = p.thisObject.javaClass.name.lowercase()
                if (!cn.contains("social") && !cn.contains("share")) return
                HookUtils.log("[Menu] show: " + cn)
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val dlg = p.thisObject as Dialog
                        val dec = dlg.window?.decorView ?: return@postDelayed
                        findAndInject(dec)
                    } catch (t: Throwable) {
                        HookUtils.log("[Menu] err: " + t.message)
                    }
                }, 500)
            }
        })
        HookUtils.log("[Menu] Dialog hook OK")
        ok = true

        // Strategy 2: Hook RecyclerView.setAdapter for share-related adapters
        HookUtils.hookOneMethod(RecyclerView::class.java, "setAdapter", object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                val rv = p.thisObject as RecyclerView
                val an = (p.args[0]?.javaClass?.name ?: "").lowercase()
                if (!an.contains("social") && !an.contains("share")) return
                HookUtils.log("[Menu] RV ad: " + an)
                Handler(Looper.getMainLooper()).postDelayed({
                    val parent = rv.parent as? ViewGroup ?: return@postDelayed
                    injectMenu(parent, rv.context)
                }, 300)
            }
        })
        HookUtils.log("[Menu] RV hook OK")

        return ok
    }
}
