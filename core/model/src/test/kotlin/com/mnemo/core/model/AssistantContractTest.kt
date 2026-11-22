package com.mnemo.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantContractTest {

    @Test
    fun testAssistantCommandCreation() {
        val entity = EntityMatch(name = "person_name", value = "Rahul", confidence = 0.95f)
        val command = AssistantCommand(
            rawQuery = "Find Rahul's phone number",
            intent = AssistantIntent.FIND_CONTACT,
            arguments = mapOf("query" to "Rahul"),
            confidenceScore = 0.95f,
            entities = listOf(entity)
        )

        assertEquals(AssistantIntent.FIND_CONTACT, command.intent)
        assertEquals("Rahul", command.arguments["query"])
        assertEquals(1, command.entities.size)
        assertEquals("person_name", command.entities[0].name)
        assertFalse(command.requiresConfirmation)
    }

    @Test
    fun testAssistantResultSuccessFactory() {
        val source = SourceReference(
            sourceType = DataSourceType.CONTACT,
            title = "Rahul",
            sourceUri = "content://contacts/1"
        )
        val result = AssistantResult.success(
            message = "Rahul's number is 555-0199",
            sources = listOf(source)
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.sources.size)
        assertEquals("Rahul", result.sources[0].title)
        assertNull(result.error)
        assertFalse(result.requiresConfirmation)
    }

    @Test
    fun testAssistantResultFailureFactory() {
        val error = AssistantError.permissionDenied(
            permission = "android.permission.READ_CONTACTS",
            message = "Missing contacts permission"
        )
        val result = AssistantResult.failure(
            message = "Contacts permission needed",
            error = error
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.error)
        assertEquals("PERMISSION_DENIED", result.error?.code)
        assertEquals("android.permission.READ_CONTACTS", result.error?.requiredPermission)
    }
}
