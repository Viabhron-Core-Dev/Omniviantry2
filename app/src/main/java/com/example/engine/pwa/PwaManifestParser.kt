package com.example.engine.pwa

import com.example.utils.LogKeeper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PwaManifestIcon(
    val src: String,
    val sizes: String? = null,
    val type: String? = null,
    val purpose: String? = null
)

data class PwaManifestData(
    val name: String,
    val shortName: String,
    val startUrl: String = "index.html",
    val display: String = "standalone",
    val orientation: String = "any",
    val themeColor: String = "#2563EB",
    val backgroundColor: String = "#FFFFFF",
    val icons: List<PwaManifestIcon> = emptyList(),
    val rawJson: String
)

object PwaManifestParser {

    /**
     * Parses web app manifest JSON string into structured PwaManifestData.
     */
    fun parse(jsonContent: String, fallbackName: String = "Web App"): PwaManifestData {
        return try {
            val json = JSONObject(jsonContent)
            val name = json.optString("name", fallbackName).ifBlank { fallbackName }
            val shortName = json.optString("short_name", name).ifBlank { name }
            val startUrl = json.optString("start_url", "index.html").ifBlank { "index.html" }
            val display = json.optString("display", "standalone")
            val orientation = json.optString("orientation", "any")
            val themeColor = json.optString("theme_color", "#2563EB")
            val backgroundColor = json.optString("background_color", "#FFFFFF")

            val iconList = mutableListOf<PwaManifestIcon>()
            val iconsArray = json.optJSONArray("icons")
            if (iconsArray != null) {
                for (i in 0 until iconsArray.length()) {
                    val iconObj = iconsArray.optJSONObject(i) ?: continue
                    val src = iconObj.optString("src", "")
                    if (src.isNotBlank()) {
                        iconList.add(
                            PwaManifestIcon(
                                src = src,
                                sizes = iconObj.optString("sizes", null),
                                type = iconObj.optString("type", null),
                                purpose = iconObj.optString("purpose", null)
                            )
                        )
                    }
                }
            }

            LogKeeper.log("INFO", "PwaManifestParser", "Manifest parsed: '$name' (icons: ${iconList.size}, startUrl: '$startUrl')")
            PwaManifestData(
                name = name,
                shortName = shortName,
                startUrl = startUrl,
                display = display,
                orientation = orientation,
                themeColor = themeColor,
                backgroundColor = backgroundColor,
                icons = iconList,
                rawJson = jsonContent
            )
        } catch (e: Exception) {
            LogKeeper.log("WARNING", "PwaManifestParser", "Failed to parse manifest JSON: ${e.message}. Using synthetic fallback.")
            synthesizeManifestData(fallbackName)
        }
    }

    /**
     * Synthesizes a valid web app manifest when none exists in the ZIP archive.
     */
    fun synthesizeManifestData(appName: String, startUrl: String = "index.html"): PwaManifestData {
        val cleanName = appName.trim().ifBlank { "Web Mini App" }
        val rawJson = JSONObject().apply {
            put("name", cleanName)
            put("short_name", cleanName)
            put("start_url", startUrl)
            put("display", "standalone")
            put("theme_color", "#2563EB")
            put("background_color", "#FFFFFF")
            put("icons", JSONArray())
        }.toString(2)

        return PwaManifestData(
            name = cleanName,
            shortName = cleanName,
            startUrl = startUrl,
            rawJson = rawJson
        )
    }

    /**
     * Locates the best icon in the extracted directory given the parsed manifest icons,
     * or searches for common icon filenames.
     */
    fun resolveBestIconFile(appDir: File, manifestIcons: List<PwaManifestIcon>): File? {
        // 1. Try manifest icons (preferred larger icons: 512, 192, or svg)
        for (icon in manifestIcons) {
            val relativePath = icon.src.removePrefix("/").removePrefix("./")
            val candidate = File(appDir, relativePath)
            if (candidate.exists() && candidate.isFile) {
                return candidate
            }
        }

        // 2. Look for common icon patterns in root or assets folder
        val searchDirs = listOf(appDir, File(appDir, "icons"), File(appDir, "assets"), File(appDir, "public"), File(appDir, "static"))
        val candidateNames = listOf(
            "icon-512x512.png", "icon-192x192.png", "icon-512.png", "icon-192.png",
            "logo.png", "logo.svg", "favicon.png", "favicon.ico", "apple-touch-icon.png", "icon.png"
        )

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                for (name in candidateNames) {
                    val f = File(dir, name)
                    if (f.exists() && f.isFile) {
                        return f
                    }
                }
            }
        }

        // 3. Any png or svg in root
        return appDir.listFiles()?.firstOrNull { 
            it.isFile && it.extension.lowercase() in listOf("png", "svg", "webp", "jpg") 
        }
    }

    /**
     * Extracts title from an index.html file if present.
     */
    fun extractTitleFromHtml(htmlFile: File): String? {
        if (!htmlFile.exists()) return null
        return try {
            val content = htmlFile.readText()
            val regex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            regex.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}
