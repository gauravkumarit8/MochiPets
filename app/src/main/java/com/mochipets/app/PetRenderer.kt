package com.mochipets.app

import android.graphics.Canvas

/**
 * Draws one character. PetView applies surface rotation, mirroring and squash first,
 * so a renderer always draws a pet standing on the floor and facing right.
 * Later, a Rive/sprite renderer can implement this same interface.
 */
interface PetRenderer {
    /** Body drawing, in the transformed (rotated/mirrored/squashed) space. */
    fun draw(c: Canvas, w: Float, h: Float, state: PetState, t: Float)

    /** Effects such as speech bubbles and hearts; never mirrored or rotated. */
    fun drawEffects(c: Canvas, w: Float, h: Float, state: PetState, t: Float, facingRight: Boolean)
}
