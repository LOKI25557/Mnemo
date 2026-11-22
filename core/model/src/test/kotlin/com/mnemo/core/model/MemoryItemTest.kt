package com.mnemo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryItemTest {

    @Test
    fun testValidMemoryItemCreation() {
        val item = MemoryItem(
            id = "mem_001",
            sourceType = DataSourceType.CONTACT,
            title = "Rahul Sharma",
            content = "Phone: +91 9876543210",
            personName = "Rahul Sharma",
            sourceUri = "content://contacts/people/1"
        )

        assertEquals("mem_001", item.id)
        assertEquals(DataSourceType.CONTACT, item.sourceType)
        assertEquals("Rahul Sharma", item.title)
        assertEquals("Rahul Sharma", item.personName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankIdThrowsException() {
        MemoryItem(
            id = "   ",
            sourceType = DataSourceType.FILE,
            title = "Sample File",
            content = "Content"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankTitleAndContentThrowsException() {
        MemoryItem(
            id = "mem_002",
            sourceType = DataSourceType.CALENDAR,
            title = "",
            content = ""
        )
    }

    @Test
    fun testToSourceReference() {
        val item = MemoryItem(
            id = "mem_12345678",
            sourceType = DataSourceType.CALENDAR,
            title = "Design Sync",
            content = "Meeting to discuss local architecture and encryption boundaries",
            sourceUri = "content://calendar/events/42"
        )

        val source = item.toSourceReference()
        assertEquals(DataSourceType.CALENDAR, source.sourceType)
        assertEquals("Design Sync", source.title)
        assertEquals("content://calendar/events/42", source.sourceUri)
        assertTrue(source.snippet!!.contains("architecture"))
    }
}
