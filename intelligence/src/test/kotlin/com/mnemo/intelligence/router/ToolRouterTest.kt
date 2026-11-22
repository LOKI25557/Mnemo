package com.mnemo.intelligence.router

import com.mnemo.core.model.AssistantCommand
import com.mnemo.core.model.AssistantIntent
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.ToolParameter
import com.mnemo.core.model.tool.MnemoTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRouterTest {

    @Test
    fun testRouterExecutesRegisteredTool() = runTest {
        val registry = ToolRegistry()
        val mockTool = object : MnemoTool {
            override val id: String = "tool.contacts.find"
            override val name: String = "Test Contact Tool"
            override val description: String = "Test"
            override val parameters: List<ToolParameter> = emptyList()

            override suspend fun execute(arguments: Map<String, String>): AssistantResult {
                return AssistantResult.success("Found mock contact")
            }
        }
        registry.register(mockTool)

        val router = DefaultToolRouter(registry)
        val command = AssistantCommand(
            rawQuery = "Find Rahul",
            intent = AssistantIntent.FIND_CONTACT,
            arguments = mapOf("query" to "Rahul")
        )

        val result = router.routeAndExecute(command)
        assertTrue(result.isSuccess)
        assertEquals("Found mock contact", result.message)
    }

    @Test
    fun testRouterReturnsFailureForUnregisteredTool() = runTest {
        val registry = ToolRegistry()
        val router = DefaultToolRouter(registry)
        val command = AssistantCommand(
            rawQuery = "Find something",
            intent = AssistantIntent.UNKNOWN
        )

        val result = router.routeAndExecute(command)
        assertFalse(result.isSuccess)
        assertEquals("NOT_FOUND", result.error?.code)
    }
}
