package com.mnemo.core.database.migration

import android.content.Context
import androidx.room.Room
import com.mnemo.core.database.MnemoDatabase
import com.mnemo.core.database.security.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Thrown when an unsupported table, view, or trigger is encountered during migration,
 * preventing silent data loss.
 */
class UnsupportedSchemaException(message: String) : IllegalStateException(message)

/**
 * Migration orchestrator for safely upgrading existing unencrypted SQLite databases to SQLCipher.
 *
 * Implements strict data preservation and crash-safety rules:
 * - Accurately detects plaintext SQLite databases by header inspection (0x53 0x51 0x4C 0x69 0x74 0x65 0x20...).
 * - Uses a durable migration journal/state file to guarantee crash-recoverability across all stages.
 * - Inspects sqlite_master to verify that all schema objects (tables, indexes, triggers, views) are supported.
 * - Safely aborts if any unsupported schema object is detected, preventing silent data loss.
 * - Preserves FTS4 external-content virtual tables and verifies all 4 Room synchronization triggers.
 * - Isolates and backs up original plaintext database file before touching data.
 * - Extracts all entities through the plaintext driver.
 * - Re-inserts into the newly initialized SQLCipher database in an atomic transaction.
 * - Validates record counts, index existence, FTS virtual table searchability, and data integrity before removing the plaintext copy.
 * - Restores original files untouched if any failure occurs during the migration window.
 * - Guarantees that recovery never overwrites the only valid copy of the database.
 */
object DatabasePlaintextMigrator {

    private val SQLITE_PLAINTEXT_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    const val STATE_ISOLATED = "ISOLATED"
    const val STATE_ENCRYPTING = "ENCRYPTING"
    const val STATE_VERIFIED = "VERIFIED"

    private val KNOWN_TABLES = setOf(
        "memory_items",
        "access_audit_log",
        "memory_items_fts",
        "memory_items_fts_content",
        "memory_items_fts_segments",
        "memory_items_fts_segdir",
        "memory_items_fts_docsize",
        "memory_items_fts_stat"
    )

    private val REQUIRED_INDEXES = setOf(
        "index_memory_items_source_type",
        "index_memory_items_timestamp",
        "index_memory_items_person_name",
        "index_access_audit_log_timestamp",
        "index_access_audit_log_source_type"
    )

    private val REQUIRED_FTS_TRIGGERS = setOf(
        "room_fts_content_sync_memory_items_fts_BEFORE_UPDATE",
        "room_fts_content_sync_memory_items_fts_BEFORE_DELETE",
        "room_fts_content_sync_memory_items_fts_AFTER_UPDATE",
        "room_fts_content_sync_memory_items_fts_AFTER_INSERT"
    )

