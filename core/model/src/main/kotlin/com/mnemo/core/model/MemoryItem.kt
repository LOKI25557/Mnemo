package com.mnemo.core.model

/**
 * Immutable model representing an item of indexed personal information stored locally on-device.
 *
 * @property id Unique identifier for the indexed memory item.
 * @property sourceType The type of local data source this memory originates from.
 * @property title Brief human-readable title or header (e.g. contact name, event title, subject).
 * @property content Primary searchable text content extracted from the local source.
 * @property personName Associated person or contact name, if applicable to the item.
 * @property timestamp Unix epoch timestamp (ms) of creation or event occurrence.
 * @property sourceUri Canonical local Android URI referencing the underlying data item.
 * @property metadata Supplementary key-value metadata preserving source-specific attributes.
 * @property indexedAt Unix epoch timestamp (ms) when this item was indexed locally.
 */
data class MemoryItem(
    val id: String,
    val sourceType: DataSourceType,
    val title: String,
    val content: String,
    val personName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceUri: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val indexedAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.isNotBlank()) { "MemoryItem id must not be blank" }
        require(title.isNotBlank() || content.isNotBlank()) {
            "MemoryItem must have either a title or content"
        }
    }

    /**
     * Converts this memory item into a user-facing source reference.
     */
    fun toSourceReference(): SourceReference {
        return SourceReference(
            sourceType = sourceType,
            title = title.ifBlank { "Item ${id.take(8)}" },
            sourceUri = sourceUri,
            snippet = content.take(160)
        )
    }
}
