package com.mnemo.intelligence.tools

import com.mnemo.core.model.AssistantError
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.SourceReference
import com.mnemo.core.model.ToolParameter
import com.mnemo.core.model.connector.InstalledAppsConnector
import com.mnemo.core.model.tool.MnemoTool

/**
 * Tool to find and launch an installed application on the device.
 */
class OpenAppTool(
    private val appsConnector: InstalledAppsConnector
) : MnemoTool {
    override val id: String = "tool.apps.open"
    override val name: String = "Open Application"
    override val description: String = "Launches an installed application on the user's phone."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(name = "appName", type = "string", description = "Name of the app to launch")
    )

    override suspend fun execute(arguments: Map<String, String>): AssistantResult {
        val appName = arguments["appName"] ?: ""
        if (appName.isBlank()) {
            return AssistantResult.failure(
                message = "Please specify which application you would like to open.",
                error = AssistantError.notFound("app", appName)
            )
        }

        val app = appsConnector.findAppByName(appName)
            ?: return AssistantResult.failure(
                message = "Could not find an installed app named '$appName'.",
                error = AssistantError.notFound("installed app", appName)
            )

        val launched = appsConnector.openApp(app.id)
        return if (launched) {
            AssistantResult.success(
                message = "Opening ${app.title}...",
                sources = listOf(
                    SourceReference(
                        sourceType = DataSourceType.INSTALLED_APP,
                        title = app.title,
                        sourceUri = app.sourceUri
                    )
                ),
                data = listOf(app)
            )
        } else {
            AssistantResult.failure(
                message = "Failed to launch ${app.title}.",
                error = AssistantError.executionFailed("App launch intent could not be started")
            )
        }
    }
}
