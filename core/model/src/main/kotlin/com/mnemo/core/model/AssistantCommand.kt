package com.mnemo.core.model

/**
 * Represents a parsed and classified user instruction.
 *
 * @property rawQuery The original verbatim query entered or spoken by the user.
 * @property intent The identified semantic intent.
 * @property arguments Normalized key-value arguments extracted from the query.
 * @property confidenceScore Classification confidence from 0.0f to 1.0f.
 * @property entities Extracted named entities (e.g. names, dates, app targets).
 * @property requiresConfirmation Flag indicating if destructive or sensitive action requires confirmation.
 */
data class AssistantCommand(
    val rawQuery: String,
    val intent: AssistantIntent,
    val arguments: Map<String, String> = emptyMap(),
    val confidenceScore: Float = 1.0f,
    val entities: List<EntityMatch> = emptyList(),
    val requiresConfirmation: Boolean = false
) {
    init {
        require(confidenceScore in 0.0f..1.0f) { "Confidence must be within 0.0..1.0" }
    }
}
