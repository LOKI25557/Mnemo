package com.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mnemo.core.database.entity.AuditLogEntity

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AuditLogEntity>)

    @Query("SELECT * FROM access_audit_log ORDER BY timestamp DESC")
    suspend fun getAll(): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM access_audit_log")
    suspend fun count(): Int

    @Query("SELECT * FROM access_audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditLogEntity>

    @Query("DELETE FROM access_audit_log WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM access_audit_log")
    suspend fun deleteAll(): Int
}
