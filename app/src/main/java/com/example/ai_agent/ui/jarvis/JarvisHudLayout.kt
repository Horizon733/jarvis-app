package com.example.ai_agent.ui.jarvis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_agent.voice.VoiceState

data class JarvisHudUiState(
    val listeningOn: Boolean,
    val isModelReady: Boolean,
    val isGenerating: Boolean,
    val voiceState: VoiceState,
    val wakeWordEnabled: Boolean,
    val statusText: String,
    val activeModelName: String?,
    val monthYear: String,
    val dayOfMonth: String,
    val lastUser: String?,
    val lastAssistant: String?,
    val inputText: String,
    val showTextInput: Boolean,
)

@Composable
fun JarvisHudLayout(
    uiState: JarvisHudUiState,
    snackbarHostState: SnackbarHostState,
    onMenuClick: () -> Unit,
    onInputChange: (String) -> Unit,
    onToggleTextInput: () -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleRecording: () -> Unit,
    onWakeToggle: () -> Unit,
    runVoiceAction: (() -> Unit) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(JarvisHudColors.BackgroundGlow, JarvisHudColors.Background),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                // `imePadding()` reserves space for the soft keyboard so the bottom bar
                // (which owns the mic / TTS-stop button) stays visible when the user
                // focuses the message input. Without it, IME draws over the bottom rows
                // and it looks like the Stop button vanished.
                .imePadding()
                .padding(horizontal = 14.dp)
                .padding(top = 8.dp, bottom = 8.dp),
        ) {
            JarvisHudTopBar(
                listeningOn = uiState.listeningOn,
                onMenuClick = onMenuClick,
            )

            // The circular HUD is the visual centrepiece but it wants a fixed 280 dp; when
            // the text input opens (usually with the keyboard about to appear) we collapse
            // it so the fixed-height rows below don't get pushed off-screen. The weight(1f)
            // Box still owns the remaining vertical space, so the transcript / bottom bar
            // stay glued to the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (!uiState.showTextInput) {
                    JarvisCircularHud(
                        voiceState = uiState.voiceState,
                        isGenerating = uiState.isGenerating,
                    )
                }
            }

            JarvisHudInfoPanels(
                isModelReady = uiState.isModelReady,
                statusText = uiState.statusText,
                activeModelName = uiState.activeModelName,
                monthYear = uiState.monthYear,
                dayOfMonth = uiState.dayOfMonth,
            )

            Spacer(Modifier.height(8.dp))

            JarvisHudTranscriptPanel(
                lastUser = uiState.lastUser,
                lastAssistant = uiState.lastAssistant,
                onClick = onToggleTextInput,
            )

            JarvisHudUtilityRow(
                wakeWordEnabled = uiState.wakeWordEnabled,
                onWakeToggle = {
                    if (uiState.wakeWordEnabled) onWakeToggle()
                    else runVoiceAction(onWakeToggle)
                },
            )

            if (uiState.showTextInput) {
                JarvisHudInputRow(
                    inputText = uiState.inputText,
                    isModelReady = uiState.isModelReady,
                    isGenerating = uiState.isGenerating,
                    onInputChange = onInputChange,
                    onSend = onSendMessage,
                    onStop = onStopGeneration,
                )
                Spacer(Modifier.height(4.dp))
            }

            JarvisHudBottomBar(
                isModelReady = uiState.isModelReady,
                isGenerating = uiState.isGenerating,
                voiceState = uiState.voiceState,
                showTextInput = uiState.showTextInput,
                onMenuClick = onMenuClick,
                onToggleTextInput = onToggleTextInput,
                onStopGeneration = onStopGeneration,
                onStopSpeaking = onStopSpeaking,
                onToggleRecording = onToggleRecording,
                runVoiceAction = runVoiceAction,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun JarvisHudTopBar(listeningOn: Boolean, onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (listeningOn) "LISTENING ON" else "LISTENING OFF",
            color = if (listeningOn) JarvisHudColors.Ok else JarvisHudColors.TextMuted,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "JARVIS",
            color = JarvisHudColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            letterSpacing = 6.sp,
            fontSize = 18.sp,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("v1.0", color = JarvisHudColors.TextMuted, fontSize = 10.sp)
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Open chats", tint = JarvisHudColors.Accent)
            }
        }
    }
}

