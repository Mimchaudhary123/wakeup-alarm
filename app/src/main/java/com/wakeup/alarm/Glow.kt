package com.wakeup.alarm

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/** The "glowing gold" look from the colour video: deep brown, pale gold and glowing gold light. */
object Glow {
    val BROWN = Color.parseColor("#603E1E")
    val BROWN_DEEP = Color.parseColor("#3A230D")
    val PALE = Color.parseColor("#F1D9A8")
    val GOLD = Color.parseColor("#FFC34D")
    val GOLD_LIGHT = Color.parseColor("#FFDB8A")
    val GOLD_DEEP = Color.parseColor("#F0A21A")

    /** Deep brown gradient with a soft golden glow in the top right corner. */
    fun background(c: Context): Drawable {
        val dm = c.resources.displayMetrics
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#704826"), BROWN, BROWN_DEEP)
        )
        val glow = GradientDrawable()
        glow.gradientType = GradientDrawable.RADIAL_GRADIENT
        glow.gradientRadius = dm.widthPixels * 1.15f
        glow.setGradientCenter(0.85f, 0.02f)
        glow.colors = intArrayOf(Color.argb(110, 255, 195, 77), Color.argb(0, 255, 195, 77))
        return LayerDrawable(arrayOf(base, glow))
    }

    fun goldFill(radiusPx: Float): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(GOLD_LIGHT, GOLD, GOLD_DEEP)
        )
        g.cornerRadius = radiusPx
        return g
    }

    fun paleCard(radiusPx: Float): GradientDrawable {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#F8E8C4"), PALE, Color.parseColor("#E9CE98"))
        )
        g.cornerRadius = radiusPx
        return g
    }

    /** A golden glow around a view. It is made by the shadow, so the view needs a background. */
    fun shadowGlow(v: View, elevationPx: Float) {
        v.elevation = elevationPx
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineSpotShadowColor = GOLD
            v.outlineAmbientShadowColor = GOLD
        }
    }

    /** The glow around the view slowly breathes in and out. */
    fun pulse(v: View, fromPx: Float, toPx: Float) {
        val a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Z, fromPx, toPx)
        a.duration = 1500
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.start()
    }

    fun textGlow(tv: TextView, radiusPx: Float) {
        tv.setShadowLayer(radiusPx, 0f, 0f, Color.argb(170, 255, 195, 77))
    }

    /** The logo sitting on a soft, slowly pulsing halo of light. */
    fun logoWithGlow(c: Context, resId: Int, sizeDp: Int): FrameLayout {
        val d = c.resources.displayMetrics.density
        val size = (sizeDp * d).toInt()
        val box = (size * 1.7f).toInt()
        val frame = FrameLayout(c)
        frame.layoutParams = LinearLayout.LayoutParams(box, box)
        val halo = View(c)
        val bg = GradientDrawable()
        bg.gradientType = GradientDrawable.RADIAL_GRADIENT
        bg.gradientRadius = box / 2f
        bg.colors = intArrayOf(Color.argb(150, 255, 195, 77), Color.argb(0, 255, 195, 77))
        halo.background = bg
        frame.addView(halo, FrameLayout.LayoutParams(box, box, Gravity.CENTER))
        val img = ImageView(c)
        img.setImageResource(resId)
        frame.addView(img, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val a = ObjectAnimator.ofFloat(halo, View.ALPHA, 0.5f, 1f)
        a.duration = 1800
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.start()
        return frame
    }

    /** A bright band of light slowly sweeps across the text. */
    fun shimmer(tv: TextView) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.duration = 3200
        anim.repeatCount = ValueAnimator.INFINITE
        anim.interpolator = LinearInterpolator()
        anim.addUpdateListener {
            val w = tv.width.toFloat()
            if (w > 0f) {
                val shift = (it.animatedValue as Float) * w * 2f - w
                tv.paint.shader = LinearGradient(
                    shift, 0f, shift + w * 0.6f, 0f,
                    intArrayOf(GOLD, Color.parseColor("#FFF3CF"), GOLD),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                tv.invalidate()
            }
        }
        anim.start()
    }

    /** Wraps a screen: glowing background, light rays from the top right, then the content. */
    fun screen(c: Context, content: View, rays: Boolean = true): FrameLayout {
        val f = FrameLayout(c)
        f.background = background(c)
        if (rays) {
            f.addView(GlowRaysView(c), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        f.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        return f
    }
}

/** Soft golden light rays that fan out from the top right and slowly shimmer. */
class GlowRaysView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val angles = floatArrayOf(100f, 112f, 124f, 137f, 150f, 163f)
    private var shaders: Array<Shader> = arrayOf()
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f)

    init {
        animator.duration = 7000
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val ox = w * 0.92f
        val oy = -h * 0.06f
        val len = h * 1.15f
        shaders = Array<Shader>(angles.size) { i ->
            val r = Math.toRadians(angles[i].toDouble())
            LinearGradient(
                ox, oy, ox + (cos(r) * len).toFloat(), oy + (sin(r) * len).toFloat(),
                Color.argb(255, 255, 195, 77), Color.argb(0, 255, 195, 77), Shader.TileMode.CLAMP
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (shaders.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val ox = w * 0.92f
        val oy = -h * 0.06f
        val len = h * 1.15f
        val spread = Math.toRadians(3.2)
        for (i in angles.indices) {
            val a = Math.toRadians(angles[i].toDouble())
            path.reset()
            path.moveTo(ox, oy)
            path.lineTo(ox + (cos(a - spread) * len).toFloat(), oy + (sin(a - spread) * len).toFloat())
            path.lineTo(ox + (cos(a + spread) * len).toFloat(), oy + (sin(a + spread) * len).toFloat())
            path.close()
            paint.shader = shaders[i]
            val wave = 0.5f + 0.5f * sin(phase * 2.0 * Math.PI + i * 1.1).toFloat()
            paint.alpha = (18 + 34 * wave).toInt()
            canvas.drawPath(path, paint)
        }
    }
}
