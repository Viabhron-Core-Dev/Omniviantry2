package com.example.engine.webaiprovider

enum class WebAiSessionMode {
    DEFAULT,      // Shared browser cookies & session
    EPHEMERAL,    // Incognito / isolated profile mode
    CUSTOM_BROWSER // Target package routing
}

data class WebAiAccountProfile(
    val id: String,
    val serviceId: String,
    val label: String,
    val launchUrl: String,
    val sessionMode: WebAiSessionMode = WebAiSessionMode.DEFAULT,
    val targetPackage: String? = null
)

data class WebAiService(
    val id: String,
    val name: String,
    val baseUrl: String,
    val brandColorHex: String,
    val enabled: Boolean = true,
    val activeProfileId: String? = null,
    val profiles: List<WebAiAccountProfile> = emptyList()
)

data class WebAiPresetBundle(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val serviceIds: List<String>
)

