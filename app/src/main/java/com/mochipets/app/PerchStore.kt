package com.mochipets.app

import android.content.SharedPreferences

/**
 * A "perch": a live pet that sits at a fixed spot on the screen, normally on top of an app icon.
 * Position is stored as fractions (0..1) of the free screen area, so it survives rotation.
 */
class Perch(
    val id: String,
    val pkg: String,
    val label: String,
    val character: CharacterType,
    val xFrac: Float,
    val yFrac: Float
)

/** Saves perches in SharedPreferences as one text value (one perch per line, tab separated). */
object PerchStore {
    private const val KEY = "perches_v1"
    const val MAX_PERCHES = 8

    private fun clean(s: String): String = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    fun encode(list: List<Perch>): String = list.joinToString("\n") {
        listOf(clean(it.id), clean(it.pkg), clean(it.label), it.character.id, it.xFrac.toString(), it.yFrac.toString())
            .joinToString("\t")
    }

    fun decode(text: String?): List<Perch> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<Perch>()
        for (line in text.split("\n")) {
            val f = line.split("\t")
            if (f.size != 6 || f[0].isEmpty() || f[1].isEmpty()) continue
            val x = f[4].toFloatOrNull() ?: continue
            val y = f[5].toFloatOrNull() ?: continue
            out.add(Perch(f[0], f[1], f[2], CharacterType.fromId(f[3]), x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)))
        }
        return out
    }

    fun all(p: SharedPreferences): List<Perch> = decode(p.getString(KEY, null))

    fun get(p: SharedPreferences, id: String): Perch? = all(p).firstOrNull { it.id == id }

    private fun save(p: SharedPreferences, list: List<Perch>) {
        p.edit().putString(KEY, encode(list)).apply()
    }

    /** Adds or replaces a perch. Returns false if the limit of [MAX_PERCHES] would be exceeded. */
    fun put(p: SharedPreferences, perch: Perch): Boolean {
        val list = all(p).toMutableList()
        val index = list.indexOfFirst { it.id == perch.id }
        if (index >= 0) {
            list[index] = perch
        } else {
            if (list.size >= MAX_PERCHES) return false
            list.add(perch)
        }
        save(p, list)
        return true
    }

    fun remove(p: SharedPreferences, id: String) {
        save(p, all(p).filter { it.id != id })
    }

    fun updatePosition(p: SharedPreferences, id: String, xFrac: Float, yFrac: Float) {
        val list = all(p).map {
            if (it.id == id) Perch(it.id, it.pkg, it.label, it.character, xFrac.coerceIn(0f, 1f), yFrac.coerceIn(0f, 1f)) else it
        }
        save(p, list)
    }
}
