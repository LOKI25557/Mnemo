package com.mnemo.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mnemo.core.database.dao.AuditLogDao
import com.mnemo.core.database.dao.MemoryDao
import com.mnemo.core.database.entity.AuditLogEntity
import com.mnemo.core.database.entity.FtsMemoryEntity
import com.mnemo.core.database.entity.MemoryEntity
import com.mnemo.core.database.migration.DatabasePlaintextMigrator
import com.mnemo.core.database.security.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.delay
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main Room database definition for Mnemo on-device storage.
 *
 * Fully encrypted at rest via SQLCipher with 256-bit AES passphrase
 * protected by hardware-backed Android Keystore.
 */
@Database(
    entities = [
        MemoryEntity::class,
        AuditLogEntity::class,
        FtsMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MnemoDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DATABASE_NAME = "mnemo_local.db"

        @Volatile
        private var instance: MnemoDatabase? = null

        private val isWiping = AtomicBoolean(false)
        private val activeOperations = AtomicInteger(0)

        private var activePassphrase: ByteArray? = null
        private val lock = Any()

        /**
         * Sets the secure wiping flag.
         */
        fun setWiping(wiping: Boolean) {
            isWiping.set(wiping)
        }

        /**
         * Returns whether a secure deletion / wipe operation is currently active.
         */
        fun isWiping(): Boolean = isWiping.get()

        /**
         * Returns the number of currently active database/repository operations.
         */
        fun getActiveOperationCount(): Int = activeOperations.get()

        /**
         * Executes an operation with concurrency tracking.
         * Rejects new operations if a secure wipe is in progress, and cancels operations if a wipe starts.
         */
        suspend fun <T> withOperation(block: suspend () -> T): T {
            if (isWiping.get()) {
                throw IllegalStateException("Database operation rejected: secure wipe in progress")
            }
            activeOperations.incrementAndGet()
            try {
                if (isWiping.get()) {
                    throw IllegalStateException("Database operation cancelled: secure wipe initiated")
                }
                return block()
            } finally {
                activeOperations.decrementAndGet()
            }
        }

        /**
         * Coordinates the initiation of a secure wipe:
         * 1. Atomically sets isWiping to true, rejecting any new database operations.
         * 2. Waits for all already-running operations to finish, up to timeoutMs.
         *
         * @param timeoutMs Maximum time in milliseconds to wait for active operations to drain.
         * @return True if all active operations drained successfully; false if another wipe is active or timeout occurred.
         */
        suspend fun startWipe(timeoutMs: Long = 5000L): Boolean {
            if (!isWiping.compareAndSet(false, true)) {
                // Another wipe is already in progress
                return false
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (activeOperations.get() > 0) {
                if (System.currentTimeMillis() > deadline) {
                    // Timeout waiting for active operations to drain
                    isWiping.set(false)
                    return false
                }
                delay(10)
            }
            return true
        }

        /**
         * Concludes a secure wipe lifecycle, resetting the wiping gate and clearing database references.
         */
        fun finishWipe() {
            synchronized(lock) {
                closeInstance()
                isWiping.set(false)
            }
        }

        /**
         * Returns the active application singleton database instance, creating and initializing it
         * with genuine SQLCipher encryption if not already created.
         *
         * @throws IllegalStateException if a secure wipe is currently in progress.
         */
        fun getInstance(
            context: Context,
            keyProvider: DatabaseEncryptionKeyProvider
        ): MnemoDatabase {
            check(!isWiping.get()) { "Cannot access database: secure wipe operation is in progress" }
            return instance ?: synchronized(lock) {
                check(!isWiping.get()) { "Cannot access database: secure wipe operation is in progress" }
                instance ?: buildDatabase(context, keyProvider).also { instance = it }
            }
        }

        /**
         * Safely closes the active database connection pool, releases open helper resources,
         * zeros out the retained in-memory passphrase byte array, and resets the singleton reference.
         *
         * Memory Sanitization & JVM Reality Note:
         * 1. Byte array zero-filling: Arrays.fill(pass, 0) overwrites the JVM heap byte array
         *    referenced by activePassphrase.
         * 2. JVM Garbage Collector realities: The Android Runtime (ART) garbage collector can relocate
         *    objects in memory during generational compaction. Pre-compaction transient memory pages
         *    may retain historical byte copies until reclaimed by the OS kernel.
         * 3. Native library memory: SQLCipher (libsqlcipher.so) allocates native heap structures
         *    via OpenSSL/BoringSSL for AES page key material. Closing the database invokes sqlite3_close(),
         *    which deallocates these native structures.
         * 4. Complete memory hygiene is only fully guaranteed upon Android OS process termination.
         */
        fun closeInstance() {
            synchronized(lock) {
                instance?.let { db ->
                    if (db.isOpen) {
                        db.close()
                    }
                    instance = null
                }
                activePassphrase?.let { pass ->
                    java.util.Arrays.fill(pass, 0.toByte())
                    activePassphrase = null
                }
            }
        }

        /**
         * Builds a production-ready SQLCipher-encrypted Room database instance.
         *
         * Passphrase Lifecycle Details:
         * Bytecode and source inspection of [SupportOpenHelperFactory] and [net.zetetic.database.sqlcipher.SQLiteOpenHelper]
         * reveals that SQLCipher stores the exact ByteArray reference passed to [SupportOpenHelperFactory]
         * inside its helper instance (this.mPassword). Room creates SQLite connections lazily when queries or
         * transactions are executed. If the ByteArray is cleared at database build time, subsequent lazy connections
         * or connection pool expansions receive all zeros, triggering decryption failure.
         * Therefore, the passphrase array must remain valid throughout the active database lifecycle,
         * and is securely zeroized inside [closeInstance] once all database connections are closed.
         *
         * @param context Application context.
         * @param keyProvider Keystore-backed key provider supplying the 256-bit passphrase.
         * @return Encrypted Room database instance.
         */
        fun buildDatabase(
            context: Context,
            keyProvider: DatabaseEncryptionKeyProvider
        ): MnemoDatabase {
            check(!isWiping.get()) { "Cannot build database: secure wipe operation is in progress" }

            // 1. Initialize SQLCipher native binaries
            System.loadLibrary("sqlcipher")

            // 2. Check and perform plaintext migration or interrupted migration recovery
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (DatabasePlaintextMigrator.hasInterruptedMigration(dbFile)) {
                DatabasePlaintextMigrator.recoverInterruptedMigration(dbFile)
            }

            if (dbFile.exists() && DatabasePlaintextMigrator.isPlaintextDatabase(dbFile)) {
                DatabasePlaintextMigrator.migratePlaintextToEncrypted(
                    context = context,
                    dbFile = dbFile,
                    keyProvider = keyProvider
                )
            }

            // 3. Obtain 256-bit passphrase from the Keystore key provider
            val passphrase = keyProvider.getPassphrase()
            require(passphrase.isNotEmpty()) { "Database encryption passphrase must not be empty" }
            activePassphrase = passphrase

            // 4. Configure genuine SQLCipher SupportOpenHelperFactory
            val openHelperFactory = SupportOpenHelperFactory(passphrase)

            // 5. Build Room database with SQLCipher factory
            return Room.databaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(openHelperFactory)
                .build()
        }

        /**
         * Builds an encrypted database using an explicit passphrase and file name.
         * Used for multi-instance tests and instrumentation test scenarios.
         */
        fun buildEncryptedDatabase(
            context: Context,
            dbName: String,
            passphrase: ByteArray
        ): MnemoDatabase {
            System.loadLibrary("sqlcipher")
            val openHelperFactory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java,
                dbName
            )
                .openHelperFactory(openHelperFactory)
                .build()
        }

        /**
         * Creates an unencrypted in-memory database instance strictly for host-based JVM unit tests
         * where Android native JNI libraries (.so) cannot be loaded.
         */
        fun buildUnencryptedInMemory(context: Context): MnemoDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                MnemoDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
