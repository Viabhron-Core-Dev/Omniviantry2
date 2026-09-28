package com.example.engine.brain

import android.content.Context
import android.content.SharedPreferences
import com.example.utils.LogKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mini-Phase 11.7: Brain Settings Store.
 * Manages persistent preferences for Chain-of-Thought thinking visibility,
 * semantic memory recall scoring threshold, and context budget monitoring.
 */
object BrainSettingsStore {

    private const val PREFS_NAME = "omnivian_brain_settings"
    private const val KEY_SHOW_THINKING = "show_thinking_process"
    private const val KEY_RECALL_THRESHOLD = "memory_recall_threshold"
    private const val KEY_MAX_RECALL_LIMIT = "memory_max_recall_limit"

    private val _showThinkingFlow = MutableStateFlow(true)
    val showThinkingFlow: StateFlow<Boolean> = _showThinkingFlow.asStateFlow()

    private val _recallThresholdFlow = MutableStateFlow(1.0f)
    val recallThresholdFlow: StateFlow<Float> = _recallThresholdFlow.asStateFlow()

    private var initialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        if (initialized) return
        val prefs = getPrefs(context)
        _showThinkingFlow.value = prefs.getBoolean(KEY_SHOW_THINKING, true)
        _recallThresholdFlow.value = prefs.getFloat(KEY_RECALL_THRESHOLD, 1.0f)
        initialized = true
    }

    fun isThinkingVisible(context: Context): Boolean {
        init(context)
        return _showThinkingFlow.value
    }

    fun setShowThinkingProcess(context: Context, visible: Boolean) {
        init(context)
        getPrefs(context).edit().putBoolean(KEY_SHOW_THINKING, visible).apply()
        _showThinkingFlow.value = visible
        LogKeeper.log("BrainSettings", "ToggleThinking", "Updated showThinkingProcess to $visible")
    }

    fun getRecallThreshold(context: Context): Float {
        init(context)
        return _recallThresholdFlow.value
    }

    fun setRecallThreshold(context: Context, threshold: Float) {
        init(context)
        val clamped = threshold.coerceIn(0.1f, 3.0f)
        getPrefs(context).edit().putFloat(KEY_RECALL_THRESHOLD, clamped).apply()
        _recallThresholdFlow.value = clamped
        LogKeeper.log("BrainSettings", "UpdateThreshold", "Updated memory recall threshold to $clamped")
    }

    fun getMaxRecallLimit(context: Context): Int {
        init(context)
        return getPrefs(context).getInt(KEY_MAX_RECALL_LIMIT, 5)
    }

    fun setMaxRecallLimit(context: Context, limit: Int) {
        init(context)
        val clamped = limit.coerceIn(1, 20)
        getPrefs(context).edit().putInt(KEY_MAX_RECALL_LIMIT, clamped).apply()
        LogKeeper.log("BrainSettings", "UpdateRecallLimit", "Updated max memory recall limit to $clamped")
    }
}
