package com.example.engine.webaiprovider

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object WebAiProviderManager {
    private const val PREFS_NAME = "web_ai_providers_prefs"
    private const val KEY_SERVICES = "configured_services_json"
    private const val KEY_LAST_ACTIVE_URL = "last_active_url"
    private const val KEY_LAST_ACTIVE_SERVICE_NAME = "last_active_service_name"

    private val _services = MutableStateFlow<List<WebAiService>>(emptyList())
    val services: StateFlow<List<WebAiService>> = _services.asStateFlow()

    private val _openTabCount = MutableStateFlow(0)
    val openTabCount: StateFlow<Int> = _openTabCount.asStateFlow()

    private val _lastActiveUrl = MutableStateFlow<String?>(null)
    val lastActiveUrl: StateFlow<String?> = _lastActiveUrl.asStateFlow()

    private val _lastActiveServiceName = MutableStateFlow<String?>(null)
    val lastActiveServiceName: StateFlow<String?> = _lastActiveServiceName.asStateFlow()

    fun updateOpenTabCount(count: Int) {
        _openTabCount.value = count.coerceAtLeast(0)
    }

    fun recordActiveLaunch(serviceName: String, url: String) {
        _lastActiveUrl.value = url
        _lastActiveServiceName.value = serviceName
        prefs?.edit()
            ?.putString(KEY_LAST_ACTIVE_URL, url)
            ?.putString(KEY_LAST_ACTIVE_SERVICE_NAME, serviceName)
            ?.apply()
    }

    val defaultPresets = listOf(
        WebAiPresetBundle(
            id = "preset_research_duo",
            title = "Research Duo",
            subtitle = "ChatGPT + Perplexity",
            iconName = "Search",
            serviceIds = listOf("chatgpt", "perplexity")
        ),
        WebAiPresetBundle(
            id = "preset_coding_triad",
            title = "Coding Triad",
            subtitle = "Claude + ChatGPT + DeepSeek",
            iconName = "Code",
            serviceIds = listOf("claude", "chatgpt", "deepseek")
        ),
        WebAiPresetBundle(
            id = "preset_reasoning_stack",
            title = "Reasoning Stack",
            subtitle = "DeepSeek + Claude + Grok",
            iconName = "Psychology",
            serviceIds = listOf("deepseek", "claude", "grok")
        ),
        WebAiPresetBundle(
            id = "preset_all_enabled",
            title = "All Active",
            subtitle = "All enabled providers",
            iconName = "Layers",
            serviceIds = emptyList() // dynamic: resolves to all enabled
        )
    )

    private var prefs: SharedPreferences? = null

    private val defaultServices = listOf(
        WebAiService(
            id = "chatgpt",
            name = "ChatGPT",
            baseUrl = "https://chatgpt.com",
            brandColorHex = "#10A37F",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_chatgpt_default",
                    serviceId = "chatgpt",
                    label = "Default Account",
                    launchUrl = "https://chatgpt.com",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        ),
        WebAiService(
            id = "claude",
            name = "Claude",
            baseUrl = "https://claude.ai",
            brandColorHex = "#D97706",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_claude_default",
                    serviceId = "claude",
                    label = "Default Account",
                    launchUrl = "https://claude.ai",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        ),
        WebAiService(
            id = "perplexity",
            name = "Perplexity",
            baseUrl = "https://perplexity.ai",
            brandColorHex = "#22B8CF",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_perplexity_default",
                    serviceId = "perplexity",
                    label = "Default Account",
                    launchUrl = "https://perplexity.ai",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        ),
        WebAiService(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://gemini.google.com",
            brandColorHex = "#1A73E8",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_gemini_default",
                    serviceId = "gemini",
                    label = "Default Account",
                    launchUrl = "https://gemini.google.com",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        ),
        WebAiService(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://chat.deepseek.com",
            brandColorHex = "#4D6BFE",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_deepseek_default",
                    serviceId = "deepseek",
                    label = "Default Account",
                    launchUrl = "https://chat.deepseek.com",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        ),
        WebAiService(
            id = "grok",
            name = "Grok",
            baseUrl = "https://grok.com",
            brandColorHex = "#111827",
            enabled = true,
            profiles = listOf(
                WebAiAccountProfile(
                    id = "profile_grok_default",
                    serviceId = "grok",
                    label = "Default Account",
                    launchUrl = "https://grok.com",
                    sessionMode = WebAiSessionMode.DEFAULT
                )
            )
        )
    )

    fun init(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _lastActiveUrl.value = sharedPrefs.getString(KEY_LAST_ACTIVE_URL, null)
        _lastActiveServiceName.value = sharedPrefs.getString(KEY_LAST_ACTIVE_SERVICE_NAME, null)
        loadServices()
    }

    private fun loadServices() {
        val jsonString = prefs?.getString(KEY_SERVICES, null)
        if (jsonString.isNullOrBlank()) {
            _services.value = defaultServices
            saveServices(defaultServices)
            return
        }

        try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<WebAiService>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val profilesList = mutableListOf<WebAiAccountProfile>()
                val profilesArray = obj.optJSONArray("profiles") ?: JSONArray()
                for (j in 0 until profilesArray.length()) {
                    val pObj = profilesArray.getJSONObject(j)
                    profilesList.add(
                        WebAiAccountProfile(
                            id = pObj.getString("id"),
                            serviceId = pObj.getString("serviceId"),
                            label = pObj.getString("label"),
                            launchUrl = pObj.getString("launchUrl"),
                            sessionMode = runCatching {
                                WebAiSessionMode.valueOf(pObj.optString("sessionMode", "DEFAULT"))
                            }.getOrDefault(WebAiSessionMode.DEFAULT),
                            targetPackage = if (pObj.has("targetPackage") && !pObj.isNull("targetPackage")) {
                                pObj.getString("targetPackage")
                            } else null
                        )
                    )
                }

                list.add(
                    WebAiService(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        baseUrl = obj.getString("baseUrl"),
                        brandColorHex = obj.optString("brandColorHex", "#1A73E8"),
                        enabled = obj.optBoolean("enabled", true),
                        activeProfileId = if (obj.has("activeProfileId") && !obj.isNull("activeProfileId")) {
                            obj.getString("activeProfileId")
                        } else profilesList.firstOrNull()?.id,
                        profiles = profilesList
                    )
                )
            }
            _services.value = if (list.isEmpty()) defaultServices else list
        } catch (e: Exception) {
            _services.value = defaultServices
        }
    }

    private fun saveServices(servicesToSave: List<WebAiService>) {
        try {
            val jsonArray = JSONArray()
            for (svc in servicesToSave) {
                val obj = JSONObject().apply {
                    put("id", svc.id)
                    put("name", svc.name)
                    put("baseUrl", svc.baseUrl)
                    put("brandColorHex", svc.brandColorHex)
                    put("enabled", svc.enabled)
                    put("activeProfileId", svc.activeProfileId ?: JSONObject.NULL)
                    val profArr = JSONArray()
                    for (p in svc.profiles) {
                        val pObj = JSONObject().apply {
                            put("id", p.id)
                            put("serviceId", p.serviceId)
                            put("label", p.label)
                            put("launchUrl", p.launchUrl)
                            put("sessionMode", p.sessionMode.name)
                            put("targetPackage", p.targetPackage ?: JSONObject.NULL)
                        }
                        profArr.put(pObj)
                    }
                    put("profiles", profArr)
                }
                jsonArray.put(obj)
            }
            prefs?.edit()?.putString(KEY_SERVICES, jsonArray.toString())?.apply()
        } catch (e: Exception) {
            // Ignore write failures
        }
    }

    fun setActiveProfileId(serviceId: String, profileId: String) {
        val updated = _services.value.map {
            if (it.id == serviceId) it.copy(activeProfileId = profileId) else it
        }
        _services.value = updated
        saveServices(updated)
    }

    fun toggleServiceEnabled(serviceId: String, enabled: Boolean) {
        val updated = _services.value.map {
            if (it.id == serviceId) it.copy(enabled = enabled) else it
        }
        _services.value = updated
        saveServices(updated)
    }

    fun addCustomService(name: String, baseUrl: String, brandColorHex: String = "#1A73E8") {
        val newId = "custom_${UUID.randomUUID().toString().take(8)}"
        val defaultProfile = WebAiAccountProfile(
            id = "profile_${newId}_default",
            serviceId = newId,
            label = "Default Account",
            launchUrl = baseUrl,
            sessionMode = WebAiSessionMode.DEFAULT
        )
        val newService = WebAiService(
            id = newId,
            name = name,
            baseUrl = baseUrl,
            brandColorHex = brandColorHex,
            enabled = true,
            profiles = listOf(defaultProfile)
        )
        val updated = _services.value + newService
        _services.value = updated
        saveServices(updated)
    }

    fun deleteService(serviceId: String) {
        val updated = _services.value.filterNot { it.id == serviceId }
        _services.value = updated
        saveServices(updated)
    }

    fun addAccountProfile(
        serviceId: String,
        label: String,
        launchUrl: String,
        sessionMode: WebAiSessionMode,
        targetPackage: String? = null
    ) {
        val profileId = "profile_${UUID.randomUUID().toString().take(8)}"
        val newProfile = WebAiAccountProfile(
            id = profileId,
            serviceId = serviceId,
            label = label,
            launchUrl = launchUrl.ifBlank { _services.value.firstOrNull { it.id == serviceId }?.baseUrl ?: "" },
            sessionMode = sessionMode,
            targetPackage = targetPackage?.ifBlank { null }
        )
        val updated = _services.value.map { svc ->
            if (svc.id == serviceId) {
                svc.copy(profiles = svc.profiles + newProfile)
            } else {
                svc
            }
        }
        _services.value = updated
        saveServices(updated)
    }

    fun removeAccountProfile(serviceId: String, profileId: String) {
        val updated = _services.value.map { svc ->
            if (svc.id == serviceId) {
                svc.copy(profiles = svc.profiles.filterNot { it.id == profileId })
            } else {
                svc
            }
        }
        _services.value = updated
        saveServices(updated)
    }

    fun resetToDefaults() {
        _services.value = defaultServices
        saveServices(defaultServices)
    }
}
