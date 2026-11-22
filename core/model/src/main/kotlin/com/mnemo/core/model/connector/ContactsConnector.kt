package com.mnemo.core.model.connector

import com.mnemo.core.model.MemoryItem

/**
 * Android system connector interface for local contacts access.
 * Implemented in `:connectors` (Dadu's ownership).
 */
interface ContactsConnector {
    /**
     * Checks if the required READ_CONTACTS permission is granted.
     */
    suspend fun hasPermission(): Boolean

    /**
     * Searches local contacts matching a query string (name, phone, email).
     */
    suspend fun searchContacts(query: String): List<MemoryItem>

    /**
     * Retrieves full contact details for a specific contact identifier.
     */
    suspend fun getContactById(id: String): MemoryItem?
}
