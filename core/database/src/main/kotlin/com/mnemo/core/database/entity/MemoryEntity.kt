package com.mnemo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem

/**
 * Room entity representing an indexed personal memory item.
 * Supports indexing across source types, timestamps, and person names.
 */
@Entity(
    tableName = "memory_items",
    indices = [
        Index(value = ["source_type"]),
        Index(value = ["timestamp"]),
        Index(value = ["person_name"])
    ]
)
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "person_name")
    val personName: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source_uri")
    val sourceUri: String,

    @ColumnInfo(name = "metadata_json")
    val metadataJson: String = "{}",

    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long = System.currentTimeMillis()
) {
    /**
     * Maps database entity to shared domain model.
     */
    fun toDomainModel(): MemoryItem {
        return MemoryItem(
            id = id,
            sourceType = try {
                DataSourceType.valueOf(sourceType)
            } catch (e: IllegalArgumentException) {
                DataSourceType.FILE
            },
            title = title,
            content = content,
            personName = personName,
            timestamp = timestamp,
            sourceUri = sourceUri,
            metadata = emptyMap(), // Serialized JSON metadata parsed by mapper
            indexedAt = indexedAt
        )
    }

    companion object {
        fun fromDomainModel(item: MemoryItem): MemoryEntity {
            return MemoryEntity(
                id = item.id,
                sourceType = item.sourceType.name,
                title = item.title,
                content = item.content,
                personName = item.personName,
                timestamp = item.timestamp,
                sourceUri = item.sourceUri,
                metadataJson = "{}",
                indexedAt = item.indexedAt
            )
        }
    }
}
