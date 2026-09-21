package com.example.engine.pwa

import org.json.JSONObject

/**
 * Data representation of per-PWA / per-Artifact settings.
 * Serialized to/from ArtifactEntity.settingsJson to persist across sessions.
 */
data class PwaAppSettings(
    val networkThrottle: String = THROTTLE_NO_LIMIT, // NO_LIMIT, FAST_3G, SLOW_3G, EDGE, OFFLINE
    val latencyMs: Long = 0L,
    val keepScreenOn: Boolean = false,
    val orientation: String = "unspecified", // unspecified, portrait, landscape
    val displayMode: String = "standalone"
) {
    companion object {
        const val THROTTLE_NO_LIMIT = "NO_LIMIT"
        const val THROTTLE_FAST_3G = "FAST_3G"
        const val THROTTLE_SLOW_3G = "SLOW_3G"
        const val THROTTLE_EDGE = "EDGE"
        const val THROTTLE_OFFLINE = "OFFLINE"

        fun fromJson(jsonStr: String?): PwaAppSettings {
            if (jsonStr.isNullOrBlank()) return PwaAppSettings()
            return try {
                val obj = JSONObject(jsonStr)
                PwaAppSettings(
                    networkThrottle = obj.optString("networkThrottle", THROTTLE_NO_LIMIT),
                    latencyMs = obj.optLong("latencyMs", 0L),
                    keepScreenOn = obj.optBoolean("keepScreenOn", false),
                    orientation = obj.optString("orientation", "unspecified"),
                    displayMode = obj.optString("displayMode", "standalone")
                )
            } catch (e: Exception) {
                PwaAppSettings()
            }
        }

        fun toJson(settings: PwaAppSettings): String {
            val obj = JSONObject()
            obj.put("networkThrottle", settings.networkThrottle)
            obj.put("latencyMs", settings.latencyMs)
            obj.put("keepScreenOn", settings.keepScreenOn)
            obj.put("orientation", settings.orientation)
            obj.put("displayMode", settings.displayMode)
            return obj.toString()
        }

        fun getPresetLatency(throttle: String): Long {
            return when (throttle) {
                THROTTLE_FAST_3G -> 150L
                THROTTLE_SLOW_3G -> 450L
                THROTTLE_EDGE -> 850L
                THROTTLE_OFFLINE -> 0L
                else -> 0L
            }
        }
    }
}
