package com.mochipets.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

/** Placeholder art: Bholu, a brown street dog with a white ear patch. */
class BholuRenderer : PetRenderer {

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

    private val cFur = Color.parseColor("#C98B52")
    private val cFurDark = Color.parseColor("#9A6439")
    private val cSnout = Color.parseColor("#F3D9B1")
    private val cOutline = Color.parseColor("#5B3A21")
    private val cInk = Color.parseColor("#2B1B12")
    private val cTongue = Color.parseColor("#FF7B93")
    private var lw = 3f

    override fun draw(c: Canvas, w: Float, h: Float, state: PetState, t: Float) {
        lw = w * 0.022f
        val asleep = state == PetState.SLEEP
        val sitting = state == PetState.SIT
        val happy = state == PetState.REACT
        val walking = state == PetState.WALK
        val loose = state == PetState.DRAGGED || state == PetState.FALL || state == PetState.HANG

        val bob = if (walking) abs(sin(t * 10f)) * h * 0.03f else 0f
        val breathe = 1f + (if (asleep) 0.03f else 0.015f) * sin(t * (if (asleep) 1.6f else 2.4f))

        c.save()
        c.translate(0f, -bob)
        c.scale(1f, breathe, w / 2f, h * 0.9f)

        // tail (wags faster when happy)
        val wag = sin(t * (if (happy) 16f else 6f)) * h * 0.07f
        path.reset()
        if (asleep) {
            path.moveTo(w * 0.18f, h * 0.82f)
            path.quadTo(w * 0.05f, h * 0.86f, w * 0.10f, h * 0.72f)
        } else {
            path.moveTo(w * 0.20f, h * 0.56f)
            path.quadTo(w * 0.06f, h * 0.46f + wag, w * 0.10f, h * 0.30f + wag)
        }
        line.color = cOutline
        line.strokeWidth = w * 0.085f
        c.drawPath(path, line)
        line.color = cFur
        line.strokeWidth = w * 0.06f
        c.drawPath(path, line)

        val swing = if (walking) sin(t * 10f) * w * 0.05f else 0f
        val dangle = if (loose) sin(t * 7f) * w * 0.03f else 0f
        val legBottom = if (loose) h * 0.97f else h * 0.90f
        val legW = w * 0.10f

        // far legs (darker, behind the body)
        if (!asleep && !sitting) {
            leg(c, w * 0.24f + swing + dangle, h * 0.62f, legW, legBottom, cFurDark)
            leg(c, w * 0.58f - swing - dangle, h * 0.62f, legW, legBottom, cFurDark)
        }

        // body
        if (asleep) oval(c, w * 0.10f, h * 0.58f, w * 0.80f, h * 0.90f, cFur)
        else if (sitting) oval(c, w * 0.16f, h * 0.40f, w * 0.60f, h * 0.90f, cFur)
        else oval(c, w * 0.16f, h * 0.44f, w * 0.76f, h * 0.78f, cFur)

        // near legs
        if (sitting) {
            leg(c, w * 0.48f, h * 0.62f, legW, h * 0.90f, cFur)
            leg(c, w * 0.20f, h * 0.80f, legW * 1.4f, h * 0.90f, cFur)
        } else if (!asleep) {
            leg(c, w * 0.32f - swing - dangle, h * 0.62f, legW, legBottom, cFur)
            leg(c, w * 0.66f + swing + dangle, h * 0.62f, legW, legBottom, cFur)
        }

        // head
        val hx = if (asleep) w * 0.70f else if (sitting) w * 0.56f else w * 0.74f
        val hy = if (asleep) h * 0.72f else if (sitting) h * 0.30f else h * 0.38f
        val hr = w * 0.19f

        // far ear
        oval(c, hx + hr * 0.1f, hy - hr * 1.0f, hx + hr * 0.8f, hy + hr * 0.1f, cFurDark)
        // head
        oval(c, hx - hr, hy - hr, hx + hr, hy + hr, cFur)
        // snout
        oval(c, hx + hr * 0.2f, hy - hr * 0.05f, hx + hr * 1.4f, hy + hr * 0.8f, cSnout)
        // near ear (floppy) with a white patch
        c.save()
        c.rotate(-12f, hx - hr * 0.5f, hy - hr * 0.5f)
        oval(c, hx - hr * 0.95f, hy - hr * 1.05f, hx - hr * 0.15f, hy + hr * 0.25f, cFurDark)
        c.restore()
        fill.color = Color.WHITE
        rect.set(hx - hr * 0.80f, hy - hr * 0.80f, hx - hr * 0.35f, hy - hr * 0.30f)
        c.drawOval(rect, fill)

        // nose
        fill.color = cInk
        c.drawCircle(hx + hr * 1.3f, hy + hr * 0.12f, w * 0.03f, fill)

        // eye
        val ex = hx + hr * 0.45f
        val ey = hy - hr * 0.12f
        if (asleep || (t % 4.6f) < 0.12f) {
            arcEye(c, ex, ey, w, false)
        } else if (happy) {
            arcEye(c, ex, ey, w, true)
        } else {
            fill.color = cInk
            c.drawCircle(ex, ey, w * 0.03f, fill)
            fill.color = Color.WHITE
            c.drawCircle(ex + w * 0.01f, ey - w * 0.01f, w * 0.01f, fill)
        }

        // mouth
        if (happy) {
            fill.color = cTongue
            rect.set(hx + hr * 0.7f, hy + hr * 0.45f, hx + hr * 1.2f, hy + hr * 1.15f)
            c.drawOval(rect, fill)
            line.color = cOutline
            line.strokeWidth = lw * 0.6f
            c.drawOval(rect, line)
        } else {
            path.reset()
            path.moveTo(hx + hr * 0.75f, hy + hr * 0.55f)
            path.quadTo(hx + hr * 1.0f, hy + hr * 0.75f, hx + hr * 1.3f, hy + hr * 0.5f)
            line.color = cOutline
            line.strokeWidth = lw * 0.6f
            c.drawPath(path, line)
        }

        c.restore()
    }

