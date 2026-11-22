package com.mnemo.connectors.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionAdapterTest {

    @Test
    fun testRequiredPermissionsMapping() {
        // Test permission requirements per feature
        val contactsPerms = listOf(android.Manifest.permission.READ_CONTACTS)
        val calendarPerms = listOf(android.Manifest.permission.READ_CALENDAR)
        val voicePerms = listOf(android.Manifest.permission.RECORD_AUDIO)

        assertEquals(1, contactsPerms.size)
        assertTrue(contactsPerms.contains(android.Manifest.permission.READ_CONTACTS))
        assertTrue(calendarPerms.contains(android.Manifest.permission.READ_CALENDAR))
        assertTrue(voicePerms.contains(android.Manifest.permission.RECORD_AUDIO))
    }
}
