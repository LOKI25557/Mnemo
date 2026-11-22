package com.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemo.core.database.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MemoryEntity>)

    @Query("SELECT * FROM memory_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memory_items WHERE source_type = :sourceType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getBySource(sourceType: String, limit: Int): List<MemoryEntity>

    @Query("""
        SELECT * FROM memory_items
        WHERE title LIKE '%' || :query || '%'
           OR content LIKE '%' || :query || '%'
           OR person_name LIKE '%' || :query || '%'
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun searchSimple(query: String, limit: Int): List<MemoryEntity>

    @Query("""
        SELECT memory_items.* FROM memory_items
        JOIN memory_items_fts ON memory_items.rowid = memory_items_fts.rowid
        WHERE memory_items_fts MATCH :query
        ORDER BY memory_items.timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 50): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memory_items_fts")
    suspend fun countFts(): Int

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM memory_items WHERE source_type = :sourceType")
    suspend fun deleteBySource(sourceType: String): Int

    @Query("DELETE FROM memory_items")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM memory_items ORDER BY timestamp DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memory_items")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memory_items")
    fun observeCount(): Flow<Int>
}
