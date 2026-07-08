package com.jros2.wearos2.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.jros2.wearos2.ros.sensors.JoystickController
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-screen virtual joystick. Drag anywhere to publish normalized X/Y through
 * [JoystickController]; releasing recenters and publishes (0, 0). An Exit button returns
 * to the caller.
 */
@Composable
fun JoystickScreen(joystick: JoystickController, onExit: () -> Unit) {
    val value by joystick.displayValue.collectAsState()
    val count by joystick.messageCount.collectAsState()
    var thumb by remember { mutableStateOf(Offset.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onSizeChanged { size ->
                center = Offset(size.width / 2f, size.height / 2f)
                radius = (min(size.width, size.height) / 2f) * 0.85f
            }
            .pointerInput(center, radius) {
                if (radius <= 0f) return@pointerInput
                awaitEachGesture {
                    fun handle(pos: Offset) {
                        val rawX = pos.x - center.x
                        val rawY = pos.y - center.y
                        val dist = sqrt(rawX * rawX + rawY * rawY)
                        val scale = if (dist > radius && dist > 0f) radius / dist else 1f
                        val cx = rawX * scale
                        val cy = rawY * scale
                        thumb = Offset(cx, cy)
                        val nx = (cx / radius).coerceIn(-1f, 1f)
                        val ny = (-cy / radius).coerceIn(-1f, 1f) // screen Y is down; invert so up is positive
                        joystick.publishAxes(nx, ny)
                    }
                    val down = awaitFirstDown()
                    handle(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        handle(change.position)
                        change.consume()
                    }
                    // Released: recenter and tell the consumer to stop.
                    thumb = Offset.Zero
                    joystick.publishAxes(0f, 0f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = if (radius > 0f) radius else (min(size.width, size.height) / 2f) * 0.85f
            drawCircle(color = AppColors.Surface, radius = r, center = c)
            drawCircle(color = AppColors.SurfaceVariant, radius = r, center = c, style = Stroke(width = 4f))
            drawCircle(color = AppColors.Primary, radius = r * 0.28f, center = Offset(c.x + thumb.x, c.y + thumb.y))
        }
        GlyphButton(
            onClick = onExit,
            diameter = 36.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        ) { glyphBack(it) }
        Text(
            text = "$value · $count",
            color = AppColors.Muted,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
        )
    }
}
