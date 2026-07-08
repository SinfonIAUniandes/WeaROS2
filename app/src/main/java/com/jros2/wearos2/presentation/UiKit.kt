package com.jros2.wearos2.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** App-wide dark palette. Kept small so the whole UI shares one look. */
object AppColors {
    val Background = Color(0xFF0E0F12)
    val Surface = Color(0xFF23262E)
    val Accent = Color(0xFF4FC3F7)
    val Start = Color(0xFF35C77A)
    val Stop = Color(0xFFEF5350)
    val OnSurface = Color(0xFFECEFF3)
    val Muted = Color(0xFF868C97)
}

/** A round icon button whose icon is drawn by [glyph] (see the glyph* helpers below). */
@Composable
fun GlyphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
    background: Color = AppColors.Surface,
    tint: Color = AppColors.OnSurface,
    enabled: Boolean = true,
    glyph: DrawScope.(Color) -> Unit,
) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (enabled) background else AppColors.Surface.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val c = if (enabled) tint else AppColors.Muted
        Canvas(Modifier.size(diameter * 0.46f)) { glyph(c) }
    }
}

/** The big central start/stop button — green play when idle, red stop when publishing. */
@Composable
fun HeroButton(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 88.dp) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (running) AppColors.Stop else AppColors.Start)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.38f)) {
            if (running) glyphStop(Color.White) else glyphPlay(Color.White)
        }
    }
}

// ---- Glyphs: each fills the Canvas it is drawn into. -------------------------------------

fun DrawScope.glyphPlay(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.22f, h * 0.08f)
            lineTo(w * 0.22f, h * 0.92f)
            lineTo(w * 0.92f, h * 0.5f)
            close()
        },
        color,
    )
}

fun DrawScope.glyphStop(color: Color) {
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
        size = Size(size.width * 0.72f, size.height * 0.72f),
        cornerRadius = CornerRadius(size.minDimension * 0.14f),
    )
}

fun DrawScope.glyphPlus(color: Color) {
    val t = size.minDimension * 0.2f
    drawRoundRect(
        color,
        topLeft = Offset(size.width / 2f - t / 2f, size.height * 0.06f),
        size = Size(t, size.height * 0.88f),
        cornerRadius = CornerRadius(t / 2f),
    )
    drawRoundRect(
        color,
        topLeft = Offset(size.width * 0.06f, size.height / 2f - t / 2f),
        size = Size(size.width * 0.88f, t),
        cornerRadius = CornerRadius(t / 2f),
    )
}

/** Settings — three horizontal "tune" sliders with knobs. */
fun DrawScope.glyphTune(color: Color) {
    val t = size.height * 0.1f
    val ys = listOf(0.22f, 0.5f, 0.78f)
    val knobX = listOf(0.68f, 0.34f, 0.6f)
    ys.forEachIndexed { i, ry ->
        val y = size.height * ry
        drawLine(color, Offset(size.width * 0.04f, y), Offset(size.width * 0.96f, y), strokeWidth = t, cap = StrokeCap.Round)
        drawCircle(color, radius = size.height * 0.12f, center = Offset(size.width * knobX[i], y))
    }
}

/** Logs — bulleted list. */
fun DrawScope.glyphList(color: Color) {
    val t = size.height * 0.12f
    listOf(0.24f, 0.5f, 0.76f).forEach { ry ->
        val y = size.height * ry
        drawCircle(color, radius = t * 0.8f, center = Offset(t * 0.8f, y))
        drawLine(color, Offset(t * 2.6f, y), Offset(size.width, y), strokeWidth = t, cap = StrokeCap.Round)
    }
}

/** SpO2 / heart. */
fun DrawScope.glyphHeart(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.02f, h * 0.48f, w * 0.18f, h * 0.05f, w * 0.5f, h * 0.30f)
            cubicTo(w * 0.82f, h * 0.05f, w * 0.98f, h * 0.48f, w * 0.5f, h * 0.88f)
            close()
        },
        color,
    )
}

/** Joystick — ring with a stick and knob. */
fun DrawScope.glyphJoystick(color: Color) {
    val c = center
    val r = size.minDimension * 0.4f
    val sw = size.minDimension * 0.1f
    drawCircle(color, radius = r, center = c, style = Stroke(width = sw))
    drawLine(color, c, Offset(c.x, c.y - r), strokeWidth = sw, cap = StrokeCap.Round)
    drawCircle(color, radius = size.minDimension * 0.15f, center = Offset(c.x, c.y - r))
}

/** Back chevron for sub-screens. */
fun DrawScope.glyphBack(color: Color) {
    val sw = size.minDimension * 0.16f
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.62f, h * 0.2f), Offset(w * 0.34f, h * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.62f, h * 0.8f), strokeWidth = sw, cap = StrokeCap.Round)
}
