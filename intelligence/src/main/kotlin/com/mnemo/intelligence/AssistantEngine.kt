package com.mnemo.intelligence

import com.mnemo.core.model.AssistantCommand
import com.mnemo.core.model.AssistantIntent
import com.mnemo.core.model.AssistantResult
import com.mnemo.intelligence.intent.RuleBasedCommandParser
import com.mnemo.intelligence.retrieval.RetrievalOrchestrator
import com.mnemo.intelligence.router.ToolRouter

/**
 * High-level assistant orchestrator coordinating parsing, routing, and retrieval.
 */
class AssistantEngine(
    private val parser: RuleBasedCommandParser,
    private val router: ToolRouter,
    private val retrievalOrchestrator: RetrievalOrchestrator
) {
    suspend fun processQuery(rawQuery: String): AssistantResult {
        // Step 1: Parse intent and extract entities
        val command = parser.parse(rawQuery)

        // Step 2: If recognized as a specific tool command, route to tool
        if (command.intent != AssistantIntent.UNKNOWN) {
            val result = router.routeAndExecute(command)
            if (result.isSuccess || result.error?.code == "PERMISSION_DENIED") {
                return result
            }
        }

        // Step 3: Fallback to general local memory retrieval
        val retrieved = retrievalOrchestrator.retrieveAndRank(rawQuery)
        return retrievalOrchestrator.buildSourceLinkedAnswer(rawQuery, retrieved)
    }
}
