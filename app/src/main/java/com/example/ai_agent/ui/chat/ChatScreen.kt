package com.example.ai_agent.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.example.ai_agent.domain.model.LlmState
import com.example.ai_agent.util.ModelImportPhase
import com.example.ai_agent.voice.VoiceLanguage
import com.example.ai_agent.voice.VoiceState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onMenuClick: () -> Unit,
    viewModel: ChatViewModel,
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingResponse by viewModel.currentStreamingResponse.collectAsStateWithLifecycle()
    val llmState by viewModel.llmState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val importPhase by viewModel.importPhase.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle()
    val wakeWordEnabled by viewModel.wakeWordEnabled.collectAsStateWithLifecycle()
    val ttsEnabled by viewModel.ttsEnabled.collectAsStateWithLifecycle()
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()
    val voiceDownloadConsented by viewModel.voiceDownloadConsented.collectAsStateWithLifecycle()
    val activeModelName by viewModel.activeModelName.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isModelReady = llmState is LlmState.Ready
    val view = LocalView.current

    DisposableEffect(voiceState, view) {
        view.keepScreenOn = voiceState == VoiceState.Speaking
        onDispose { view.keepScreenOn = false }
    }

    var pendingVoiceAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showVoiceConsent by rememberSaveable { mutableStateOf(false) }
    var consentVoiceOnboarding by rememberSaveable { mutableStateOf(true) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.updateMicPermission(granted)
        if (granted) {
            pendingVoiceAction?.invoke()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice")
            }
        }
        pendingVoiceAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.updateMicPermission(true)
            action()
        } else {
            pendingVoiceAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun runVoiceAction(action: () -> Unit) {
        if (!voiceDownloadConsented) {
            pendingVoiceAction = { runWithMicPermission(action) }
            showVoiceConsent = true
            return
        }
        runWithMicPermission(action)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.updateMicPermission(granted)
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                msg.action?.invoke()
            }
            viewModel.clearUserMessage()
        }
    }

    var stickToBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total == 0 || lastVisible >= total - 2
        }.collect { atBottom ->
            stickToBottom = atBottom
        }
    }

    LaunchedEffect(messages.size, isGenerating, streamingResponse.isNotEmpty(), stickToBottom) {
        if (!stickToBottom) return@LaunchedEffect
        val target = when {
            streamingResponse.isNotEmpty() -> messages.size
            isGenerating -> messages.size
            messages.isNotEmpty() -> messages.size - 1
            else -> return@LaunchedEffect
        }
        if (target >= 0) {
            listState.scrollToItem(target)
        }
    }

    // Follow the streaming response reactively rather than polling every 100 ms. The old
    // `while(true){ delay(100); … }` loop was a battery/CPU drain during every generation
    // and kept recomposing the list even after the user scrolled away.
    LaunchedEffect(listState) {
        snapshotFlow { streamingResponse }
            .collect { text ->
                if (text.isNotEmpty() && stickToBottom) {
                    listState.scrollToItem(messages.size)
                }
            }
    }

    if (!onboardingComplete) {
        AlertDialog(
            onDismissRequest = { viewModel.completeOnboarding(false) },
            title = { Text("Welcome to AI Agent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This app runs AI chat entirely on your device. " +
                            "Pick or download a GGUF model to get started.",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = consentVoiceOnboarding,
                            onCheckedChange = { consentVoiceOnboarding = it },
                        )
                        Text(
                            "Download voice models (~240 MB) for speech input and TTS",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.completeOnboarding(consentVoiceOnboarding) },
                ) {
                    Text("Get started")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.completeOnboarding(false) }) {
                    Text("Not now")
                }
            },
        )
    }

    if (showVoiceConsent) {
        AlertDialog(
            onDismissRequest = {
                showVoiceConsent = false
                pendingVoiceAction = null
            },
            title = { Text("Download voice models?") },
            text = {
                Text(
                    "Speech features need about 240 MB of on-device models (STT + TTS). " +
                        "Download once, then voice works offline.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVoiceConsent = false
                        viewModel.consentVoiceDownload()
                        pendingVoiceAction?.invoke()
                        pendingVoiceAction = null
                    },
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showVoiceConsent = false
                        pendingVoiceAction = null
                    },
                ) {
                    Text("Not now")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Agent")
                        activeModelName?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ModelMetadataPanel(
                metadata = modelMetadata,
                importPhase = importPhase,
                isImporting = isImporting,
                llmState = llmState,
            )

            VoiceControlBar(
                voiceState = voiceState,
                voiceLanguage = voiceLanguage,
                wakeWordEnabled = wakeWordEnabled,
                ttsEnabled = ttsEnabled,
                isModelReady = isModelReady,
                onLanguageSelected = viewModel::setVoiceLanguage,
                onWakeWordToggle = { enabled ->
                    if (enabled) {
                        runVoiceAction { viewModel.setWakeWordEnabled(true) }
                    } else {
                        viewModel.setWakeWordEnabled(false)
                    }
                },
                onTtsChanged = viewModel::setTtsEnabled,
                onStopSpeaking = viewModel::stopSpeaking,
            )

            Box(modifier = Modifier.weight(1f)) {
                if (!isModelReady && messages.isEmpty() && streamingResponse.isEmpty()) {
                    ModelMissingView(
                        onPickModel = { modelPickerLauncher.launch(arrayOf("*/*")) },
                        isImporting = isImporting,
                    )
                } else if (messages.isEmpty() && streamingResponse.isEmpty()) {
                    EmptyChatView()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message.content, message.isUser)
                        }
                        if (streamingResponse.isNotEmpty()) {
                            item(key = "streaming-response") {
                                MessageBubble(streamingResponse, false)
                            }
                        }
                        if (isGenerating && streamingResponse.isEmpty()) {
                            item(key = "typing-indicator") {
                                TypingIndicator()
                            }
                        }
                    }
                }
            }

            ChatInput(
                onSend = { viewModel.sendMessage(it) },
                onStop = { viewModel.stopGeneration() },
                onPickModel = { modelPickerLauncher.launch(arrayOf("*/*")) },
                onMicClick = {
                    when (voiceState) {
                        VoiceState.Speaking -> viewModel.stopSpeaking()
                        else -> runVoiceAction { viewModel.toggleVoiceRecording() }
                    }
                },
                onStopSpeaking = viewModel::stopSpeaking,
                isGenerating = isGenerating,
                isModelReady = isModelReady,
                isImporting = isImporting,
                voiceState = voiceState,
            )
        }
    }
}

