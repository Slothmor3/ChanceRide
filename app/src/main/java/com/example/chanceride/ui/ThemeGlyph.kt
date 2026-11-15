package com.example.chanceride.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Fallback artwork for theme icons and optional PNG assets. */
class ThemeGlyph(context: Context, private val kind: String, private val tile: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 100f, height / 100f)
        paint.color = tile; paint.style = Paint.Style.FILL
        if (kind !in setOf("brand", "restart", "wheel", "arrow", "chevron")) canvas.drawRoundRect(0f,0f,100f,100f,26f,26f,paint)
        paint.color = if (kind == "wheel" && tile == Color.WHITE) Palette.muted else Color.WHITE
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        when (kind) {
            "chat" -> {
                canvas.drawRoundRect(43f,25f,77f,50f,5f,5f,paint)
                canvas.drawPath(Path().apply { moveTo(66f,50f); lineTo(77f,61f); lineTo(77f,47f) },paint)
                canvas.drawPath(Path().apply {
                    moveTo(34f,43f); lineTo(29f,43f); quadTo(25f,43f,25f,48f)
                    lineTo(25f,78f); lineTo(38f,66f); lineTo(55f,66f)
                    quadTo(60f,66f,60f,61f); lineTo(60f,59f)
                },paint)
            }
            "brand" -> {
                paint.style = Paint.Style.FILL; paint.color = Color.rgb(231,225,255)
                canvas.drawRoundRect(5f,14f,59f,54f,10f,10f,paint)
                canvas.drawPath(Path().apply { moveTo(17f,50f); lineTo(17f,66f); lineTo(38f,50f); close() },paint)
                paint.color = Color.rgb(255,122,86)
                canvas.drawRoundRect(35f,33f,89f,73f,10f,10f,paint)
                canvas.drawPath(Path().apply { moveTo(57f,70f); lineTo(77f,86f); lineTo(77f,70f); close() },paint)
            }
            "arrow" -> {
                paint.strokeWidth = 10f
                canvas.drawLine(10f,50f,80f,50f,paint)
                canvas.drawPath(Path().apply { moveTo(48f,16f); lineTo(82f,50f); lineTo(48f,84f) },paint)
            }
            "chevron" -> {
                paint.color = Color.rgb(55,57,59)
                paint.strokeWidth = 12f
                canvas.drawPath(Path().apply { moveTo(18f,8f); lineTo(82f,50f); lineTo(18f,92f) },paint)
            }
            "wheel" -> {
                canvas.drawCircle(50f,50f,19f,paint)
                canvas.drawCircle(50f,50f,5f,paint)
                canvas.drawLine(31f,50f,45f,50f,paint)
                canvas.drawLine(55f,50f,69f,50f,paint)
                canvas.drawLine(50f,55f,50f,69f,paint)
            }
            "restart" -> {
                paint.color = Palette.muted
                canvas.drawArc(23f,24f,79f,80f,225f,310f,false,paint)
                canvas.drawLine(23f,22f,23f,40f,paint)
                canvas.drawLine(23f,40f,42f,40f,paint)
            }
            "heart" -> {
                // Interlocked heart: outer contour and clasped hands from the reference.
                canvas.drawPath(Path().apply {
                    moveTo(50f,35f)
                    cubicTo(37f,21f,21f,30f,26f,45f)
                    cubicTo(28f,52f,39f,63f,47f,70f)
                    quadTo(51f,74f,55f,69f)
                    quadTo(58f,66f,54f,62f)
                    quadTo(61f,67f,64f,61f)
                    quadTo(67f,57f,61f,53f)
                    quadTo(67f,58f,71f,52f)
                    cubicTo(84f,37f,69f,22f,57f,33f)
                    lineTo(45f,44f)
                    quadTo(41f,48f,45f,51f)
                    quadTo(49f,54f,53f,50f)
                    lineTo(58f,46f)
                    quadTo(62f,42f,66f,46f)
                    lineTo(72f,51f)
                }, paint)
            }
            "spark" -> {
                val path = Path().apply {
                    moveTo(50f,22f); lineTo(57f,43f); lineTo(78f,50f); lineTo(57f,57f)
                    lineTo(50f,78f); lineTo(43f,57f); lineTo(22f,50f); lineTo(43f,43f); close()
                }; canvas.drawPath(path,paint)
                canvas.drawCircle(69f,73f,5f,paint)
                canvas.drawLine(31f,25f,31f,35f,paint)
                canvas.drawLine(26f,30f,36f,30f,paint)
            }
            "loop" -> {
                canvas.drawArc(25f,25f,77f,77f,0f,300f,false,paint)
                canvas.drawLine(74f,24f,74f,40f,paint); canvas.drawLine(58f,40f,74f,40f,paint)
            }
            "seat" -> {
                canvas.drawCircle(50f,35f,9f,paint); canvas.drawLine(50f,47f,50f,65f,paint)
                canvas.drawLine(34f,66f,66f,66f,paint); canvas.drawLine(34f,66f,34f,78f,paint)
                canvas.drawLine(66f,66f,66f,78f,paint)
            }
            else -> {
                paint.style = Paint.Style.FILL; paint.color = Color.rgb(255,122,86)
                canvas.drawRoundRect(21f,26f,58f,53f,6f,6f,paint)
                val tail = Path().apply { moveTo(28f,51f); lineTo(28f,62f); lineTo(43f,51f); close() }
                canvas.drawPath(tail,paint)
                paint.color = Color.rgb(80,211,178)
                canvas.drawRoundRect(42f,40f,79f,67f,6f,6f,paint)
                canvas.drawPath(Path().apply { moveTo(58f,66f); lineTo(71f,75f); lineTo(71f,65f); close() },paint)
            }
        }
        canvas.restore()
    }
}
