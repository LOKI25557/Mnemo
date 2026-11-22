package com.mnemo.core.model.connector

import com.mnemo.core.model.MemoryItem

/**
 * Android system connector interface for local notification and message indexing.
 * Implemented in `:connectors` (Dadu's ownership).
 */
interface NotificationsConnector {
    /**
     * Checks if NotificationListenerService access has been authorized by the user.
     */
    suspend fun hasPermission(): Boolean

    /**
     * Retrieves recent captured messages or notification records.
     *
     * @param limit Maximum number of records to return.
     */
    suspend fun getRecentMessages(limit: Int = 20): List<MemoryItem>

    /**
     * Searches message content for keywords or participant names.
     */
    suspend fun searchMessages(query: String): List<MemoryItem>
}
