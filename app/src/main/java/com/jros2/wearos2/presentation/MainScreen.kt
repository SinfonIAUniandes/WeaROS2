package com.jros2.wearos2.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.jros2.wearos2.ros.WearSensorBridge
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val TWO_PI = (2.0 * PI).toFloat()

/**
 * Home screen. Controls sit centered; the value slider is a ring hugging the watch edge
 * (drag it or turn the bezel) so it never pushes the layout around. Touching the ring
 * blocks the center buttons until you let go, to avoid mis-taps.
 */
@Composable
fun MainScreen(
    bridge: WearSensorBridge,
    onStartBridge: () -> Unit,
    onStopBridge: () -> Unit,
    onJoystick: () -> Unit,
    onSpo2: () -> Unit,
    onSettings: () -> Unit,
    onLogs: () -> Unit,
) {
    val running by bridge.isRunning.collectAsState()
    val sliderValue by bridge.slider.value.collectAsState()
    var ringActive by remember { mutableStateOf(false) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var radiusPx by remember { mutableStateOf(0f) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun setFromPosition(pos: Offset) {
        var theta = atan2(pos.x - center.x, -(pos.y - center.y)) // 0 at top, clockwise
        if (theta < 0f) theta += TWO_PI
        bridge.slider.setValue(theta / TWO_PI)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onSizeChanged {
                center = Offset(it.width / 2f, it.height / 2f)
                radiusPx = min(it.width, it.height) / 2f
            }
            .onRotaryScrollEvent { event ->
                bridge.slider.setValue(bridge.slider.value.value + event.verticalScrollPixels / 2000f)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            // Ring gesture lives on the parent: buttons (children) consume their own taps
            // first, so this only fires for un-consumed touches out at the edge.
            .pointerInput(center, radiusPx) {
                if (radiusPx <= 0f) return@pointerInput
                val bandInner = radiusPx * 0.70f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val dx = down.position.x - center.x
                    val dy = down.position.y - center.y
                    if (sqrt(dx * dx + dy * dy) < bandInner) return@awaitEachGesture // center, ignore
                    ringActive = true
                    down.consume()
                    setFromPosition(down.position)
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        setFromPosition(ch.position)
                        ch.consume()
                    }
                    ringActive = false
                }
            },
    ) {
        // The slider ring around the edge.
        Canvas(Modifier.fillMaxSize()) {
            val sw = AppDimens.RingWidth.toPx()
            val r = min(size.width, size.height) / 2f - sw / 2f - 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(AppColors.SurfaceVariant, radius = r, center = c, style = Stroke(sw))
            drawArc(
                color = AppColors.Primary,
                startAngle = -90f,
                sweepAngle = sliderValue * 360f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            val a = sliderValue * TWO_PI
            drawCircle(AppColors.OnSurface, radius = sw * 0.85f, center = Offset(c.x + sin(a) * r, c.y - cos(a) * r))
        }

        // Centered controls.
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlyphButton(onClick = onSettings, diameter = AppDimens.SmallIcon) { glyphTune(it) }
                GlyphButton(onClick = onLogs, diameter = AppDimens.SmallIcon) { glyphLogs(it) }
            }
            Spacer(Modifier.height(6.dp))
            HeroButton(running = running, onClick = { if (running) onStopBridge() else onStartBridge() })
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlyphButton(onClick = onJoystick) { glyphJoystick(it) }
                GlyphButton(
                    onClick = { bridge.button.press() },
                    enabled = running,
                    background = AppColors.Primary,
                    tint = AppColors.OnPrimary,
                ) { glyphPing(it) }
                GlyphButton(onClick = onSpo2) { glyphHeart(it) }
            }
        }

        // Live value, shown only while adjusting the ring, so the resting screen stays clean.
        if (ringActive) {
            Text(
                text = "${(sliderValue * 100f).toInt()}%",
                color = AppColors.Primary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }

        // Block center buttons while the ring is in use.
        if (ringActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}
