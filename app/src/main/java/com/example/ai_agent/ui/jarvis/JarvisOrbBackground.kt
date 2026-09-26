package com.example.ai_agent.ui.jarvis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ai_agent.voice.VoiceState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisOrbBackground(
    voiceState: VoiceState,
    modifier: Modifier = Modifier,
) {
    val active = voiceState is VoiceState.Recording ||
        voiceState is VoiceState.Speaking ||
        voiceState is VoiceState.Transcribing
    val transition = rememberInfiniteTransition(label = "orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "rotation",
    )
    val breath by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (active) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 900 else 2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * 0.22f * breath

        for (i in 0 until 48) {
            val angle = (i / 48f) * 6.283f + rotation * 0.02f
            val r = base * (0.85f + (i % 5) * 0.03f)
            val px = center.x + cos(angle) * r
            val py = center.y + sin(angle) * r * 0.92f
            drawCircle(
                color = JarvisHudColors.OrbRing.copy(alpha = 0.15f + (i % 3) * 0.05f),
                radius = 2f + (i % 4),
                center = Offset(px, py),
            )
        }

        rotate(rotation, center) {
            for (ring in 0 until 4) {
                drawCircle(
                    color = JarvisHudColors.Border.copy(alpha = 0.22f - ring * 0.04f),
                    radius = base * (1.1f + ring * 0.18f),
                    center = center,
                    style = Stroke(width = 1.2f + ring * 0.3f),
                )
            }
        }

        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    JarvisHudColors.OrbCore.copy(alpha = 0.55f),
                    JarvisHudColors.OrbCore.copy(alpha = 0.08f),
                    JarvisHudColors.Background.copy(alpha = 0f),
                ),
                center = center,
                radius = base * 1.35f,
            ),
            radius = base * 1.35f,
            center = center,
        )
        drawCircle(
            color = JarvisHudColors.OrbCore.copy(alpha = 0.35f),
            radius = base * 0.35f,
            center = center,
        )
    }
}
