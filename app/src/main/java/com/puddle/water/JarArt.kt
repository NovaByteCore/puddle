package com.puddle.water

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.sin

/** Draws the jar with the active critter into a bitmap, for the home-screen widget. */
fun jarBitmap(critterId: String, level: Float, dark: Boolean, w: Int = 220, h: Int = 260): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp.asImageBitmap())
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
        drawJarScene(critterId, level.coerceIn(0f, 1f), dark)
    }
    return bmp
}

private fun DrawScope.drawJarScene(critterId: String, level: Float, dark: Boolean) {
    val glass = if (dark) Color(0xFF1A4148) else Color(0xFFDFF1EE)
    val outline = if (dark) Color(0xFFE3F5F3) else Color(0xFF123A3F)
    val water = Color(0xFF4FC3F7)
    val lid = Color(0xFF3F8F45)

    val w = size.width
    val h = size.height
    val stroke = w * 0.03f
    val left = w * 0.1f
    val right = w * 0.9f
    val top = h * 0.15f
    val bottom = h * 0.97f
    val jar = Path().apply { addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(w * 0.16f))) }

    val waterTop = bottom - (bottom - top) * level
    val cs = w * 0.42f
    val rest = bottom - cs * 0.62f
    val floating = waterTop + cs * 0.1f
    val cy = minOf(rest, maxOf(floating, top + cs * 0.75f))
    val amp = if (level > 0.001f && level < 0.999f) w * 0.022f else 0f

    clipPath(jar) {
        drawRect(glass)
        drawRect(water.copy(alpha = 0.3f), Offset(left, waterTop), Size(right - left, bottom - waterTop))
        drawCritter(critterId, Offset(w / 2, cy), cs, mood = if (level >= 1f) 1f else level)
        if (level > 0.001f) {
            val p = Path()
            p.moveTo(left, bottom)
            var x = left
            while (x <= right + 4f) {
                p.lineTo(x, waterTop + sin(((x - left) / w) * 2 * PI.toFloat() * 1.5f + 0.8f) * amp)
                x += 3f
            }
            p.lineTo(right, bottom)
            p.close()
            drawPath(p, water.copy(alpha = 0.5f))
        }
    }
    drawPath(jar, outline, style = Stroke(stroke))
    drawLine(
        Color.White.copy(alpha = 0.55f),
        Offset(left + w * 0.08f, top + h * 0.1f),
        Offset(left + w * 0.08f, top + h * 0.3f),
        strokeWidth = stroke * 1.3f,
        cap = StrokeCap.Round,
    )
    val lidTop = Offset(left - w * 0.03f, top - h * 0.085f)
    val lidSize = Size(right - left + w * 0.06f, h * 0.085f)
    drawRoundRect(lid, lidTop, lidSize, CornerRadius(w * 0.04f))
    drawRoundRect(outline, lidTop, lidSize, CornerRadius(w * 0.04f), style = Stroke(stroke))
}
