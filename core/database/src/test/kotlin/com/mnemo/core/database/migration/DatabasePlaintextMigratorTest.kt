package com.mnemo.core.database.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class DatabasePlaintextMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDir: File

    private val sqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Before
    fun setUp() {
        testDir = tempFolder.newFolder("db_test_dir")
    }

    @Test
    fun testIsPlaintextDatabase_validHeader() {
        val file = File(testDir, "valid_plain.db")
        file.writeBytes(sqliteMagic + ByteArray(100))

        assertTrue(DatabasePlaintextMigrator.isPlaintextDatabase(file))
    }

    @Test
    fun testIsPlaintextDatabase_encryptedOrRandom() {
        val file = File(testDir, "encrypted.db")
        file.writeBytes(ByteArray(116) { 0x7E })

        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(file))
    }

    @Test
    fun testIsPlaintextDatabase_shortOrEmptyFile() {
        val emptyFile = File(testDir, "empty.db")
        emptyFile.writeBytes(ByteArray(0))
        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(emptyFile))

        val shortFile = File(testDir, "short.db")
        shortFile.writeBytes(ByteArray(10))
        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(shortFile))

        val nonExistent = File(testDir, "missing.db")
        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(nonExistent))
    }

    @Test
    fun testHasInterruptedMigration_detection() {
        val dbFile = File(testDir, "mnemo.db")
        assertFalse(DatabasePlaintextMigrator.hasInterruptedMigration(dbFile))

        val backupFile = File(testDir, "${dbFile.name}.plaintext_backup")
        backupFile.writeBytes(sqliteMagic + ByteArray(32))
        assertTrue(DatabasePlaintextMigrator.hasInterruptedMigration(dbFile))

        backupFile.delete()
        assertFalse(DatabasePlaintextMigrator.hasInterruptedMigration(dbFile))

        val stateFile = File(testDir, "${dbFile.name}.migration_state")
        stateFile.writeBytes(DatabasePlaintextMigrator.STATE_ISOLATED.toByteArray())
        assertTrue(DatabasePlaintextMigrator.hasInterruptedMigration(dbFile))
    }

    @Test
    fun testRecovery_whenVerifiedStatePresent_safelyWipesBackupAndKeepsTarget() {
        val dbFile = File(testDir, "mnemo.db")
        val backupFile = File(testDir, "${dbFile.name}.plaintext_backup")
        val stateFile = File(testDir, "${dbFile.name}.migration_state")

        // Encrypted target exists and is complete
        dbFile.writeBytes(ByteArray(256) { 0x55 })
        backupFile.writeBytes(sqliteMagic + ByteArray(256))
        stateFile.writeBytes(DatabasePlaintextMigrator.STATE_VERIFIED.toByteArray())

        DatabasePlaintextMigrator.recoverInterruptedMigration(dbFile)

        // Verified encrypted db must remain untouched
        assertTrue("Target encrypted database must be preserved", dbFile.exists())
        assertEquals(256L, dbFile.length())

        // Backup and state file must be removed
        assertFalse("Backup file must be wiped and removed", backupFile.exists())
        assertFalse("Migration state file must be deleted", stateFile.exists())
    }

    @Test
    fun testRecovery_whenEncryptingInterrupted_restoresPlaintextBackup() {
        val dbFile = File(testDir, "mnemo.db")
        val backupFile = File(testDir, "${dbFile.name}.plaintext_backup")
        val stateFile = File(testDir, "${dbFile.name}.migration_state")

        // Incomplete / corrupt dbFile
        dbFile.writeBytes(ByteArray(50) { 0x00 })
        // Intact plaintext backup
        backupFile.writeBytes(sqliteMagic + ByteArray(500))
        stateFile.writeBytes(DatabasePlaintextMigrator.STATE_ENCRYPTING.toByteArray())

        DatabasePlaintextMigrator.recoverInterruptedMigration(dbFile)

        // Target dbFile must now be restored to valid plaintext backup
        assertTrue("Database file must exist", dbFile.exists())
        assertEquals(516L, dbFile.length())
        assertTrue(DatabasePlaintextMigrator.isPlaintextDatabase(dbFile))

        // Backup and state file removed
        assertFalse(backupFile.exists())
        assertFalse(stateFile.exists())
    }

    @Test
    fun testRecovery_neverOverwritesWhenBackupCorrupted_unrecoverableState() {
        val dbFile = File(testDir, "mnemo.db")
        val backupFile = File(testDir, "${dbFile.name}.plaintext_backup")
        val stateFile = File(testDir, "${dbFile.name}.migration_state")

        // Incomplete dbFile
        dbFile.writeBytes(ByteArray(100) { 0x11 })
        // Corrupted backup (e.g. wiped halfway with zeros, not valid SQLite)
        backupFile.writeBytes(ByteArray(200) { 0x00 })
        stateFile.writeBytes(DatabasePlaintextMigrator.STATE_ISOLATED.toByteArray())

        // Must fail with IllegalStateException and preserve both files untouched
        assertThrows(IllegalStateException::class.java) {
            DatabasePlaintextMigrator.recoverInterruptedMigration(dbFile)
        }

        assertTrue("dbFile must be preserved for forensic recovery", dbFile.exists())
        assertTrue("backupFile must be preserved for forensic recovery", backupFile.exists())
    }

    @Test
    fun testWipeAndDelete_overwritesAndUnlinks() {
        val file = File(testDir, "sensitive.db")
        file.writeBytes(ByteArray(8192) { 0xAA.toByte() })
        assertTrue(file.exists())

        DatabasePlaintextMigrator.wipeAndDelete(file)
        assertFalse(file.exists())
    }
}
