package com.example.ai_agent.data.repository

import com.example.ai_agent.data.local.ChatDao
import com.example.ai_agent.data.local.ChatEntity
import com.example.ai_agent.data.local.MessageEntity
import com.example.ai_agent.data.local.ModelDao
import com.example.ai_agent.data.local.ModelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val modelDao: ModelDao
) {
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    fun getAllModels(): Flow<List<ModelEntity>> = modelDao.getAllModels()

    suspend fun getActiveModel(): ModelEntity? = modelDao.getActiveModel()

    suspend fun addModel(model: ModelEntity) {
        modelDao.activateModel(model)
    }

    suspend fun deleteModel(model: ModelEntity) {
        modelDao.deleteModel(model)
    }

    fun getMessagesForChat(chatId: Long): Flow<List<MessageEntity>> = chatDao.getMessagesForChat(chatId)

    suspend fun createChat(title: String): Long {
        return chatDao.insertChat(ChatEntity(title = title))
    }

    suspend fun getRecentMessages(chatId: Long, limit: Int = 20): List<MessageEntity> =
        chatDao.getRecentMessagesForChat(chatId, limit)

    suspend fun saveMessage(chatId: Long, content: String, isUser: Boolean) {
        chatDao.insertMessage(
            MessageEntity(
                chatId = chatId,
                content = content,
                isUser = isUser
            )
        )
    }

    suspend fun deleteChat(chatId: Long) {
        chatDao.deleteChatWithMessages(chatId)
    }

    suspend fun clearHistory() {
        chatDao.clearAllHistory()
    }

    suspend fun renameChat(chatId: Long, title: String) {
        val chat = chatDao.getChatById(chatId) ?: return
        chatDao.updateChat(chat.copy(title = title))
    }
}
