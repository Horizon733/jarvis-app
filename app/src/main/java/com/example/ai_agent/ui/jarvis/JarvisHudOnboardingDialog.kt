package com.example.ai_agent.ui.jarvis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun JarvisHudOnboardingDialog(
    consentVoiceOnboarding: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to AI Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick or download a GGUF model to get started.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = consentVoiceOnboarding,
                        onCheckedChange = onConsentChange,
                    )
                    Text("Download voice models for speech", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Get started")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
    )
}
