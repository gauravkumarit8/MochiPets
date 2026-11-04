package com.mochipets.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/** Builds the picture used for a "pet icon" shortcut: the app's own icon with the pet sitting on it. */
object PetIcon {

    /**
     * The app icon fills the tile and the pet sits in the bottom-left corner, overlapping it.
     * (Most launchers draw a small Mochi Pets badge in the bottom-right corner of pinned shortcuts,
     * so the pet is kept on the left.)
     */
    fun compose(context: Context, appIcon: Drawable, type: CharacterType, size: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val inset = (size * 0.06f).toInt()
        appIcon.setBounds(inset, inset, size - inset, size - inset)
        appIcon.draw(canvas)

        val petSize = (size * 0.62f).toInt()
        val pet = PetView(context)
        pet.character = type
        pet.state = PetState.IDLE
        pet.facingRight = true
        pet.animTime = 0.5f
        pet.showShadow = false
        pet.layout(0, 0, petSize, petSize)
        canvas.save()
        canvas.translate(0f, (size - petSize).toFloat())
        pet.draw(canvas)
        canvas.restore()
        return bmp
    }
}
