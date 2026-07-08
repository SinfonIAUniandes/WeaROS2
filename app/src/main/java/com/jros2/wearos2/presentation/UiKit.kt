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

/**
 * The single source of truth for colors. Every screen references these semantic roles
 * (never raw hex), so restyling the whole app is a matter of editing this one object.
 */
object AppColors {
    val Background = Color(0xFF0F1216)     // app background
    val Surface = Color(0xFF1C212A)        // idle buttons, cards, input fields
    val SurfaceVariant = Color(0xFF2C323D) // ring track, dividers, raised surfaces
    val Primary = Color(0xFF5AC8FA)        // the one accent: interactive / value
    val OnPrimary = Color(0xFF04222E)      // content on top of Primary
    val Start = Color(0xFF35C759)          // hero when idle (go)
    val Stop = Color(0xFFFF5A5A)           // hero when publishing (stop)
    val OnSurface = Color(0xFFE8ECF2)      // primary text / icons
    val Muted = Color(0xFF8A92A0)          // secondary text
}

/** Shared sizes, kept here so spacing/scale is tuned in one place. */
object AppDimens {
    val Hero = 88.dp
    val NavIcon = 42.dp
    val SmallIcon = 38.dp
    val RingWidth = 10.dp
}

/** A round icon button whose icon is drawn by [glyph] (see the glyph* helpers below). */
@Composable
fun GlyphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = AppDimens.NavIcon,
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
fun HeroButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = AppDimens.Hero,
) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (running) AppColors.Stop else AppColors.Start)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.38f)) {
            if (running) glyphStop(AppColors.OnSurface) else glyphPlay(AppColors.OnSurface)
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

/** Publish trigger — a broadcast "ping": a dot emitting concentric waves. */
fun DrawScope.glyphPing(color: Color) {
    val c = center
    val d = size.minDimension
    drawCircle(color, radius = d * 0.12f, center = c)
    drawCircle(color, radius = d * 0.28f, center = c, style = Stroke(width = d * 0.08f))
    drawCircle(color, radius = d * 0.44f, center = c, style = Stroke(width = d * 0.08f))
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

/** Logs — a document page with text lines (distinct from the settings glyph). */
fun DrawScope.glyphLogs(color: Color) {
    val w = size.width
    val h = size.height
    val sw = size.minDimension * 0.09f
    drawRoundRect(
        color,
        topLeft = Offset(w * 0.16f, h * 0.05f),
        size = Size(w * 0.68f, h * 0.9f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = Stroke(width = sw),
    )
    listOf(0.32f, 0.5f, 0.68f).forEach { ry ->
        drawLine(color, Offset(w * 0.32f, h * ry), Offset(w * 0.68f, h * ry), strokeWidth = sw, cap = StrokeCap.Round)
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
