package com.example.ai_agent.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_agent.util.DownloadStatus
import com.example.ai_agent.util.CuratedModels
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val inferenceSettings by viewModel.inferenceSettings.collectAsStateWithLifecycle()
    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()
    val downloadStatus by viewModel.downloadStatus.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val jarvisHudEnabled by viewModel.jarvisHudEnabled.collectAsStateWithLifecycle()

    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var modelToDeletePath by rememberSaveable { mutableStateOf<String?>(null) }
    var modelUrl by rememberSaveable { mutableStateOf("") }
    var downloadFileName by rememberSaveable { mutableStateOf("model.gguf") }
    var hfTokenInput by rememberSaveable(hfToken) { mutableStateOf(hfToken ?: "") }

    // Slider state pattern:
    //   - Local state owns the current thumb position (so drags are smooth).
    //   - DataStore-backed `inferenceSettings` is pulled in via LaunchedEffect, but only
    //     when the user isn't currently dragging that slider — otherwise a mid-drag
    //     DataStore emission would visibly snap the thumb back to the stored value.
    //   - onValueChangeFinished commits to DataStore; next emission is equal to the local
    //     value so the LaunchedEffect sync is a no-op.
    // The original code keyed `rememberSaveable` on the value itself, which meant every
    // DataStore write reset the local state — same visible snap-back bug.
    var temperature by rememberSaveable { mutableStateOf(inferenceSettings.temperature) }
    var topK by rememberSaveable { mutableStateOf(inferenceSettings.topK) }
    var maxTokens by rememberSaveable { mutableStateOf(inferenceSettings.maxTokens) }
    var draggingTemperature by remember { mutableStateOf(false) }
    var draggingTopK by remember { mutableStateOf(false) }
    var draggingMaxTokens by remember { mutableStateOf(false) }
    LaunchedEffect(inferenceSettings) {
        if (!draggingTemperature) temperature = inferenceSettings.temperature
        if (!draggingTopK) topK = inferenceSettings.topK
        if (!draggingMaxTokens) maxTokens = inferenceSettings.maxTokens
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jarvis HUD", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Cyan holographic HUD with circular display. Standard chat UI when off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = jarvisHudEnabled,
                        onCheckedChange = viewModel::setJarvisHudEnabled,
                    )
                }
            }

            SettingsSection(title = "Model Configuration") {
                Text(
                    "Active: ${activeModel?.name ?: "None selected"}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                downloadStatus?.let { status ->
                    when (status) {
                        is DownloadStatus.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text("Downloading ${status.modelName}")
                                Text(
                                    "${status.progress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LinearProgressIndicator(
                                    progress = { status.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(
                                    onClick = viewModel::cancelDownload,
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Stop (resume later)")
                                }
                            }
                        }
                        is DownloadStatus.Error -> {
                            Text(
                                "Download error: ${status.message}",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        is DownloadStatus.Success -> {
                            Text("Download complete", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (importing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (models.isNotEmpty()) {
                    Text("Saved models", style = MaterialTheme.typography.labelMedium)
                    models.forEach { model ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = model.path == activeModel?.path,
                                    onClick = { viewModel.selectModel(model) },
                                )
                                Text(model.name, modifier = Modifier.padding(start = 8.dp))
                            }
                            IconButton(onClick = { modelToDeletePath = model.path }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete model")
                            }
                        }
                    }
                }

                Button(
                    onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                    enabled = !importing,
                ) {
                    Text("Import .gguf file")
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Recommended Q4 models", style = MaterialTheme.typography.labelMedium)
                CuratedModels.all.forEach { model ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(model.description, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { viewModel.startDownload(model) },
                                enabled = downloadStatus !is DownloadStatus.Downloading,
                            ) {
                                Text("Download (${model.estimatedSizeMb} MB)")
                            }
                        }
                    }
                }

                Text("Download a GGUF model", style = MaterialTheme.typography.labelMedium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Paste a direct HTTPS link to an instruction-tuned .gguf model.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelUrl,
                            onValueChange = { modelUrl = it },
                            label = { Text("Model URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = downloadFileName,
                            onValueChange = { downloadFileName = it },
                            label = { Text("Save as") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.startDownload(modelUrl, downloadFileName) },
                            enabled = modelUrl.startsWith("https://") &&
                                downloadFileName.endsWith(".gguf", ignoreCase = true) &&
                                downloadStatus !is DownloadStatus.Downloading,
                        ) {
                            Text("Download model")
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Text("Hugging Face token (optional)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = hfTokenInput,
                    onValueChange = { hfTokenInput = it },
                    label = { Text("HF token for gated models") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { viewModel.saveHfToken(hfTokenInput) }) {
                    Text("Save token")
                }
            }

            SettingsSection(title = "Inference Parameters") {
                Text("Temperature: ${String.format(Locale.getDefault(), "%.1f", temperature)}")
                Slider(
                    value = temperature,
                    onValueChange = {
                        draggingTemperature = true
                        temperature = it
                    },
                    onValueChangeFinished = {
                        viewModel.updateInferenceSettings(inferenceSettings.copy(temperature = temperature))
                        draggingTemperature = false
                    },
                    valueRange = 0.1f..2.0f,
                )

                Text("Top K: $topK")
                Slider(
                    value = topK.toFloat(),
                    onValueChange = {
                        draggingTopK = true
                        topK = it.toInt()
                    },
                    onValueChangeFinished = {
                        viewModel.updateInferenceSettings(inferenceSettings.copy(topK = topK))
                        draggingTopK = false
                    },
                    valueRange = 1f..100f,
                )

                Text("Max Tokens: $maxTokens")
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = {
                        draggingMaxTokens = true
                        maxTokens = it.toInt()
                    },
                    onValueChangeFinished = {
                        viewModel.updateInferenceSettings(inferenceSettings.copy(maxTokens = maxTokens))
                        draggingMaxTokens = false
                    },
                    valueRange = 64f..2048f,
                )
            }

            SettingsSection(title = "Data Management") {
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Clear All History", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All History") },
            text = { Text("Are you sure you want to delete all conversations? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirm = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    models.firstOrNull { it.path == modelToDeletePath }?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDeletePath = null },
            title = { Text("Delete model?") },
            text = { Text("This will remove \"${model.name}\" from the device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(model)
                        modelToDeletePath = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDeletePath = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
