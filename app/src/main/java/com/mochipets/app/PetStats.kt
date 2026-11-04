package com.mochipets.app

import android.content.SharedPreferences
import java.util.TimeZone

/**
 * Progression rules: XP, levels, happiness, daily reward and treats.
 * Pure logic on top of SharedPreferences, with the clock passed in so it can be tested.
 *
 * Per pet (keyed by character id): xp, happiness.
 * Global: daily reward day + streak, treat cooldown, daily XP caps.
 */
object PetStats {
    private const val START_HAPPINESS = 60f
    private const val DECAY_PER_HOUR = 2f
    private const val TAP_CAP_PER_DAY = 50
    private const val TIME_CAP_PER_DAY = 30
    const val TREAT_COOLDOWN_MS = 30L * 60L * 1000L

    class Snapshot(
        val level: Int,
        val xp: Int,
        val xpIntoLevel: Int,
        val xpForNextLevel: Int,
        val happiness: Int
    )

    class Daily(val claimedToday: Boolean, val streak: Int)

    /** levelUp is the new level number if the pet levelled up, otherwise 0. */
    class Claim(val xp: Int, val streak: Int, val levelUp: Int)

    /** Total XP needed to reach a level: L2 = 20, L3 = 60, L4 = 120, L5 = 200 ... */
    fun xpForLevel(level: Int): Int = 10 * level * (level - 1)

    fun levelForXp(xp: Int): Int {
        var l = 1
        while (xpForLevel(l + 1) <= xp) l++
        return l
    }

    fun localDay(now: Long): Long = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

    fun speedFactor(happiness: Int): Float = 0.7f + 0.5f * (happiness.coerceIn(0, 100) / 100f)

    fun moodWord(happiness: Int): String = when {
        happiness >= 70 -> "Happy"
        happiness >= 30 -> "Okay"
        else -> "Sad"
    }

    private fun happinessNow(p: SharedPreferences, id: String, now: Long): Float {
        val stored = p.getFloat("happy_$id", START_HAPPINESS)
        val ts = p.getLong("happy_ts_$id", now)
        val hours = (now - ts).coerceAtLeast(0L) / 3_600_000f
        return (stored - DECAY_PER_HOUR * hours).coerceIn(0f, 100f)
    }

    /** Current level of a pet, from stored XP. */
    fun level(p: SharedPreferences, id: String): Int = levelForXp(p.getInt("xp_$id", 0))

    fun snapshot(p: SharedPreferences, id: String, now: Long): Snapshot {
        val xp = p.getInt("xp_$id", 0)
        val level = levelForXp(xp)
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        return Snapshot(level, xp, xp - base, next - base, happinessNow(p, id, now).toInt())
    }

    /** Returns the new level if this XP caused a level-up, otherwise 0. */
    fun addXp(p: SharedPreferences, id: String, amount: Int): Int {
        if (amount <= 0) return 0
        val old = p.getInt("xp_$id", 0)
        val updated = old + amount
        p.edit().putInt("xp_$id", updated).apply()
        val before = levelForXp(old)
        val after = levelForXp(updated)
        return if (after > before) after else 0
    }

    fun addHappiness(p: SharedPreferences, id: String, amount: Float, now: Long) {
        val v = (happinessNow(p, id, now) + amount).coerceIn(0f, 100f)
        p.edit().putFloat("happy_$id", v).putLong("happy_ts_$id", now).apply()
    }

    private fun rollDay(p: SharedPreferences, now: Long) {
        val today = localDay(now)
        if (p.getLong("day", -1L) != today) {
            p.edit().putLong("day", today).putInt("tap_xp", 0).putInt("time_xp", 0).apply()
        }
    }

    /** Called when the user taps the pet. Gives +1 XP and +1 happiness, up to 50 taps per day. */
    fun onTap(p: SharedPreferences, id: String, now: Long): Int {
        rollDay(p, now)
        val used = p.getInt("tap_xp", 0)
        if (used >= TAP_CAP_PER_DAY) return 0
        p.edit().putInt("tap_xp", used + 1).apply()
        addHappiness(p, id, 1f, now)
        return addXp(p, id, 1)
    }

    /** Called every 5 minutes while the pet is on screen. +1 XP, up to 30 per day. */
    fun onTimeTick(p: SharedPreferences, id: String, now: Long): Int {
        rollDay(p, now)
        val used = p.getInt("time_xp", 0)
        if (used >= TIME_CAP_PER_DAY) return 0
        p.edit().putInt("time_xp", used + 1).apply()
        return addXp(p, id, 1)
    }

    fun treatCooldownLeftMs(p: SharedPreferences, now: Long): Long =
        (TREAT_COOLDOWN_MS - (now - p.getLong("treat_ts", 0L))).coerceIn(0L, TREAT_COOLDOWN_MS)

    /** Returns -1 while the treat is cooling down, otherwise the new level (or 0 if no level-up). */
    fun giveTreat(p: SharedPreferences, id: String, now: Long): Int {
        if (treatCooldownLeftMs(p, now) > 0L) return -1
        p.edit().putLong("treat_ts", now).apply()
        addHappiness(p, id, 15f, now)
        return addXp(p, id, 3)
    }

    fun daily(p: SharedPreferences, now: Long): Daily {
        val today = localDay(now)
        val last = p.getLong("daily_day", -1L)
        val streak = p.getInt("daily_streak", 0)
        val claimed = last == today
        val effective = if (claimed || last == today - 1) streak else 0
        return Daily(claimed, effective)
    }

    /** Returns null if today's reward was already claimed. Reward: 10 XP, +2 per streak day (max +12). */
    fun claimDaily(p: SharedPreferences, id: String, now: Long): Claim? {
        val today = localDay(now)
        val last = p.getLong("daily_day", -1L)
        if (last == today) return null
        val streak = (if (last == today - 1) p.getInt("daily_streak", 0) else 0) + 1
        p.edit().putLong("daily_day", today).putInt("daily_streak", streak).apply()
        val reward = 10 + 2 * (minOf(streak, 7) - 1)
        addHappiness(p, id, 20f, now)
        val levelUp = addXp(p, id, reward)
        return Claim(reward, streak, levelUp)
    }
}