    override fun drawEffects(c: Canvas, w: Float, h: Float, state: PetState, t: Float, facingRight: Boolean) {
        if (state == PetState.REACT) {
            val cx = if (facingRight) w * 0.66f else w * 0.34f
            rect.set(cx - w * 0.27f, h * 0.01f, cx + w * 0.27f, h * 0.17f)
            fill.color = Color.WHITE
            c.drawRoundRect(rect, w * 0.06f, w * 0.06f, fill)
            line.color = cOutline
            line.strokeWidth = lw * 0.8f
            c.drawRoundRect(rect, w * 0.06f, w * 0.06f, line)
            textPaint.color = cInk
            textPaint.textSize = w * 0.12f
            c.drawText("Woof!", cx, h * 0.125f, textPaint)
        }
        if (state == PetState.SLEEP) {
            val phase = (t * 0.5f) % 1f
            textPaint.textSize = w * 0.18f
            textPaint.color = Color.argb((255 * (1f - phase)).toInt(), 138, 122, 153)
            val px = if (facingRight) w * 0.84f else w * 0.16f
            c.drawText("z", px, h * 0.60f - phase * h * 0.12f, textPaint)
        }
    }

    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        rect.set(l, t, r, b)
        fill.color = color
        c.drawOval(rect, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawOval(rect, line)
    }

    private fun leg(c: Canvas, x: Float, top: Float, width: Float, bottom: Float, color: Int) {
        rect.set(x, top, x + width, bottom)
        fill.color = color
        c.drawRoundRect(rect, width / 2f, width / 2f, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawRoundRect(rect, width / 2f, width / 2f, line)
    }

    private fun arcEye(c: Canvas, ex: Float, ey: Float, w: Float, happy: Boolean) {
        val r = w * 0.035f
        path.reset()
        path.moveTo(ex - r, ey)
        path.quadTo(ex, if (happy) ey - r * 1.6f else ey + r * 1.6f, ex + r, ey)
        line.color = cInk
        line.strokeWidth = lw * 1.2f
        c.drawPath(path, line)
    }
}
