package com.example.ai_agent

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai_agent.ui.chat.ChatScreen
import com.example.ai_agent.ui.chat.ChatViewModel
import com.example.ai_agent.ui.components.NavDrawerContent
import com.example.ai_agent.ui.jarvis.JarvisHudScreen
import com.example.ai_agent.ui.navigation.AppRoute
import com.example.ai_agent.ui.settings.SettingsScreen
import com.example.ai_agent.ui.theme.AiAgentTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // Read `jarvisHudEnabled` OUTSIDE the theme wrapper so the whole app (drawer,
            // dialogs, snackbars, Settings screen) re-themes when the toggle flips, not
            // just the HUD screen.
            val chatViewModel: ChatViewModel = hiltViewModel()
            val jarvisHudEnabled by chatViewModel.jarvisHudEnabled.collectAsStateWithLifecycle()
            AiAgentTheme(jarvisMode = jarvisHudEnabled) {
                MainApp(chatViewModel = chatViewModel, jarvisHudEnabled = jarvisHudEnabled)
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    @Composable
    fun MainApp(chatViewModel: ChatViewModel, jarvisHudEnabled: Boolean) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val chats by chatViewModel.chats.collectAsStateWithLifecycle()
        val selectedChatId by chatViewModel.currentChatId.collectAsStateWithLifecycle()
        var route by rememberSaveable { mutableStateOf(AppRoute.Home) }

        // Observe process-wide lifecycle instead of hand-plumbing onBackground/onForeground
        // fields on the Activity. This is the standard Android pattern for app-wide fg/bg
        // notifications and correctly handles configuration changes.
        DisposableEffect(chatViewModel) {
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    chatViewModel.onAppForegrounded()
                }
                override fun onStop(owner: LifecycleOwner) {
                    chatViewModel.onAppBackgrounded()
                }
            }
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        when (route) {
            AppRoute.Settings -> {
                BackHandler { route = AppRoute.Home }
                SettingsScreen(onBackClick = { route = AppRoute.Home })
            }

            AppRoute.Home -> {
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        NavDrawerContent(
                            chats = chats,
                            selectedChatId = selectedChatId,
                            onChatClick = { chatId ->
                                chatViewModel.selectChat(chatId)
                                scope.launch { drawerState.close() }
                            },
                            onNewChatClick = {
                                chatViewModel.startNewChat()
                                scope.launch { drawerState.close() }
                            },
                            onSettingsClick = {
                                scope.launch { drawerState.close() }
                                route = AppRoute.Settings
                            },
                            onDeleteChat = chatViewModel::deleteChat,
                            onRenameChat = chatViewModel::renameChat,
                        )
                    },
                ) {
                    if (jarvisHudEnabled) {
                        JarvisHudScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            viewModel = chatViewModel,
                        )
                    } else {
                        ChatScreen(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            viewModel = chatViewModel,
                        )
                    }
                }
            }
        }
    }
}
