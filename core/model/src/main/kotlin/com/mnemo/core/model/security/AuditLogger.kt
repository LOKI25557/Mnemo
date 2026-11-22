package com.mnemo.core.model.security

import com.mnemo.core.model.DataSourceType

/**
 * Access audit logging contract to track and inspect local data access.
 * Non-negotiable privacy requirement: Transparent auditing of all data queries.
 */
interface AuditLogger {
    /**
     * Records a local access event.
     *
     * @param sourceType Which local data source was queried.
     * @param action The specific operation performed (e.g. "QUERY", "INDEX", "DELETE").
     * @param details Additional context or record count.
     */
    suspend fun logAccess(
        sourceType: DataSourceType,
        action: String,
        details: String
    )

    /**
     * Retrieves recent audit records for user inspection in the Privacy Dashboard.
     *
     * @param limit Max entries to retrieve.
     */
    suspend fun getRecentAuditRecords(limit: Int = 100): List<AuditRecord>
}

/**
 * Immutable audit record entry.
 */
data class AuditRecord(
    val id: String,
    val timestamp: Long,
    val sourceType: DataSourceType,
    val action: String,
    val details: String
)
