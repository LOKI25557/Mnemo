package com.mnemo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.security.AuditRecord

/**
 * Room entity storing access audit events.
 * Enables user inspection of what data was accessed, when, and by what intent.
 */
@Entity(
    tableName = "access_audit_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["source_type"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "details")
    val details: String
) {
    fun toDomainModel(): AuditRecord {
        return AuditRecord(
            id = id,
            timestamp = timestamp,
            sourceType = try {
                DataSourceType.valueOf(sourceType)
            } catch (e: Exception) {
                DataSourceType.FILE
            },
            action = action,
            details = details
        )
    }

    companion object {
        fun fromDomainModel(record: AuditRecord): AuditLogEntity {
            return AuditLogEntity(
                id = record.id,
                timestamp = record.timestamp,
                sourceType = record.sourceType.name,
                action = record.action,
                details = record.details
            )
        }
    }
}
