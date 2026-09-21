package com.example.engine.lifecycle

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import com.example.engine.media.ExoPlayerPool
import com.example.engine.omniroot.local.LocalAiManager
import com.example.utils.LogKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HeavyTaskThrottler freezes/suspends resource-heavy background processes
 * (on-device GGUF llama.cpp inference engine, ExoPlayer media playback,
 * background database sync, and caches) to provide maximum RAM headroom
 * for Web AI browser tabs and avoid Android Low Memory Killer (LMK) terminations.
 */
object HeavyTaskThrottler {
    private const val TAG = "HeavyTaskThrottler"

    private val _isSuspended = MutableStateFlow(false)
    val isSuspended: StateFlow<Boolean> = _isSuspended.asStateFlow()

    private val _lastFreedMemoryMb = MutableStateFlow(0L)
    val lastFreedMemoryMb: StateFlow<Long> = _lastFreedMemoryMb.asStateFlow()

    /**
     * Suspend heavy tasks:
     * 1. Release on-device GGUF / llama.cpp model weights from RAM
     * 2. Stop and release any active audio/video media playback in ExoPlayerPool
     * 3. Invoke ComponentCallbacks2 memory trimming to drop bitmap/view caches
     * 4. Request garbage collection to immediately reclaim freed heap
     */
    fun suspendHeavyTasks(context: Context) {
        val initialMem = getAvailableRamMb(context)
        LogKeeper.log("INFO", TAG, "suspendHeavyTasks: Suspending heavy tasks to prepare for Web AI Browser session. Current free RAM: ${initialMem}MB")

        try {
            // 1. Unload local GGUF/llama.cpp models
            LocalAiManager.unload()
            LogKeeper.log("INFO", TAG, "LocalAiManager: Model weights unloaded from RAM.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error unloading LocalAiManager: ${e.message}")
        }

        try {
            // 2. Stop and release any active media playback
            ExoPlayerPool.release()
            LogKeeper.log("INFO", TAG, "ExoPlayerPool: Active audio/video sessions released.")
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error releasing ExoPlayerPool: ${e.message}")
        }

        try {
            // 3. System memory trimming
            val appContext = context.applicationContext
            if (appContext is ComponentCallbacks2) {
                appContext.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            } else if (context is ComponentCallbacks2) {
                context.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
            }
        } catch (e: Exception) {
            LogKeeper.log("WARNING", TAG, "Error invoking onTrimMemory: ${e.message}")
        }

        // 4. Force garbage collection to reclaim memory immediately
        System.gc()
        Runtime.getRuntime().gc()

        val afterMem = getAvailableRamMb(context)
        val freed = (afterMem - initialMem).coerceAtLeast(0L)
        _lastFreedMemoryMb.value = freed
        _isSuspended.value = true

        LogKeeper.log("INFO", TAG, "suspendHeavyTasks completed. Available RAM: ${afterMem}MB (approx +${freed}MB reclaimed).")
    }

    /**
     * Restores light services and clears the suspended state when returning to engine mode.
     */
    fun resumeHeavyTasks(context: Context) {
        if (!_isSuspended.value) return
        LogKeeper.log("INFO", TAG, "resumeHeavyTasks: Restoring engine mode from Web AI session.")
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
