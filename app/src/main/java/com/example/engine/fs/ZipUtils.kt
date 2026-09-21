package com.example.engine.fs

import com.example.utils.LogKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    suspend fun unzip(zipFile: File, targetDirectory: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            LogKeeper.log("INFO", "ZipUtils", "Starting unzip of ${zipFile.name} (${zipFile.length()} bytes) into ${targetDirectory.absolutePath}")
            if (!targetDirectory.exists()) {
                targetDirectory.mkdirs()
            }
            var extractedCount = 0
            var totalBytes = 0L

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val newFile = File(targetDirectory, entry.name)
                    // Security check to prevent Zip Slip vulnerability
                    val canonicalDestPath = targetDirectory.canonicalPath
                    val canonicalNewFilePath = newFile.canonicalPath
                    if (!canonicalNewFilePath.startsWith(canonicalDestPath + File.separator)) {
                        val errMsg = "ZipSlip blocked! Entry is outside target dir: ${entry.name}"
                        LogKeeper.log("ERROR", "ZipUtils", errMsg)
                        return@withContext Result.failure(Exception(errMsg))
                    }

                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(newFile)).use { bos ->
                            val buffer = ByteArray(4096)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                bos.write(buffer, 0, len)
                                totalBytes += len
                            }
                        }
                        extractedCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            LogKeeper.log("INFO", "ZipUtils", "Successfully unzipped ${zipFile.name}: $extractedCount files, $totalBytes bytes extracted")
            Result.success(Unit)
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ZipUtils", "Failed to unzip ${zipFile.name}: ${e.message}", e.stackTraceToString())
            Result.failure(e)
        }
    }

    suspend fun zipDirectory(sourceDirectory: File, targetZipFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            LogKeeper.log("INFO", "ZipUtils", "Starting zip compression of ${sourceDirectory.name} into ${targetZipFile.name}")
            targetZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipFile))).use { zos ->
                zipFile(sourceDirectory, sourceDirectory, zos)
            }
            LogKeeper.log("INFO", "ZipUtils", "Successfully zipped ${sourceDirectory.name} -> ${targetZipFile.name} (${targetZipFile.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            LogKeeper.log("ERROR", "ZipUtils", "Failed to zip directory ${sourceDirectory.name}: ${e.message}", e.stackTraceToString())
            Result.failure(e)
        }
    }

    private fun zipFile(fileToZip: File, rootDir: File, zos: ZipOutputStream) {
        if (fileToZip.isHidden) return

        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles()
            if (children != null) {
                for (childFile in children) {
                    zipFile(childFile, rootDir, zos)
                }
            }
        } else {
            val entryName = fileToZip.absolutePath.substring(rootDir.absolutePath.length + 1)
            val zipEntry = ZipEntry(entryName)
            zos.putNextEntry(zipEntry)
            FileInputStream(fileToZip).use { fis ->
                val buffer = ByteArray(4096)
                var length: Int
                while (fis.read(buffer).also { length = it } >= 0) {
                    zos.write(buffer, 0, length)
                }
            }
            zos.closeEntry()
        }
    }
}
