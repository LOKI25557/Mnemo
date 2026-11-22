package com.mnemo.core.security

import com.mnemo.core.model.DataSourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuditLoggerTest {

    @Test
    fun testLogAccessAndRetrieveRecent() = runTest {
        val logger = LocalAuditLogger()
        logger.logAccess(DataSourceType.CONTACT, "SEARCH", "Queried 'Rahul'")
        logger.logAccess(DataSourceType.CALENDAR, "QUERY_EVENTS", "Today's events")

        val records = logger.getRecentAuditRecords(10)
        assertEquals(2, records.size)
        // Most recent should appear first
        assertEquals(DataSourceType.CALENDAR, records[0].sourceType)
        assertEquals("QUERY_EVENTS", records[0].action)
        assertEquals(DataSourceType.CONTACT, records[1].sourceType)
    }

    @Test
    fun testLogCapacityBounding() = runTest {
        val logger = LocalAuditLogger()
        repeat(550) { index ->
            logger.logAccess(DataSourceType.FILE, "SCAN", "File item $index")
        }

        val records = logger.getRecentAuditRecords(1000)
        assertTrue(records.size <= 500)
    }
}
