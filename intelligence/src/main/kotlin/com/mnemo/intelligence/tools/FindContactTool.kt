package com.mnemo.intelligence.tools

import com.mnemo.core.model.AssistantError
import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.SourceReference
import com.mnemo.core.model.ToolParameter
import com.mnemo.core.model.connector.ContactsConnector
import com.mnemo.core.model.tool.MnemoTool

/**
 * Starter tool for resolving and finding contact information on-device.
 */
class FindContactTool(
    private val contactsConnector: ContactsConnector
) : MnemoTool {
    override val id: String = "tool.contacts.find"
    override val name: String = "Find Contact"
    override val description: String = "Finds contact numbers, emails, and details stored in device contacts."
    override val parameters: List<ToolParameter> = listOf(
        ToolParameter(name = "query", type = "string", description = "Name or term to look up")
    )

    override suspend fun execute(arguments: Map<String, String>): AssistantResult {
        val query = arguments["query"] ?: ""
        if (query.isBlank()) {
            return AssistantResult.failure(
                message = "Please specify whose contact details you would like to find.",
                error = AssistantError.notFound("contact", query)
            )
        }

        if (!contactsConnector.hasPermission()) {
            return AssistantResult.failure(
                message = "Contacts permission has not been granted. Please enable it in Settings to search contacts.",
                error = AssistantError.permissionDenied("android.permission.READ_CONTACTS", "Missing contacts permission")
            )
        }

        val items = contactsConnector.searchContacts(query)
        if (items.isEmpty()) {
            return AssistantResult.success(
                message = "No contact found matching '$query'.",
                sources = emptyList()
            )
        }

        val top = items.first()
        val sources = listOf(
            SourceReference(
                sourceType = DataSourceType.CONTACT,
                title = top.title,
                sourceUri = top.sourceUri,
                snippet = top.content
            )
        )
        return AssistantResult.success(
            message = "Found ${top.title}: ${top.content}",
            sources = sources,
            data = items
        )
    }
}
