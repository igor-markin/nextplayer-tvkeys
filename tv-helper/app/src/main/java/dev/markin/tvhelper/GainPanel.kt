package dev.markin.tvhelper

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Small non-focusable panel in the bottom-right corner. It never takes input focus,
 * so the app underneath keeps playing; the service routes remote keys to it instead.
 */
class GainPanel(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val gainText = text(sizeSp = 34f)
    private val limiterText = text(sizeSp = 18f)
    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(16)
        setPadding(padding, padding, padding, padding)
        background = GradientDrawable().apply {
            setColor(0xE0202020.toInt())
            cornerRadius = dp(12).toFloat()
        }
        addView(text(sizeSp = 16f).apply { setText(R.string.panel_title) })
        addView(gainText)
        addView(limiterText)
        addView(text(sizeSp = 13f).apply { setText(R.string.panel_hint) })
    }

    var isShown = false
        private set

    fun show(gainDb: Int, limiter: Boolean) {
        render(gainDb, limiter)
        if (isShown) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(32)
            y = dp(32)
        }
        windowManager.addView(root, params)
        isShown = true
    }

    fun render(gainDb: Int, limiter: Boolean) {
        gainText.text = context.getString(R.string.panel_gain, gainDb)
        limiterText.setText(if (limiter) R.string.panel_limiter_on else R.string.panel_limiter_off)
    }

    fun hide() {
        if (!isShown) return
        windowManager.removeView(root)
        isShown = false
    }

    private fun text(sizeSp: Float) = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}
