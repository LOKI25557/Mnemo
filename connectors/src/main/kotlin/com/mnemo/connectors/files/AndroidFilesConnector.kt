package com.mnemo.connectors.files

import android.content.Context
import android.os.Environment
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.connector.FilesConnector
import java.io.File

/**
 * Concrete local file discovery and document search connector.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class AndroidFilesConnector(
    private val context: Context
) : FilesConnector {

    override suspend fun hasPermission(): Boolean {
        // App-specific internal and external directories require zero runtime permissions on Android 10+
        return true
    }

    override suspend fun searchFiles(query: String, extensions: List<String>?): List<MemoryItem> {
        val results = mutableListOf<MemoryItem>()
        val searchDirs = listOfNotNull(
            context.getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (matchesFilter(file, query, extensions)) {
                        results.add(
                            MemoryItem(
                                id = "file_${file.name.hashCode()}",
                                sourceType = DataSourceType.FILE,
                                title = file.name,
                                content = "File: ${file.name} (${file.length() / 1024} KB) in ${file.parent}",
                                sourceUri = "file://${file.absolutePath}"
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    private fun matchesFilter(file: File, query: String, extensions: List<String>?): Boolean {
        if (!file.isFile) return false
        val matchesName = query.isBlank() || file.name.contains(query, ignoreCase = true)
        val matchesExt = extensions == null || extensions.any { file.extension.equals(it, ignoreCase = true) }
        return matchesName && matchesExt
    }
}