@Composable
private fun ModelMetadataPanel(
    metadata: String?,
    importPhase: ModelImportPhase?,
    isImporting: Boolean,
    llmState: LlmState,
) {
    val statusText = when {
        llmState is LlmState.Ready -> "Model ready"
        importPhase is ModelImportPhase.Failed -> "Import failed: ${importPhase.message}"
        importPhase == ModelImportPhase.Parsing -> "Parsing GGUF metadata..."
        importPhase == ModelImportPhase.Copying -> "Copying model into app storage..."
        importPhase == ModelImportPhase.Loading || llmState is LlmState.Loading -> "Loading model..."
        else -> null
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = metadata ?: "Pick a GGUF model file to see metadata here.",
                style = MaterialTheme.typography.bodySmall,
            )
            statusText?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        llmState is LlmState.Ready -> MaterialTheme.colorScheme.tertiary
                        importPhase is ModelImportPhase.Failed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (isImporting) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun MessageBubble(content: String, isUser: Boolean) {
    val backgroundColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    val maxBubbleWidth = if (isUser) 280.dp else 340.dp

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = backgroundColor,
            shape = shape,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = maxBubbleWidth),
        ) {
            if (isUser) {
                Text(
                    text = content,
                    color = textColor,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                AssistantMarkdownMessage(content = content, textColor = textColor)
            }
        }
    }
}

