package com.mnemo.intelligence.router

import com.mnemo.core.model.AssistantCommand
import com.mnemo.core.model.AssistantIntent
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.tool.MnemoTool

/**
 * Routes parsed assistant commands to corresponding tool handlers.
 */
interface ToolRouter {
    suspend fun routeAndExecute(command: AssistantCommand): AssistantResult
}

class DefaultToolRouter(
    private val registry: ToolRegistry
) : ToolRouter {

    override suspend fun routeAndExecute(command: AssistantCommand): AssistantResult {
        val tool = findToolForIntent(command.intent)
            ?: return AssistantResult.failure(
                message = "I couldn't find a matching local tool for this command.",
                error = com.mnemo.core.model.AssistantError.notFound("tool", command.intent.name)
            )

        return tool.execute(command.arguments)
    }

    private fun findToolForIntent(intent: AssistantIntent): MnemoTool? {
        val targetId = when (intent) {
            AssistantIntent.FIND_CONTACT -> "tool.contacts.find"
            AssistantIntent.SHOW_CALENDAR_EVENTS -> "tool.calendar.events"
            AssistantIntent.SEARCH_MESSAGES -> "tool.messages.search"
            AssistantIntent.OPEN_APPLICATION -> "tool.apps.open"
            AssistantIntent.FIND_FILES -> "tool.files.search"
            AssistantIntent.SUMMARIZE_TODAY -> "tool.calendar.events"
            AssistantIntent.EXTRACT_COMMITMENTS -> "tool.messages.search"
            else -> null
        }
        return targetId?.let { registry.get(it) }
    }
}
