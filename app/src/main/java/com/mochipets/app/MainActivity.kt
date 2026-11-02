package com.mochipets.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val pad = dp(20)
        val ink = Color.parseColor("#3A2A33")
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
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(statusText)

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
            text = "Tip: tap Mochi for a reaction, drag and throw her. She naps at night and when the battery is low."
            setTextColor(Color.parseColor("#7A6A73"))
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val canDraw = Settings.canDrawOverlays(this)
        val running = PetOverlayService.isRunning
        permissionButton.visibility = if (canDraw) View.GONE else View.VISIBLE
        toggleButton.isEnabled = canDraw
        toggleButton.text = if (running) "Stop Mochi" else "Start Mochi"
        statusText.text = when {
            !canDraw -> "Mochi needs permission to appear over other apps. We never read your screen."
            running -> "Mochi is on your screen. Go to your home screen and say hi!"
            else -> "Ready. Tap Start to bring Mochi to your screen."
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
