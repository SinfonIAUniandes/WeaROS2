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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.jros2.wearos2.ros.WearSensorBridge

/**
 * The home screen. Everything the app is about is one tap away: a big central start/stop
 * button, quick icons to the sub-screens, a momentary publish button, and an integrated
 * value slider. While the slider is being dragged (or the rotating bezel is turned) the
 * other buttons are blocked to avoid mis-taps.
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
    var sliderActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onRotaryScrollEvent { event ->
                bridge.slider.setValue(bridge.slider.value.value + event.verticalScrollPixels / 2000f)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        // Controls, kept clear of the slider strip at the bottom.
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlyphButton(onClick = onSettings, diameter = 38.dp) { glyphTune(it) }
                GlyphButton(onClick = onLogs, diameter = 38.dp) { glyphList(it) }
            }
            Spacer(Modifier.height(6.dp))
            HeroButton(running = running, onClick = { if (running) onStopBridge() else onStartBridge() })
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlyphButton(onClick = onJoystick, diameter = 42.dp) { glyphJoystick(it) }
                GlyphButton(
                    onClick = { bridge.button.press() },
                    diameter = 42.dp,
                    enabled = running,
                    background = AppColors.Accent,
                    tint = Color.Black,
                ) { glyphPlus(it) }
                GlyphButton(onClick = onSpo2, diameter = 42.dp) { glyphHeart(it) }
            }
        }

        // Scrim that eats stray touches over the controls while the slider is in use.
        if (sliderActive) {
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

        // Integrated slider — drawn last so it stays above the scrim and keeps receiving input.
        MainSlider(
            value = sliderValue,
            onValue = { bridge.slider.setValue(it) },
            onActiveChange = { sliderActive = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.68f)
                .height(24.dp)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun MainSlider(
    value: Float,
    onValue: (Float) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var width by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(AppColors.Surface)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onActiveChange(true)
                    onValue((down.position.x / width).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        onValue((ch.position.x / width).coerceIn(0f, 1f))
                        ch.consume()
                    }
                    onActiveChange(false)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val fillW = size.width * value
            drawRoundRect(
                AppColors.Accent,
                topLeft = Offset(0f, 0f),
                size = Size(fillW, h),
                cornerRadius = CornerRadius(h / 2f),
            )
            drawCircle(
                Color.White,
                radius = h * 0.42f,
                center = Offset(fillW.coerceIn(h * 0.42f, size.width - h * 0.42f), h / 2f),
            )
        }
    }
}
