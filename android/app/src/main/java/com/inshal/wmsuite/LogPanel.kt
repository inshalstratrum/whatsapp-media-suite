package com.inshal.wmsuite

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Collapsible, draggable activity log panel: drag the header to resize,
 * tap the arrow (or the header) to collapse, X to close - reopen it from
 * the floating chip in the app.
 */
@SuppressLint("ClickableViewAccessibility")
class LogPanel(context: Context) : FrameLayout(context) {

    private val density = context.resources.displayMetrics.density
    private val screenH = context.resources.displayMetrics.heightPixels
    private val panel = LinearLayout(context)
    private val body = TextView(context)
    private val scroll = ScrollView(context)
    private val toggle = TextView(context)
    private var collapsed = false
    private var expandedHeight = 0
    private var headerHeight = 0
    private var dragStartY = 0f
    private var dragStartH = 0
    private var moved = false
    var onClosed: (() -> Unit)? = null

    init {
        val g = GradientDrawable()
        g.setColor(0xFF11151C.toInt())
        g.cornerRadius = dp(16).toFloat()
        panel.background = g
        panel.orientation = LinearLayout.VERTICAL
        panel.elevation = dp(12).toFloat()
        headerHeight = dp(46)
        panel.layoutParams = FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(14), dp(10), dp(10), dp(10))
        header.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val grip = TextView(context)
        grip.text = "="
        grip.textSize = 16f
        grip.typeface = Typeface.DEFAULT_BOLD
        grip.setTextColor(0xFF97A5B4.toInt())
        header.addView(grip)

        val title = TextView(context)
        title.text = "  ACTIVITY LOG"
        title.textSize = 13f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(0xFFE8ECF1.toInt())
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val clear = action("CLEAR")
        clear.setOnClickListener { body.text = "" }
        header.addView(clear)

        toggle.text = "v"
        toggle.textSize = 14f
        toggle.typeface = Typeface.DEFAULT_BOLD
        toggle.setTextColor(0xFF25D366.toInt())
        toggle.setPadding(dp(12), dp(6), dp(12), dp(6))
        toggle.setOnClickListener {
            if (collapsed) expand() else collapse()
        }
        header.addView(toggle)

        val close = action("X")
        close.setTextColor(0xFFFF8A80.toInt())
        close.setOnClickListener { hide() }
        header.addView(close)

        header.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = ev.rawY
                    dragStartH = panel.layoutParams.height
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = dragStartY - ev.rawY
                    if (abs(dy) > 8) moved = true
                    var h = dragStartH + dy.toInt()
                    if (h < headerHeight) h = headerHeight
                    val maxH = (screenH * 0.6f).toInt()
                    if (h > maxH) h = maxH
                    if (h > headerHeight + dp(8)) {
                        collapsed = false
                        expandedHeight = h
                        toggle.text = "v"
                    }
                    setHeight(h)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        if (collapsed) expand() else collapse()
                    }
                    true
                }
                else -> false
            }
        }

        panel.addView(header)

        body.textSize = 11f
        body.typeface = Typeface.MONOSPACE
        body.setTextColor(0xFFB9C4D0.toInt())
        body.setPadding(dp(14), dp(4), dp(14), dp(10))
        scroll.addView(body)
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(panel)
        expandedHeight = (screenH * 0.30f).toInt()
        setHeight(expandedHeight)
    }

    private fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    private fun action(label: String): TextView {
        val t = TextView(context)
        t.text = label
        t.textSize = 12f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(0xFF97A5B4.toInt())
        t.setPadding(dp(10), dp(8), dp(10), dp(8))
        return t
    }

    private fun setHeight(h: Int) {
        val lp = panel.layoutParams
        lp.height = h
        panel.layoutParams = lp
    }

    fun collapse() {
        collapsed = true
        toggle.text = "^"
        setHeight(headerHeight)
    }

    fun expand() {
        if (expandedHeight < headerHeight + dp(10)) expandedHeight = (screenH * 0.30f).toInt()
        collapsed = false
        toggle.text = "v"
        setHeight(expandedHeight)
    }

    fun hide() {
        visibility = View.GONE
        onClosed?.invoke()
    }

    fun showPanel() {
        visibility = View.VISIBLE
        expand()
    }

    /** Thread-safe log append. */
    fun append(msg: String) {
        post {
            var t = body.text.toString() + "\n- " + msg
            if (t.length > 12000) t = t.substring(t.length - 12000)
            body.text = t
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}