@Composable
private fun JarvisHudInfoPanels(
    isModelReady: Boolean,
    statusText: String,
    activeModelName: String?,
    monthYear: String,
    dayOfMonth: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        JarvisGlassPanel(modifier = Modifier.weight(1f).height(118.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("CORE", color = JarvisHudColors.TextMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                Text(
                    if (isModelReady) "ONLINE" else "OFFLINE",
                    color = if (isModelReady) JarvisHudColors.Ok else JarvisHudColors.Error,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    statusText,
                    color = JarvisHudColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activeModelName != null) {
                    Text(
                        activeModelName,
                        color = JarvisHudColors.TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        JarvisGlassPanel(modifier = Modifier.weight(1f).height(118.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(monthYear, color = JarvisHudColors.TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(
                        dayOfMonth,
                        color = JarvisHudColors.TextPrimary,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
                JarvisCalendarDots()
            }
        }
    }
}

@Composable
private fun JarvisCalendarDots() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(4) { col ->
                    val lit = (row * 4 + col) % 5 == 0
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (lit) JarvisHudColors.Accent else JarvisHudColors.BorderSoft,
                                RoundedCornerShape(1.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun JarvisHudTranscriptPanel(
    lastUser: String?,
    lastAssistant: String?,
    onClick: () -> Unit,
) {
    JarvisGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (lastUser != null) {
                JarvisMessageBlock(label = "You", body = lastUser, maxLines = 2)
            }
            if (lastAssistant != null) {
                Spacer(Modifier.height(6.dp))
                JarvisMessageBlock(label = "Jarvis", body = lastAssistant, maxLines = 3)
            } else if (lastUser == null) {
                Text(
                    "Tap mic to speak · tap here to type",
                    color = JarvisHudColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun JarvisHudUtilityRow(wakeWordEnabled: Boolean, onWakeToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JarvisHudPillButton(
            label = if (wakeWordEnabled) "WAKE ON" else "WAKE",
            highlighted = wakeWordEnabled,
            onClick = onWakeToggle,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WbSunny,
                contentDescription = null,
                tint = JarvisHudColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("100%", color = JarvisHudColors.TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun JarvisHudInputRow(
    inputText: String,
    isModelReady: Boolean,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message…", color = JarvisHudColors.TextMuted) },
            enabled = isModelReady && !isGenerating,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = JarvisHudColors.TextPrimary,
                unfocusedTextColor = JarvisHudColors.TextPrimary,
                focusedBorderColor = JarvisHudColors.BorderSoft,
                unfocusedBorderColor = JarvisHudColors.BorderSoft.copy(alpha = 0.5f),
                cursorColor = JarvisHudColors.Accent,
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true,
        )
        IconButton(
            onClick = { if (isGenerating) onStop() else onSend() },
            enabled = isModelReady && (inputText.isNotBlank() || isGenerating),
        ) {
            Icon(
                if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = JarvisHudColors.Accent,
            )
        }
    }
}

@Composable
private fun JarvisHudBottomBar(
    isModelReady: Boolean,
    isGenerating: Boolean,
    voiceState: VoiceState,
    showTextInput: Boolean,
    onMenuClick: () -> Unit,
    onToggleTextInput: () -> Unit,
    onStopGeneration: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleRecording: () -> Unit,
    runVoiceAction: (() -> Unit) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Chats", tint = JarvisHudColors.TextSecondary)
        }
        JarvisMicButton(
            // Active (glowing + Stop icon) whenever tapping the button will *stop* something:
            // ongoing generation, an active recording, or in-progress TTS. Previously the
            // Speaking case was missing so the icon still read as Mic even though the tap
            // stops TTS — same visual bug you saw when the transcript panel was open.
            active = voiceState is VoiceState.Recording ||
                voiceState is VoiceState.Speaking ||
                isGenerating,
            enabled = isModelReady,
            onClick = {
                when {
                    isGenerating -> onStopGeneration()
                    voiceState is VoiceState.Speaking -> onStopSpeaking()
                    voiceState is VoiceState.Recording -> onToggleRecording()
                    else -> runVoiceAction(onToggleRecording)
                }
            },
        )
        IconButton(onClick = onToggleTextInput) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Type",
                tint = if (showTextInput) JarvisHudColors.Accent else JarvisHudColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun JarvisMicButton(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(
                if (active) JarvisHudColors.Glow.copy(alpha = 0.35f) else JarvisHudColors.GlassBottom,
                shape,
            )
            .border(2.dp, if (active) JarvisHudColors.Accent else JarvisHudColors.Border, shape)
            .semantics {
                role = Role.Button
                contentDescription = if (active) "Stop" else "Voice input"
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (active) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = null,
            tint = JarvisHudColors.Accent,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun JarvisMessageBlock(label: String, body: String, maxLines: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label.uppercase(),
            color = JarvisHudColors.TextMuted,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
        )
        Text(
            body,
            color = JarvisHudColors.TextPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Visible else TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JarvisHudPillButton(label: String, highlighted: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                if (highlighted) JarvisHudColors.Glow.copy(alpha = 0.15f) else JarvisHudColors.GlassBottom,
                shape,
            )
            .border(
                1.dp,
                if (highlighted) JarvisHudColors.Border else JarvisHudColors.BorderSoft.copy(alpha = 0.4f),
                shape,
            ),
    ) {
        Text(
            label,
            color = if (highlighted) JarvisHudColors.TextPrimary else JarvisHudColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}
