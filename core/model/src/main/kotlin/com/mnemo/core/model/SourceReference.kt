package com.mnemo.core.model

/**
 * References the verified origin of a retrieved fact or assistant answer.
 * Core privacy principle: Every assistant response must link back to its local source.
 *
 * @property sourceType The local provider type (e.g. CONTACT, CALENDAR).
 * @property title Short title of the source entity.
 * @property sourceUri Canonical on-device URI (content:// or file://) for deep-linking.
 * @property snippet Brief extracted excerpt proving the answer's provenance.
 */
data class SourceReference(
    val sourceType: DataSourceType,
    val title: String,
    val sourceUri: String,
    val snippet: String? = null
) {
    init {
        require(title.isNotBlank()) { "SourceReference title must not be blank" }
    }
}
