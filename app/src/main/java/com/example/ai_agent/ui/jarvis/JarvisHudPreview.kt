package com.example.ai_agent.ui.jarvis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_agent.voice.VoiceState

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun JarvisHudLayoutPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(JarvisHudColors.BackgroundGlow, JarvisHudColors.Background),
                ),
            )
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("JARVIS", color = JarvisHudColors.TextPrimary, letterSpacing = 6.sp, fontSize = 18.sp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                JarvisCircularHud(voiceState = VoiceState.Idle, isGenerating = false)
            }
        }
    }
}
