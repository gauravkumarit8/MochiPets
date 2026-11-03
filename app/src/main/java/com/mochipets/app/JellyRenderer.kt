package com.mochipets.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.sin

/** Placeholder art: Jelly, a squishy mint-green slime that hops. */
class JellyRenderer : PetRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val path = Path()

    private val cBody = Color.argb(235, 142, 227, 200)
    private val cOutline = Color.parseColor("#3FAF93")
    private val cInk = Color.parseColor("#1F3B33")
    private val cCheek = Color.argb(120, 255, 143, 177)
    private var lw = 3f

    override fun draw(c: Canvas, w: Float, h: Float, state: PetState, t: Float) {
        lw = w * 0.022f
        val asleep = state == PetState.SLEEP
        val happy = state == PetState.REACT
        val loose = state == PetState.DRAGGED || state == PetState.FALL || state == PetState.HANG

        val sx: Float
        val sy: Float
        var lift = 0f
        when {
            state == PetState.WALK -> {
                // hop cycle: 0 = on the ground (squashed), 1 = top of the hop (stretched)
                val phase = (t * 3.2f) % 1f
                val air = sin(phase * PI.toFloat())
                sy = 0.86f + 0.24f * air
                sx = 1.10f - 0.16f * air
                lift = air * h * 0.16f
            }
            happy -> {
                sy = 0.72f + 0.05f * sin(t * 14f)
                sx = 1.14f
            }
            loose -> {
                sy = 1.18f
                sx = 0.90f
            }
            asleep -> {
                sy = 0.78f + 0.02f * sin(t * 1.6f)
                sx = 1.06f
            }
            else -> {
                sy = 1f + 0.04f * sin(t * 3f)
                sx = 1f - 0.03f * sin(t * 3f)
            }
        }

        c.save()
        c.translate(0f, -lift)
        c.scale(sx, sy, w / 2f, h * 0.90f)

        // body blob
        path.reset()
        path.moveTo(w * 0.14f, h * 0.90f)
        path.cubicTo(w * 0.08f, h * 0.60f, w * 0.28f, h * 0.28f, w * 0.50f, h * 0.28f)
        path.cubicTo(w * 0.72f, h * 0.28f, w * 0.92f, h * 0.60f, w * 0.86f, h * 0.90f)
        path.quadTo(w * 0.50f, h * 0.95f, w * 0.14f, h * 0.90f)
        path.close()
        fill.color = cBody
        c.drawPath(path, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawPath(path, line)

        // glossy highlight
        fill.color = Color.argb(110, 255, 255, 255)
        rect.set(w * 0.24f, h * 0.36f, w * 0.40f, h * 0.50f)
        c.drawOval(rect, fill)

        // eyes
        val ey = h * 0.60f
        val exL = w * 0.37f
        val exR = w * 0.63f
        if (asleep || (t % 5f) < 0.12f) {
            arcEye(c, exL, ey, w, false)
            arcEye(c, exR, ey, w, false)
        } else if (happy) {
            arcEye(c, exL, ey, w, true)
            arcEye(c, exR, ey, w, true)
        } else {
            fill.color = cInk
            rect.set(exL - w * 0.04f, ey - w * 0.055f, exL + w * 0.04f, ey + w * 0.055f)
            c.drawOval(rect, fill)
            rect.set(exR - w * 0.04f, ey - w * 0.055f, exR + w * 0.04f, ey + w * 0.055f)
            c.drawOval(rect, fill)
            fill.color = Color.WHITE
            c.drawCircle(exL + w * 0.012f, ey - w * 0.02f, w * 0.013f, fill)
            c.drawCircle(exR + w * 0.012f, ey - w * 0.02f, w * 0.013f, fill)
        }

        // cheeks and mouth
        fill.color = cCheek
        c.drawCircle(w * 0.29f, h * 0.68f, w * 0.035f, fill)
        c.drawCircle(w * 0.71f, h * 0.68f, w * 0.035f, fill)
        path.reset()
        path.moveTo(w * 0.45f, h * 0.69f)
        path.quadTo(w * 0.50f, if (happy) h * 0.78f else h * 0.73f, w * 0.55f, h * 0.69f)
        line.color = cInk
        line.strokeWidth = lw * 0.7f
        c.drawPath(path, line)

        c.restore()
    }

    override fun drawEffects(c: Canvas, w: Float, h: Float, state: PetState, t: Float, facingRight: Boolean) {
        if (state == PetState.REACT) {
            fill.color = Color.parseColor("#FFD54F")
            for (i in 0 until 3) {
                val a = t * 5f + i * 2.1f
                val x = w * (0.22f + 0.28f * i) + sin(a) * w * 0.03f
                val y = h * 0.20f - (sin(a * 0.7f) * 0.5f + 0.5f) * h * 0.06f
                c.drawCircle(x, y, w * 0.025f, fill)
            }
        }
        if (state == PetState.SLEEP) {
            // snot-bubble that grows and pops
            val phase = (t * 0.4f) % 1f
            line.color = Color.argb(200, 63, 175, 147)
            line.strokeWidth = lw * 0.7f
            val cx = if (facingRight) w * 0.66f else w * 0.34f
            c.drawCircle(cx, h * 0.58f, w * (0.01f + 0.06f * phase), line)
        }
    }

    private fun arcEye(c: Canvas, ex: Float, ey: Float, w: Float, happy: Boolean) {
        val r = w * 0.04f
        path.reset()
        path.moveTo(ex - r, ey)
        path.quadTo(ex, if (happy) ey - r * 1.7f else ey + r * 1.7f, ex + r, ey)
        line.color = cInk
        line.strokeWidth = lw * 1.2f
        c.drawPath(path, line)
    }
}
