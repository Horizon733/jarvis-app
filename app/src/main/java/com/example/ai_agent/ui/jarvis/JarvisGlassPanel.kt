package com.example.ai_agent.ui.jarvis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun JarvisGlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(JarvisHudColors.GlassTop, JarvisHudColors.GlassBottom),
                ),
            )
            .border(1.dp, JarvisHudColors.BorderSoft, shape),
        content = content,
    )
}
