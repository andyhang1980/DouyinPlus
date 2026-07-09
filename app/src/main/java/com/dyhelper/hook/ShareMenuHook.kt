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

    companion object {
        private const val TAG = "[ShareMenu]"
    }

    override fun name(): String = "Share"

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

    private fun dialogRow(ctx: Context, label: String, onTap: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.textSize = 16f
        tv.setTextColor(Color.parseColor("#222222"))
        tv.setPadding(0, dp(ctx, 12), 0, dp(ctx, 12))
        tv.setOnClickListener { onTap() }
        return tv
    }

    private fun showActionSheet(ctx: Context) {
        try {
            val dlgClass = Class.forName("android.app.Dialog")
            val dlg = dlgClass.getConstructor(Context::class.java).newInstance(ctx)
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

            container.addView(dialogRow(ctx, "\u590D\u5236\u94FE\u63A5") {
                copyFn(ctx)
                dlgClass.getMethod("dismiss").invoke(dlg)
            })

            val videoOrImageLabel = if (checkImage()) "\u4E0B\u8F7D\u56FE\u7247" else "\u4E0B\u8F7D\u89C6\u9891"
            container.addView(dialogRow(ctx, videoOrImageLabel) {
                if (checkImage()) imageFn(ctx) else videoFn(ctx)
                dlgClass.getMethod("dismiss").invoke(dlg)
            })

            container.addView(dialogRow(ctx, "\u4E0B\u8F7D\u97F3\u9891") {
                audioFn(ctx)
                dlgClass.getMethod("dismiss").invoke(dlg)
            })

            dlgClass.getMethod("setContentView", View::class.java).invoke(dlg, container)
            val window = dlgClass.getMethod("window").invoke(dlg)
            if (window != null) {
                window.javaClass.getMethod("setGravity", Int::class.java).invoke(window, Gravity.BOTTOM)
            }
            dlgClass.getMethod("show").invoke(dlg)
        } catch (t: Throwable) {
            HookUtils.log("$TAG actionSheet err: " + t.message)
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

    @Suppress("UNCHECKED_CAST")
    private fun tryInject(rv: RecyclerView) {
        val cl = rv.javaClass.name.lowercase()
        if (!cl.contains("socialactionspanel") && !cl.contains("imshare")) return
        if (rv.visibility != View.VISIBLE) return

        var cur: View = rv
        for (i in 0..5) {
            val p = cur.parent as? ViewGroup ?: break
            val tag = "dyhelper_menu_row"
            if (p.findViewWithTag<View>(tag) != null) return

            val bar = buildBar(rv.context)
            bar.tag = tag

            val lp: ViewGroup.LayoutParams = if (p is FrameLayout) {
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    leftMargin = dp(rv.context, 12f)
                    rightMargin = dp(rv.context, 12f)
                    bottomMargin = dp(rv.context, 60f)
                }
            } else if (p is LinearLayout) {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(rv.context, 8f)
                    bottomMargin = dp(rv.context, 8f)
                }
            } else {
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            try {
                p.addView(bar, lp)
                HookUtils.log("$TAG bar injected into " + p.javaClass.simpleName + " depth=$i")
            } catch (t: Throwable) {
                HookUtils.log("$TAG addView err: " + t.message)
            }
            return
        }
        HookUtils.log("$TAG no parent after 6 levels")
    }

    override fun init(loader: ClassLoader): Boolean {
        var ok = false

        try {
            val rvClass = Class.forName("androidx.recyclerview.widget.RecyclerView", false, loader)
            if (HookUtils.hookAllMethods(rvClass, "onLayout", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val v = p.thisObject as? RecyclerView ?: return
                        tryInject(v)
                    } catch (t: Throwable) {
                        HookUtils.log("$TAG onLayout err: " + t.message)
                    }
                }
            })) {
                HookUtils.log("$TAG RecyclerView.onLayout hooked")
                ok = true
            }
        } catch (t: Throwable) {
            HookUtils.log("$TAG rv hook err: " + t.message)
        }

        try {
            val vClass = Class.forName("android.view.View", false, loader)
            if (HookUtils.hookAllMethods(vClass, "setVisibility", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    try {
                        val v = p.thisObject as? RecyclerView ?: return
                        if (p.args[0] as? Int != View.VISIBLE) return
                        val cl = v.javaClass.name.lowercase()
                        if (!cl.contains("socialactionspanel") && !cl.contains("imshare")) return
                        Handler(Looper.getMainLooper()).postDelayed({
                            tryInject(v)
                        }, 150)
                    } catch (_: Throwable) {}
                }
            })) {
                HookUtils.log("$TAG View.setVisibility hooked")
                ok = true
            }
        } catch (_: Throwable) {}

        return ok
    }
}