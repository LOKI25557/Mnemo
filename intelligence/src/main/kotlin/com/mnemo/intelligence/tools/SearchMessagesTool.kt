package com.mnemo.intelligence.tools

import com.mnemo.core.model.AssistantError
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.SourceReference
import com.mnemo.core.model.ToolParameter
import com.mnemo.core.model.connector.NotificationsConnector
import com.mnemo.core.model.tool.MnemoTool

/**
 * Starter tool for searching locally captured notifications and messages.
 */
class SearchMessagesTool(
    private val notificationsConnector: NotificationsConnector
) : MnemoTool {
    override val id: String = "tool.messages.search"
    override val name: String = "Search Messages"
    override val description: String = "Searches locally captured notification and message history."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(name = "query", type = "string", description = "Keyword or sender to search")
    )

    override suspend fun execute(arguments: Map<String, String>): AssistantResult {
        val query = arguments["query"] ?: ""
        if (!notificationsConnector.hasPermission()) {
            return AssistantResult.failure(
                message = "Notification access has not been granted. Enable Notification Access in Settings to search messages.",
                error = AssistantError.permissionDenied("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "Notification access missing")
            )
        }

        val results = notificationsConnector.searchMessages(query)
        if (results.isEmpty()) {
            return AssistantResult.success(
                message = "No messages found matching '$query'.",
                sources = emptyList()
            )
        }

        val sources = results.map {
            SourceReference(
                sourceType = DataSourceType.MESSAGE,
                title = it.title,
                sourceUri = it.sourceUri,
                snippet = it.content
            )
        }

        return AssistantResult.success(
            message = "Found ${results.size} message(s) mentioning '$query'.",
            sources = sources,
            data = results
        )
    }
}
