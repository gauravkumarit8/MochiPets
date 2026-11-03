package com.mochipets.app

import kotlin.random.Random

enum class PetState { IDLE, WALK, SIT, SLEEP, DRAGGED, FALL, REACT, CLIMB, HANG }

/**
 * Pure logic: position, state machine and simple physics. No Android UI code,
 * so it is easy to test and to reuse for every character.
 */
class PetBrain(
    private val petSize: Int,
    private val density: Float,
    private var screenW: Int,
    private var screenH: Int,
    private val floorMargin: Int,
    private val character: CharacterType = CharacterType.MOCHI
) {
    var state: PetState = PetState.IDLE
        private set
    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var facingRight: Boolean = true
        private set
    var stateTime: Float = 0f
        private set

    /** 0 = floor, 1 = left wall, 2 = right wall, 3 = ceiling. */
    var surface: Int = 0
        private set

    /** 1 right after a hard landing, fades to 0. Drives squash and stretch. */
    var squash: Float = 0f
        private set

    /** 0..1. Higher means more naps. Set by the service from time of day and battery. */
    var sleepiness: Float = 0f
    var speedScale: Float = 1f

    private var stateDuration = 3f
    private var vx = 0f
    private var vy = 0f
    private val gravity = 900f * density

    private val floorY: Float
        get() = (screenH - petSize - floorMargin).toFloat()

    private val topY: Float
        get() = 32f * density

    init {
        x = ((screenW - petSize) / 2f).coerceAtLeast(0f)
        y = floorY
        enter(PetState.IDLE)
    }

    private fun rand(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

    private fun enter(s: PetState) {
        state = s
        stateTime = 0f
        stateDuration = when (s) {
            PetState.IDLE -> rand(3f, 8f)
            PetState.WALK -> rand(4f, 12f)
            PetState.SIT -> rand(4f, 10f)
            PetState.SLEEP -> rand(20f, 60f)
            PetState.REACT -> 1.6f
            PetState.HANG -> rand(4f, 10f)
            else -> Float.MAX_VALUE
        }
        if (s != PetState.CLIMB && s != PetState.HANG) surface = 0
        if (s == PetState.WALK) facingRight = Random.nextBoolean()
    }

    private fun startClimb(left: Boolean) {
        surface = if (left) 1 else 2
        enter(PetState.CLIMB)
        facingRight = !left
    }

    private fun weighted(vararg options: Pair<PetState, Float>): PetState {
        var total = 0f
        for (o in options) total += o.second
        var r = Random.nextFloat() * total
        for (o in options) {
            r -= o.second
            if (r <= 0f) return o.first
        }
        return options.last().first
    }

    private fun pickNext() {
        val sleepWeight = 8f + 70f * sleepiness
        val next = when (state) {
            PetState.SLEEP, PetState.REACT -> PetState.IDLE
            else -> weighted(
                PetState.IDLE to 20f,
                PetState.WALK to 45f,
                PetState.SIT to 20f,
                PetState.SLEEP to sleepWeight
            )
        }
        enter(next)
    }

    fun update(dt: Float) {
        stateTime += dt
        if (squash > 0f) squash = (squash - dt / 0.22f).coerceAtLeast(0f)
        val maxX = (screenW - petSize).toFloat().coerceAtLeast(0f)

        when (state) {
            PetState.WALK -> {
                val dir = if (facingRight) 1f else -1f
                x += dir * 40f * density * speedScale * character.speedMul * dt
                if (x <= 0f) {
                    x = 0f
                    if (character.canClimb && Random.nextFloat() < 0.4f) startClimb(true) else facingRight = true
                } else if (x >= maxX) {
                    x = maxX
                    if (character.canClimb && Random.nextFloat() < 0.4f) startClimb(false) else facingRight = false
                }
                y = floorY
            }
            PetState.CLIMB -> {
                x = if (surface == 1) 0f else maxX
                y -= 55f * density * speedScale * dt
                if (y <= topY) {
                    y = topY
                    surface = 3
                    enter(PetState.HANG)
                    facingRight = Random.nextBoolean()
                }
            }
            PetState.HANG -> {
                y = topY
                if (stateTime >= stateDuration) {
                    vx = 0f
                    vy = 0f
                    enter(PetState.FALL)
                }
            }
            PetState.FALL -> {
                vy += gravity * dt
                x += vx * dt
                y += vy * dt
                if (x < 0f) { x = 0f; vx = -vx * 0.5f }
                if (x > maxX) { x = maxX; vx = -vx * 0.5f }
                if (y < 0f) { y = 0f; vy = -vy * 0.3f }
                if (y >= floorY) {
                    y = floorY
                    if (vy > 150f * density) {
                        squash = (vy / (900f * density)).coerceIn(0.35f, 1f)
                        vy = -vy * 0.35f
                        vx *= 0.7f
                    } else {
                        vy = 0f
                        vx = 0f
                        enter(PetState.IDLE)
                    }
                }
            }
            PetState.IDLE, PetState.SIT, PetState.SLEEP, PetState.REACT -> y = floorY
            else -> {}
        }

        when (state) {
            PetState.IDLE, PetState.WALK, PetState.SIT, PetState.SLEEP, PetState.REACT ->
                if (stateTime >= stateDuration) pickNext()
            else -> {}
        }
    }

    fun grab() {
        enter(PetState.DRAGGED)
    }

    fun dragTo(nx: Float, ny: Float) {
        if (state != PetState.DRAGGED) return
        val maxX = (screenW - petSize).toFloat().coerceAtLeast(0f)
        val maxY = (screenH - petSize).toFloat().coerceAtLeast(0f)
        val cx = nx.coerceIn(0f, maxX)
        if (cx > x + 1f) facingRight = true else if (cx < x - 1f) facingRight = false
        x = cx
        y = ny.coerceIn(0f, maxY)
    }

    fun release(velX: Float, velY: Float) {
        vx = velX
        vy = velY
        enter(PetState.FALL)
    }

    fun tap() {
        if (state == PetState.DRAGGED || state == PetState.FALL) return
        if (state == PetState.CLIMB || state == PetState.HANG) {
            vx = 0f
            vy = 0f
            enter(PetState.FALL)
            return
        }
        enter(PetState.REACT)
    }

    fun setScreen(w: Int, h: Int) {
        screenW = w
        screenH = h
        x = x.coerceIn(0f, (w - petSize).toFloat().coerceAtLeast(0f))
        if (state == PetState.CLIMB || state == PetState.HANG) {
            vx = 0f
            vy = 0f
            enter(PetState.FALL)
        } else if (state != PetState.DRAGGED && state != PetState.FALL) {
            y = floorY
        }
    }
}
