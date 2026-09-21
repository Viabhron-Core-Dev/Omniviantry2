package com.example.engine.webaiprovider

data class WebAiActiveTab(
    val id: String,
    val serviceId: String,
    val serviceName: String,
    val brandColorHex: String,
    val url: String,
    val sessionMode: WebAiSessionMode = WebAiSessionMode.DEFAULT,
    val profileLabel: String = "Default",
    val targetPackage: String? = null,
    val isCurrent: Boolean = false
)
