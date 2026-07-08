package com.jros2.wearos2.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.jros2.wearos2.ros.sensors.samsung.SamsungPpgSensor
import kotlinx.coroutines.delay
import kotlin.math.min

private const val MEASUREMENT_MS = 35_000L

/**
 * On-demand SpO2 measurement screen. A ring fills over the ~35 s measurement window (like
 * the Samsung reference app); the center shows the live status/result. Tap the heart to
 * start; it self-stops on completion or timeout.
 */
@Composable
fun Spo2Screen(samsung: SamsungPpgSensor, onExit: () -> Unit) {
    val measuring by samsung.spo2Measuring.collectAsState()
    val result by samsung.spo2Result.collectAsState()
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(measuring) {
        if (measuring) {
            progress = 0f
            val tick = 250L
            val steps = (MEASUREMENT_MS / tick).toInt()
            for (i in 1..steps) {
                delay(tick)
                progress = i / steps.toFloat()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val c = center
            val r = min(size.width, size.height) / 2f - 8f
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2f, r * 2f)
            drawCircle(AppColors.SurfaceVariant, radius = r, center = c, style = Stroke(width = 12f))
            drawArc(
                color = AppColors.Primary,
                startAngle = -90f,
                sweepAngle = (if (measuring) progress else 0f) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 12f),
            )
        }

        Text(
            text = result,
            color = AppColors.OnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
        )

        GlyphButton(
            onClick = onExit,
            diameter = 36.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
        ) { glyphBack(it) }

        GlyphButton(
            onClick = { samsung.measureSpo2() },
            diameter = 48.dp,
            enabled = !measuring,
            background = AppColors.Primary,
            tint = AppColors.OnPrimary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
        ) { glyphHeart(it) }
    }
}
