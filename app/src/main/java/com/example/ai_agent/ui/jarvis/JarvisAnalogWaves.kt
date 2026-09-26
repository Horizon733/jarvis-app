package com.example.ai_agent.ui.jarvis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun JarvisAnalogWaves(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "phase",
    )

    Canvas(
        modifier = modifier.fillMaxWidth(),
    ) {
        val w = size.width
        val h = size.height
        for (x in 0..w.toInt() step 20) {
            drawLine(
                color = JarvisHudColors.BorderSoft.copy(alpha = 0.35f),
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), h),
                strokeWidth = 1f,
            )
        }
        for (y in 0..h.toInt() step 20) {
            drawLine(
                color = JarvisHudColors.BorderSoft.copy(alpha = 0.35f),
                start = Offset(0f, y.toFloat()),
                end = Offset(w, y.toFloat()),
                strokeWidth = 1f,
            )
        }

        fun wave(
            amplitude: Float,
            frequency: Float,
            speed: Float,
            color: androidx.compose.ui.graphics.Color,
            stroke: Float,
            offset: Float,
        ) {
            val path = androidx.compose.ui.graphics.Path()
            var started = false
            for (x in 0..w.toInt()) {
                val n = x / w
                val yPos = h * 0.5f +
                    sin(n * frequency + phase * speed + offset) * amplitude +
                    sin(n * frequency * 0.52f - phase * speed * 0.7f) * amplitude * 0.35f
                if (!started) {
                    path.moveTo(x.toFloat(), yPos)
                    started = true
                } else {
                    path.lineTo(x.toFloat(), yPos)
                }
            }
            drawPath(path, color, style = Stroke(width = stroke))
        }

        wave(16f, 16f, 1.5f, JarvisHudColors.WavePrimary, 2f, 0f)
        wave(10f, 22f, 2.1f, JarvisHudColors.WaveSecondary, 1.5f, 1.8f)
        wave(22f, 10f, 1.1f, JarvisHudColors.WaveTertiary, 1f, 2.6f)
    }
}
