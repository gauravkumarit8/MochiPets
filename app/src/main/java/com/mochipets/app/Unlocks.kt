package com.mochipets.app

import android.content.SharedPreferences

/**
 * Which pets the user can use. Mochi is always free. Others unlock when the user has
 * watched enough rewarded ads, or has raised Mochi to the required level.
 */
object Unlocks {

    /** The pet the user selected, or Mochi if the saved choice is still locked. */
    fun selected(p: SharedPreferences): CharacterType {
        val saved = CharacterType.fromId(p.getString(Prefs.KEY_CHARACTER, null))
        return if (isUnlocked(p, saved)) saved else CharacterType.MOCHI
    }

    fun adsWatched(p: SharedPreferences, type: CharacterType): Int = p.getInt("ads_${type.id}", 0)

    fun isUnlocked(p: SharedPreferences, type: CharacterType): Boolean {
        if (type.adsToUnlock == 0) return true
        if (p.getBoolean("unlocked_${type.id}", false)) return true
        return PetStats.level(p, CharacterType.MOCHI.id) >= type.unlockLevel
    }

    /** Records one completed rewarded ad. Returns true if this ad unlocked the pet. */
    fun recordAdWatched(p: SharedPreferences, type: CharacterType): Boolean {
        val n = adsWatched(p, type) + 1
        val done = n >= type.adsToUnlock
        val e = p.edit().putInt("ads_${type.id}", n)
        if (done) e.putBoolean("unlocked_${type.id}", true)
        e.apply()
        return done
    }
}
