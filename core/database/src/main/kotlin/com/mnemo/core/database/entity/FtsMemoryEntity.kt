package com.mnemo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS full-text search virtual table for fast local indexed querying.
 * Tokens are indexed across title, content, and person name.
 */
@Entity(tableName = "memory_items_fts")
@Fts4(contentEntity = MemoryEntity::class)
data class FtsMemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "person_name")
    val personName: String?
)
