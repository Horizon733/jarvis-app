package com.example.ai_agent

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.ai_agent.voice.VoiceNativeLibs
import dagger.hilt.android.HiltAndroidApp
import kotlin.concurrent.thread
import javax.inject.Inject

@HiltAndroidApp
class AiAgentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Native libs (~15+ MB) previously loaded synchronously on the main thread, stalling
        // cold-start for every user — even ones who never touch voice. Kick them off in the
        // background; `VoiceNativeLibs.ensureLoaded` internally serialises so any first voice
        // call still completes correctly even if it races this thread.
        thread(name = "voice-native-preload", isDaemon = true, priority = Thread.NORM_PRIORITY) {
            VoiceNativeLibs.ensureLoaded()
        }
    }
}
