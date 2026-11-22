package com.mnemo.core.model.connector

import com.mnemo.core.model.MemoryItem

/**
 * Android system connector interface for local file discovery and document search.
 * Implemented in `:connectors` (Dadu's ownership).
 */
interface FilesConnector {
    /**
     * Checks if media/storage permissions are granted.
     */
    suspend fun hasPermission(): Boolean

    /**
     * Searches local downloaded or public documents matching a query.
     *
     * @param query Search query for file name or content keywords.
     * @param extensions Optional list of file extensions to filter (e.g. listOf("pdf", "docx")).
     */
    suspend fun searchFiles(query: String, extensions: List<String>? = null): List<MemoryItem>
}
