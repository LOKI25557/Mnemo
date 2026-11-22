package com.mnemo.core.model.tool

import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.ToolParameter

/**
 * Common contract for all on-device capabilities and tools executable by Mnemo.
 *
 * Each tool provides metadata describing its arguments and executes asynchronously
 * via Coroutines returning a standardized [AssistantResult].
 */
interface MnemoTool {
    /** Unique stable identifier for this tool (e.g. "contacts.search", "calendar.events"). */
    val id: String

    /** Human-readable name. */
    val name: String

    /** Clear description of the tool's capability for the router. */
    val description: String

    /** Declared parameter schema accepted by this tool. */
    val parameters: List<ToolParameter>

    /**
     * Executes the tool with the provided arguments.
     *
     * @param arguments Map of parameter name to value passed from the user or query planner.
     * @return Standardized execution result containing message, sources, and data.
     */
    suspend fun execute(arguments: Map<String, String>): AssistantResult
}
