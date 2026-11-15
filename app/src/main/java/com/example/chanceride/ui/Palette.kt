package com.example.chanceride.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

object Palette {
    val background = Color.rgb(246,247,249)
    val ink = Color.rgb(24,25,28)
    val muted = Color.rgb(105,108,114)
    val conversation = Color.rgb(96,65,255)
    val dark = Color.rgb(69,72,80)
    val line = Color.rgb(222,224,230)
    val pale = Color.rgb(235,236,239)
    val pink = Color.rgb(250,233,245)
}
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
        if (stroke != null) setStroke(1, stroke)
    }
fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return java.lang.String.format(java.util.Locale.KOREA, "%02d:%02d", seconds / 60, seconds % 60)
}
