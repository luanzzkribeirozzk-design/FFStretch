package com.lnstretch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var alpha: Float,
        var alphaSpeed: Float, var color: Int
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(0xFFFF4500.toInt(), 0xFFFF6B00.toInt(), 0xFF7C00FF.toInt(), 0xFFFFFFFF.toInt())
    private var w = 0f
    private var h = 0f

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    override fun onSizeChanged(nw: Int, nh: Int, ow: Int, oh: Int) {
        super.onSizeChanged(nw, nh, ow, oh)
        w = nw.toFloat(); h = nh.toFloat()
        particles.clear()
        repeat(55) { particles.add(createParticle()) }
    }

    private fun createParticle() = Particle(
        x = Random.nextFloat() * w,
        y = Random.nextFloat() * h,
        vx = (Random.nextFloat() - 0.5f) * 0.6f,
        vy = -Random.nextFloat() * 0.8f - 0.2f,
        radius = Random.nextFloat() * 3f + 1f,
        alpha = Random.nextFloat(),
        alphaSpeed = Random.nextFloat() * 0.008f + 0.002f,
        color = colors[Random.nextInt(colors.size)]
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            p.x += p.vx; p.y += p.vy
            p.alpha += p.alphaSpeed
            if (p.alpha > 1f) { p.alpha = 1f; p.alphaSpeed = -p.alphaSpeed }
            if (p.alpha < 0f) { p.alpha = 0f; p.alphaSpeed = -p.alphaSpeed }
            if (p.y < -10f || p.x < -10f || p.x > w + 10f) {
                p.x = Random.nextFloat() * w
                p.y = h + 5f
                p.vx = (Random.nextFloat() - 0.5f) * 0.6f
                p.vy = -Random.nextFloat() * 0.8f - 0.2f
                p.alpha = 0f
            }
            paint.color = p.color
            paint.alpha = (p.alpha * 120).toInt()
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
        postInvalidateOnAnimation()
    }
}
