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

    private fun circleBg(ctx: Context, sizeDp: Int): View {
        val v = View(ctx)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#1A000000"))
        v.background = bg
        v.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp.toFloat()), dp(ctx, sizeDp.toFloat()))
        return v
    }

    private fun itemColumn(ctx: Context, iconChar: String, label: String, onTap: () -> Unit): LinearLayout {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))

        val tvIcon = TextView(ctx)
        tvIcon.text = iconChar
        tvIcon.textSize = 18f
        tvIcon.gravity = Gravity.CENTER
        tvIcon.setTextColor(Color.WHITE)

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.parseColor("#FF2D555F"))
        tvIcon.background = bg
        tvIcon.layoutParams = LinearLayout.LayoutParams(dp(ctx, 38), dp(ctx, 38))

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

    private fun dialogRow(ctx: Context, label: String, onTap: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 16f
        tv.setTextColor(Color.parseColor("#111111"))
        tv.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        tv.setOnClickListener { onTap() }
        return tv
    }

    private fun showActionSheet(ctx: Context) {
        val dlg = XposedHelpers.newInstance(Class.forName("android.app.Dialog"), ctx)
        val container = LinearLayout(ctx)
        container.orientation = LinearLayout.VERTICAL
        container.setBackgroundColor(Color.WHITE)
        container.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))

        container.addView(dialogRow(ctx, "\u590D\u5236\u94FE\u63A5") {
            copyFn(ctx)
            try { XposedHelpers.callMethod(dlg, "dismiss") } catch (_: Throwable) {}
        })
        container.addView(dialogRow(ctx, if (checkImage()) "\u4E0B\u8F7D\u56FE\u7247" else "\u4E0B\u8F7D\u89C6\u9891") {
            if (checkImage()) imageFn(ctx) else videoFn(ctx)
            try { XposedHelpers.callMethod(dlg, "dismiss") } catch (_: Throwable) {}
        })
        container.addView(dialogRow(ctx, "\u4E0B\u8F7D\u97F3\u9891") {
            audioFn(ctx)
            try { XposedHelpers.callMethod(dlg, "dismiss") } catch (_: Throwable) {}
        })

        XposedHelpers.callMethod(dlg, "setContentView", container)
        val window = XposedHelpers.callMethod(dlg, "window")
        if (window != null) XposedHelpers.callMethod(window, "setGravity", Gravity.BOTTOM)
        try { XposedHelpers.callMethod(dlg, "show") } catch (_: Throwable) {}
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

        val padLR = dp(ctx, 12)
        bar.addView(itemColumn(ctx, "\u{1F4BE}", "\u4FDD\u5B58") { showActionSheet(ctx) })
        bar.addView(itemColumn(ctx, "\u{1F517}", "\u590D\u5236") { copyFn(ctx) })
        bar.addView(itemColumn(ctx, "\u{1F4F9}", if (checkImage()) "\u56FE\u7247" else "\u89C6\u9891") {
            if (checkImage()) imageFn(ctx) else videoFn(ctx)
        })
        bar.addView(itemColumn(ctx, "\u{1F3B5}", "\u97F3\u9891") { audioFn(ctx) })

        for (i in 0 until bar.childCount) {
            val lp = bar.getChildAt(i).layoutParams as LinearLayout.LayoutParams
            lp.topMargin = 0; lp.bottomMargin = 0
        }
        return bar
    }

    private fun tryInject(v: View) {
        val rv = v as? RecyclerView ?: return
        val cl = rv.javaClass.name.lowercase()
        if (!cl.contains("socialactionspanel") && !cl.contains("imshare")) return

        for (i in 0..6) {
            val ancestor = rv.parent ?: break
            val ap = ancestor as? ViewGroup ?: break
            if (ap.javaClass.name.lowercase().contains("frame") || ap.javaClass.name.lowercase().contains("linear")) {
                val tag = "dyhelper_menu_row"
                if (ap.findViewWithTag<View>(tag) != null) return
                val bar = buildBar(rv.context)
                bar.tag = tag
                val lp = if (ap is FrameLayout) {
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        leftMargin = dp(rv.context, 12f)
                        rightMargin = dp(rv.context, 12f)
                    }
                } else {
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        topMargin = dp(rv.context, 8f)
                        bottomMargin = dp(rv.context, 8f)
                    }
                }
                ap.addView(bar, lp)
                HookUtils.log("$TAG bar injected into " + ap.javaClass.simpleName)
                return
            }
            rv.parent?.parent?.let { rv.parent }?.let { break }
        }
    }

    override fun init(loader: ClassLoader): Boolean {
        var ok = false
        try {
            val rv = Class.forName("androidx.recyclerview.widget.RecyclerView", false, loader)
            if (HookUtils.hookAllMethods(rv, "onLayout", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try { tryInject(p.thisObject as? RecyclerView ?: return) } catch (t: Throwable) { HookUtils.log("$TAG layout: " + t.message) }
                }
            })) { HookUtils.log("$TAG RecyclerView.onLayout ok"); ok = true }
        } catch (t: Throwable) { HookUtils.log("$TAG rv: " + t.message) }

        // Second pass: setVisibility 可见时再试
        try {
            val vv = Class.forName("android.view.View", false, loader)
            if (HookUtils.hookAllMethods(vv, "setVisibility", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val v = p.thisObject as? RecyclerView ?: return
                        if (p.args[0] as? Int != View.VISIBLE) return
                        Handler(Looper.getMainLooper()).postDelayed({ tryInject(v) }, 120)
                    } catch (_: Throwable) {}
                }
            })) { HookUtils.log("$TAG setVisibility ok"); ok = true }
        } catch (_: Throwable) {}

        return ok
    }
}