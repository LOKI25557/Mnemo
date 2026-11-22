package com.mnemo.app.ui.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticStateEvaluatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFactoryConfiguredWhenDatabaseFileMissing() {
        val nonExistent = File(tempFolder.root, "missing.db")
        val report = DiagnosticStateEvaluator.evaluate(
            dbFile = nonExistent,
            isPlaintext = false,
            isDatabaseOpen = false,
            hasInstrumentationPassed = false
        )

        assertEquals(DatabaseDiagnosticStatus.SQLCIPHER_CONFIGURED, report.databaseStatus)
        assertEquals("SQLCIPHER CONFIGURED", report.databaseStatus.badge)
        assertEquals(RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING, report.runtimeVerificationStatus)
        assertEquals("RUNTIME VERIFICATION PENDING", report.runtimeVerificationStatus.badge)
    }

    @Test
    fun testDatabaseInitializedWhenFileMissingButOpen() {
        val nonExistent = File(tempFolder.root, "missing.db")
        val report = DiagnosticStateEvaluator.evaluate(
            dbFile = nonExistent,
            isPlaintext = false,
            isDatabaseOpen = true,
            hasInstrumentationPassed = false
        )

        assertEquals(DatabaseDiagnosticStatus.DB_INITIALIZED, report.databaseStatus)
        assertEquals("DB INITIALIZED", report.databaseStatus.badge)
        assertEquals(RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING, report.runtimeVerificationStatus)
    }

    @Test
    fun testCiphertextAtRestWhenDatabaseFileExistsAndNotPlaintext() {
        val dbFile = tempFolder.newFile("encrypted.db")
        dbFile.writeBytes(ByteArray(1024) { 0x55 })

        val report = DiagnosticStateEvaluator.evaluate(
            dbFile = dbFile,
            isPlaintext = false,
            isDatabaseOpen = true,
            hasInstrumentationPassed = false
        )

        assertEquals(DatabaseDiagnosticStatus.CIPHERTEXT_AT_REST, report.databaseStatus)
        assertEquals("CIPHERTEXT AT REST", report.databaseStatus.badge)
        // Must remain PENDING when on-device test has not run
        assertEquals(RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING, report.runtimeVerificationStatus)
        assertEquals("RUNTIME VERIFICATION PENDING", report.runtimeVerificationStatus.badge)
    }

    @Test
    fun testEncryptionUnavailableWhenPlaintextDatabaseDetected() {
        val dbFile = tempFolder.newFile("plaintext.db")
        dbFile.writeBytes(ByteArray(1024) { 0x11 })

        val report = DiagnosticStateEvaluator.evaluate(
            dbFile = dbFile,
            isPlaintext = true,
            isDatabaseOpen = false,
            hasInstrumentationPassed = false
        )

        assertEquals(DatabaseDiagnosticStatus.ENCRYPTION_UNAVAILABLE, report.databaseStatus)
        assertEquals("ENCRYPTION UNAVAILABLE", report.databaseStatus.badge)
        assertEquals(RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING, report.runtimeVerificationStatus)
    }

    @Test
    fun testRuntimeEncryptionVerifiedOnlyWhenInstrumentationExplicitlyPassed() {
        val dbFile = tempFolder.newFile("db.db")
        dbFile.writeBytes(ByteArray(512))

        // When tests have NOT executed on device -> must be PENDING
        val reportPending = DiagnosticStateEvaluator.evaluate(
            dbFile = dbFile,
            isPlaintext = false,
            isDatabaseOpen = true,
            hasInstrumentationPassed = false
        )
        assertEquals(RuntimeVerificationStatus.RUNTIME_VERIFICATION_PENDING, reportPending.runtimeVerificationStatus)

        // Only when actual execution succeeded -> VERIFIED
        val reportVerified = DiagnosticStateEvaluator.evaluate(
            dbFile = dbFile,
            isPlaintext = false,
            isDatabaseOpen = true,
            hasInstrumentationPassed = true
        )
        assertEquals(RuntimeVerificationStatus.RUNTIME_ENCRYPTION_VERIFIED, reportVerified.runtimeVerificationStatus)
        assertEquals("RUNTIME ENCRYPTION VERIFIED", reportVerified.runtimeVerificationStatus.badge)
    }
}
