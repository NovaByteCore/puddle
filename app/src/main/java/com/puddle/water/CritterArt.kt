package com.puddle.water

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

private val Ink = Color(0xFF123A3F)
private val Cheek = Color(0xFFFF8F7A)
private val LockedGrey = Color(0xFFA7B6B8)

/**
 * Draws one of Puddle's original critters centred on [c].
 * [mood] 0 = thirsty, 0..1 = content, 1 = goal reached.
 */
fun DrawScope.drawCritter(id: String, c: Offset, size: Float, locked: Boolean = false, mood: Float = 0.6f) {
    val r = size / 2f
    val crit = Critters.byId(id)
    val body = if (locked) LockedGrey else Color(crit.body)
    fun k(v: Long): Color = if (locked) LockedGrey else Color(v)
    val sw = r * 0.08f
    val sides = listOf(-1f, 1f)

    // Parts that sit behind the body
    when (id) {
        "frog" -> for (sg in sides) drawCircle(body, r * 0.36f, Offset(c.x + sg * r * 0.45f, c.y - r * 0.72f))
        "cat" -> for (sg in sides) {
            val ear = Path().apply {
                moveTo(c.x + sg * r * 0.85f, c.y - r * 0.35f)
                lineTo(c.x + sg * r * 0.75f, c.y - r * 1.15f)
                lineTo(c.x + sg * r * 0.2f, c.y - r * 0.8f)
                close()
            }
            drawPath(ear, body)
            val inner = Path().apply {
                moveTo(c.x + sg * r * 0.7f, c.y - r * 0.55f)
                lineTo(c.x + sg * r * 0.67f, c.y - r * 0.95f)
                lineTo(c.x + sg * r * 0.38f, c.y - r * 0.76f)
                close()
            }
            drawPath(inner, k(0xFFFFB5A7))
        }
        "penguin" -> for (sg in sides) {
            drawOval(k(0xFFFFA726), Offset(c.x + sg * r * 0.42f - r * 0.25f, c.y + r * 0.8f), Size(r * 0.5f, r * 0.28f))
        }
        "duck" -> {
            drawCircle(body, r * 0.17f, Offset(c.x - r * 0.14f, c.y - r * 1.0f))
            drawCircle(body, r * 0.21f, Offset(c.x + r * 0.08f, c.y - r * 1.04f))
        }
        "axolotl" -> {
            val gill = k(0xFFE0607E)
            for (sg in sides) for (i in 0..2) {
                val sx = c.x + sg * r * 0.7f
                val sy = c.y - r * 0.35f + i * r * 0.25f
                val ex = c.x + sg * r * (1.25f - i * 0.05f)
                val ey = sy - r * (0.45f - i * 0.2f)
                drawLine(gill, Offset(sx, sy), Offset(ex, ey), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
            }
        }
        "octopus" -> for (i in 0..4) {
            drawCircle(body, r * 0.22f, Offset(c.x - r * 0.72f + i * r * 0.36f, c.y + r * 0.88f))
        }
        "whale" -> {
            val spout = k(0xFF8FDCEF)
            val w = r * 0.1f
            drawLine(spout, Offset(c.x, c.y - r * 0.8f), Offset(c.x, c.y - r * 1.2f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(spout, Offset(c.x, c.y - r * 1.2f), Offset(c.x - r * 0.3f, c.y - r * 1.4f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(spout, Offset(c.x, c.y - r * 1.2f), Offset(c.x + r * 0.3f, c.y - r * 1.4f), strokeWidth = w, cap = StrokeCap.Round)
        }
    }

    // Body
    if (id == "whale") drawOval(body, Offset(c.x - r * 1.15f, c.y - r * 0.85f), Size(r * 2.3f, r * 1.75f))
    else drawCircle(body, r, c)

    // Parts in front of the body
    when (id) {
        "frog" -> drawOval(k(0xFFC9EEBB), Offset(c.x - r * 0.55f, c.y + r * 0.22f), Size(r * 1.1f, r * 0.68f))
        "penguin" -> drawOval(k(0xFFF5F7F7), Offset(c.x - r * 0.7f, c.y - r * 0.55f), Size(r * 1.4f, r * 1.4f))
        "turtle" -> {
            drawArc(k(0xFF8D6E63), 180f, 180f, true, Offset(c.x - r * 1.08f, c.y - r * 1.12f), Size(r * 2.16f, r * 1.5f))
            for ((dx, dy) in listOf(-0.5f to -0.6f, 0f to -0.85f, 0.5f to -0.6f)) {
                drawCircle(k(0xFF6D4C41), r * 0.14f, Offset(c.x + dx * r, c.y + dy * r))
            }
        }
        "whale" -> drawOval(k(0xFFB9DDF5), Offset(c.x - r * 0.75f, c.y + r * 0.22f), Size(r * 1.5f, r * 0.52f))
    }

    if (locked) return

    // Eyes
    val isFrog = id == "frog"
    val eyeY = if (isFrog) c.y - r * 0.72f else c.y - r * 0.08f
    val eyeDx = if (isFrog) r * 0.45f else r * 0.36f
    val pr = if (isFrog) r * 0.13f else r * 0.15f
    for (sg in sides) {
        if (isFrog) drawCircle(Color.White, r * 0.24f, Offset(c.x + sg * eyeDx, eyeY))
        val e = Offset(c.x + sg * eyeDx, eyeY + if (mood < 0.05f) r * 0.03f else 0f)
        drawCircle(Ink, pr, e)
        drawCircle(Color.White, pr * 0.38f, Offset(e.x + pr * 0.35f, e.y - pr * 0.35f))
    }

    // Cheeks
    for (sg in sides) {
        drawOval(
            Cheek.copy(alpha = 0.55f),
            Offset(c.x + sg * r * 0.58f - r * 0.14f, c.y + r * 0.14f),
            Size(r * 0.28f, r * 0.16f),
        )
    }

    // Mouth or beak
    val line = Stroke(width = sw, cap = StrokeCap.Round)
    when (id) {
        "penguin" -> {
            val beak = Path().apply {
                moveTo(c.x - r * 0.14f, c.y + r * 0.1f)
                lineTo(c.x + r * 0.14f, c.y + r * 0.1f)
                lineTo(c.x, c.y + r * 0.32f)
                close()
            }
            drawPath(beak, Color(0xFFFFA726))
        }
        "duck" -> drawOval(Color(0xFFFF9800), Offset(c.x - r * 0.26f, c.y + r * 0.08f), Size(r * 0.52f, r * 0.24f))
        "cat" -> {
            drawArc(Ink, 0f, 180f, false, Offset(c.x - r * 0.2f, c.y + r * 0.08f), Size(r * 0.2f, r * 0.16f), style = line)
            drawArc(Ink, 0f, 180f, false, Offset(c.x, c.y + r * 0.08f), Size(r * 0.2f, r * 0.16f), style = line)
            for (sg in sides) for (i in 0..1) {
                drawLine(
                    Ink.copy(alpha = 0.55f),
                    Offset(c.x + sg * r * 0.45f, c.y + r * 0.2f + i * r * 0.12f),
                    Offset(c.x + sg * r * 0.95f, c.y + r * 0.12f + i * r * 0.22f),
                    strokeWidth = sw * 0.6f,
                    cap = StrokeCap.Round,
                )
            }
        }
        else -> when {
            mood < 0.05f -> drawArc(Ink, 200f, 140f, false, Offset(c.x - r * 0.16f, c.y + r * 0.2f), Size(r * 0.32f, r * 0.22f), style = line)
            mood >= 1f -> drawArc(Ink, 0f, 180f, true, Offset(c.x - r * 0.22f, c.y - r * 0.02f), Size(r * 0.44f, r * 0.38f))
            else -> drawArc(Ink, 20f, 140f, false, Offset(c.x - r * 0.2f, c.y), Size(r * 0.4f, r * 0.3f), style = line)
        }
    }
}
