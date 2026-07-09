package com.dyhelper.hook

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

class ShareMenuHook(
    private val copyFn: (Context) -> Unit,
    private val videoFn: (Context) -> Unit,
    private val audioFn: (Context) -> Unit,
    private val imageFn: (Context) -> Unit,
    private val checkImage: () -> Boolean
) : BaseHook {

    companion object { private const val TAG = "[ShareMenu]" }

    override fun name() = "Share"

    private fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    private fun itemColumn(ctx: Context, iconText: String, label: String, onTap: () -> Unit): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4))

        val tvIcon = TextView(ctx)
        tvIcon.text = iconText
        tvIcon.textSize = 16f
        tvIcon.gravity = Gravity.CENTER
        tvIcon.setTextColor(Color.WHITE)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#FF2D555F"))
        tvIcon.background = bg
        val iconSize = dp(ctx, 38)
        tvIcon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)

        val tvLabel = TextView(ctx)
        tvLabel.text = label
        tvLabel.textSize = 11f
        tvLabel.setTextColor(Color.parseColor("#222222"))
        tvLabel.gravity = Gravity.CENTER

        col.addView(tvIcon)
        col.addView(tvLabel)
        col.setOnClickListener { try { onTap() } catch (_: Throwable) {} }
        return col
    }

    private fun showActionSheet(ctx: Context) {
        try {
            val dlg = XposedHelpers.newInstance(Class.forName("android.app.Dialog"), ctx)
            val container = LinearLayout(ctx)
            container.orientation = LinearLayout.VERTICAL
            container.setBackgroundColor(Color.WHITE)
            container.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))

            val title = TextView(ctx)
            title.text = "\u64CD\u4F5C"
            title.textSize = 18f
            title.setTextColor(Color.parseColor("#111111"))
            title.setPadding(0, 0, 0, dp(ctx, 12))
            container.addView(title)

            val actions = listOf(
                "\u590D\u5236\u94FE\u63A5" to { copyFn(ctx) },
                (if (checkImage()) "\u4E0B\u8F7D\u56FE\u7247" else "\u4E0B\u8F7D\u89C6\u9891") to { if (checkImage()) imageFn(ctx) else videoFn(ctx) },
                "\u4E0B\u8F7D\u97F3\u9891" to { audioFn(ctx) }
            )
            for ((label, fn) in actions) {
                val tv = TextView(ctx)
                tv.text = label
                tv.textSize = 16f
                tv.setTextColor(Color.parseColor("#222222"))
                tv.setPadding(0, dp(ctx, 12), 0, dp(ctx, 12))
                tv.setOnClickListener {
                    try { fn() } catch (_: Throwable) {}
                    try { XposedHelpers.callMethod(dlg, "dismiss") } catch (_: Throwable) {}
                }
                container.addView(tv)
            }

            XposedHelpers.callMethod(dlg, "setContentView", container)
            val window = XposedHelpers.callMethod(dlg, "window")
            if (window != null) XposedHelpers.callMethod(window, "setGravity", Gravity.BOTTOM)
            XposedHelpers.callMethod(dlg, "show")
        } catch (t: Throwable) {
            HookUtils.log("$TAG dialog err: " + t.message)
        }
    }

    private fun buildBar(ctx: Context): LinearLayout {
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#F8F8F8"))
        bg.cornerRadius = dp(ctx, 16).toFloat()
        bg.setStroke(dp(ctx, 1), Color.parseColor("#22000000"))
        bar.background = bg
        bar.setPadding(dp(ctx, 10), dp(ctx, 10), dp(ctx, 10), dp(ctx, 10))

        bar.addView(itemColumn(ctx, "\u{1F4BE}", "\u4FDD\u5B58") { showActionSheet(ctx) })
        bar.addView(itemColumn(ctx, "\u{1F517}", "\u590D\u5236") { copyFn(ctx) })
        bar.addView(itemColumn(ctx, "\u{1F4F9}", if (checkImage()) "\u56FE\u7247" else "\u89C6\u9891") {
            if (checkImage()) imageFn(ctx) else videoFn(ctx)
        })
        bar.addView(itemColumn(ctx, "\u{1F3B5}", "\u97F3\u9891") { audioFn(ctx) })
        return bar
    }

    private fun tryInject(rv: RecyclerView) {
        val cl = rv.javaClass.name.lowercase()
        if (!cl.contains("socialactionspanel") && !cl.contains("imshare")) return
        if (rv.visibility != View.VISIBLE) return

        for (i in 0..5) {
            val parent = rv.parent as? ViewGroup ?: break
            val tag = "dyhelper_menu_row"
            if (parent.findViewWithTag<View>(tag) != null) return

            val bar = buildBar(rv.context)
            bar.tag = tag
            val lp: ViewGroup.LayoutParams = when (parent) {
                is FrameLayout -> FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    leftMargin = dp(rv.context, 12f)
                    rightMargin = dp(rv.context, 12f)
                    bottomMargin = dp(rv.context, 60f)
                }
                is LinearLayout -> LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(rv.context, 8f)
                    bottomMargin = dp(rv.context, 8f)
                }
                else -> ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            parent.addView(bar, lp)
            HookUtils.log("$TAG bar injected into " + parent.javaClass.simpleName + " at depth $i")
            return
        }
        HookUtils.log("$TAG no parent found after 6 levels")
    }

    override fun init(loader: ClassLoader): Boolean {
        var ok = false
        try {
            val rv = Class.forName("androidx.recyclerview.widget.RecyclerView", false, loader)
            if (HookUtils.hookAllMethods(rv, "onLayout", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val v = p.thisObject as? RecyclerView ?: return
                        tryInject(v)
                    } catch (t: Throwable) { HookUtils.log("$TAG layout: " + t.message) }
                }
            })) { HookUtils.log("$TAG RecyclerView.onLayout ok"); ok = true }
        } catch (t: Throwable) { HookUtils.log("$TAG rv: " + t.message) }

        try {
            val vv = Class.forName("android.view.View", false, loader)
            if (HookUtils.hookAllMethods(vv, "setVisibility", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val v = p.thisObject as? RecyclerView ?: return
                        if (p.args[0] as? Int != View.VISIBLE) return
                        val cl = v.javaClass.name.lowercase()
                        if (!cl.contains("socialactionspanel") && !cl.contains("imshare")) return
                        Handler(Looper.getMainLooper()).postDelayed({ tryInject(v) }, 150)
                    } catch (_: Throwable) {}
                }
            })) { HookUtils.log("$TAG setVisibility ok"); ok = true }
        } catch (_: Throwable) {}

        return ok
    }
}