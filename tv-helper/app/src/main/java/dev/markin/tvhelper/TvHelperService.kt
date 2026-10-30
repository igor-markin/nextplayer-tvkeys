package dev.markin.tvhelper

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Remote shortcuts and the global volume boost.
 *
 * A long press of SETTINGS opens the TV picture settings. A long press of TTX shows
 * the boost panel; while it is shown, the D-pad, OK and Back keys control the panel
 * and never reach the app underneath. The boost level and limiter state survive
 * restarts. Adb broadcasts, which require the DUMP permission that only the shell
 * and the system hold, can set the boost and turn on key logging.
 */
class TvHelperService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val gain = GlobalGain()
    private lateinit var panel: GainPanel
    private lateinit var profile: PictureProfile

    private val longPressActions = mapOf<Int, () -> Unit>(
        KeyEvent.KEYCODE_SETTINGS to { openPictureSettings() },
        KeyEvent.KEYCODE_TV_TELETEXT to { showPanel() },
        CHANNEL_LIST_KEYCODE to { applyProfile() },
    )
    private val trackers = longPressActions.mapValues { LongPressTracker(LONG_PRESS_MS) }
    private val timeouts = longPressActions.mapValues { (keyCode, action) ->
        Runnable { if (trackers.getValue(keyCode).onTimeout()) action() }
    }
    private val hidePanel = Runnable { closePanel() }

    private var gainDb = 0
    private var limiter = true
    private var logKeys = false
    private var closeOnTeletextUp = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SET_GAIN -> setGain(
                    intent.getIntExtra(EXTRA_DB, 0),
                    intent.getBooleanExtra(EXTRA_LIMITER, true),
                    if (intent.getStringExtra(EXTRA_MODE) == "le") GlobalGain.Mode.LOUDNESS else GlobalGain.Mode.DYNAMICS,
                )
                ACTION_LOG_KEYS -> logKeys = intent.getBooleanExtra(EXTRA_ENABLED, true)
                ACTION_DUMP_TREE -> NodeDump.log(rootInActiveWindow, windows)
                ACTION_APPLY_PROFILE -> applyProfile()
            }
        }
    }

    override fun onServiceConnected() {
        panel = GainPanel(this)
        profile = PictureProfile(this, ::reportProfile)
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_GAIN)
            addAction(ACTION_LOG_KEYS)
            addAction(ACTION_DUMP_TREE)
            addAction(ACTION_APPLY_PROFILE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Manifest.permission.DUMP, null, RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter, Manifest.permission.DUMP, null)
        }
        val prefs = prefs()
        setGain(prefs.getInt(PREF_GAIN_DB, 0), prefs.getBoolean(PREF_LIMITER, true))
        Log.i(TAG, "Connected, restored +$gainDb dB, limiter=$limiter")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (logKeys) Log.i(TAG, "Key ${event.keyCode} action=${event.action} repeat=${event.repeatCount}")
        if (panel.isShown && handlePanelKey(event)) return true

        val tracker = trackers[event.keyCode] ?: return false
        val timeout = timeouts.getValue(event.keyCode)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    handler.removeCallbacks(timeout)
                    handler.postDelayed(timeout, LONG_PRESS_MS)
                }
                when (tracker.onDown(event.repeatCount, event.downTime, event.eventTime)) {
                    LongPressTracker.Action.PASS -> false
                    LongPressTracker.Action.CONSUME -> true
                    LongPressTracker.Action.FIRE -> {
                        handler.removeCallbacks(timeout)
                        longPressActions.getValue(event.keyCode)()
                        true
                    }
                }
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(timeout)
                tracker.onUp()
            }
            else -> false
        }
    }

    /** Returns true when the key belongs to the panel and must not reach the app. */
    private fun handlePanelKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) setGain(gainDb - GainLevels.STEP_DB, limiter)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) setGain(gainDb + GainLevels.STEP_DB, limiter)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_UP,
            -> if (down && event.repeatCount == 0) setGain(gainDb, !limiter)
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!down) applyProfile()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down) closePanel()
                return true
            }
            KeyEvent.KEYCODE_TV_TELETEXT -> {
                // The release of the press that opened the panel must not close it.
                if (down && event.repeatCount == 0) closeOnTeletextUp = true
                if (!down && closeOnTeletextUp) closePanel()
                return closeOnTeletextUp
            }
            else -> return false
        }
        if (panel.isShown) {
            panel.render(gainDb, limiter)
            scheduleHide()
        }
        return true
    }

    private fun applyProfile() {
        if (::panel.isInitialized) closePanel()
        profile.start()
    }

    private fun reportProfile(result: PictureProfile.Result) {
        val message = when {
            result.problems.isNotEmpty() -> getString(R.string.profile_problems, result.problems.joinToString())
            result.changed.isEmpty() -> getString(R.string.profile_already)
            else -> getString(R.string.profile_applied, result.changed.joinToString())
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showPanel() {
        panel.show(gainDb, limiter)
        scheduleHide()
    }

    private fun closePanel() {
        closeOnTeletextUp = false
        handler.removeCallbacks(hidePanel)
        panel.hide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, PANEL_TIMEOUT_MS)
    }

    private fun openPictureSettings() {
        val byAction = Intent(PICTURE_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val byComponent = Intent()
            .setComponent(ComponentName(TV_SETTINGS_PACKAGE, PICTURE_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val intent = if (byAction.resolveActivity(packageManager) != null) byAction else byComponent
        runCatching { startActivity(intent) }
            .onSuccess { Log.i(TAG, "Opened picture settings") }
            .onFailure { Log.e(TAG, "Cannot open picture settings", it) }
    }

    private fun setGain(db: Int, limiter: Boolean, mode: GlobalGain.Mode = GlobalGain.Mode.DYNAMICS) {
        runCatching { gain.apply(db, mode, limiter) }
            .onFailure { Log.e(TAG, "Cannot apply gain", it) }
        gainDb = gain.gainDb
        this.limiter = limiter
        prefs().edit()
            .putInt(PREF_GAIN_DB, gainDb)
            .putBoolean(PREF_LIMITER, limiter)
            .apply()
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(commandReceiver) }
        if (::panel.isInitialized) panel.hide()
        gain.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TvHelper"
        const val LONG_PRESS_MS = 600L

        // CH LIST on the Xiaomi remote; -1 matches no key until the real code is captured.
        const val CHANNEL_LIST_KEYCODE = -1
        const val PANEL_TIMEOUT_MS = 5_000L
        const val PREFS_NAME = "tv_helper"
        const val PREF_GAIN_DB = "gain_db"
        const val PREF_LIMITER = "limiter"
        const val PICTURE_SETTINGS_ACTION = "com.mitv.settings.PICTURE_SETTINGS"
        const val TV_SETTINGS_PACKAGE = "com.android.tv.settings"
        const val PICTURE_ACTIVITY = "com.android.tv.settings.partnercustomizer.picture.PictureActivity"
        const val ACTION_SET_GAIN = "dev.markin.tvhelper.SET_GAIN"
        const val ACTION_LOG_KEYS = "dev.markin.tvhelper.LOG_KEYS"
        const val ACTION_DUMP_TREE = "dev.markin.tvhelper.DUMP_TREE"
        const val ACTION_APPLY_PROFILE = "dev.markin.tvhelper.APPLY_PROFILE"
        const val EXTRA_DB = "db"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LIMITER = "limiter"
        const val EXTRA_ENABLED = "enabled"
    }
}
