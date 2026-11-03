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
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.util.Calendar

class PetOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.mochipets.app.START"
        const val ACTION_STOP = "com.mochipets.app.STOP"
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning = false
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var petView: PetView? = null
    private var brain: PetBrain? = null

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
                Intent.ACTION_SCREEN_ON -> if (petView != null) startLoop()
            }
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val b = brain
            val v = petView
            if (b == null || v == null) return

            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            val elapsed = frameTimeNanos - lastFrameNanos
            if (elapsed >= frameIntervalNanos - 2_000_000L) {
                val dt = (elapsed / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameNanos = frameTimeNanos
                animTime += dt

                if (frameTimeNanos - lastMoodNanos > 60_000_000_000L) {
                    lastMoodNanos = frameTimeNanos
                    b.sleepiness = computeSleepiness()
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
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        removePet()
        addPet()
        isRunning = true
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val size = screenSize()
        brain?.setScreen(size.first, size.second)
    }

    override fun onDestroy() {
        removePet()
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
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop), stopIntent).build())
            .setOngoing(true)
            .build()
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

    private fun addPet() {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val sizeDp = prefs.getInt(Prefs.KEY_SIZE_DP, Prefs.DEFAULT_SIZE_DP)
        val speed = prefs.getInt(Prefs.KEY_SPEED_PCT, Prefs.DEFAULT_SPEED_PCT) / 100f
        val saver = prefs.getBoolean(Prefs.KEY_SAVER, false)
        val character = CharacterType.fromId(prefs.getString(Prefs.KEY_CHARACTER, null))
        val sizePx = (sizeDp * density).toInt()
        val screen = screenSize()

        val b = PetBrain(sizePx, density, screen.first, screen.second, (72 * density).toInt(), character)
        b.speedScale = speed
        b.sleepiness = computeSleepiness()
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

        frameIntervalNanos = if (saver) 66_666_666L else 33_333_333L
        animTime = 0f
        lastMoodNanos = 0L
        startLoop()
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

    // ---------- touch: tap, drag, throw ----------

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
                }
                vt?.recycle()
                velocityTracker = null
                dragging = false
                return true
            }
        }
        return false
    }

    // ---------- mood ----------

    private fun computeSleepiness(): Float {
        var s = 0f
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 22 || hour < 6) s += 0.6f
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