@Composable
private fun AssistantMarkdownMessage(content: String, textColor: Color) {
    val context = LocalContext.current
    MarkdownText(
        markdown = content,
        modifier = Modifier.padding(12.dp),
        style = TextStyle(
            color = textColor,
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        ),
        linkColor = MaterialTheme.colorScheme.primary,
        isTextSelectable = true,
        onLinkClicked = { url ->
            // ACTION_VIEW can throw ActivityNotFoundException for `intent:` / `mailto:` /
            // malformed URIs, and on emulators without a browser. Swallow rather than crash.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
    )
}

@Composable
private fun VoiceControlBar(
    voiceState: VoiceState,
    voiceLanguage: VoiceLanguage,
    wakeWordEnabled: Boolean,
    ttsEnabled: Boolean,
    isModelReady: Boolean,
    onLanguageSelected: (VoiceLanguage) -> Unit,
    onWakeWordToggle: (Boolean) -> Unit,
    onTtsChanged: (Boolean) -> Unit,
    onStopSpeaking: () -> Unit,
) {
    val statusText = when (voiceState) {
        VoiceState.Idle -> null
        VoiceState.DownloadingModels -> "Preparing voice models (first time, ~240 MB)..."
        is VoiceState.Installing -> "Installing ${voiceState.label}..."
        is VoiceState.Downloading -> {
            val size = if (voiceState.totalMb > 0) {
                "${voiceState.downloadedMb}/${voiceState.totalMb} MB"
            } else {
                "${voiceState.downloadedMb} MB"
            }
            if (voiceState.progress > 0) {
                "Downloading ${voiceState.label}: ${voiceState.progress}% ($size)"
            } else {
                "Downloading ${voiceState.label}: $size"
            }
        }
        VoiceState.ListeningForWakeWord -> "Listening for \"Hey Jarvis\"..."
        VoiceState.Recording -> "Recording — tap mic to stop"
        VoiceState.Transcribing -> "Transcribing..."
        VoiceState.Speaking -> "Speaking — tap Stop to interrupt"
        is VoiceState.Error -> voiceState.message
    }

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = voiceLanguage == VoiceLanguage.ENGLISH,
                    onClick = { onLanguageSelected(VoiceLanguage.ENGLISH) },
                    label = { Text("EN") },
                    enabled = isModelReady,
                )
                if (VoiceLanguage.HINDI_ENABLED) {
                    FilterChip(
                        selected = voiceLanguage == VoiceLanguage.HINDI,
                        onClick = { onLanguageSelected(VoiceLanguage.HINDI) },
                        label = { Text("HI") },
                        enabled = isModelReady,
                    )
                }
                FilterChip(
                    selected = wakeWordEnabled,
                    onClick = { onWakeWordToggle(!wakeWordEnabled) },
                    label = { Text("Wake word") },
                    enabled = isModelReady,
                )
                FilterChip(
                    selected = ttsEnabled,
                    onClick = { onTtsChanged(!ttsEnabled) },
                    label = { Text("TTS") },
                    enabled = isModelReady,
                )
                if (voiceState == VoiceState.Speaking) {
                    FilledTonalButton(onClick = onStopSpeaking) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
            statusText?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (voiceState is VoiceState.Error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (voiceState is VoiceState.Downloading ||
                voiceState is VoiceState.DownloadingModels ||
                voiceState is VoiceState.Installing
            ) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
    HorizontalDivider()
}

@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onPickModel: () -> Unit,
    onMicClick: () -> Unit,
    onStopSpeaking: () -> Unit,
    isGenerating: Boolean,
    isModelReady: Boolean,
    isImporting: Boolean,
    voiceState: VoiceState,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val isSpeaking = voiceState == VoiceState.Speaking

    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (isModelReady) "Type a message..." else "Pick a GGUF model file to start chatting.",
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                enabled = isModelReady && !isGenerating && !isImporting && !isSpeaking,
            )

            if (isGenerating) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.primary)
                }
            } else if (isSpeaking) {
                IconButton(onClick = onStopSpeaking) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop speaking", tint = MaterialTheme.colorScheme.error)
                }
            } else if (!isModelReady) {
                FloatingActionButton(
                    onClick = { if (!isImporting) onPickModel() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Pick GGUF model")
                }
            } else {
                val voiceBusy = voiceState is VoiceState.Downloading ||
                    voiceState is VoiceState.DownloadingModels ||
                    voiceState is VoiceState.Installing
                IconButton(
                    onClick = onMicClick,
                    enabled = !isImporting && !voiceBusy,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (voiceState == VoiceState.Recording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSend(text)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun EmptyChatView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Good evening.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            "JARVIS is online and ready to assist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}

@Composable
fun ModelMissingView(onPickModel: () -> Unit, isImporting: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No model loaded", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import a GGUF model from Downloads, Files, or another app. " +
                "The app will read metadata, copy the file locally, and load it for on-device chat.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPickModel, enabled = !isImporting) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pick GGUF file")
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "AI is thinking...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
