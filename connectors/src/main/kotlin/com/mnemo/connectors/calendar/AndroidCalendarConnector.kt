package com.mnemo.connectors.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.connector.CalendarConnector

/**
 * Concrete Android Calendar Provider connector.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class AndroidCalendarConnector(
    private val context: Context
) : CalendarConnector {

    override suspend fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun getEvents(startTime: Long, endTime: Long): List<MemoryItem> {
        if (!hasPermission()) return emptyList()

        val events = mutableListOf<MemoryItem>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC LIMIT 50"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val title = if (titleIdx >= 0) it.getString(titleIdx) else "Event"
                    val desc = if (descIdx >= 0) it.getString(descIdx) ?: "" else ""
                    val start = if (startIdx >= 0) it.getLong(startIdx) else startTime

                    events.add(
                        MemoryItem(
                            id = "event_$id",
                            sourceType = DataSourceType.CALENDAR,
                            title = title,
                            content = desc.ifBlank { "Event at timestamp: $start" },
                            timestamp = start,
                            sourceUri = "content://com.android.calendar/events/$id"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return events
    }

    override suspend fun searchEvents(query: String): List<MemoryItem> {
        if (!hasPermission() || query.isBlank()) return emptyList()
        // In Phase 0 starter implementation, searches events across 30 days
        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24 * 60 * 60 * 1000
        return getEvents(now - thirtyDays, now + thirtyDays).filter {
            it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
        }
    }
}
