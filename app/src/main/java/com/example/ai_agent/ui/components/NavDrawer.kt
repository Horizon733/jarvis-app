package com.example.ai_agent.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ai_agent.data.local.ChatEntity

@Composable
fun NavDrawerContent(
    chats: List<ChatEntity>,
    selectedChatId: Long?,
    onChatClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onRenameChat: (Long, String) -> Unit,
) {
    var chatToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var chatToRenameId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    ModalDrawerSheet(drawerShape = MaterialTheme.shapes.large) {
        Spacer(Modifier.height(12.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("New Chat") },
            selected = false,
            onClick = onNewChatClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "Recent Conversations",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chats, key = { it.id }) { chat ->
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(chat.title, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(
                                onClick = {
                                    chatToRenameId = chat.id
                                    renameText = chat.title
                                },
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(onClick = { chatToDeleteId = chat.id }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    selected = chat.id == selectedChatId,
                    onClick = { onChatClick(chat.id) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        Spacer(Modifier.height(12.dp))
    }

    chats.firstOrNull { it.id == chatToDeleteId }?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDeleteId = null },
            title = { Text("Delete conversation?") },
            text = { Text("This will permanently delete \"${chat.title}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(chat.id)
                        chatToDeleteId = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDeleteId = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    chats.firstOrNull { it.id == chatToRenameId }?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToRenameId = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameChat(chat.id, renameText)
                        chatToRenameId = null
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToRenameId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
