package com.mochipets.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * Placeholder art: Mochi the cat drawn with Canvas shapes.
 * Later this view is replaced by a Rive/sprite renderer; the brain stays the same.
 */
class PetView(context: Context) : View(context) {

    var state: PetState = PetState.IDLE
    var facingRight: Boolean = true
    var animTime: Float = 0f
    var surface: Int = 0
    var squash: Float = 0f
    var character: CharacterType = CharacterType.MOCHI
    var showShadow: Boolean = true
    var editMode: Boolean = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FF8FB1")
    }

    private val bholuRenderer = BholuRenderer()
    private val jellyRenderer = JellyRenderer()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    private val cWhite = Color.WHITE
    private val cOutline = Color.parseColor("#B79AA8")
    private val cPink = Color.parseColor("#FF8FB1")
    private val cEye = Color.parseColor("#3A2A33")
    private val cShadow = Color.argb(45, 0, 0, 0)
    private var lw = 3f

    override fun onDraw(canvas: Canvas) {
        when (character) {
            CharacterType.MOCHI -> drawMochi(canvas)
            CharacterType.BHOLU -> drawWithRenderer(canvas, bholuRenderer)
            CharacterType.JELLY -> drawWithRenderer(canvas, jellyRenderer)
        }
        if (editMode) drawEditRing(canvas)
    }

    /** Dashed ring that shows a pet can be dragged (used while placing pets on icons). */
    private fun drawEditRing(canvas: Canvas) {
        val w = width.toFloat()
        ringPaint.strokeWidth = w * 0.04f
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(w * 0.08f, w * 0.06f), 0f)
        canvas.drawCircle(w / 2f, height / 2f, w * 0.47f, ringPaint)
    }

    private fun drawWithRenderer(canvas: Canvas, r: PetRenderer) {
        val w = width.toFloat()
        val h = height.toFloat()
        val dragged = state == PetState.DRAGGED || state == PetState.FALL
        if (showShadow && !dragged && surface == 0) {
            fill.color = cShadow
            rect.set(w * 0.14f, h * 0.90f, w * 0.86f, h * 0.99f)
            canvas.drawOval(rect, fill)
        }
        canvas.save()
        when (surface) {
            1 -> canvas.rotate(90f, w / 2f, h / 2f)
            2 -> canvas.rotate(-90f, w / 2f, h / 2f)
            3 -> canvas.rotate(180f, w / 2f, h / 2f)
        }
        if (!facingRight) canvas.scale(-1f, 1f, w / 2f, h / 2f)
        canvas.scale(1f + 0.14f * squash, 1f - 0.2f * squash, w / 2f, h * 0.9f)
        r.draw(canvas, w, h, state, animTime)
        canvas.restore()
        r.drawEffects(canvas, w, h, state, animTime, facingRight)
    }

    private fun drawMochi(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = animTime
        lw = w * 0.022f

        val asleep = state == PetState.SLEEP
        val dragged = state == PetState.DRAGGED || state == PetState.FALL
        val walking = state == PetState.WALK || state == PetState.CLIMB
        val happy = state == PetState.REACT
        val sitting = state == PetState.SIT
        val loose = dragged || state == PetState.HANG

        // ground shadow (not mirrored)
        if (showShadow && !dragged && surface == 0) {
            fill.color = cShadow
            rect.set(w * 0.14f, h * 0.90f, w * 0.86f, h * 0.99f)
            canvas.drawOval(rect, fill)
        }

        val bob = if (walking) abs(sin(t * 10f)) * h * 0.035f else 0f
        val breathe = 1f + (if (asleep) 0.03f else 0.02f) * sin(t * (if (asleep) 1.6f else 2.6f))

        canvas.save()
        when (surface) {
            1 -> canvas.rotate(90f, w / 2f, h / 2f)
            2 -> canvas.rotate(-90f, w / 2f, h / 2f)
            3 -> canvas.rotate(180f, w / 2f, h / 2f)
        }
        if (!facingRight) canvas.scale(-1f, 1f, w / 2f, h / 2f)
        canvas.translate(0f, -bob)
        canvas.scale(1f + 0.14f * squash, breathe * (1f - 0.2f * squash), w / 2f, h * 0.9f)

        // tail
        val sway = sin(t * (if (happy) 9f else 3f)) * h * 0.05f
        path.reset()
        if (asleep) {
            path.moveTo(w * 0.22f, h * 0.80f)
            path.quadTo(w * 0.40f, h * 0.93f, w * 0.62f, h * 0.85f)
        } else {
            path.moveTo(w * 0.22f, h * 0.70f)
            path.quadTo(w * 0.04f, h * 0.60f + sway, w * 0.10f, h * 0.36f + sway)
        }
        line.color = cOutline
        line.strokeWidth = w * 0.085f
        canvas.drawPath(path, line)
        line.color = cWhite
        line.strokeWidth = w * 0.06f
        canvas.drawPath(path, line)

        // legs
        if (!asleep) {
            val swing = if (walking) sin(t * 10f) * w * 0.05f else 0f
            val dangle = if (loose) sin(t * 7f) * w * 0.03f else 0f
            val legBottom = if (loose) h * 0.96f else h * 0.90f
            val legW = w * 0.10f
            val l1 = w * 0.30f - swing + dangle
            val l2 = w * 0.62f + swing - dangle
            outlinedRound(canvas, l1, h * 0.70f, l1 + legW, legBottom, legW / 2f)
            outlinedRound(canvas, l2, h * 0.70f, l2 + legW, legBottom, legW / 2f)
        }

        // body
        if (asleep) outlinedOval(canvas, w * 0.14f, h * 0.58f, w * 0.82f, h * 0.90f)
        else if (sitting) outlinedOval(canvas, w * 0.26f, h * 0.42f, w * 0.72f, h * 0.90f)
        else outlinedOval(canvas, w * 0.18f, h * 0.48f, w * 0.78f, h * 0.82f)

        // head
        val hx = if (asleep) w * 0.64f else if (sitting) w * 0.52f else w * 0.66f
        val hy = if (asleep) h * 0.66f else if (sitting) h * 0.32f else h * 0.38f
        val hr = w * 0.21f

        ear(canvas, hx - hr * 0.95f, hy - hr * 0.15f, hx - hr * 0.70f, hy - hr * 1.45f, hx - hr * 0.05f, hy - hr * 0.85f)
        ear(canvas, hx + hr * 0.95f, hy - hr * 0.15f, hx + hr * 0.70f, hy - hr * 1.45f, hx + hr * 0.05f, hy - hr * 0.85f)
        outlinedOval(canvas, hx - hr, hy - hr, hx + hr, hy + hr)

        // scarf
        if (!asleep) {
            outlinedRound(canvas, hx - hr * 0.8f, hy + hr * 0.78f, hx + hr * 0.8f, hy + hr * 1.2f, w * 0.03f, cPink)
            outlinedRound(canvas, hx + hr * 0.15f, hy + hr * 1.05f, hx + hr * 0.6f, hy + hr * 1.75f, w * 0.02f, cPink)
        }

        // eyes
        val ex = hr * 0.42f
        val ey = hy + hr * 0.05f
        if (asleep) {
            arcEye(canvas, hx - ex, ey, false)
            arcEye(canvas, hx + ex, ey, false)
        } else if (happy) {
            arcEye(canvas, hx - ex, ey, true)
            arcEye(canvas, hx + ex, ey, true)
        } else if ((t % 4.2f) < 0.12f) {
            arcEye(canvas, hx - ex, ey, false)
            arcEye(canvas, hx + ex, ey, false)
        } else {
            fill.color = cEye
            canvas.drawCircle(hx - ex, ey, w * 0.028f, fill)
            canvas.drawCircle(hx + ex, ey, w * 0.028f, fill)
        }

        // cheeks, nose, mouth
        fill.color = Color.argb(110, 255, 143, 177)
        canvas.drawCircle(hx - hr * 0.68f, hy + hr * 0.38f, w * 0.035f, fill)
        canvas.drawCircle(hx + hr * 0.68f, hy + hr * 0.38f, w * 0.035f, fill)
        fill.color = cPink
        canvas.drawCircle(hx, hy + hr * 0.32f, w * 0.016f, fill)
        path.reset()
        path.moveTo(hx - w * 0.03f, hy + hr * 0.52f)
        path.quadTo(hx - w * 0.015f, hy + hr * 0.64f, hx, hy + hr * 0.52f)
        path.quadTo(hx + w * 0.015f, hy + hr * 0.64f, hx + w * 0.03f, hy + hr * 0.52f)
        line.color = cEye
        line.strokeWidth = lw * 0.6f
        canvas.drawPath(path, line)

        canvas.restore()

        // effects (drawn unmirrored, positions mapped by facing)
        if (happy) {
            val rise = (sin(t * 8f) * 0.5f + 0.5f) * h * 0.03f
            textPaint.textSize = w * 0.22f
            textPaint.color = Color.parseColor("#FF5C8A")
            val px = if (facingRight) hx + hr * 1.0f else w - (hx + hr * 1.0f)
            canvas.drawText("\u2665", px, hy - hr * 0.9f - rise, textPaint)
        }
        if (asleep) {
            val phase = (t * 0.5f) % 1f
            textPaint.textSize = w * 0.18f
            textPaint.color = Color.argb((255 * (1f - phase)).toInt(), 138, 122, 153)
            val px = if (facingRight) hx + hr * 1.1f else w - (hx + hr * 1.1f)
            canvas.drawText("z", px, hy - hr * 0.6f - phase * h * 0.12f, textPaint)
        }
    }

    private fun outlinedOval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int = cWhite) {
        rect.set(l, t, r, b)
        fill.color = color
        c.drawOval(rect, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawOval(rect, line)
    }

    private fun outlinedRound(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, color: Int = cWhite) {
        rect.set(l, t, r, b)
        fill.color = color
        c.drawRoundRect(rect, rad, rad, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawRoundRect(rect, rad, rad, line)
    }

    private fun ear(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        path.reset()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        fill.color = cWhite
        c.drawPath(path, fill)
        line.color = cOutline
        line.strokeWidth = lw
        c.drawPath(path, line)
        val cx = (x1 + x2 + x3) / 3f
        val cy = (y1 + y2 + y3) / 3f
        path.reset()
        path.moveTo(cx + (x1 - cx) * 0.5f, cy + (y1 - cy) * 0.5f)
        path.lineTo(cx + (x2 - cx) * 0.5f, cy + (y2 - cy) * 0.5f)
        path.lineTo(cx + (x3 - cx) * 0.5f, cy + (y3 - cy) * 0.5f)
        path.close()
        fill.color = Color.parseColor("#FFC2D1")
        c.drawPath(path, fill)
    }

    private fun arcEye(c: Canvas, ex: Float, ey: Float, happy: Boolean) {
        val r = width * 0.035f
        path.reset()
        path.moveTo(ex - r, ey)
        path.quadTo(ex, if (happy) ey - r * 1.6f else ey + r * 1.6f, ex + r, ey)
        line.color = cEye
        line.strokeWidth = lw * 1.2f
        c.drawPath(path, line)
    }
}
