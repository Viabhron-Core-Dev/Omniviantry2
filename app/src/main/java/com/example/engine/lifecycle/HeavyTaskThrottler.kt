package com.example.engine.lifecycle

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import coil.Coil
import com.example.engine.media.ExoPlayerPool
import com.example.engine.omniroot.artifact.ArtifactProviderPool
import com.example.engine.omniroot.local.LocalAiManager
import com.example.utils.LogKeeper
import com.example.utils.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HeavyTaskThrottler freezes/suspends resource-heavy background processes
 * (on-device GGUF llama.cpp inference engine, ExoPlayer media playback,
 * headless artifact WebViews, Voice recognition/TTS, and image caches)
 * to provide maximum RAM headroom for Web AI browser tabs and avoid
 * Android Low Memory Killer (LMK) terminations on low-end devices.
 */
object HeavyTaskThrottler {
    private const val TAG = "HeavyTaskThrottler"

    private val _isSuspended = MutableStateFlow(false)
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    private val _lastFreedMemoryMb = MutableStateFlow(0L)
    val lastFreedMemoryMb: StateFlow<Long> = _lastFreedMemoryMb.asStateFlow()

    /**
     * Deep Hibernation & Memory Evacuation:
     * 1. Release on-device GGUF / llama.cpp model weights from RAM
     * 2. Stop and release any active audio/video media playback in ExoPlayerPool
     * 3. Pause all active headless WebViews in ArtifactProviderPool
     * 4. Halt voice listening and speaking engines
     * 5. Clear Coil image memory cache and bitmap pools
     * 6. Issue ComponentCallbacks2.TRIM_MEMORY_COMPLETE to release UI surfaces & backing buffers
     * 7. Force garbage collection to immediately reclaim freed heap
     */
    fun suspendHeavyTasks(context: Context) {
        val initialMem = getAvailableRamMb(context)
        LogKeeper.log("INFO", TAG, "suspendHeavyTasks: Deep hibernation started for Web AI session. Initial free RAM: ${initialMem}MB")

        // 1. Unload local GGUF/llama.cpp models
        try {
            LocalAiManager.unload()
            LogKeeper.log("INFO", TAG, "LocalAiManager: Model weights unloaded from RAM.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error unloading LocalAiManager: ${e.message}")
        }

        // 2. Stop and release any active media playback
        try {
            ExoPlayerPool.release()
            LogKeeper.log("INFO", TAG, "ExoPlayerPool: Active audio/video sessions released.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error releasing ExoPlayerPool: ${e.message}")
        }

        // 3. Pause headless WebViews
        try {
            ArtifactProviderPool.getInstance(context).pauseAll()
            LogKeeper.log("INFO", TAG, "ArtifactProviderPool: All headless WebViews paused.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error pausing ArtifactProviderPool: ${e.message}")
        }

        // 4. Halt active Voice engines
        try {
            VoiceManager.stopListening()
            VoiceManager.stopSpeaking()
            LogKeeper.log("INFO", TAG, "VoiceManager: Voice listening and TTS stopped.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error stopping VoiceManager: ${e.message}")
        }

        // 5. Clear Coil image and bitmap memory caches
        try {
            Coil.imageLoader(context).memoryCache?.clear()
            LogKeeper.log("INFO", TAG, "Coil: Image memory cache cleared.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error clearing Coil memory cache: ${e.message}")
        }

        // 6. Max system memory trimming: TRIM_MEMORY_COMPLETE
        try {
            val appContext = context.applicationContext
            if (appContext is ComponentCallbacks2) {
                appContext.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
            if (context is ComponentCallbacks2 && context !== appContext) {
                context.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error invoking onTrimMemory: ${e.message}")
        }

        // 7. Force garbage collection and finalizer pass
        try {
            System.gc()
            Runtime.getRuntime().gc()
        } catch (_: Exception) {}

        val afterMem = getAvailableRamMb(context)
        val freed = (afterMem - initialMem).coerceAtLeast(0L)
        _lastFreedMemoryMb.value = freed
        _isSuspended.value = true

        LogKeeper.log("INFO", TAG, "Deep hibernation complete. Available RAM: ${afterMem}MB (approx +${freed}MB reclaimed).")
    }

    /**
     * Restores light services and unpauses background operations when returning to engine mode.
     */
    fun resumeHeavyTasks(context: Context) {
        if (!_isSuspended.value) return
        LogKeeper.log("INFO", TAG, "resumeHeavyTasks: Restoring engine mode from Web AI session.")
        try {
            ArtifactProviderPool.getInstance(context).resumeAll()
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error resuming ArtifactProviderPool: ${e.message}")
        }
        _isSuspended.value = false
    }

    fun getAvailableRamMb(context: Context): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
    }
}
