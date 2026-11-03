package com.mochipets.app

/**
 * The characters the user can pick. Behaviour differences live here (speed, climbing),
 * the drawing lives in a PetRenderer per character.
 */
enum class CharacterType(
    val id: String,
    val displayName: String,
    val tagline: String,
    val canClimb: Boolean,
    val speedMul: Float
) {
    MOCHI("mochi", "Mochi", "Sleepy white cat. Climbs walls and naps a lot.", true, 1.0f),
    BHOLU("bholu", "Bholu", "Loyal, goofy street dog. Fast on the ground, barks when tapped.", false, 1.15f),
    JELLY("jelly", "Jelly", "Bouncy slime. Hops around and sticks to walls.", true, 0.85f);

    companion object {
        fun fromId(id: String?): CharacterType =
            values().firstOrNull { it.id == id } ?: MOCHI
    }
}
