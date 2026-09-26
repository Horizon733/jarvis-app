package com.example.ai_agent.ui.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ai_agent.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun JarvisHudVoiceGate(
    viewModel: ChatViewModel,
    voiceDownloadConsented: Boolean,
    snackbarHostState: SnackbarHostState,
    content: @Composable (runVoiceAction: (() -> Unit) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showVoiceConsent by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.updateMicPermission(granted)
        if (granted) {
            pendingAction?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice")
            }
        }
        pendingAction = null
    }

    val runWithMicPermission = remember(viewModel, context, micPermissionLauncher) {
        { action: () -> Unit ->
            runWithMicPermissionImpl(
                context = context,
                viewModel = viewModel,
                action = action,
                onNeedPermission = { pendingAction = it },
                launchPermissionRequest = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
        }
    }

    val runVoiceAction = remember(voiceDownloadConsented, runWithMicPermission) {
        { action: () -> Unit ->
            if (!voiceDownloadConsented) {
                pendingAction = { runWithMicPermission(action) }
                showVoiceConsent = true
            } else {
                runWithMicPermission(action)
            }
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.updateMicPermission(granted)
    }

    if (showVoiceConsent) {
        AlertDialog(
            onDismissRequest = {
                showVoiceConsent = false
                pendingAction = null
            },
            title = { Text("Download voice models?") },
            text = { Text("Speech needs on-device models (~240 MB). Download once for offline voice.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVoiceConsent = false
                        viewModel.consentVoiceDownload()
                        pendingAction?.invoke()
                        pendingAction = null
                    },
                ) { Text("Download") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVoiceConsent = false
                        pendingAction = null
                    },
                ) { Text("Not now") }
            },
        )
    }

    content(runVoiceAction)
}

private fun runWithMicPermissionImpl(
    context: Context,
    viewModel: ChatViewModel,
    action: () -> Unit,
    onNeedPermission: (() -> Unit) -> Unit,
    launchPermissionRequest: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        viewModel.updateMicPermission(true)
        action()
    } else {
        onNeedPermission(action)
        launchPermissionRequest()
    }
}
