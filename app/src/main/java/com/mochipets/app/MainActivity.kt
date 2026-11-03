package com.mochipets.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private class Preview(val type: CharacterType, val view: PetView, val card: LinearLayout)

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var taglineText: TextView
    private lateinit var permissionButton: Button
    private lateinit var toggleButton: Button
    private lateinit var levelText: TextView
    private lateinit var xpBar: ProgressBar
    private lateinit var happyText: TextView
    private lateinit var happyBar: ProgressBar
    private lateinit var dailyButton: Button
    private lateinit var treatButton: Button
    private var statsTick = 0

    private val previews = mutableListOf<Preview>()
    private val handler = Handler(Looper.getMainLooper())
    private var previewTime = 0f
    private val previewTick = object : Runnable {
        override fun run() {
            previewTime += 0.04f
            statsTick++
            if (statsTick % 25 == 0) refreshStats()
            for (p in previews) {
                p.view.animTime = previewTime
                p.view.invalidate()
            }
            handler.postDelayed(this, 40L)
        }
    }

    private val ink = Color.parseColor("#3A2A33")
    private val pink = Color.parseColor("#FF8FB1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#FFF7FA"))
        }

        root.addView(TextView(this).apply {
            text = "Mochi Pets"
            textSize = 30f
            setTextColor(ink)
        })

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(ink)
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(statusText)

        // ---- pet picker ----
        root.addView(TextView(this).apply {
            text = "Choose your pet"
            textSize = 18f
            setTextColor(ink)
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        for (type in CharacterType.values()) {
            val petView = PetView(this).apply {
                character = type
                state = PetState.WALK
                facingRight = true
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener { selectCharacter(type) }
            }
            card.addView(petView, LinearLayout.LayoutParams(dp(84), dp(84)))
            card.addView(TextView(this).apply {
                text = type.displayName
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ink)
            })
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(card, lp)
            previews.add(Preview(type, petView, card))
        }
        root.addView(row)

        taglineText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#7A6A73"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(taglineText)

        // ---- stats card ----
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#F0D6E0"))
            }
        }
        levelText = TextView(this).apply {
            textSize = 16f
            setTextColor(ink)
        }
        statsCard.addView(levelText)
        xpBar = progressBar()
        statsCard.addView(xpBar)
        happyText = TextView(this).apply {
            textSize = 14f
            setTextColor(ink)
            setPadding(0, dp(10), 0, 0)
        }
        statsCard.addView(happyText)
        happyBar = progressBar()
        statsCard.addView(happyBar)
        dailyButton = Button(this).apply { setOnClickListener { claimDaily() } }
        statsCard.addView(dailyButton)
        treatButton = Button(this).apply { setOnClickListener { giveTreat() } }
        statsCard.addView(treatButton)
        root.addView(statsCard)
        root.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)))

        permissionButton = Button(this).apply {
            text = "Allow display over other apps"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        }
        root.addView(permissionButton)

        toggleButton = Button(this).apply {
            setOnClickListener {
                if (PetOverlayService.isRunning) {
                    startService(
                        Intent(this@MainActivity, PetOverlayService::class.java)
                            .setAction(PetOverlayService.ACTION_STOP)
                    )
                } else {
                    startPet()
                }
                postDelayed({ refreshUi() }, 600)
            }
        }
        root.addView(toggleButton)

        // size slider: 80..200 dp
        val sizeDp = prefs.getInt(Prefs.KEY_SIZE_DP, Prefs.DEFAULT_SIZE_DP)
        val sizeLabel = TextView(this).apply {
            setTextColor(ink)
            setPadding(0, dp(20), 0, 0)
            text = "Pet size: $sizeDp dp"
        }
        root.addView(sizeLabel)
        root.addView(
            slider(120, (sizeDp - 80).coerceIn(0, 120),
                onProgress = { sizeLabel.text = "Pet size: ${80 + it} dp" },
                onStop = {
                    prefs.edit().putInt(Prefs.KEY_SIZE_DP, 80 + it).apply()
                    applyIfRunning()
                })
        )

        // speed slider: 50..200 percent
        val speedPct = prefs.getInt(Prefs.KEY_SPEED_PCT, Prefs.DEFAULT_SPEED_PCT)
        val speedLabel = TextView(this).apply {
            setTextColor(ink)
            setPadding(0, dp(12), 0, 0)
            text = "Walk speed: $speedPct%"
        }
        root.addView(speedLabel)
        root.addView(
            slider(150, (speedPct - 50).coerceIn(0, 150),
                onProgress = { speedLabel.text = "Walk speed: ${50 + it}%" },
                onStop = {
                    prefs.edit().putInt(Prefs.KEY_SPEED_PCT, 50 + it).apply()
                    applyIfRunning()
                })
        )

        root.addView(CheckBox(this).apply {
            text = "Battery saver (15 fps)"
            setTextColor(ink)
            isChecked = prefs.getBoolean(Prefs.KEY_SAVER, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.KEY_SAVER, checked).apply()
                applyIfRunning()
            }
        })

        root.addView(TextView(this).apply {
            text = "Tip: tap your pet for a reaction, drag and throw it. Pets nap at night and when the battery is low."
            setTextColor(Color.parseColor("#7A6A73"))
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
        refreshPicker()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        refreshStats()
        handler.removeCallbacks(previewTick)
        handler.post(previewTick)
    }

    override fun onPause() {
        handler.removeCallbacks(previewTick)
        super.onPause()
    }

    private fun selectedCharacter(): CharacterType =
        CharacterType.fromId(prefs.getString(Prefs.KEY_CHARACTER, null))

    private fun selectCharacter(type: CharacterType) {
        prefs.edit().putString(Prefs.KEY_CHARACTER, type.id).apply()
        refreshPicker()
        refreshUi()
        refreshStats()
        applyIfRunning()
    }

    private fun refreshPicker() {
        val selected = selectedCharacter()
        for (p in previews) {
            val isSelected = p.type == selected
            p.card.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (isSelected) Color.parseColor("#FFE3EC") else Color.TRANSPARENT)
                setStroke(dp(2), if (isSelected) pink else Color.TRANSPARENT)
            }
        }
        taglineText.text = selected.tagline
    }

    private fun progressBar(): ProgressBar {
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.progressTintList = ColorStateList.valueOf(pink)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10))
        lp.topMargin = dp(6)
        bar.layoutParams = lp
        return bar
    }

    private fun refreshStats() {
        val now = System.currentTimeMillis()
        val type = selectedCharacter()
        val s = PetStats.snapshot(prefs, type.id, now)
        levelText.text = "${type.displayName}  -  Level ${s.level}  -  ${s.xpIntoLevel}/${s.xpForNextLevel} XP"
        xpBar.max = s.xpForNextLevel
        xpBar.progress = s.xpIntoLevel
        happyText.text = "Happiness: ${s.happiness}%  (${PetStats.moodWord(s.happiness)})"
        happyBar.max = 100
        happyBar.progress = s.happiness

        val d = PetStats.daily(prefs, now)
        if (d.claimedToday) {
            dailyButton.text = "Daily reward claimed (streak ${d.streak})"
            dailyButton.isEnabled = false
        } else {
            dailyButton.text = "Claim daily reward (streak ${d.streak + 1})"
            dailyButton.isEnabled = true
        }

        val left = PetStats.treatCooldownLeftMs(prefs, now)
        if (left > 0L) {
            treatButton.text = "Treat ready in ${(left + 59_999L) / 60_000L} min"
            treatButton.isEnabled = false
        } else {
            treatButton.text = "Give a treat (+happiness)"
            treatButton.isEnabled = true
        }
    }

    private fun claimDaily() {
        val type = selectedCharacter()
        val claim = PetStats.claimDaily(prefs, type.id, System.currentTimeMillis()) ?: return
        var msg = "+${claim.xp} XP and a happier ${type.displayName}! Streak: ${claim.streak}"
        if (claim.levelUp > 0) msg += "  Level ${claim.levelUp}!"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        refreshStats()
    }

    private fun giveTreat() {
        val type = selectedCharacter()
        val result = PetStats.giveTreat(prefs, type.id, System.currentTimeMillis())
        if (result >= 0) {
            var msg = "${type.displayName} loved the treat!"
            if (result > 0) msg += "  Level $result!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        refreshStats()
    }

    private fun refreshUi() {
        val canDraw = Settings.canDrawOverlays(this)
        val running = PetOverlayService.isRunning
        val name = selectedCharacter().displayName
        permissionButton.visibility = if (canDraw) View.GONE else View.VISIBLE
        toggleButton.isEnabled = canDraw
        toggleButton.text = if (running) "Stop pet" else "Start pet"
        statusText.text = when {
            !canDraw -> "Your pet needs permission to appear over other apps. We never read your screen."
            running -> "$name is on your screen. Go to your home screen and say hi!"
            else -> "Ready. Pick a pet, then tap Start."
        }
    }

    private fun startPet() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_START)
        )
    }

    private fun applyIfRunning() {
        if (PetOverlayService.isRunning) startPet()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun slider(
        maxValue: Int,
        startValue: Int,
        onProgress: (Int) -> Unit,
        onStop: (Int) -> Unit
    ): SeekBar {
        val bar = SeekBar(this)
        bar.max = maxValue
        bar.progress = startValue
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                onProgress(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                onStop(bar.progress)
            }
        })
        return bar
    }
}
