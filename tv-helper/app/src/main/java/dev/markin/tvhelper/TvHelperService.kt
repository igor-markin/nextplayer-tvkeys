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

/**
 * Opens the TV picture settings on a long press of the watched remote key and holds
 * the global volume boost. Both are controlled over adb broadcasts, which require the
 * DUMP permission that only the shell and the system hold.
 */
class TvHelperService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val tracker = LongPressTracker(LONG_PRESS_MS)
    private val gain = GlobalGain()
    private val openPictureOnTimeout = Runnable { if (tracker.onTimeout()) openPictureSettings() }

    private var watchedKeyCode = KeyEvent.KEYCODE_MENU
    private var logKeys = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SET_GAIN -> setGain(intent)
                ACTION_SET_KEY -> setWatchedKey(intent.getIntExtra(EXTRA_KEYCODE, watchedKeyCode))
                ACTION_LOG_KEYS -> logKeys = intent.getBooleanExtra(EXTRA_ENABLED, true)
            }
        }
    }

    override fun onServiceConnected() {
        watchedKeyCode = prefs().getInt(PREF_KEYCODE, KeyEvent.KEYCODE_MENU)
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_GAIN)
            addAction(ACTION_SET_KEY)
            addAction(ACTION_LOG_KEYS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Manifest.permission.DUMP, null, RECEIVER_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter, Manifest.permission.DUMP, null)
        }
        Log.i(TAG, "Connected, watching keycode $watchedKeyCode")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (logKeys) Log.i(TAG, "Key ${event.keyCode} action=${event.action} repeat=${event.repeatCount}")
        if (event.keyCode != watchedKeyCode) return false

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleDown(event)
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(openPictureOnTimeout)
                tracker.onUp()
            }
            else -> false
        }
    }

    private fun handleDown(event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            handler.removeCallbacks(openPictureOnTimeout)
            handler.postDelayed(openPictureOnTimeout, LONG_PRESS_MS)
        }
        return when (tracker.onDown(event.repeatCount, event.downTime, event.eventTime)) {
            LongPressTracker.Action.PASS -> false
            LongPressTracker.Action.CONSUME -> true
            LongPressTracker.Action.FIRE -> {
                handler.removeCallbacks(openPictureOnTimeout)
                openPictureSettings()
                true
            }
        }
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

    private fun setGain(intent: Intent) {
        val mode = when (intent.getStringExtra(EXTRA_MODE)) {
            "le" -> GlobalGain.Mode.LOUDNESS
            else -> GlobalGain.Mode.DYNAMICS
        }
        val limiter = intent.getBooleanExtra(EXTRA_LIMITER, true)
        runCatching { gain.apply(intent.getIntExtra(EXTRA_DB, 0), mode, limiter) }
            .onFailure { Log.e(TAG, "Cannot apply gain", it) }
    }

    private fun setWatchedKey(keyCode: Int) {
        watchedKeyCode = keyCode
        prefs().edit().putInt(PREF_KEYCODE, keyCode).apply()
        Log.i(TAG, "Watching keycode $keyCode")
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(openPictureOnTimeout)
        runCatching { unregisterReceiver(commandReceiver) }
        gain.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TvHelper"
        const val LONG_PRESS_MS = 600L
        const val PREFS_NAME = "tv_helper"
        const val PREF_KEYCODE = "keycode"
        const val PICTURE_SETTINGS_ACTION = "com.mitv.settings.PICTURE_SETTINGS"
        const val TV_SETTINGS_PACKAGE = "com.android.tv.settings"
        const val PICTURE_ACTIVITY = "com.android.tv.settings.partnercustomizer.picture.PictureActivity"
        const val ACTION_SET_GAIN = "dev.markin.tvhelper.SET_GAIN"
        const val ACTION_SET_KEY = "dev.markin.tvhelper.SET_KEY"
        const val ACTION_LOG_KEYS = "dev.markin.tvhelper.LOG_KEYS"
        const val EXTRA_DB = "db"
        const val EXTRA_MODE = "mode"
        const val EXTRA_LIMITER = "limiter"
        const val EXTRA_KEYCODE = "keycode"
        const val EXTRA_ENABLED = "enabled"
    }
}
