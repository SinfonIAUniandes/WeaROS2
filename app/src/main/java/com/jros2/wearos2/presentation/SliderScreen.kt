package com.jros2.wearos2.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.jros2.wearos2.ros.sensors.SliderController
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

/**
 * Full-screen round slider. The value (0–100%) maps to the angle around the ring, starting
 * at the top and increasing clockwise. Turn the rotating bezel / crown or drag around the
 * ring to change it; each change publishes a normalized 0.0–1.0 [SliderController] value.
 */
@Composable
fun SliderScreen(slider: SliderController, onExit: () -> Unit) {
    val value by slider.value.collectAsState()
    val count by slider.messageCount.collectAsState()
    var center by remember { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size -> center = Offset(size.width / 2f, size.height / 2f) }
            .onRotaryScrollEvent { event ->
                // One notch of the bezel is a few dozen pixels; scale so a full turn ≈ full range.
                // Read the live value (not the recomposed snapshot) so fast turns don't drop steps.
                slider.setValue(slider.value.value + event.verticalScrollPixels / 2000f)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(center) {
                awaitEachGesture {
                    fun handle(pos: Offset) {
                        val dx = pos.x - center.x
                        val dy = pos.y - center.y
                        // Angle from the top (12 o'clock), clockwise, normalized to 0..1.
                        var theta = atan2(dx, -dy)
                        if (theta < 0f) theta += (2f * PI).toFloat()
                        slider.setValue(theta / (2f * PI).toFloat())
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
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) / 2f - 6f
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2f, r * 2f)
            // Track
            drawCircle(color = Color(0xFF2A2A2A), radius = r, center = c, style = Stroke(width = 14f))
            // Value arc, from the top clockwise
            drawArc(
                color = Color(0xFF4FC3F7),
                startAngle = -90f,
                sweepAngle = value * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 14f),
            )
        }
        Text(
            text = "${(value * 100f).toInt()}%",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
        Button(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        ) {
            Text("Exit")
        }
        Text(
            text = "sent · $count",
            color = Color.Gray,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}
