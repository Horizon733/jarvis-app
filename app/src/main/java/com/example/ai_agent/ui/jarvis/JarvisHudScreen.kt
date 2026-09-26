package com.example.ai_agent.ui.jarvis

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_agent.domain.model.LlmState
import com.example.ai_agent.ui.chat.ChatViewModel
import com.example.ai_agent.voice.VoiceState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun JarvisHudScreen(
    onMenuClick: () -> Unit,
    viewModel: ChatViewModel,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingResponse by viewModel.currentStreamingResponse.collectAsStateWithLifecycle()
    val llmState by viewModel.llmState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val voiceDownloadConsented by viewModel.voiceDownloadConsented.collectAsStateWithLifecycle()
    val activeModelName by viewModel.activeModelName.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val isModelReady = llmState is LlmState.Ready
    val view = LocalView.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var showTextInput by rememberSaveable { mutableStateOf(false) }
    var consentVoiceOnboarding by rememberSaveable { mutableStateOf(true) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    val lastUser = messages.lastOrNull { it.isUser }?.content?.let(::sanitizeHudMessage)
    val lastAssistant = sanitizeHudMessage(
        when {
            streamingResponse.isNotEmpty() -> streamingResponse
            else -> messages.lastOrNull { !it.isUser }?.content
        },
    )
    val statusText = jarvisHudStatusText(voiceState, isGenerating, llmState)
    val listeningOn = voiceState is VoiceState.ListeningForWakeWord ||
        voiceState is VoiceState.Recording

    DisposableEffect(voiceState, view) {
        view.keepScreenOn = voiceState == VoiceState.Speaking
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(60_000)
        }
    }

    if (!onboardingComplete) {
        JarvisHudOnboardingDialog(
            consentVoiceOnboarding = consentVoiceOnboarding,
            onConsentChange = { consentVoiceOnboarding = it },
            onConfirm = { viewModel.completeOnboarding(consentVoiceOnboarding) },
            onDismiss = { viewModel.completeOnboarding(false) },
        )
    }

    JarvisHudVoiceGate(
        viewModel = viewModel,
        voiceDownloadConsented = voiceDownloadConsented,
        snackbarHostState = snackbarHostState,
    ) { runVoiceAction ->
        val monthYear = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase() +
            " " + today.year

        JarvisHudLayout(
            uiState = JarvisHudUiState(
                listeningOn = listeningOn,
                isModelReady = isModelReady,
                isGenerating = isGenerating,
                voiceState = voiceState,
                wakeWordEnabled = wakeWordEnabled,
                statusText = statusText,
                activeModelName = activeModelName,
                monthYear = monthYear,
                dayOfMonth = today.dayOfMonth.toString(),
                lastUser = lastUser,
                lastAssistant = lastAssistant,
                inputText = inputText,
                showTextInput = showTextInput,
            ),
            snackbarHostState = snackbarHostState,
            onMenuClick = onMenuClick,
            onInputChange = { inputText = it },
            onToggleTextInput = { showTextInput = !showTextInput },
            onSendMessage = {
                viewModel.sendMessage(inputText)
                inputText = ""
            },
            onStopGeneration = viewModel::stopGeneration,
            onStopSpeaking = viewModel::stopSpeaking,
            onToggleRecording = viewModel::toggleVoiceRecording,
            onWakeToggle = {
                if (wakeWordEnabled) {
                    viewModel.setWakeWordEnabled(false)
                } else {
                    viewModel.setWakeWordEnabled(true)
                }
            },
            runVoiceAction = runVoiceAction,
        )
    }
}
