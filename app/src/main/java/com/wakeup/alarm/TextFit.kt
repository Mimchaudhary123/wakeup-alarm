package com.wakeup.alarm

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/**
 * Sets the biggest text size at which the whole text fits the box AND no word is ever cut
 * in the middle: lines break only between words.
 */
object TextFit {
    private class Params(val minSp: Float, val maxSp: Float) {
        var lastKey = ""
    }

    fun apply(tv: TextView, text: String, minSp: Float, maxSp: Float) {
        tv.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        tv.includeFontPadding = false
        tv.text = text
        if (tv.tag !is Params) {
            tv.tag = Params(minSp, maxSp)
            tv.addOnLayoutChangeListener(
                View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    v.post { fit(v as TextView) }
                }
            )
        }
        fit(tv)
    }

    private fun fit(tv: TextView) {
        val p = tv.tag as? Params ?: return
        val availW = tv.width - tv.paddingLeft - tv.paddingRight
        val availH = tv.height - tv.paddingTop - tv.paddingBottom
        if (availW <= 0 || availH <= 0) return
        val text = tv.text.toString()
        val key = text + "|" + availW + "|" + availH
        if (key == p.lastKey) return
        p.lastKey = key

        val dm = tv.resources.displayMetrics
        val paint = TextPaint(tv.paint)
        paint.shader = null
        val words = text.split(" ", "\n").filter { it.isNotEmpty() }
        var lo = p.minSp
        var hi = p.maxSp
        var best = p.minSp
        repeat(14) {
            val mid = (lo + hi) / 2f
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, mid, dm)
            val wordsFit = words.all { paint.measureText(it) <= availW }
            val ok = wordsFit && heightOf(text, paint, availW) <= availH
            if (ok) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, best)
    }

    private fun heightOf(text: String, paint: TextPaint, width: Int): Int =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
            .height
}
