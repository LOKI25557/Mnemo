package com.mnemo.intelligence.tools

import com.mnemo.core.model.AssistantError
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.SourceReference
import com.mnemo.core.model.ToolParameter
import com.mnemo.core.model.connector.CalendarConnector
import com.mnemo.core.model.tool.MnemoTool

/**
 * Starter tool for fetching upcoming calendar events from the local calendar provider.
 */
class UpcomingEventsTool(
    private val calendarConnector: CalendarConnector
) : MnemoTool {
    override val id: String = "tool.calendar.events"
    override val name: String = "Upcoming Events"
    override val description: String = "Retrieves upcoming scheduled events from device calendar."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(name = "range", type = "string", description = "Time range (e.g. today, upcoming)", isRequired = false)
    )

    override suspend fun execute(arguments: Map<String, String>): AssistantResult {
        if (!calendarConnector.hasPermission()) {
            return AssistantResult.failure(
                message = "Calendar permission is required to read your schedule. Please grant it in privacy settings.",
                error = AssistantError.permissionDenied("android.permission.READ_CALENDAR", "Calendar permission missing")
            )
        }

        val now = System.currentTimeMillis()
        val oneDayLater = now + (24 * 60 * 60 * 1000)
        val events = calendarConnector.getEvents(now, oneDayLater)

        if (events.isEmpty()) {
            return AssistantResult.success(
                message = "You have no upcoming events scheduled for today.",
                sources = emptyList()
            )
        }

        val sources = events.map {
            SourceReference(
                sourceType = DataSourceType.CALENDAR,
                title = it.title,
                sourceUri = it.sourceUri,
                snippet = it.content
            )
        }

        return AssistantResult.success(
            message = "You have ${events.size} upcoming event(s) scheduled.",
            sources = sources,
            data = events
        )
    }
}
