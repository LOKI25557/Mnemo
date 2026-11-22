package com.mnemo.core.security

import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.security.AuditLogger
import com.mnemo.core.model.security.AuditRecord
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory & persistence-ready audit logger.
 * Tracks access events transparently for user inspection.
 */
class LocalAuditLogger : AuditLogger {

    private val auditLog = CopyOnWriteArrayList<AuditRecord>()

    override suspend fun logAccess(sourceType: DataSourceType, action: String, details: String) {
        val record = AuditRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sourceType = sourceType,
            action = action,
            details = details
        )
        auditLog.add(0, record)
        // Keep in-memory buffer bounded to 500 records
        if (auditLog.size > 500) {
            auditLog.removeAt(auditLog.lastIndex)
        }
    }

    override suspend fun getRecentAuditRecords(limit: Int): List<AuditRecord> {
        return auditLog.take(limit)
    }
}