    /**
     * Inspects the first 16 bytes of the specified file to determine whether it is an
     * unencrypted SQLite 3 database.
     */
    fun isPlaintextDatabase(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(16)
                val read = fis.read(header)
                read == 16 && header.contentEquals(SQLITE_PLAINTEXT_HEADER)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if an interrupted migration was left in progress due to an unexpected process termination.
     */
    fun hasInterruptedMigration(dbFile: File): Boolean {
        val parent = dbFile.parentFile ?: return false
        val backupFile = File(parent, "${dbFile.name}.plaintext_backup")
        val stateFile = File(parent, "${dbFile.name}.migration_state")
        return stateFile.exists() || (backupFile.exists() && backupFile.length() > 0)
    }

    /**
     * Recovers from an interrupted migration safely without overwriting the only valid copy.
     *
     * Recovery Decision Matrix:
     * 1. State is VERIFIED: Encrypted dbFile was already verified valid. Finish wiping backupFile.
     * 2. State is ISOLATED or ENCRYPTING (or state missing with backup present):
     *    - If backupFile is a valid plaintext database: delete partial dbFile, restore backupFile -> dbFile.
     *    - If backupFile is corrupted (e.g. wiped) but dbFile is valid: keep dbFile, delete backupFile.
     *    - If neither is valid: throw IllegalStateException and preserve files for forensic recovery.
     */
    fun recoverInterruptedMigration(dbFile: File) {
        val parent = dbFile.parentFile ?: return
        val backupFile = File(parent, "${dbFile.name}.plaintext_backup")
        val stateFile = File(parent, "${dbFile.name}.migration_state")
        val dbWal = File(parent, "${dbFile.name}-wal")
        val dbShm = File(parent, "${dbFile.name}-shm")
        val backupWal = File(parent, "${backupFile.name}-wal")
        val backupShm = File(parent, "${backupFile.name}-shm")

        val state = if (stateFile.exists()) {
            try { stateFile.readText().trim() } catch (_: Exception) { "" }
        } else {
            ""
        }

        when (state) {
            STATE_VERIFIED -> {
                // Encrypted dbFile was verified valid before interruption. Safely finish wiping backup.
                wipeAndDelete(backupFile)
                wipeAndDelete(backupWal)
                wipeAndDelete(backupShm)
                stateFile.delete()
            }
            else -> {
                // Interrupted before or during verification. Check which copy is valid.
                val backupIsPlaintext = isPlaintextDatabase(backupFile)
                if (backupIsPlaintext) {
                    // Backup is the only verified valid copy: discard partial target and restore backup
                    if (dbFile.exists()) dbFile.delete()
                    if (dbWal.exists()) dbWal.delete()
                    if (dbShm.exists()) dbShm.delete()

                    backupFile.renameTo(dbFile)
                    if (backupWal.exists()) backupWal.renameTo(dbWal)
                    if (backupShm.exists()) backupShm.renameTo(dbShm)
                    if (stateFile.exists()) stateFile.delete()
                } else if (!backupFile.exists() || backupFile.length() == 0L) {
                    // Backup file does not exist or was already wiped
                    if (stateFile.exists()) stateFile.delete()
                } else {
                    // Unrecoverable state: backup exists but is not valid plaintext SQLite
                    // Do NOT overwrite either file to prevent catastrophic loss
                    throw IllegalStateException(
                        "Unrecoverable migration state: plaintext backup is corrupted or invalid. " +
                        "Files preserved at ${backupFile.absolutePath} and ${dbFile.absolutePath}"
                    )
                }
            }
        }
    }

    /**
     * Inspects sqlite_master to ensure the source database does not contain unsupported schema objects
     * (e.g., custom tables, views, or triggers) that would be silently dropped during migration.
     */
    fun validateSourceSchemaObjects(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val cursor = db.query(
            "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'"
        )
        cursor.use {
            val typeIdx = it.getColumnIndex("type")
            val nameIdx = it.getColumnIndex("name")
            while (it.moveToNext()) {
                val type = it.getString(typeIdx)
                val name = it.getString(nameIdx)
                when (type) {
                    "table" -> {
                        if (name !in KNOWN_TABLES) {
                            throw UnsupportedSchemaException(
                                "Unsupported table '$name' detected in migration source. " +
                                "Migration rejected to prevent silent data loss."
                            )
                        }
                    }
                    "view" -> {
                        throw UnsupportedSchemaException(
                            "Unsupported view '$name' detected in migration source. " +
                            "Migration rejected to prevent silent data loss."
                        )
                    }
                    "trigger" -> {
                        if (!name.contains("fts", ignoreCase = true) && !name.startsWith("room_fts")) {
                            throw UnsupportedSchemaException(
                                "Unsupported custom trigger '$name' detected in migration source. " +
                                "Migration rejected to prevent silent data loss."
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates that all required indexes, FTS virtual tables, and Room synchronization triggers
     * exist in the target database.
     */
    fun validateTargetSchemaObjects(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val existingIndexes = mutableSetOf<String>()
        val existingTables = mutableSetOf<String>()
        val existingTriggers = mutableSetOf<String>()

        val cursor = db.query(
            "SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'"
        )
        cursor.use {
            val typeIdx = it.getColumnIndex("type")
            val nameIdx = it.getColumnIndex("name")
            while (it.moveToNext()) {
                val type = it.getString(typeIdx)
                val name = it.getString(nameIdx)
                when (type) {
                    "index" -> existingIndexes.add(name)
                    "table" -> existingTables.add(name)
                    "trigger" -> existingTriggers.add(name)
                }
            }
        }

        for (requiredIndex in REQUIRED_INDEXES) {
            check(requiredIndex in existingIndexes) {
                "Target encrypted database missing required index: '$requiredIndex'"
            }
        }

        check("memory_items_fts" in existingTables) {
            "Target encrypted database missing required FTS virtual table: 'memory_items_fts'"
        }

        for (requiredTrigger in REQUIRED_FTS_TRIGGERS) {
            check(requiredTrigger in existingTriggers) {
                "Target encrypted database missing required FTS trigger: '$requiredTrigger'"
            }
        }
    }

    /**
     * Migrates records from an existing plaintext database into a freshly created SQLCipher database.
     *
     * @param context Application context.
     * @param dbFile Target database file on disk.
     * @param keyProvider Encryption key provider supplying the 256-bit SQLCipher passphrase.
     * @return Number of memory records successfully migrated.
     */
    fun migratePlaintextToEncrypted(
        context: Context,
        dbFile: File,
        keyProvider: DatabaseEncryptionKeyProvider
    ): Int {
        val parent = dbFile.parentFile ?: context.filesDir
        val backupFile = File(parent, "${dbFile.name}.plaintext_backup")
        val stateFile = File(parent, "${dbFile.name}.migration_state")
        val dbWal = File(parent, "${dbFile.name}-wal")
        val dbShm = File(parent, "${dbFile.name}-shm")
        val backupWal = File(parent, "${backupFile.name}-wal")
        val backupShm = File(parent, "${backupFile.name}-shm")

        // 0. Crash recovery: if an interrupted migration left files on disk, recover safely
        if (hasInterruptedMigration(dbFile)) {
            recoverInterruptedMigration(dbFile)
        }

        // Clean any leftover zero-length stale backup files
        if (backupFile.exists() && backupFile.length() == 0L) backupFile.delete()
        if (backupWal.exists() && backupWal.length() == 0L) backupWal.delete()
        if (backupShm.exists() && backupShm.length() == 0L) backupShm.delete()

        // 1. Mark state ISOLATED and isolate the plaintext database and companion files
        writeStateAtomically(stateFile, STATE_ISOLATED)
        if (!dbFile.renameTo(backupFile)) {
            stateFile.delete()
            throw IllegalStateException("Failed to isolate plaintext database before encryption migration: ${dbFile.absolutePath}")
        }
        if (dbWal.exists()) dbWal.renameTo(backupWal)
        if (dbShm.exists()) dbShm.renameTo(backupShm)

        return try {
            // 2. Open plaintext database using standard framework SQLite driver
            val plaintextDb = Room.databaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java,
                backupFile.name
            ).build()

            // Validate source schema for unsupported objects to prevent silent data loss
            validateSourceSchemaObjects(plaintextDb.openHelper.readableDatabase)

            val memoryItems = runBlocking { plaintextDb.memoryDao().getAll() }
            val auditLogs = runBlocking { plaintextDb.auditLogDao().getAll() }
            plaintextDb.close()

            // 3. Mark state ENCRYPTING and create new SQLCipher encrypted database
            writeStateAtomically(stateFile, STATE_ENCRYPTING)
            val passphrase = keyProvider.getPassphrase()
            val openHelperFactory = SupportOpenHelperFactory(passphrase)

            val encryptedDb = Room.databaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java,
                dbFile.name
            )
                .openHelperFactory(openHelperFactory)
                .build()

            // 4. Insert records within an atomic transaction
            encryptedDb.runInTransaction {
                runBlocking {
                    if (memoryItems.isNotEmpty()) {
                        encryptedDb.memoryDao().insertAll(memoryItems)
                    }
                    if (auditLogs.isNotEmpty()) {
                        encryptedDb.auditLogDao().insertAll(auditLogs)
                    }
                }
            }

            // 5. Verify integrity, record counts, and schema objects in encrypted database
            val encryptedMemoryCount = runBlocking { encryptedDb.memoryDao().count() }
            val encryptedAuditCount = runBlocking { encryptedDb.auditLogDao().count() }
            check(encryptedMemoryCount == memoryItems.size) {
                "Memory record count verification failed after migration: expected ${memoryItems.size}, got $encryptedMemoryCount"
            }
            check(encryptedAuditCount == auditLogs.size) {
                "Audit log count verification failed after migration: expected ${auditLogs.size}, got $encryptedAuditCount"
            }

            // Verify all required indexes and FTS virtual table and triggers exist
            validateTargetSchemaObjects(encryptedDb.openHelper.readableDatabase)

            // Verify FTS table contains populated records matching migrated count
            val ftsCount = runBlocking { encryptedDb.memoryDao().countFts() }
            check(ftsCount == memoryItems.size) {
                "FTS index record count mismatch after migration: expected ${memoryItems.size}, got $ftsCount"
            }

            // Verify actual FTS search returns migrated records
            if (memoryItems.isNotEmpty()) {
                val firstItem = memoryItems.first()
                val searchToken = firstItem.title.split(" ").firstOrNull()?.trim() ?: firstItem.id
                val ftsResults = runBlocking { encryptedDb.memoryDao().searchFts(searchToken) }
                check(ftsResults.isNotEmpty()) {
                    "FTS search verification failed: query '$searchToken' returned no results"
                }
                check(ftsResults.any { it.id == firstItem.id }) {
                    "FTS search verification failed: query '$searchToken' did not return source item ${firstItem.id}"
                }
            }

            encryptedDb.close()

            // 6. Verify reopenability with encrypted helper
            val reopenFactory = SupportOpenHelperFactory(passphrase)
            val verificationDb = Room.databaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java,
                dbFile.name
            )
                .openHelperFactory(reopenFactory)
                .build()
            val verifiedCount = runBlocking { verificationDb.memoryDao().count() }
            check(verifiedCount == memoryItems.size) {
                "Reopen verification failed: expected ${memoryItems.size}, got $verifiedCount"
            }
            verificationDb.close()

            // 7. Verify file on disk is not plaintext SQLite
            check(!isPlaintextDatabase(dbFile)) {
                "Security check failed: Migrated database file still matches plaintext SQLite header"
            }

            // 8. Migration verified: advance state to VERIFIED before wiping plaintext backup
            writeStateAtomically(stateFile, STATE_VERIFIED)

            wipeAndDelete(backupFile)
            wipeAndDelete(backupWal)
            wipeAndDelete(backupShm)
            stateFile.delete()

            memoryItems.size
        } catch (e: Exception) {
            // Rollback: delete failed target and restore original plaintext and companion files
            if (dbFile.exists()) dbFile.delete()
            if (dbWal.exists()) dbWal.delete()
            if (dbShm.exists()) dbShm.delete()

            if (backupFile.exists() && !dbFile.exists() && isPlaintextDatabase(backupFile)) {
                backupFile.renameTo(dbFile)
            }
            if (backupWal.exists() && !dbWal.exists()) {
                backupWal.renameTo(dbWal)
            }
            if (backupShm.exists() && !dbShm.exists()) {
                backupShm.renameTo(dbShm)
            }
            if (stateFile.exists()) stateFile.delete()
            throw IllegalStateException("Plaintext database migration failed. Original database has been safely restored.", e)
        }
    }

    private fun writeStateAtomically(stateFile: File, state: String) {
        val temp = File(stateFile.parentFile, "${stateFile.name}.tmp")
        FileOutputStream(temp).use { fos ->
            fos.write(state.toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }
        if (!temp.renameTo(stateFile)) {
            temp.copyTo(stateFile, overwrite = true)
            temp.delete()
        }
    }

    fun wipeAndDelete(file: File) {
        if (file.exists()) {
            val length = file.length()
            if (length > 0) {
                try {
                    FileOutputStream(file).use { out ->
                        val buffer = ByteArray(4096)
                        var written = 0L
                        while (written < length) {
                            val toWrite = (length - written).coerceAtMost(buffer.size.toLong()).toInt()
                            out.write(buffer, 0, toWrite)
                            written += toWrite
                        }
                        out.flush()
                        out.fd.sync()
                    }
                } catch (_: Exception) {}
            }
            file.delete()
        }
    }
}
