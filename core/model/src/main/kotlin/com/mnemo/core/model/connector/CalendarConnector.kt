package com.mnemo.core.model.connector

import com.mnemo.core.model.MemoryItem

/**
 * Android system connector interface for local calendar events.
 * Implemented in `:connectors` (Dadu's ownership).
 */
interface CalendarConnector {
    /**
     * Checks if READ_CALENDAR permission is granted.
     */
    suspend fun hasPermission(): Boolean

    /**
     * Retrieves calendar events occurring within a specified time window.
     *
     * @param startTime Unix epoch timestamp (ms) start boundary.
     * @param endTime Unix epoch timestamp (ms) end boundary.
     */
    suspend fun getEvents(startTime: Long, endTime: Long): List<MemoryItem>

    /**
     * Searches calendar events matching a title, description, or attendee query.
     */
    suspend fun searchEvents(query: String): List<MemoryItem>
}
