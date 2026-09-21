package com.example.engine.sandbox

import android.content.Context
import android.os.Build
import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

enum class LinuxEnvironmentStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    EXTRACTING,
    READY,
    ERROR
}

data class LinuxInstallProgress(
    val status: LinuxEnvironmentStatus = LinuxEnvironmentStatus.NOT_INSTALLED,
    val progressPercent: Float = 0f,
    val message: String = "",
    val error: String? = null,
    val totalSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L
)

object LinuxSandboxManager {
    private val _installProgress = MutableStateFlow(LinuxInstallProgress())
    val installProgress: StateFlow<LinuxInstallProgress> = _installProgress.asStateFlow()

    private val httpClient = OkHttpClient.Builder().build()

    fun getBaseDir(context: Context): File {
        return context.filesDir
    }

    fun getProotDir(context: Context): File {
        return File(getBaseDir(context), "proot").apply { mkdirs() }
    }

    fun getRootfsDir(context: Context): File {
        return File(getBaseDir(context), "rootfs")
    }

    fun isEnvironmentReady(context: Context): Boolean {
        val rootfsDir = getRootfsDir(context)
        val alpineEtc = File(rootfsDir, "etc/alpine-release")
        val alpineSh = File(rootfsDir, "bin/sh")
        return rootfsDir.exists() && (alpineEtc.exists() || alpineSh.exists())
    }

