package dev.markin.tvhelper

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Logs the window tree so the settings screens can be inspected over adb. */
object NodeDump {
    private const val TAG = "TvHelperTree"

    fun log(activeRoot: AccessibilityNodeInfo?, windows: List<AccessibilityWindowInfo>) {
        Log.i(TAG, "Active root: ${activeRoot?.packageName}")
        activeRoot?.let { log(it, depth = 1) }
        for (window in windows) {
            Log.i(TAG, "Window type=${window.type} active=${window.isActive} focused=${window.isFocused} title=${window.title}")
            window.root?.let { log(it, depth = 1) }
        }
    }

    private fun log(node: AccessibilityNodeInfo, depth: Int) {
        val actions = node.actionList.joinToString(",") { it.id.toString() }
        val range = node.rangeInfo?.let { " range=${it.min}..${it.max}:${it.current}" }.orEmpty()
        Log.i(
            TAG,
            "${" ".repeat(depth)}${node.className} id=${node.viewIdResourceName} text=${node.text} " +
                "click=${node.isClickable} focus=${node.isFocused} actions=$actions$range",
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { log(it, depth + 1) }
        }
    }
}
