package com.mochipets.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Draws pets in overlay windows:
 *  - the roaming pet (walks, climbs, can be dragged and thrown), and
 *  - "perch" pets that sit on a chosen spot, normally on top of an app icon.
 */
class PetOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.mochipets.app.START"
        const val ACTION_STOP = "com.mochipets.app.STOP"
        /** Start (or restart) with perch pets unlocked so the user can drag them onto icons. */
        const val ACTION_EDIT = "com.mochipets.app.EDIT"
        /** Switch between moving perch pets and locking them in place. */
        const val ACTION_TOGGLE_EDIT = "com.mochipets.app.TOGGLE_EDIT"
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIF_ID = 1
        private const val PERCH_SIZE_DP = 64
        private const val EDIT_TIMEOUT_MS = 60_000L

        @Volatile
        var isRunning = false
    }

    private class PerchHolder(
        val perch: Perch,
        val view: PetView,
        val params: WindowManager.LayoutParams,
        val phase: Float
    )

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var petView: PetView? = null
    private var brain: PetBrain? = null
    private var currentCharacter: CharacterType = CharacterType.MOCHI
    private var minutesRunning = 0
    private val statsPrefs by lazy { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }

    private val perchHolders = LinkedHashMap<String, PerchHolder>()
    private var editMode = false
    private var perchGrabX = 0f
    private var perchGrabY = 0f
    private var perchNight = false
    private var lastNightCheck = -100f
    private var perchFrameCounter = 0
    private val handler = Handler(Looper.getMainLooper())
    private val editTimeout = Runnable { setEditMode(false) }

    private var running = false
    private var lastFrameNanos = 0L
    private var lastMoodNanos = 0L
    private var frameIntervalNanos = 33_333_333L
    private var animTime = 0f
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    private var velocityTracker: VelocityTracker? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var grabOffsetX = 0f
    private var grabOffsetY = 0f
    private var dragging = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopLoop()
                Intent.ACTION_SCREEN_ON -> if (petView != null || perchHolders.isNotEmpty()) startLoop()
            }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return

            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            val elapsed = frameTimeNanos - lastFrameNanos
            if (elapsed >= frameIntervalNanos - 2_000_000L) {
                val dt = (elapsed / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameNanos = frameTimeNanos
                animTime += dt

                val b = brain
                val v = petView
                if (b != null && v != null) stepRoamingPet(frameTimeNanos, dt, b, v)
                updatePerches()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == ACTION_TOGGLE_EDIT && isRunning) {
            setEditMode(!editMode)
            return START_STICKY
        }
        editMode = action == ACTION_EDIT
        rebuildAll()
        isRunning = true
        if (editMode) {
            handler.removeCallbacks(editTimeout)
            handler.postDelayed(editTimeout, EDIT_TIMEOUT_MS)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val size = screenSize()
        brain?.setScreen(size.first, size.second)
        repositionPerches()
    }

    override fun onDestroy() {
        handler.removeCallbacks(editTimeout)
        removeAll()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // not registered
        }
        isRunning = false
        super.onDestroy()
    }

    // ---------- foreground notification ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP), flags
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(if (editMode) R.string.notif_text_edit else R.string.notif_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
        if (PerchStore.all(statsPrefs).isNotEmpty()) {
            val toggleIntent = PendingIntent.getService(
                this, 2, Intent(this, PetOverlayService::class.java).setAction(ACTION_TOGGLE_EDIT), flags
            )
            val label = getString(if (editMode) R.string.notif_lock else R.string.notif_move)
            builder.addAction(Notification.Action.Builder(null, label, toggleIntent).build())
        }
        builder.addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stopIntent).build())
        return builder.build()
    }

    // ---------- overlay ----------

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels, m.heightPixels)
        }
    }

    /** Builds everything from the saved settings: the roaming pet (optional) and all perch pets. */
    private fun rebuildAll() {
        removeAll()
        val perches = PerchStore.all(statsPrefs)
        // Never leave the screen empty: if there are no perches, the roaming pet is always shown.
        val roam = statsPrefs.getBoolean(Prefs.KEY_ROAM, true) || perches.isEmpty()
        val saver = statsPrefs.getBoolean(Prefs.KEY_SAVER, false)
        frameIntervalNanos = if (saver) 66_666_666L else 33_333_333L
        animTime = 0f
        lastMoodNanos = 0L
        lastNightCheck = -100f
        if (roam) addPet()
        addPerches(perches)
        startLoop()
    }

    private fun removeAll() {
        removePet()
        removePerches()
    }

    private fun addPet() {
        val prefs = statsPrefs
        val density = resources.displayMetrics.density
        val sizeDp = prefs.getInt(Prefs.KEY_SIZE_DP, Prefs.DEFAULT_SIZE_DP)
        val speed = prefs.getInt(Prefs.KEY_SPEED_PCT, Prefs.DEFAULT_SPEED_PCT) / 100f
        var character = CharacterType.fromId(prefs.getString(Prefs.KEY_CHARACTER, null))
        if (!Unlocks.isUnlocked(prefs, character)) character = CharacterType.MOCHI
        val sizePx = (sizeDp * density).toInt()
        val screen = screenSize()

        val b = PetBrain(sizePx, density, screen.first, screen.second, (72 * density).toInt(), character)
        currentCharacter = character
        minutesRunning = 0
        b.speedScale = speed
        applyMood(b)
        brain = b

        val v = PetView(this)
        v.character = character
        petView = v

        params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = b.x.toInt()
            y = b.y.toInt()
        }

        v.setOnTouchListener { _, e -> handleTouch(e) }
        wm.addView(v, params)
    }

    private fun removePet() {
        stopLoop()
        petView?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                // already gone
            }
        }
        petView = null
        brain = null
        lastX = Int.MIN_VALUE
        lastY = Int.MIN_VALUE
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun startLoop() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    // ---------- roaming pet: one animation step ----------

    private fun stepRoamingPet(frameTimeNanos: Long, dt: Float, b: PetBrain, v: PetView) {
        if (frameTimeNanos - lastMoodNanos > 60_000_000_000L) {
            val firstTick = lastMoodNanos == 0L
            lastMoodNanos = frameTimeNanos
            applyMood(b)
            if (!firstTick) {
                minutesRunning++
                if (minutesRunning % 5 == 0) {
                    val lvl = PetStats.onTimeTick(statsPrefs, currentCharacter.id, System.currentTimeMillis())
                    if (lvl > 0) showLevelUp(lvl)
                }
            }
        }

        b.update(dt)
        v.state = b.state
        v.facingRight = b.facingRight
        v.surface = b.surface
        v.squash = b.squash
        v.animTime = animTime

        val nx = b.x.toInt()
        val ny = b.y.toInt()
        if (nx != lastX || ny != lastY) {
            lastX = nx
            lastY = ny
            params.x = nx
            params.y = ny
            try {
                wm.updateViewLayout(v, params)
            } catch (e: Exception) {
                // view already removed
            }
        }
        v.invalidate()
    }

    // ---------- perch pets (live pets sitting on app icons) ----------

    private fun perchFlags(editing: Boolean): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        // When locked, touches pass straight through to the icon underneath.
        if (!editing) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun addPerches(perches: List<Perch>) {
        val density = resources.displayMetrics.density
        val sizePx = (PERCH_SIZE_DP * density).toInt()
        val screen = screenSize()
        for (perch in perches) {
            val v = PetView(this)
            v.character = if (Unlocks.isUnlocked(statsPrefs, perch.character)) perch.character else CharacterType.MOCHI
            v.showShadow = false
            v.editMode = editMode
            v.facingRight = perch.xFrac < 0.5f
            val p = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                perchFlags(editMode),
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (perch.xFrac * (screen.first - sizePx)).toInt().coerceAtLeast(0)
                y = (perch.yFrac * (screen.second - sizePx)).toInt().coerceAtLeast(0)
            }
            val phase = ((perch.id.hashCode() and 0x7fffffff) % 100) / 100f * 30f
            val holder = PerchHolder(perch, v, p, phase)
            v.setOnTouchListener { _, e -> handlePerchTouch(holder, e) }
            try {
                wm.addView(v, p)
                perchHolders[perch.id] = holder
            } catch (e: Exception) {
                // could not add this window; skip it
            }
        }
    }

    private fun removePerches() {
        for (h in perchHolders.values) {
            try {
                wm.removeView(h.view)
            } catch (e: Exception) {
                // already gone
            }
        }
        perchHolders.clear()
    }

    private fun repositionPerches() {
        val screen = screenSize()
        for (h in perchHolders.values) {
            val saved = PerchStore.get(statsPrefs, h.perch.id) ?: continue
            h.params.x = (saved.xFrac * (screen.first - h.params.width)).toInt().coerceAtLeast(0)
            h.params.y = (saved.yFrac * (screen.second - h.params.height)).toInt().coerceAtLeast(0)
            try {
                wm.updateViewLayout(h.view, h.params)
            } catch (e: Exception) {
                // view already removed
            }
        }
    }

    /** Perch pets only breathe, blink and nap, so they are redrawn less often than the roaming pet. */
    private fun updatePerches() {
        if (perchHolders.isEmpty()) return
        perchFrameCounter++
        if (perchFrameCounter % 3 != 0) return

        if (animTime - lastNightCheck > 30f) {
            lastNightCheck = animTime
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            perchNight = hour >= 22 || hour < 6
        }
        for (h in perchHolders.values) {
            val t = (animTime + h.phase) % 30f
            h.view.state = when {
                perchNight -> PetState.SLEEP
                t in 22f..28f -> PetState.SIT
                else -> PetState.IDLE
            }
            h.view.animTime = animTime
            h.view.invalidate()
        }
    }

    private fun setEditMode(on: Boolean) {
        editMode = on
        handler.removeCallbacks(editTimeout)
        if (on) handler.postDelayed(editTimeout, EDIT_TIMEOUT_MS)
        for (h in perchHolders.values) {
            h.params.flags = perchFlags(on)
            h.view.editMode = on
            try {
                wm.updateViewLayout(h.view, h.params)
            } catch (e: Exception) {
                // view already removed
            }
            h.view.invalidate()
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun handlePerchTouch(h: PerchHolder, e: MotionEvent): Boolean {
        if (!editMode) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                perchGrabX = e.rawX - h.params.x
                perchGrabY = e.rawY - h.params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val screen = screenSize()
                val maxX = (screen.first - h.params.width).coerceAtLeast(0)
                val maxY = (screen.second - h.params.height).coerceAtLeast(0)
                h.params.x = (e.rawX - perchGrabX).toInt().coerceIn(0, maxX)
                h.params.y = (e.rawY - perchGrabY).toInt().coerceIn(0, maxY)
                try {
                    wm.updateViewLayout(h.view, h.params)
                } catch (ex: Exception) {
                    // view already removed
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val screen = screenSize()
                val maxX = (screen.first - h.params.width).coerceAtLeast(1)
                val maxY = (screen.second - h.params.height).coerceAtLeast(1)
                val xFrac = h.params.x.toFloat() / maxX
                val yFrac = h.params.y.toFloat() / maxY
                PerchStore.updatePosition(statsPrefs, h.perch.id, xFrac, yFrac)
                h.view.facingRight = xFrac < 0.5f
                // Keep edit mode alive while the user is still arranging pets.
                handler.removeCallbacks(editTimeout)
                handler.postDelayed(editTimeout, EDIT_TIMEOUT_MS)
                return true
            }
        }
        return false
    }

    // ---------- touch on the roaming pet: tap, drag, throw ----------

    private fun handleTouch(e: MotionEvent): Boolean {
        val b = brain ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = e.rawX
                downRawY = e.rawY
                grabOffsetX = e.rawX - b.x
                grabOffsetY = e.rawY - b.y
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(e) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(e)
                if (!dragging) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    val dx = e.rawX - downRawX
                    val dy = e.rawY - downRawY
                    if (dx * dx + dy * dy > slop * slop) {
                        dragging = true
                        b.grab()
                    }
                }
                if (dragging) b.dragTo(e.rawX - grabOffsetX, e.rawY - grabOffsetY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val vt = velocityTracker
                if (dragging) {
                    vt?.addMovement(e)
                    vt?.computeCurrentVelocity(1000)
                    b.release((vt?.xVelocity ?: 0f) * 0.7f, (vt?.yVelocity ?: 0f) * 0.7f)
                } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                    b.tap()
                    onPetTapped()
                }
                vt?.recycle()
                velocityTracker = null
                dragging = false
                return true
            }
        }
        return false
    }

    // ---------- stats ----------

    private fun onPetTapped() {
        val lvl = PetStats.onTap(statsPrefs, currentCharacter.id, System.currentTimeMillis())
        if (lvl > 0) showLevelUp(lvl)
    }

    private fun showLevelUp(level: Int) {
        Toast.makeText(this, "${currentCharacter.displayName} reached level $level!", Toast.LENGTH_LONG).show()
    }

    private fun applyMood(b: PetBrain) {
        b.sleepiness = computeSleepiness()
        val happiness = PetStats.snapshot(statsPrefs, currentCharacter.id, System.currentTimeMillis()).happiness
        b.moodSpeed = PetStats.speedFactor(happiness)
    }

    // ---------- mood ----------

    private fun computeSleepiness(): Float {
        var s = 0f
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 6) s += 0.6f
        val happiness = PetStats.snapshot(statsPrefs, currentCharacter.id, System.currentTimeMillis()).happiness
        if (happiness < 30) s += 0.25f
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            if (pct < 15 && !charging) s += 0.5f
        }
        return s.coerceAtMost(1f)
    }
}
