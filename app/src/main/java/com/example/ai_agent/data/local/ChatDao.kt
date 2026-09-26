package com.example.ai_agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY createdAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: Long): ChatEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit
        ) ORDER BY timestamp ASC
        """,
    )
    suspend fun getRecentMessagesForChat(chatId: Long, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Transaction
    suspend fun deleteChatWithMessages(chatId: Long) {
        deleteMessagesForChat(chatId)
        deleteChat(chatId)
    }

    @Transaction
    suspend fun clearAllHistory() {
        deleteAllMessages()
        deleteAllChats()
    }
}
