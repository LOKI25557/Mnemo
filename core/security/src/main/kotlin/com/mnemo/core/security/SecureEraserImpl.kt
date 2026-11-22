package com.mnemo.core.security

import android.content.Context
import com.mnemo.core.model.repository.MemoryRepository
import com.mnemo.core.model.security.KeyStoreProvider
import com.mnemo.core.model.security.SecureEraser
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore

/**
 * Concrete implementation of [SecureEraser].
 * Performs multi-layer sanitization with cryptographic erasure as the primary defense:
 * 1. Coordinates with concurrency gate via [startWipeCoordinator], blocking new operations and draining active queries.
 * 2. Empties all logical database records via active repositories.
 * 3. Closes all active Room and SQLCipher database connections via [onBeforeWipe].
 * 4. Overwrites and deletes local database files, journals (WAL, SHM), migration journals, and backups.
 * 5. Overwrites and deletes the encrypted passphrase envelope file.
 * 6. Revokes master encryption keys in Android Keystore ([KeyStoreProvider.DEFAULT_KEY_ALIAS]).
 * 7. Safely concludes the wipe lifecycle via [finishWipeCoordinator].
 *
 * Concurrency Safety:
 * Employs a non-blocking [Mutex] to reject concurrent simultaneous wipe requests without deadlocks.
 *
 * Security Reality on Flash Memory (NAND / eMMC / UFS):
 * Flash memory utilizes wear-leveling controllers, bad block retirement, and out-of-place writes.
 * Software zero-filling cannot guarantee that physical flash cells containing previous data are erased.
 * Cryptographic erasure is the primary defense: by destroying the hardware-backed Keystore master key
 * and overwriting the 256-bit passphrase envelope, all underlying on-disk database pages
 * become mathematically irrecoverable ciphertext.
 */
class SecureEraserImpl(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val keyStoreProvider: KeyStoreProvider? = null,
    private val startWipeCoordinator: (suspend () -> Boolean)? = null,
    private val onBeforeWipe: (() -> Unit)? = null,
    private val finishWipeCoordinator: (() -> Unit)? = null
) : SecureEraser {

    private val wipeMutex = Mutex()

    override suspend fun secureWipeAll(): Boolean {
        // Reject simultaneous wipe requests safely
        if (!wipeMutex.tryLock()) {
            return false
        }

        return try {
            // Step 1: Coordinate database operation blocking and drain active queries
            val wipeInitiated = startWipeCoordinator?.invoke() ?: true
            if (!wipeInitiated) {
                return false
            }

            // Step 2: Wipe logical records from active repositories (best effort before closure)
            try {
                memoryRepository.deleteAll()
            } catch (_: Exception) {}

            // Step 3: Coordinate database closure to flush WAL and close SQLite connections safely
            try {
                onBeforeWipe?.invoke()
            } catch (_: Exception) {}

            // Step 4: Overwrite and delete all database files, companion journals, and migration state
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val parent = dbFile.parentFile ?: context.filesDir
            val filesToWipe = listOf(
                dbFile,
                File(parent, "$DATABASE_NAME-wal"),
                File(parent, "$DATABASE_NAME-shm"),
                File(parent, "$DATABASE_NAME.plaintext_backup"),
                File(parent, "$DATABASE_NAME.plaintext_backup-wal"),
                File(parent, "$DATABASE_NAME.plaintext_backup-shm"),
                File(parent, "$DATABASE_NAME.migration_state"),
                File(parent, "$DATABASE_NAME.migration_state.tmp"),
                File(context.filesDir, "security/mnemo_db_passphrase.bin"),
                File(context.filesDir, "security/mnemo_db_passphrase.bin.tmp")
            )

            var allFilesCleaned = true
            for (file in filesToWipe) {
                if (file.exists()) {
                    val wiped = wipeFile(file)
                    if (!wiped) {
                        allFilesCleaned = false
                    }
                }
            }

            // Step 5: Revoke master keys in Android Keystore (Cryptographic Erasure)
            val keystoreKeyDeleted = keyStoreProvider?.deleteKey(KeyStoreProvider.DEFAULT_KEY_ALIAS) ?: true
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val aliasesToRevoke = listOf(
                KeyStoreProvider.DEFAULT_KEY_ALIAS,
                "mnemo_master_database_key",
                "mnemo_db_master_key"
            )
            for (alias in aliasesToRevoke) {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                }
            }

            allFilesCleaned && keystoreKeyDeleted
        } catch (e: Exception) {
            false
        } finally {
            finishWipeCoordinator?.invoke()
            wipeMutex.unlock()
        }
    }

    private fun wipeFile(file: File): Boolean {
        if (!file.exists()) return true
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
            } catch (_: Exception) {
                return false
            }
        }
        return file.delete()
    }

    companion object {
        const val DATABASE_NAME = "mnemo_local.db"
    }
}
