package com.example.ai_agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class, ModelEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun modelDao(): ModelDao

    companion object {
        /** v3 → v4: add composite index on messages(chatId, timestamp). Non-destructive. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_messages_chatId_timestamp " +
                        "ON messages (chatId, timestamp)"
                )
            }
        }
    }
}
