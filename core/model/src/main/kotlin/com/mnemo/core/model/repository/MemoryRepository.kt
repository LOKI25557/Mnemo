package com.mnemo.core.model.repository

import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import kotlinx.coroutines.flow.Flow

/**
 * Storage abstraction contract for indexed local memory items.
 * Implemented in `:core:database` (Nikhi's ownership).
 *
 * All operations run against encrypted local SQLite storage.
 */
interface MemoryRepository {
    /**
     * Inserts or replaces a memory item in local storage.
     */
    suspend fun insert(item: MemoryItem)

    /**
     * Inserts or replaces multiple memory items in a single transaction.
     */
    suspend fun insertAll(items: List<MemoryItem>)

    /**
     * Performs a full-text search across indexed memory items.
     *
     * @param query Search query text.
     * @param limit Maximum results to return.
     */
    suspend fun search(query: String, limit: Int = 20): List<MemoryItem>

    /**
     * Retrieves an item by its unique identifier.
     */
    suspend fun getById(id: String): MemoryItem?

    /**
     * Retrieves all items belonging to a specific source type.
     */
    suspend fun getBySource(sourceType: DataSourceType, limit: Int = 50): List<MemoryItem>

    /**
     * Deletes a specific item by ID.
     * @return True if an item was deleted, false otherwise.
     */
    suspend fun deleteById(id: String): Boolean

    /**
     * Deletes all items belonging to a specific data source.
     */
    suspend fun deleteBySource(sourceType: DataSourceType): Int

    /**
     * Irrevocably wipes all indexed personal memory items from local storage.
     * Non-negotiable privacy requirement: Supports user-controlled total deletion.
     */
    suspend fun deleteAll(): Unit

    /**
     * Returns total count of indexed items.
     */
    suspend fun count(): Int

    /**
     * Observes real-time item count via Flow.
     */
    fun observeCount(): Flow<Int>
}
