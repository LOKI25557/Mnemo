package com.mnemo.intelligence.retrieval

import com.mnemo.core.model.AssistantResult
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.SourceReference
import com.mnemo.core.model.repository.MemoryRepository

/**
 * Orchestrates multi-source local retrieval across indexed memories.
 *
 * Implemented by Loki (AI & Intelligence Lead).
 */
class RetrievalOrchestrator(
    private val memoryRepository: MemoryRepository
) {
    /**
     * Searches all local indexed memory and formats source-linked results.
     */
    suspend fun retrieveAndRank(query: String, limit: Int = 10): List<MemoryItem> {
        if (query.isBlank()) return emptyList()
        return memoryRepository.search(query, limit)
    }

    /**
     * Synthesizes retrieved memory items into a clear offline summary with source links.
     */
    fun buildSourceLinkedAnswer(query: String, items: List<MemoryItem>): AssistantResult {
        if (items.isEmpty()) {
            return AssistantResult.success(
                message = "I couldn't find any local information matching \"$query\".",
                sources = emptyList()
            )
        }

        val sources = items.map {
            SourceReference(
                sourceType = it.sourceType,
                title = it.title,
                sourceUri = it.sourceUri,
                snippet = it.content.take(160)
            )
        }

        val summary = buildString {
            append("Found ${items.size} item(s) related to \"$query\":\n\n")
            items.take(3).forEachIndexed { index, item ->
                append("${index + 1}. ")
                if (item.personName != null) {
                    append("${item.personName} — ")
                }
                append("${item.title}: ${item.content.take(120)}\n")
            }
        }

        return AssistantResult.success(
            message = summary.trim(),
            sources = sources,
            data = items
        )
    }
}
