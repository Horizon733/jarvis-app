package com.example.ai_agent.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    // Every message query filters by chatId and orders by timestamp — without this index
    // getMessagesForChat / getRecentMessagesForChat are full table scans.
    indices = [Index(value = ["chatId", "timestamp"], name = "idx_messages_chatId_timestamp")],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
