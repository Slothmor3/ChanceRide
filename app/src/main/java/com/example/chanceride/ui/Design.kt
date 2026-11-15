package com.example.chanceride.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class Design(private val context: Context) {
    private val suppliedIcons = mutableMapOf<String, android.graphics.drawable.Drawable?>()
    private fun suppliedIcon(kind: String): android.graphics.drawable.Drawable? {
        val filename = when (kind) {
            "logo" -> "logo.png"
            "arrow" -> "arrow_right.png"
            "wheel" -> "steering_wheel.png"
            "chevron" -> "chevron_right.png"
            "restart" -> "restart.png"
            else -> return null
        }
        if (suppliedIcons.containsKey(filename)) return suppliedIcons[filename]
        val drawable = tryLoadIcon(filename)
        suppliedIcons[filename] = drawable
        return drawable
    }
    private fun tryLoadIcon(filename: String): android.graphics.drawable.Drawable? {
        return run {
            try {
                context.assets.open("design/$filename").use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { bitmap ->
                        android.graphics.drawable.BitmapDrawable(context.resources, bitmap).apply {
                            isFilterBitmap = true
                        }
                    }
                }
            } catch (_: java.io.FileNotFoundException) {
                null // Use the existing vector drawing until the supplied original is added.
            }
        }
    }
    /** Reference app surface: 840 × 1180, excluding the mockup's device frame. */
    fun page(): FrameLayout = FrameLayout(context).apply { setBackgroundColor(Palette.background) }
    fun place(parent: FrameLayout, child: View, x: Int, y: Int, width: Int, height: Int) {
        parent.addView(child, FrameLayout.LayoutParams(if (width < 0) width else context.dp(width),
            if (height < 0) height else context.dp(height)).apply {
            leftMargin = context.dp(x); topMargin = context.dp(y)
        })
    }
    fun label(parent: FrameLayout, value: String, x: Int, y: Int, width: Int, height: Int,
        font: Float, bold: Boolean = false, color: Int = Palette.ink, centered: Boolean = false): TextView =
        text(value, font, bold, color).apply {
            gravity = if (centered) Gravity.CENTER else Gravity.TOP
            place(parent, this, x, y, width, height)
        }
    fun pageHeader(parent: FrameLayout, title: String = "어쩌다 동승", subtitle: String = "",
        color: Int = Palette.dark, icon: String = "logo", exit: (() -> Unit)? = null): TextView {
        place(parent, glyph(icon, color, 48), 24, 54, 48, 48)
        val label = label(parent, title, 84, 62, 600, 40, 24f, true)
        if (subtitle.isNotEmpty()) {
            label.layoutParams.width = context.dp(132)
            label(parent, subtitle, 218, 65, 450, 36, 24f)
        }
        if (exit != null) place(parent, button("나가기", action = exit).apply {
            textSize = 26f; minHeight = 0; minimumHeight = 0
            background = rounded(Palette.pale, context.dp(18))
        }, 699, 46, 115, 64)
        return label
    }
    fun bottomActions(parent: FrameLayout, left: String, right: String,
        onLeft: () -> Unit, onRight: () -> Unit): Pair<Button, Button> {
        val first = footerButton(button(left, action = onLeft))
        val second = footerButton(button(right, true, onRight))
        place(parent, first, 24, 1058, 388, 100)
        place(parent, second, 428, 1058, 388, 100)
        return first to second
    }
    fun tile(radius: Int = 40, color: Int = Color.WHITE): FrameLayout = page().apply {
        background = rounded(color, context.dp(radius), Palette.line)
    }
    fun footerButton(button: Button): Button = button.apply {
        textSize = 32f
        minHeight = context.dp(100)
        background = rounded(if (currentTextColor == Color.WHITE) Palette.dark else Palette.pale, context.dp(32))
    }
    fun column(gap: Int = 0): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (gap > 0) setPadding(context.dp(gap), context.dp(gap), context.dp(gap), context.dp(gap))
    }
    fun text(value: String, size: Float = 22f, bold: Boolean = false, color: Int = Palette.ink): TextView =
        TextView(context).apply {
            text = value; textSize = size; setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    fun button(value: String, primary: Boolean = false, action: () -> Unit): Button =
        androidx.appcompat.widget.AppCompatButton(context).apply {
            text = value; textSize = 28f; isAllCaps = false
            setTextColor(if (primary) Color.WHITE else Palette.ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = rounded(if (primary) Palette.dark else Palette.pale, context.dp(18))
            backgroundTintList = null
            minHeight = context.dp(64)
            setPadding(context.dp(18), context.dp(12), context.dp(18), context.dp(12))
            setOnClickListener { action() }
        }
    fun space(parent: LinearLayout, height: Int = 20) { parent.addView(View(context), LinearLayout.LayoutParams(1, context.dp(height))) }
    fun add(parent: LinearLayout, child: View, height: Int = -2, weight: Float = 0f) {
        parent.addView(child, LinearLayout.LayoutParams(-1, if (height >= 0) context.dp(height) else height, weight))
    }
    fun clickable(view: View, description: String, action: () -> Unit) {
        view.isClickable = true; view.isFocusable = true; view.contentDescription = description
        view.setOnClickListener { action() }
    }
    fun glyph(kind: String, color: Int, size: Int): View {
        val source = suppliedIcon(kind)
        val original = source?.constantState?.newDrawable(context.resources)?.mutate() ?: source
        val view = if (original != null) ImageView(context).apply {
            setImageDrawable(original)
            scaleType = ImageView.ScaleType.FIT_CENTER
            // These two artwork bounds sit inside larger reference layout slots.
            val inset = when (kind) {
                "wheel" -> (size * .31f).toInt()
                "restart" -> (size * .125f).toInt()
                else -> 0
            }
            setPadding(context.dp(inset), context.dp(inset), context.dp(inset), context.dp(inset))
            if (kind == "wheel" && color == Color.WHITE) setColorFilter(Palette.muted)
        } else ThemeGlyph(context, kind, color)
        return view.apply {
            layoutParams = LinearLayout.LayoutParams(context.dp(size), context.dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    fun progress(value: Int, max: Int, color: Int): ProgressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        this.max = max; progress = value
        val track = rounded(Palette.pale, context.dp(6))
        val fill = android.graphics.drawable.ClipDrawable(rounded(color, context.dp(6)),
            Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
        progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        setPadding(0, 0, 0, 0)
        minimumHeight = 0
    }
}
