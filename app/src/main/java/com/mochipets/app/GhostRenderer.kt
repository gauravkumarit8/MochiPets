package com.mochipets.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.sin

/** Placeholder art: Boo, a shy little ghost that floats just above the floor. */
class GhostRenderer : PetRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val rect = RectF()
    private val path = Path()

    private val cBody = Color.argb(245, 246, 242, 255)
    private val cOutline = Color.parseColor("#B8A9D9")
    private val cInk = Color.parseColor("#3B2F55")
    private val cCheek = Color.argb(120, 255, 143, 177)
    private var lw = 3f

    override fun draw(c: Canvas, w: Float, h: Float, state: PetState, t: Float) {
        lw = w * 0.022f
        val asleep = state == PetState.SLEEP
        val happy = state == PetState.REACT
        val loose = state == PetState.DRAGGED || state == PetState.FALL || state == PetState.HANG
        val moving = state == PetState.WALK || state == PetState.CLIMB

        // gentle floating bob, a little faster while moving
        val bob = sin(t * (if (moving) 5f else 2.2f)) * h * (if (asleep) 0.008f else 0.03f)
        val lift = if (loose || asleep) 0f else h * 0.03f
        val sy = if (loose) 1.15f else if (asleep) 0.88f else 1f
        val sx = if (loose) 0.9f else if (asleep) 1.05f else 1f
        val tilt = if (moving) sin(t * 5f) * 4f else 0f

        c.save()
        c.translate(0f, -lift - bob)
        c.rotate(tilt, w / 2f, h * 0.9f)
        c.scale(sx, sy, w / 2f, h * 0.9f)

        // body: dome on top, scalloped hem at the bottom
        path.reset()
        path.moveTo(w * 0.18f, h * 0.88f)
        path.cubicTo(w * 0.10f, h * 0.50f, w * 0.28f, h * 0.18f, w * 0.50f, h * 0.18f)
        path.cubicTo(w * 0.72f, h * 0.18f, w * 0.90f, h * 0.50f, w * 0.82f, h * 0.88f)
        var x = 0.82f
        for (i in 0 until 4) {
            val wave = sin(t * 4f + i) * h * 0.012f
            path.quadTo(w * (x - 0.08f), h * 0.97f + wave, w * (x - 0.16f), h * 0.88f)
            x -= 0.16f
        }
        path.close()
        fill.color = cBody
        c.drawPath(path, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawPath(path, line)

        // soft highlight
        fill.color = Color.argb(120, 255, 255, 255)
        rect.set(w * 0.26f, h * 0.28f, w * 0.38f, h * 0.40f)
        c.drawOval(rect, fill)

        // eyes
        val ey = h * 0.50f
        val exL = w * 0.39f
        val exR = w * 0.61f
        if (asleep || (t % 5.5f) < 0.12f) {
            arcEye(c, exL, ey, w, false)
            arcEye(c, exR, ey, w, false)
        } else if (happy) {
            arcEye(c, exL, ey, w, true)
            arcEye(c, exR, ey, w, true)
        } else {
            fill.color = cInk
            rect.set(exL - w * 0.04f, ey - w * 0.06f, exL + w * 0.04f, ey + w * 0.06f)
            c.drawOval(rect, fill)
            rect.set(exR - w * 0.04f, ey - w * 0.06f, exR + w * 0.04f, ey + w * 0.06f)
            c.drawOval(rect, fill)
            fill.color = Color.WHITE
            c.drawCircle(exL + w * 0.012f, ey - w * 0.025f, w * 0.014f, fill)
            c.drawCircle(exR + w * 0.012f, ey - w * 0.025f, w * 0.014f, fill)
        }

        // cheeks and a small round mouth
        fill.color = cCheek
        c.drawCircle(w * 0.31f, h * 0.60f, w * 0.035f, fill)
        c.drawCircle(w * 0.69f, h * 0.60f, w * 0.035f, fill)
        fill.color = cInk
        c.drawCircle(w * 0.50f, h * 0.62f, w * (if (happy) 0.03f else 0.018f), fill)

        c.restore()
    }

    override fun drawEffects(c: Canvas, w: Float, h: Float, state: PetState, t: Float, facingRight: Boolean) {
        if (state == PetState.REACT) {
            val cx = if (facingRight) w * 0.66f else w * 0.34f
            rect.set(cx - w * 0.24f, h * 0.01f, cx + w * 0.24f, h * 0.17f)
            fill.color = Color.WHITE
            c.drawRoundRect(rect, w * 0.06f, w * 0.06f, fill)
            line.color = cOutline
            line.strokeWidth = lw * 0.8f
            c.drawRoundRect(rect, w * 0.06f, w * 0.06f, line)
            textPaint.color = cInk
            textPaint.textSize = w * 0.12f
            c.drawText("Boo!", cx, h * 0.125f, textPaint)
        }
        if (state == PetState.SLEEP) {
            val phase = (t * 0.5f) % 1f
            textPaint.textSize = w * 0.18f
            textPaint.color = Color.argb((255 * (1f - phase)).toInt(), 138, 122, 153)
            val px = if (facingRight) w * 0.78f else w * 0.22f
            c.drawText("z", px, h * 0.40f - phase * h * 0.12f, textPaint)
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
