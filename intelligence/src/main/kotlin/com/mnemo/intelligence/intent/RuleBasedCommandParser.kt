package com.mnemo.intelligence.intent

import com.mnemo.core.model.AssistantCommand
import com.mnemo.core.model.AssistantIntent
import com.mnemo.core.model.EntityMatch
import java.util.Locale

/**
 * Deterministic, rule-based natural language command parser.
 * Operates completely offline with zero latency and zero privacy exposure.
 *
 * Implemented by Loki (AI & Intelligence Lead).
 */
class RuleBasedCommandParser {

    fun parse(rawQuery: String): AssistantCommand {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.UNKNOWN,
                confidenceScore = 0.0f
            )
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Summary: "Give me a summary of today's important events", "Summarize today"
        if (lower.contains("summary") || lower.contains("summarize") || lower.contains("briefing")) {
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.SUMMARIZE_TODAY,
                arguments = emptyMap(),
                confidenceScore = 0.89f
            )
        }

        // 2. Commitments: "What commitments did I make this week?", "My promises"
        if (lower.contains("commitment") || lower.contains("promises") || lower.contains("what did i promise")) {
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.EXTRACT_COMMITMENTS,
                arguments = emptyMap(),
                confidenceScore = 0.85f
            )
        }

        // 3. Contact lookup: "Find Rahul's phone number", "Who is Rahul", "Call Sarah"
        if (lower.contains("phone number") || lower.contains("contact") ||
            (lower.startsWith("find") && (lower.contains("'s") || lower.contains("number")))) {
            val name = extractContactName(lower)
            val entities = if (name != null) {
                listOf(EntityMatch(name = "person_name", value = name, confidence = 0.9f))
            } else emptyList()

            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.FIND_CONTACT,
                arguments = mapOf("query" to (name ?: "")),
                confidenceScore = 0.95f,
                entities = entities
            )
        }

        // 4. Calendar / Events: "Show my upcoming calendar events", "What is on my schedule today"
        if (lower.contains("calendar") || lower.contains("upcoming event") ||
            lower.contains("events") || lower.contains("my schedule") || lower.contains("meeting today")) {
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.SHOW_CALENDAR_EVENTS,
                arguments = mapOf("range" to "upcoming"),
                confidenceScore = 0.92f
            )
        }

        // 5. Messages / Notifications: "Find the message where someone mentioned a meeting"
        if (lower.contains("message") || lower.contains("text from") || lower.contains("chat")) {
            val keyword = extractMessageKeyword(lower)
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.SEARCH_MESSAGES,
                arguments = mapOf("query" to (keyword ?: "")),
                confidenceScore = 0.88f,
                entities = if (keyword != null) {
                    listOf(EntityMatch("keyword", keyword, 0.85f))
                } else emptyList()
            )
        }

        // 6. Launch app: "Open the calculator", "Launch camera"
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val appName = lower.removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("start ")
                .removePrefix("the ")
                .trim()
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.OPEN_APPLICATION,
                arguments = mapOf("appName" to appName),
                confidenceScore = 0.96f,
                entities = listOf(EntityMatch("app_name", appName, 0.95f))
            )
        }

        // 7. Files: "Find my downloaded PDF files", "Search for document resume"
        if (lower.contains("file") || lower.contains("pdf") || lower.contains("document") || lower.contains("download")) {
            val ext = if (lower.contains("pdf")) "pdf" else null
            return AssistantCommand(
                rawQuery = rawQuery,
                intent = AssistantIntent.FIND_FILES,
                arguments = buildMap {
                    put("query", lower)
                    ext?.let { put("extension", it) }
                },
                confidenceScore = 0.90f
            )
        }

        // Fallback: general query
        return AssistantCommand(
            rawQuery = rawQuery,
            intent = AssistantIntent.UNKNOWN,
            arguments = mapOf("query" to rawQuery),
            confidenceScore = 0.40f
        )
    }

    private fun extractContactName(query: String): String? {
        val regex = Regex("""(?:find|who is|call|phone number of|for|show)\s+([A-Za-z]+)(?:'s|\s+phone|\s+number)?""")
        val match = regex.find(query)
        val extracted = match?.groupValues?.getOrNull(1)?.trim()
        return if (extracted.isNullOrBlank() || extracted == "my" || extracted == "the") {
            null
        } else {
            extracted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun extractMessageKeyword(query: String): String? {
        val regex = Regex("""mentioned\s+([A-Za-z0-9_\s]+)""")
        val match = regex.find(query)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
}
