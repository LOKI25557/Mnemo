package com.mnemo.core.model

/**
 * Represents a recognized named entity extracted from natural language input.
 *
 * @property name Entity identifier or category (e.g. "person_name", "date_range", "app_name").
 * @property value Extracted literal or normalized value.
 * @property confidence Match confidence from 0.0f to 1.0f.
 * @property startIndex Character start position in raw query.
 * @property endIndex Character end position in raw query.
 */
data class EntityMatch(
    val name: String,
    val value: String,
    val confidence: Float = 1.0f,
    val startIndex: Int = -1,
    val endIndex: Int = -1
) {
    init {
        require(name.isNotBlank()) { "Entity name cannot be blank" }
        require(confidence in 0.0f..1.0f) { "Confidence must be between 0.0 and 1.0" }
    }
}
