package com.example.ai_agent.ui.jarvis

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_agent.voice.VoiceState
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JarvisCircularHud(
    voiceState: VoiceState,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = voiceState is VoiceState.Recording ||
        voiceState is VoiceState.Speaking ||
        voiceState is VoiceState.Transcribing ||
        isGenerating

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    val transition = rememberInfiniteTransition(label = "hud")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing)),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (active) 1f else 0.92f,
        animationSpec = infiniteRepeatable(tween(if (active) 700 else 2000), RepeatMode.Reverse),
        label = "pulse",
    )

    val timeText = now.format(DateTimeFormatter.ofPattern("h:mm"))
    val amPm = now.format(DateTimeFormatter.ofPattern("a"))
    val seconds = now.format(DateTimeFormatter.ofPattern("ss"))

    Box(modifier = modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.42f * pulse

            for (i in 0 until 6) {
                val angle = (i * 60f) + spin * 0.15f
                rotate(angle, center) {
                    drawLine(
                        color = JarvisHudColors.BorderSoft,
                        start = Offset(center.x, center.y - r * 0.55f),
                        end = Offset(center.x, center.y - r * 0.35f),
                        strokeWidth = 1f,
                    )
                }
            }

            rotate(spin * 0.4f, center) {
                drawCircle(
                    color = JarvisHudColors.Border.copy(alpha = 0.35f),
                    radius = r * 1.05f,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
                for (tick in 0 until 48) {
                    val a = tick * 7.5f
                    val inner = r * 0.92f
                    val outer = r * (if (tick % 6 == 0) 1.02f else 0.98f)
                    val x1 = center.x + cos(Math.toRadians(a.toDouble())).toFloat() * inner
                    val y1 = center.y + sin(Math.toRadians(a.toDouble())).toFloat() * inner
                    val x2 = center.x + cos(Math.toRadians(a.toDouble())).toFloat() * outer
                    val y2 = center.y + sin(Math.toRadians(a.toDouble())).toFloat() * outer
                    drawLine(
                        JarvisHudColors.Accent.copy(alpha = if (tick % 6 == 0) 0.7f else 0.25f),
                        Offset(x1, y1),
                        Offset(x2, y2),
                        strokeWidth = if (tick % 6 == 0) 2f else 1f,
                    )
                }
            }

            val arcSweep = if (active) 110f else 55f
            drawArc(
                color = JarvisHudColors.AccentWarm.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = Offset(center.x - r * 0.78f, center.y - r * 0.78f),
                size = androidx.compose.ui.geometry.Size(r * 1.56f, r * 1.56f),
                style = Stroke(width = 3f),
            )
            drawArc(
                color = JarvisHudColors.AccentTeal.copy(alpha = 0.75f),
                startAngle = 40f,
                sweepAngle = arcSweep * 0.7f,
                useCenter = false,
                topLeft = Offset(center.x - r * 0.65f, center.y - r * 0.65f),
                size = androidx.compose.ui.geometry.Size(r * 1.3f, r * 1.3f),
                style = Stroke(width = 2f),
            )

            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        JarvisHudColors.Glow.copy(alpha = 0.35f),
                        JarvisHudColors.Background.copy(alpha = 0f),
                    ),
                    center = center,
                    radius = r * 0.9f,
                ),
                radius = r * 0.9f,
                center = center,
            )

            val tri = Path().apply {
                moveTo(center.x, center.y - r * 0.12f)
                lineTo(center.x - r * 0.06f, center.y + r * 0.04f)
                lineTo(center.x + r * 0.06f, center.y + r * 0.04f)
                close()
            }
            drawPath(tri, JarvisHudColors.Accent.copy(alpha = 0.9f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                timeText,
                color = JarvisHudColors.TextPrimary,
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )
            RowVerticalAmPm(amPm = amPm, seconds = seconds)
        }
    }
}

@Composable
private fun RowVerticalAmPm(amPm: String, seconds: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(amPm, color = JarvisHudColors.TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(seconds, color = JarvisHudColors.TextMuted, fontSize = 11.sp)
    }
}
