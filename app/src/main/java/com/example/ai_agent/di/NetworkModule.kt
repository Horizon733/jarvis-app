package com.example.ai_agent.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.MINUTES)
            .callTimeout(45, TimeUnit.MINUTES)
            .build()
}
