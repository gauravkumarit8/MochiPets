package com.mochipets.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Lets the user pick an installed app and then either
 *  1. add a home-screen shortcut whose icon is the app icon with the selected pet on it, or
 *  2. place a live pet on top of that app's icon on the home screen (a "perch").
 */
class AppPickerActivity : Activity() {

    private class AppItem(val pkg: String, val label: String, val icon: Drawable)

    private class RowHolder(val icon: ImageView, val label: TextView, val note: TextView)

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private var allItems: List<AppItem> = emptyList()
    private var shown: List<AppItem> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val ink = Color.parseColor("#3A2A33")

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = shown.size

        override fun getItem(position: Int): Any = shown[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val holder: RowHolder
            val row: View
            if (convertView == null) {
                val icon = ImageView(this@AppPickerActivity)
                val label = TextView(this@AppPickerActivity).apply {
                    textSize = 16f
                    setTextColor(ink)
                }
                val note = TextView(this@AppPickerActivity).apply {
                    textSize = 12f
                    setTextColor(Color.parseColor("#C2557A"))
                }
                val texts = LinearLayout(this@AppPickerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label)
                    addView(note)
                }
                val r = LinearLayout(this@AppPickerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.leftMargin = dp(14)
                    addView(texts, lp)
                }
                holder = RowHolder(icon, label, note)
                r.tag = holder
                row = r
            } else {
                row = convertView
                holder = convertView.tag as RowHolder
            }
            val item = shown[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.note.text = if (PerchStore.get(prefs, item.pkg) != null) "Live pet placed on this icon" else ""
            holder.note.visibility = if (holder.note.text.isEmpty()) View.GONE else View.VISIBLE
            return row
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.parseColor("#FFF7FA"))
        }
        root.addView(TextView(this).apply {
            text = "Pet icons"
            textSize = 26f
            setTextColor(ink)
        })
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#7A6A73"))
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(statusText)

        val search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    applyFilter(s?.toString() ?: "")
                }
            })
        }
        root.addView(search)

        val listView = ListView(this).apply {
            this.adapter = this@AppPickerActivity.adapter
            setOnItemClickListener { _, _, position, _ -> showActions(shown[position]) }
        }
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = systemBarInsets(insets)
            root.setPadding(pad, bars.first + dp(16), pad, bars.second + pad)
            insets
        }
        setContentView(root)

        statusText.text = "Loading your apps..."
        Thread {
            val items = loadApps()
            handler.post {
                allItems = items
                applyFilter("")
                refreshStatus()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        adapter.notifyDataSetChanged()
    }

    private fun refreshStatus() {
        if (allItems.isEmpty()) return
        val pet = Unlocks.selected(prefs).displayName
        statusText.text = "Tap an app to put $pet on its icon. Change your pet on the main screen."
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

    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (ri in infos) {
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || !seen.add(pkg)) continue
            out.add(AppItem(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)))
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isEmpty()) allItems else allItems.filter { it.label.lowercase().contains(q) }
        adapter.notifyDataSetChanged()
    }

    // ---------- actions ----------

    private fun showActions(item: AppItem) {
        val type = Unlocks.selected(prefs)
        val options = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        options.add("Add an icon shortcut with ${type.displayName} on ${item.label}")
        actions.add { createShortcut(item, type) }

        options.add("Place a live ${type.displayName} on this app's icon")
        actions.add { placeLivePet(item, type) }

        if (PerchStore.get(prefs, item.pkg) != null) {
            options.add("Remove the live pet from this icon")
            actions.add { removeLivePet(item) }
        }

        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(options.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launcherName(): String {
        @Suppress("DEPRECATION")
        val ri = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return ri?.activityInfo?.packageName ?: "unknown"
    }

    private fun createShortcut(item: AppItem, type: CharacterType) {
        val details = "Details: launcher ${launcherName()}, Android ${Build.VERSION.SDK_INT}."
        val sm = getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            showResult(
                "Your launcher can't add shortcuts",
                "Your home screen app does not let other apps add icons to it. " +
                    "Use Place a live pet on this app's icon instead.\n\n$details",
                null
            )
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(item.pkg)
        if (launch == null) {
            showResult("Can't make this shortcut", "${item.label} cannot be opened from a shortcut.", null)
            return
        }
        val bitmap = PetIcon.compose(this, item.icon, type)
        val info = ShortcutInfo.Builder(this, "petico_${item.pkg}_${type.id}")
            .setShortLabel(item.label)
            .setIcon(Icon.createWithBitmap(bitmap))
            .setIntent(launch)
            .build()
        val accepted = sm.requestPinShortcut(info, null)
        if (accepted) {
            showResult(
                "Shortcut requested",
                "This is the icon that will be added. Your home screen app should now ask you to add it. " +
                    "Tap Add (or Add automatically).\n\n" +
                    "If you do not see a prompt within a few seconds, your launcher blocked it. " +
                    "Use Place a live pet on this app's icon instead.\n\n$details",
                bitmap
            )
        } else {
            showResult(
                "Launcher did not accept the shortcut",
                "Use Place a live pet on this app's icon instead.\n\n$details",
                bitmap
            )
        }
    }

    private fun placeLivePet(item: AppItem, type: CharacterType) {
        if (!Settings.canDrawOverlays(this)) {
            showResult(
                "Permission needed",
                "First allow Display over other apps for Mochi Pets. You can do that on the main screen.",
                null
            )
            return
        }
        val perch = Perch(item.pkg, item.pkg, item.label, type, 0.5f, 0.5f)
        if (!PerchStore.put(prefs, perch)) {
            showResult(
                "Too many live pets",
                "You can place up to ${PerchStore.MAX_PERCHES} live pets. Remove one first.",
                null
            )
            return
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_EDIT)
        )
        adapter.notifyDataSetChanged()
        showResult(
            "Live ${type.displayName} placed on ${item.label}",
            "1. Tap Go to home screen.\n" +
                "2. Find the small pet with the dashed pink ring (it starts in the middle of the screen) " +
                "and drag it onto the ${item.label} icon.\n" +
                "3. Pull down the notification shade and tap Lock pets.\n\n" +
                "If you do not see the notification, the pet locks itself after 1 minute.",
            null,
            "Go to home screen"
        ) {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** A clear result message: dialogs are much harder to miss than toasts. */
    private fun showResult(
        title: String,
        message: String,
        preview: Bitmap?,
        extraLabel: String? = null,
        extraAction: (() -> Unit)? = null
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), 0)
        }
        if (preview != null) {
            val lp = LinearLayout.LayoutParams(dp(96), dp(96))
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = dp(10)
            box.addView(ImageView(this).apply { setImageBitmap(preview) }, lp)
        }
        box.addView(TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(ink)
        })
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("OK", null)
        if (extraLabel != null && extraAction != null) {
            builder.setNeutralButton(extraLabel) { _, _ -> extraAction() }
        }
        builder.show()
    }

    private fun removeLivePet(item: AppItem) {
        PerchStore.remove(prefs, item.pkg)
        if (PetOverlayService.isRunning) {
            ContextCompat.startForegroundService(
                this,
                Intent(this, PetOverlayService::class.java).setAction(PetOverlayService.ACTION_START)
            )
        }
        adapter.notifyDataSetChanged()
        toast("Removed.")
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