    suspend fun checkStatus(context: Context) = withContext(Dispatchers.IO) {
        if (isEnvironmentReady(context)) {
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.READY,
                progressPercent = 1f,
                message = "Alpine Linux rootfs is initialized and ready."
            )
        } else {
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.NOT_INSTALLED,
                progressPercent = 0f,
                message = "Alpine Linux rootfs not installed."
            )
        }
    }

    /**
     * Downloads and extracts official lightweight Alpine Mini RootFS (~3-5MB compressed, ~12MB uncompressed).
     */
    suspend fun downloadAndInstallRootfs(
        context: Context,
        mirrorUrlOverride: String? = null
    ) = withContext(Dispatchers.IO) {
        if (_installProgress.value.status == LinuxEnvironmentStatus.DOWNLOADING ||
            _installProgress.value.status == LinuxEnvironmentStatus.EXTRACTING
        ) {
            return@withContext
        }

        val arch = getAlpineArch()
        val defaultUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/$arch/alpine-minirootfs-3.20.2-$arch.tar.gz"
        val downloadUrl = mirrorUrlOverride?.ifBlank { defaultUrl } ?: defaultUrl

        val rootfsDir = getRootfsDir(context)
        val cacheFile = File(context.cacheDir, "alpine-minirootfs.tar.gz")

        try {
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.DOWNLOADING,
                progressPercent = 0.05f,
                message = "Connecting to Alpine mirror ($arch)..."
            )

            // Step 1: Download tar.gz
            val request = Request.Builder().url(downloadUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body from mirror.")
                val contentLength = body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val pct = if (contentLength > 0) {
                                0.05f + (totalRead.toFloat() / contentLength.toFloat()) * 0.55f
                            } else {
                                0.35f
                            }
                            _installProgress.value = LinuxInstallProgress(
                                status = LinuxEnvironmentStatus.DOWNLOADING,
                                progressPercent = pct.coerceIn(0.05f, 0.60f),
                                message = "Downloading Alpine Linux (${totalRead / 1024} KB)...",
                                totalSizeBytes = contentLength,
                                downloadedBytes = totalRead
                            )
                        }
                    }
                }
            }

            // Step 2: Extract tar.gz into rootfs directory
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.EXTRACTING,
                progressPercent = 0.65f,
                message = "Extracting Alpine Linux root filesystem..."
            )

            if (!rootfsDir.exists()) {
                rootfsDir.mkdirs()
            }

            extractTarGz(cacheFile, rootfsDir)

            // Setup basic resolv.conf and /tmp
            val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
            val resolvConf = File(etcDir, "resolv.conf")
            if (!resolvConf.exists()) {
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            }
            File(rootfsDir, "tmp").apply { mkdirs() }
            File(rootfsDir, "workspace").apply { mkdirs() }

            // Cleanup archive
            cacheFile.delete()

            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.READY,
                progressPercent = 1f,
                message = "Alpine Linux rootfs ready (${getAlpineArch()})."
            )
            LogKeeper.log("LinuxSandboxManager", "Success", "Alpine Linux rootfs successfully unpacked to ${rootfsDir.absolutePath}")

        } catch (e: Exception) {
            LogKeeper.log("LinuxSandboxManager", "InstallFailed", "Failed to install Alpine rootfs: ${e.message}")
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.ERROR,
                progressPercent = 0f,
                message = "Installation failed",
                error = e.message ?: "Unknown installation error"
            )
        }
    }

    /**
     * Pure-Kotlin TAR + GZIP extraction without external Apache Commons dependency.
     */
    private fun extractTarGz(tarGzFile: File, targetDir: File) {
        val canonicalTargetDirPath = targetDir.canonicalPath
        BufferedInputStream(FileInputStream(tarGzFile)).use { bis ->
            GZIPInputStream(bis).use { gzis ->
                val tarStream = SimpleTarStream(gzis)
                var entry = tarStream.getNextEntry()
                while (entry != null) {
                    val outputFile = File(targetDir, entry.name)
                    val canonicalDestinationPath = outputFile.canonicalPath

                    if (!canonicalDestinationPath.startsWith(canonicalTargetDirPath)) {
                        // Zip-slip security guardrail
                        entry = tarStream.getNextEntry()
                        continue
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            tarStream.copyEntryData(fos, entry.size)
                        }
                        if (outputFile.name == "sh" || outputFile.name == "busybox" ||
                            outputFile.parentFile?.name == "bin" || outputFile.parentFile?.name == "sbin") {
                            outputFile.setExecutable(true, false)
                            outputFile.setReadable(true, false)
                        }
                    }
                    entry = tarStream.getNextEntry()
                }
            }
        }
    }

    suspend fun uninstallRootfs(context: Context) = withContext(Dispatchers.IO) {
        try {
            val rootfsDir = getRootfsDir(context)
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            _installProgress.value = LinuxInstallProgress(
                status = LinuxEnvironmentStatus.NOT_INSTALLED,
                progressPercent = 0f,
                message = "Alpine Linux removed."
            )
        } catch (e: Exception) {
            LogKeeper.log("LinuxSandboxManager", "UninstallError", "Failed to delete rootfs: ${e.message}")
        }
    }

    private fun getAlpineArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> "aarch64"
            abi.contains("armeabi") || abi.contains("armv7") -> "armhf"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> "aarch64"
        }
    }

    private class SimpleTarEntry(
        val name: String,
        val size: Long,
        val isDirectory: Boolean
    )

    private class SimpleTarStream(private val inputStream: InputStream) {
        private val headerBuffer = ByteArray(512)

        fun getNextEntry(): SimpleTarEntry? {
            var readTotal = 0
            while (readTotal < 512) {
                val r = inputStream.read(headerBuffer, readTotal, 512 - readTotal)
                if (r == -1) break
                readTotal += r
            }
            if (readTotal < 512) return null

            // Check if block is all zeros (end of archive)
            var allZero = true
            for (i in 0 until 512) {
                if (headerBuffer[i] != 0.toByte()) {
                    allZero = false
                    break
                }
            }
            if (allZero) return null

            // Extract entry name (0..99)
            val nameBytes = headerBuffer.copyOfRange(0, 100)
            val name = parseNullTerminatedString(nameBytes).trim().removePrefix("./").removePrefix("/")
            if (name.isEmpty()) return null

            // Extract file size (124..135, octal string)
            val sizeBytes = headerBuffer.copyOfRange(124, 136)
            val sizeStr = parseNullTerminatedString(sizeBytes).trim()
            val size = try {
                if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L
            } catch (_: Exception) {
                0L
            }

            // Type flag at offset 156
            val typeFlag = headerBuffer[156].toInt().toChar()
            val isDir = typeFlag == '5' || name.endsWith("/")

            return SimpleTarEntry(name = name, size = size, isDirectory = isDir)
        }

        fun copyEntryData(outputStream: FileOutputStream, size: Long) {
            var remaining = size
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                val r = inputStream.read(buffer, 0, toRead)
                if (r == -1) break
                outputStream.write(buffer, 0, r)
                remaining -= r
            }

            // Skip TAR 512-byte block padding
            val padding = (512 - (size % 512)) % 512
            if (padding > 0) {
                var padRemaining = padding
                while (padRemaining > 0) {
                    val skipped = inputStream.skip(padRemaining)
                    if (skipped <= 0) {
                        val dummy = ByteArray(padRemaining.toInt())
                        val r = inputStream.read(dummy)
                        if (r == -1) break
                        padRemaining -= r
                    } else {
                        padRemaining -= skipped
                    }
                }
            }
        }

        private fun parseNullTerminatedString(bytes: ByteArray): String {
            val nullIdx = bytes.indexOf(0)
            val len = if (nullIdx >= 0) nullIdx else bytes.size
            return String(bytes, 0, len, Charsets.UTF_8)
        }
    }
}
