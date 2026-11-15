package com.example.chanceride.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Hosts the reference portrait layout while preserving normal Activity recreation. */
abstract class RideActivity : AppCompatActivity() {
    protected lateinit var design: Design
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Keep the supplied portrait design even when the car's host window stays landscape.
        design = Design(this)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
    protected fun present(view: View) {
        val shell = PortraitContentFrame(this).apply { setBackgroundColor(Color.rgb(237,239,243)) }
        view.setBackgroundColor(Palette.background)
        shell.addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_HORIZONTAL))
        ViewCompat.setOnApplyWindowInsetsListener(shell) { target, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            target.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(shell)
        ViewCompat.requestApplyInsets(shell)
    }
}

/**
 * Some AAOS hosts and large-screen Android versions ignore orientation requests.
 * Size the actual content from its measured window, rather than stretching the UI.
 */
private class PortraitContentFrame(context: Context) : FrameLayout(context) {
    private var fit = 1f
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val referenceW = context.dp(840)
        val referenceH = context.dp(1180)
        fit = minOf((w - paddingLeft - paddingRight).toFloat() / referenceW,
            (h - paddingTop - paddingBottom).toFloat() / referenceH).coerceAtLeast(0f)
        for (index in 0 until childCount) getChildAt(index).measure(
            MeasureSpec.makeMeasureSpec(referenceW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(referenceH, MeasureSpec.EXACTLY))
        setMeasuredDimension(w, h)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val x = paddingLeft + ((width - paddingLeft - paddingRight - child.measuredWidth * fit) / 2).toInt()
            val y = paddingTop + ((height - paddingTop - paddingBottom - child.measuredHeight * fit) / 2).toInt()
            child.pivotX = 0f; child.pivotY = 0f
            child.scaleX = fit; child.scaleY = fit
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }
}
