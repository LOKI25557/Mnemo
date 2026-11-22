package com.mnemo.app.feature.privacy

import com.mnemo.core.model.security.AuditRecord

/**
 * State for Privacy Dashboard.
 * Assigned to Pravee (UI) & Nikhi (Security Lead).
 */
data class PrivacyUiState(
    val isEncryptionActive: Boolean = true,
    val isHardwareKeystoreSupported: Boolean = true,
    val biometricStatus: String = "Ready",
    val indexedItemCount: Int = 0,
    val auditRecords: List<AuditRecord> = emptyList()
)
