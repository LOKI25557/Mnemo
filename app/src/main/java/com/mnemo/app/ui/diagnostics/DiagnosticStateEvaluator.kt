package com.mnemo.app.ui.diagnostics

import androidx.compose.ui.graphics.Color
import java.io.File

enum class DatabaseDiagnosticStatus(
    val badge: String,
    val description: String,
    val color: Color
) {
    SQLCIPHER_CONFIGURED(
        badge = "SQLCIPHER CONFIGURED",
        description = "SQLCipher open helper factory is configured with Android Keystore passphrase. Database file will initialize upon first write.",
        color = Color(0xFF38BDF8) // Sky400
    ),
    DB_INITIALIZED(
        badge = "DB INITIALIZED",
        description = "Database connection was opened successfully. Awaiting verified ciphertext on disk.",
        color = Color(0xFF6366F1) // Indigo500
    ),
    CIPHERTEXT_AT_REST(
        badge = "CIPHERTEXT AT REST",
        description = "Database file exists on disk and is verified as ciphertext (SQLite plaintext header absent).",
        color = Color(0xFF6366F1) // Indigo500
    ),
    ENCRYPTION_UNAVAILABLE(
        badge = "ENCRYPTION UNAVAILABLE",
        description = "Unencrypted plaintext SQLite database detected on disk. Data is not encrypted at rest. Migration required.",
        color = Color(0xFFEF4444) // Red500
    )
}

enum class RuntimeVerificationStatus(
    val badge: String,
    val description: String,
    val color: Color
) {
    RUNTIME_VERIFICATION_PENDING(
        badge = "RUNTIME VERIFICATION PENDING",
        description = "Instrumentation test suite has not yet executed on this hardware or emulator. Hardware-level cryptographic verification pending connected test execution.",
        color = Color(0xFFF59E0B) // Amber500
    ),
    RUNTIME_ENCRYPTION_VERIFIED(
        badge = "RUNTIME ENCRYPTION VERIFIED",
        description = "Verified on connected physical device or emulator through successful execution of the 9-point SqlCipherEncryptionTest suite.",
        color = Color(0xFF10B981) // Emerald500
    ),
    RUNTIME_VERIFICATION_FAILED(
        badge = "RUNTIME VERIFICATION FAILED",
        description = "Instrumentation test execution reported decryption failure or security violation.",
        color = Color(0xFFEF4444) // Red500
    )
}

data class DiagnosticReport(
    val databaseStatus: DatabaseDiagnosticStatus,
    val runtimeVerificationStatus: RuntimeVerificationStatus
)

object DiagnosticStateEvaluator {

    /**
     * Evaluates live storage and encryption status without false security claims.
     *
     * @param dbFile Pointer to the database file on disk.
     * @param isPlaintext Whether the file matches the standard SQLite 3 plaintext magic header.
     * @param isDatabaseOpen Whether a Room/SQLCipher database connection is actively open.
     * @param hasInstrumentationPassed Whether an actual runtime instrumentation test run succeeded on this device.
     */
    fun evaluate(
        dbFile: File?,
        isPlaintext: Boolean,
        isDatabaseOpen: Boolean,
        hasInstrumentationPassed: Boolean = false
    ): DiagnosticReport {
        val fileExistsAndNonEmpty = dbFile != null && dbFile.exists() && dbFile.length() > 0

        val dbStatus = when {
            fileExistsAndNonEmpty && isPlaintext -> DatabaseDiagnosticStatus.ENCRYPTION_UNAVAILABLE
            fileExistsAndNonEmpty -> DatabaseDiagnosticStatus.CIPHERTEXT_AT_REST
            isDatabaseOpen -> DatabaseDiagnosticStatus.DB_INITIALIZED
            else -> DatabaseDiagnosticStatus.SQLCIPHER_CONFIGURED
        }

        // NON-NEGOTIABLE CRITERION: A test suite merely existing or compiling must NEVER count
        // as runtime verification. Until instrumentation tests have actually executed successfully,
        // display RUNTIME VERIFICATION PENDING.
        val runtimeStatus = if (hasInstrumentationPassed) {
            RuntimeVerificationStatus.RUNTIME_ENCRYPTION_VERIFIED
        } else {
            RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING
        }

        return DiagnosticReport(
            databaseStatus = dbStatus,
            runtimeVerificationStatus = runtimeStatus
        )
    }
}
