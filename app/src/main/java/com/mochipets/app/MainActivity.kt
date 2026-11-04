package com.mochipets.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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
import android.view.WindowInsets
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

    private class Preview(
        val type: CharacterType,
        val view: PetView,
        val card: LinearLayout,
        val label: TextView
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var ads: AdsManager
    private lateinit var statusText: TextView
    private lateinit var taglineText: TextView
    private lateinit var permissionButton: Button
    private lateinit var toggleButton: Button
    private lateinit var privacyButton: Button
    private lateinit var perchText: TextView
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
        ads = AdsManager(this)
        ads.onStateChanged = {
            refreshPicker()
            refreshUi()
        }

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
                setOnClickListener { onPetCardClicked(type) }
            }
            card.addView(petView, LinearLayout.LayoutParams(dp(84), dp(84)))
            card.addView(TextView(this).apply {
                text = type.displayName
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ink)
            })
            val label = TextView(this).apply {
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#7A6A73"))
            }
            card.addView(label)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(4), 0, dp(4), 0)
            row.addView(card, lp)
            previews.add(Preview(type, petView, card, label))
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

        // ---- pets on app icons ----
        root.addView(Button(this).apply {
            text = "Pet icons for my apps"
            setOnClickListener { startActivity(Intent(this@MainActivity, AppPickerActivity::class.java)) }
        })
        perchText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#7A6A73"))
            setPadding(0, dp(2), 0, dp(4))
        }
        root.addView(perchText)
        root.addView(CheckBox(this).apply {
            text = "Pet walks around the screen"
            setTextColor(ink)
            isChecked = prefs.getBoolean(Prefs.KEY_ROAM, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.KEY_ROAM, checked).apply()
                applyIfRunning()
            }
        })

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

        privacyButton = Button(this).apply {
            text = "Privacy and ad settings"
            visibility = View.GONE
            setOnClickListener { ads.showPrivacyOptions() }
        }
        root.addView(privacyButton)

        root.addView(TextView(this).apply {
            text = "Tip: tap your pet for a reaction, drag and throw it. Pets nap at night and when the battery is low. " +
                "Bholu and Jelly unlock by watching short ads or by raising Mochi's level."
            setTextColor(Color.parseColor("#7A6A73"))
            setPadding(0, dp(24), 0, 0)
        })

        root.addView(Button(this).apply {
            text = "Pet not showing? Get help"
            setOnClickListener { showHelp() }
        })
        root.addView(TextView(this).apply {
            text = "Mochi Pets version " + appVersion()
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#A89AA2"))
            setPadding(0, dp(12), 0, 0)
        })

        // Android 15+ draws apps edge to edge: keep the content clear of the system bars.
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(root)
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = systemBarInsets(insets)
            root.setPadding(pad, bars.first + dp(16), pad, bars.second + pad)
            insets
        }
        setContentView(scroll)
        refreshPicker()

        ads.start()
        if (!prefs.getBoolean(Prefs.KEY_WELCOME_SEEN, false)) showWelcome()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        refreshPicker()
        refreshStats()
        ads.loadRewarded()
        handler.removeCallbacks(previewTick)
        handler.post(previewTick)
    }

    override fun onPause() {
        handler.removeCallbacks(previewTick)
        super.onPause()
    }

    private fun appVersion(): String {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    /** Shown once, the first time the app is opened. */
    private fun showWelcome() {
        AlertDialog.Builder(this)
            .setTitle("Welcome to Mochi Pets")
            .setMessage(
                "Your pet lives on top of your screen.\n\n" +
                    "1. Allow Display over other apps, so your pet can appear.\n" +
                    "2. Pick a pet and tap Start pet.\n\n" +
                    "We never read, record or capture your screen. " +
                    "Ads are shown only inside this app, and only when you choose to watch one to unlock a pet."
            )
            .setPositiveButton("Got it", null)
            .setOnDismissListener { prefs.edit().putBoolean(Prefs.KEY_WELCOME_SEEN, true).apply() }
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Pet not showing?")
            .setMessage(
                "- Make sure Display over other apps is allowed for Mochi Pets.\n\n" +
                    "- Allow notifications, so you can see the notification with the Stop button.\n\n" +
                    "- If the pet disappears after a while (common on Samsung, Xiaomi, Oppo, Vivo and Realme phones), " +
                    "open App settings, then Battery, and choose Unrestricted. Also lock the app in the recent apps screen.\n\n" +
                    "- If the permission switch is greyed out: open App settings, tap the three-dot menu and choose " +
                    "Allow restricted settings.\n\n" +
                    "- Still stuck? Tap Stop pet, then Start pet again."
            )
            .setPositiveButton("Open app settings") { _: DialogInterface, _: Int ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
            .setNegativeButton("Close", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun systemBarInsets(insets: WindowInsets): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            Pair(bars.top, bars.bottom)
        } else {
            Pair(insets.systemWindowInsetTop, insets.systemWindowInsetBottom)
        }
    }

    // ---------- pets: select, lock, unlock ----------

    private fun selectedCharacter(): CharacterType {
        val saved = CharacterType.fromId(prefs.getString(Prefs.KEY_CHARACTER, null))
        return if (Unlocks.isUnlocked(prefs, saved)) saved else CharacterType.MOCHI
    }

    private fun onPetCardClicked(type: CharacterType) {
        if (Unlocks.isUnlocked(prefs, type)) {
            selectCharacter(type)
        } else {
            showUnlockDialog(type)
        }
    }

    private fun selectCharacter(type: CharacterType) {
        prefs.edit().putString(Prefs.KEY_CHARACTER, type.id).apply()
        refreshPicker()
        refreshUi()
        refreshStats()
        applyIfRunning()
    }

    private fun showUnlockDialog(type: CharacterType) {
        val watched = Unlocks.adsWatched(prefs, type)
        val mochiLevel = PetStats.level(prefs, CharacterType.MOCHI.id)
        val message = "Watch ${type.adsToUnlock} short ads ($watched/${type.adsToUnlock} done), " +
            "or raise Mochi to level ${type.unlockLevel} (Mochi is level $mochiLevel now)."
        AlertDialog.Builder(this)
            .setTitle("Unlock ${type.displayName}")
            .setMessage(message)
            .setPositiveButton(if (ads.isReady) "Watch ad" else "Load ad") { _, _ -> watchAdFor(type) }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun watchAdFor(type: CharacterType) {
        if (!ads.isReady) {
            ads.loadRewarded()
            Toast.makeText(this, "The ad is loading. Please try again in a few seconds.", Toast.LENGTH_SHORT).show()
            return
        }
        ads.showRewarded(
            onReward = {
                val unlocked = Unlocks.recordAdWatched(prefs, type)
                if (unlocked) {
                    Toast.makeText(this, "${type.displayName} unlocked!", Toast.LENGTH_LONG).show()
                    selectCharacter(type)
                } else {
                    val left = type.adsToUnlock - Unlocks.adsWatched(prefs, type)
                    Toast.makeText(this, "Thanks! $left more to unlock ${type.displayName}.", Toast.LENGTH_SHORT).show()
                }
                refreshPicker()
            },
            onFailed = {
                Toast.makeText(this, "Could not show an ad right now. Please try again later.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun refreshPicker() {
        val selected = selectedCharacter()
        for (p in previews) {
            val isSelected = p.type == selected
            val unlocked = Unlocks.isUnlocked(prefs, p.type)
            p.card.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (isSelected) Color.parseColor("#FFE3EC") else Color.TRANSPARENT)
                setStroke(dp(2), if (isSelected) pink else Color.TRANSPARENT)
            }
            p.view.alpha = if (unlocked) 1f else 0.4f
            p.label.text = when {
                unlocked && p.type.adsToUnlock == 0 -> "Free"
                unlocked -> "Unlocked"
                else -> "Locked: ${Unlocks.adsWatched(prefs, p.type)}/${p.type.adsToUnlock} ads"
            }
        }
        taglineText.text = selected.tagline
    }

    // ---------- stats ----------

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
        refreshPicker()
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
        refreshPicker()
    }

    private fun refreshUi() {
        val canDraw = Settings.canDrawOverlays(this)
        val running = PetOverlayService.isRunning
        val name = selectedCharacter().displayName
        permissionButton.visibility = if (canDraw) View.GONE else View.VISIBLE
        toggleButton.isEnabled = canDraw
        toggleButton.text = if (running) "Stop pet" else "Start pet"
        privacyButton.visibility = if (ads.privacyOptionsRequired) View.VISIBLE else View.GONE
        val perchCount = PerchStore.all(prefs).size
        perchText.text = if (perchCount == 0) {
            "No pets on app icons yet."
        } else {
            "$perchCount live pet(s) on app icons. Use Pet icons for my apps to change them."
        }
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
