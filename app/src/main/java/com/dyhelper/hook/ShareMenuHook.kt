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
import de.robv.android.xposed.XposedBridge

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
            MI("u590Du5236u94FEu63A5", Runnable { copyFn(ctx) }),
            MI(if (img) "u56FEu7247u4E0Bu8F7D" else "u89C6u9891u4E0Bu8F7D", Runnable {
                if (img) imageFn(ctx) else videoFn(ctx)
            }),
            MI("u97F3u9891u4E0Bu8F7D", Runnable { audioFn(ctx) })
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

        // === Primary: Hook Dialog.show, filter for share panels ===
        try {
            val m = Dialog::class.java.getDeclaredMethod("show")
            m.isAccessible = true
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val dlgClass = p.thisObject.javaClass.name.lowercase()
                    if (!dlgClass.contains("social") && !dlgClass.contains("share")) return
                    HookUtils.log("[Menu] Dialog.show: " + dlgClass)
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val w = (p.thisObject as Dialog).window ?: return@postDelayed
                            val d = w.decorView ?: return@postDelayed
                            findAndInject(d)
                        } catch (t: Throwable) {
                            HookUtils.log("[Menu] inject err: " + t.message)
                        }
                    }, 500)
                }
            })
            HookUtils.log("[Menu] Dialog hook OK")
            ok = true
        } catch (t: Throwable) {
            HookUtils.log("[Menu] Dialog hook err: " + t.message)
        }

        // === Strategy 2: Hook known share panel class methods ===
        val targets = listOf(
            "com.ss.android.ugc.aweme.share.socialpanel.dialog.SocialActionsPanel"
        )
        for (cn in targets) {
            val cls = HookUtils.findClass(loader, cn) ?: continue
            HookUtils.log("[Menu] Found class: " + cn)
            // Try to hook constructor
            for (ctor in cls.declaredConstructors) {
                if (ctor.parameterTypes.size !in 1..5) continue
                ctor.isAccessible = true
                XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                val dlg = p.thisObject as? Dialog ?: return@postDelayed
                                val w = dlg.window ?: return@postDelayed
                                findAndInject(w.decorView ?: return@postDelayed)
                            } catch (_: Exception) {}
                        }, 600)
                    }
                })
                HookUtils.log("[Menu] Ctor hook: " + cn)
                ok = true
                break
            }
        }

        // === Strategy 3: RV setAdapter fallback for SocialActionsPanelRecyclerView ===
        try {
            val rvC = RecyclerView::class.java
            for (mtd in rvC.declaredMethods) {
                if (mtd.name != "setAdapter" || mtd.parameterTypes.size != 1) continue
                mtd.isAccessible = true
                XposedBridge.hookMethod(mtd, object : XC_MethodHook() {
                    override fun afterHookedMethod(p: MethodHookParam) {
                        val rv = p.thisObject
                        if (!rv.javaClass.name.contains("SocialActions")) return
                        HookUtils.log("[Menu] SocialActions RV adapter set, parent=" +
                            ((rv as View).parent as? ViewGroup)?.javaClass?.simpleName)
                        Handler(Looper.getMainLooper()).postDelayed({
                            val parent = (rv as View).parent as? ViewGroup ?: return@postDelayed
                            injectMenu(parent, rv.context)
                        }, 300)
                    }
                })
                HookUtils.log("[Menu] SocialActions RV hook OK")
                if (!ok) ok = true
                break
            }
        } catch (t: Throwable) {
            HookUtils.log("[Menu] RV hook err: " + t.message)
        }

        return ok
    }
}
