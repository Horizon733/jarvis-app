package com.example.ai_agent.di

import android.content.Context
import androidx.room.Room
import com.example.ai_agent.data.local.AppDatabase
import com.example.ai_agent.data.local.ChatDao
import com.example.ai_agent.data.local.ModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ai_agent_db",
        )
            .addMigrations(AppDatabase.MIGRATION_3_4)
            // Safety net: if a schema change ships without a migration we prefer a wipe over a
            // crash-loop. TODO(post-demo): remove this once every schema bump has an explicit
            // Migration and revisit ModelEntity's `path` primary key (currently breaks on
            // any change of files-dir).
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }
}
