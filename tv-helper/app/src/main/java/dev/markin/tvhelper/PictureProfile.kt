package dev.markin.tvhelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Switches the picture mode to Custom by driving the TV picture settings screen,
 * because the picture parameters can only be changed by the system settings app.
 * Custom keeps its own values, so nothing else is touched and the menu closes
 * right away.
 */
class PictureProfile(
    private val service: AccessibilityService,
    private val onDone: (Result) -> Unit,
) {

    data class Result(val changed: List<String>, val problems: List<String>)

    private class Step(val name: String, val attempt: (AccessibilityNodeInfo) -> Boolean)

    private val handler = Handler(Looper.getMainLooper())
    private val steps = ArrayDeque<Step>()
    private val changed = mutableListOf<String>()
    private val problems = mutableListOf<String>()
    private var current: Step? = null
    private var deadline = 0L
    private var lastActionAt = 0L

    val isRunning get() = current != null

    fun start() {
        if (isRunning) return
        changed.clear()
        problems.clear()
        steps.clear()
        steps += Step(PICTURE) { root ->
            val title = title(root)
            // A list left open on top of the Picture screen is closed first.
            if (title != null && title != PICTURE && settled()) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            title == PICTURE
        }
        steps += ensureValue(PICTURE, "Picture Mode", "Custom")
        steps += Step("Close") { root ->
            val inSettings = root.packageName == SETTINGS_PACKAGE
            if (inSettings && settled()) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            !inSettings
        }

        val intent = Intent(PICTURE_SETTINGS_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { service.startActivity(intent) }
            .onFailure {
                onDone(Result(emptyList(), listOf("Picture")))
                return
            }
        next()
    }

    /** Opens the row's list and picks the value, unless the row already shows it. */
    private fun ensureValue(screen: String, row: String, value: String) = Step(row) { root ->
        if (title(root) != screen) return@Step false
        val summary = rowSummary(root, row) ?: return@Step false
        if (summary == value) return@Step true
        if (!settled() || !click(rowNode(root, row))) return@Step false
        steps.addFirst(Step("$row → $value") { list ->
            title(list) == row && settled() && click(findByExactText(list, value))
        })
        // Choosing Custom opens Equalizer Detail, so only leaving the list is awaited.
        steps.add(1, Step(screen) { next -> title(next) != row })
        changed += "$row: $summary → $value"
        true
    }


    private fun next() {
        val step = steps.removeFirstOrNull()
        current = step
        if (step == null) {
            Log.i(TAG, "Profile done, changed=$changed, problems=$problems")
            onDone(Result(changed.toList(), problems.toList()))
            return
        }
        deadline = SystemClock.uptimeMillis() + STEP_TIMEOUT_MS
        tick()
    }

    private fun tick() {
        val step = current ?: return
        val root = service.rootInActiveWindow
        if (root != null && step.attempt(root)) {
            next()
            return
        }
        if (SystemClock.uptimeMillis() > deadline) {
            Log.e(TAG, "Profile step failed: ${step.name}")
            problems += step.name
            current = null
            steps.clear()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            onDone(Result(changed.toList(), problems.toList()))
            return
        }
        handler.postDelayed(::tick, POLL_MS)
    }

    /** Lets a screen transition finish before the next click or Back. */
    private fun settled(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionAt < SETTLE_MS) return false
        lastActionAt = now
        return true
    }

    private fun title(root: AccessibilityNodeInfo) = findById(root, TITLE_ID)?.text?.toString()

    private fun findById(root: AccessibilityNodeInfo, id: String) =
        root.findAccessibilityNodeInfosByViewId(id).firstOrNull()

    private fun findByExactText(root: AccessibilityNodeInfo, text: String) =
        root.findAccessibilityNodeInfosByText(text).firstOrNull { it.text?.toString() == text }

    private fun rowNode(root: AccessibilityNodeInfo, row: String): AccessibilityNodeInfo? {
        var node = findByExactText(root, row)
        while (node != null && !node.isClickable) node = node.parent
        return node
    }

    private fun rowSummary(root: AccessibilityNodeInfo, row: String): String? {
        val title = findByExactText(root, row) ?: return null
        return title.parent?.findAccessibilityNodeInfosByViewId(SUMMARY_ID)
            ?.firstOrNull()?.text?.toString()
    }

    private fun click(node: AccessibilityNodeInfo?): Boolean {
        var target = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private companion object {
        const val TAG = "TvHelper"
        const val POLL_MS = 100L
        const val STEP_TIMEOUT_MS = 3_000L
        const val SETTLE_MS = 400L
        const val PICTURE = "Picture"
        const val SETTINGS_PACKAGE = "com.android.tv.settings"
        const val PICTURE_SETTINGS_ACTION = "com.mitv.settings.PICTURE_SETTINGS"
        const val TITLE_ID = "com.android.tv.settings:id/decor_title"
        const val SUMMARY_ID = "android:id/summary"
    }
}
