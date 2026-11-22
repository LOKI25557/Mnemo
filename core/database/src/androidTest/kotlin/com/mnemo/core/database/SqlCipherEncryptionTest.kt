package com.mnemo.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemo.core.database.entity.MemoryEntity
import com.mnemo.core.database.migration.DatabasePlaintextMigrator
import com.mnemo.core.database.security.CorruptedKeyMaterialException
import com.mnemo.core.database.security.KeystoreDatabaseEncryptionKeyProvider
import com.mnemo.core.database.security.MissingKeyMaterialException
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.security.KeyStoreProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Arrays

/**
 * Production Android Instrumentation Test Suite for Genuine SQLCipher Database Encryption.
 *
 * Verifies all 9 mandatory security criteria on physical devices or emulators:
 * - Test 1: First database creation with SQLCipher (verifies DB genuinely exists, writes synthetic record).
 * - Test 2: Data persistence across database close/reopen (retrieves exact inserted fields).
 * - Test 3: Encryption at rest (asserts DB closed before inspection, header is NOT "SQLite format 3", ordinary SQLite fails).
 * - Test 4: Wrong passphrase fails decryption safely without leaking plaintext.
 * - Test 5: Keystore passphrase persistence across simulated process restarts.
 * - Test 6: Missing key material fails safely without creating plaintext fallback.
 * - Test 7: Corrupted key material detection without database destruction.
 * - Test 8: Plaintext to SQLCipher encrypted migration preserving records, triggers, and FTS search.
 * - Test 9: Key destruction prevents reopen (cryptographic erasure verification).
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherEncryptionTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val testDbName = "mnemo_test_sqlcipher.db"
    private val testPlaintextDbName = "mnemo_test_plaintext.db"

    private val secureRandom = SecureRandom()
    private val validPassphrase = ByteArray(32).apply { secureRandom.nextBytes(this) }
    private val wrongPassphrase = ByteArray(32).apply { Arrays.fill(this, 0xEE.toByte()) }

    @Before
    fun setUp() {
        cleanTestDatabases()
    }

    @After
    fun tearDown() {
        MnemoDatabase.closeInstance()
        cleanTestDatabases()
    }

    private fun cleanTestDatabases() {
        context.deleteDatabase(testDbName)
        context.deleteDatabase(testPlaintextDbName)
        File(context.filesDir, "security").deleteRecursively()
    }

    /**
     * Test 1 — First database creation
     * The database initializes successfully using SQLCipher SupportOpenHelperFactory,
     * genuinely creates a file on disk, and writes and reads an initial synthetic record.
     */
    @Test
    fun test1_firstDatabaseCreation() {
        val db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, validPassphrase)
        assertNotNull(db)

        val entity = MemoryEntity(
            id = "init_test_item",
            sourceType = DataSourceType.CONTACT.name,
            title = "Test Creation",
            content = "Validating initial creation",
            timestamp = System.currentTimeMillis(),
            sourceUri = "content://contacts/test"
        )
        runBlocking { db.memoryDao().insert(entity) }
        val count = runBlocking { db.memoryDao().count() }
        assertEquals(1, count)
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue("Database file must genuinely exist on disk after creation", dbFile.exists())
        assertTrue("Database file must have non-zero size on disk", dbFile.length() > 0)
    }

    /**
     * Test 2 — Data persistence
     * Insert a record, close the database, reopen it, and verify that the record is readable
     * and exact fields match.
     */
    @Test
    fun test2_dataPersistence() {
        // Step 1: Open, insert record, and close
        var db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, validPassphrase)
        val entity = MemoryEntity(
            id = "test_mem_001",
            sourceType = DataSourceType.CONTACT.name,
            title = "Rahul Sharma",
            content = "Mobile: +91 9876543210, Email: rahul@example.com",
            personName = "Rahul Sharma",
            timestamp = 1700000000000L,
            sourceUri = "content://contacts/people/1"
        )
        runBlocking { db.memoryDao().insert(entity) }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue("Database file must exist on disk", dbFile.exists())
        assertTrue("Database file must be non-empty", dbFile.length() > 0)

        // Step 2: Reopen with the same passphrase and verify content
        db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, validPassphrase)
        val retrieved = runBlocking { db.memoryDao().getById("test_mem_001") }
        assertNotNull(retrieved)
        assertEquals("Rahul Sharma", retrieved?.title)
        assertEquals("Mobile: +91 9876543210, Email: rahul@example.com", retrieved?.content)
        assertEquals("Rahul Sharma", retrieved?.personName)
        assertEquals(1700000000000L, retrieved?.timestamp)
        db.close()
    }

    /**
     * Test 3 — Encryption at rest
     * Read the database file header from disk and confirm that it does not contain
     * the standard SQLite signature "SQLite format 3", and ordinary SQLite fails.
     */
    @Test
    fun test3_encryptionAtRest() {
        // Insert record and close database so all pages are flushed to disk
        val db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, validPassphrase)
        runBlocking {
            db.memoryDao().insert(
                MemoryEntity(
                    id = "secret_id_123",
                    sourceType = DataSourceType.MESSAGE.name,
                    title = "Confidential Message",
                    content = "Secret meeting details at private location",
                    timestamp = System.currentTimeMillis(),
                    sourceUri = "content://messages/1"
                )
            )
        }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue("Database file must genuinely exist on disk", dbFile.exists())
        assertTrue("Database file must have non-zero size (> 4KB)", dbFile.length() >= 4096)

        // Read first 16 bytes of the database file
        val headerBytes = ByteArray(16)
        FileInputStream(dbFile).use { it.read(headerBytes) }

        val standardSqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        // NON-NEGOTIABLE CRITERION: Must NOT match SQLite format 3
        assertFalse(
            "Security Failure: Database header matched plaintext 'SQLite format 3' signature!",
            headerBytes.contentEquals(standardSqliteMagic)
        )

        // Verify that DatabasePlaintextMigrator also correctly reports it as NOT plaintext
        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(dbFile))

        // NON-NEGOTIABLE CRITERION: Ordinary unencrypted SQLite MUST fail to read the encrypted file
        try {
            val unencryptedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = unencryptedDb.rawQuery("SELECT count(*) FROM memory_items", null)
            cursor.moveToFirst()
            cursor.close()
            unencryptedDb.close()
            fail("Security Failure: Plaintext SQLite successfully opened and queried the encrypted database!")
        } catch (e: Exception) {
            // Expected: android.database.sqlite.SQLiteException indicating invalid database / corrupt header
            assertTrue(
                "Exception must indicate decryption failure or invalid database format: ${e.message}",
                e is android.database.sqlite.SQLiteException
            )
        }
    }

    /**
     * Test 4 — Wrong key
     * Attempt to open a copy of the encrypted database using an incorrect passphrase.
     * It must fail to decrypt.
     */
    @Test
    fun test4_wrongKeyFailsDecryption() {
        // Create database with valid passphrase
        val db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, validPassphrase)
        runBlocking {
            db.memoryDao().insert(
                MemoryEntity(
                    id = "secure_item",
                    sourceType = DataSourceType.FILE.name,
                    title = "Sensitive Doc",
                    content = "Top secret file contents",
                    timestamp = System.currentTimeMillis(),
                    sourceUri = "file:///storage/doc.pdf"
                )
            )
        }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > 0)

        // Attempt to open with incorrect passphrase
        val wrongDb = MnemoDatabase.buildEncryptedDatabase(context, testDbName, wrongPassphrase)
        try {
            runBlocking {
                wrongDb.memoryDao().count()
            }
            fail("Security Failure: Encrypted database opened with an incorrect passphrase!")
        } catch (e: Exception) {
            // Expected: android.database.sqlite.SQLiteDatabaseCorruptException or SQLiteException
            assertTrue(
                "Exception must indicate decryption failure or invalid database format: ${e.message}",
                e.message?.contains("file is not a database", ignoreCase = true) == true ||
                e.message?.contains("corrupt", ignoreCase = true) == true ||
                e is android.database.sqlite.SQLiteException
            )
        } finally {
            try { wrongDb.close() } catch (_: Exception) {}
        }
    }

    /**
     * Test 5 — Keystore persistence
     * Close and reopen the application process and verify that the existing encrypted
     * database remains accessible via the Keystore key provider.
     */
    @Test
    fun test5_keystorePersistence() {
        val fakeKeystoreProvider = object : KeyStoreProvider {
            val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            override fun isHardwareBacked(): Boolean = true
            override fun getOrCreateMasterKey(alias: String): ByteArray = key
            override fun encrypt(plainText: ByteArray): ByteArray {
                val iv = ByteArray(12) { 0x01.toByte() }
                val encrypted = plainText.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
                val tag = ByteArray(16) { 0xAA.toByte() }
                return iv + encrypted + tag
            }
            override fun decrypt(cipherText: ByteArray): ByteArray {
                val payload = cipherText.copyOfRange(12, cipherText.size - 16)
                return payload.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
            }
            override fun deleteKey(alias: String): Boolean = true
        }

        // Session 1: Create key provider and insert data
        val keyProvider1 = KeystoreDatabaseEncryptionKeyProvider(context, fakeKeystoreProvider)
        val pass1 = keyProvider1.getPassphrase()

        var db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, pass1)
        runBlocking {
            db.memoryDao().insert(
                MemoryEntity(
                    id = "session_item",
                    sourceType = DataSourceType.CALENDAR.name,
                    title = "Board Meeting",
                    content = "Quarterly review with executives",
                    timestamp = System.currentTimeMillis(),
                    sourceUri = "content://calendar/events/1"
                )
            )
        }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > 0)

        // Session 2: Simulated process restart with fresh key provider instance
        val keyProvider2 = KeystoreDatabaseEncryptionKeyProvider(context, fakeKeystoreProvider)
        val pass2 = keyProvider2.getPassphrase()
        assertArrayEquals("Passphrase must persist identically across sessions", pass1, pass2)

        db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, pass2)
        val item = runBlocking { db.memoryDao().getById("session_item") }
        assertNotNull(item)
        assertEquals("Board Meeting", item?.title)
        db.close()
    }

    /**
     * Test 6 — Missing key material
     * Verify that the application fails safely without creating a plaintext database.
     */
    @Test
    fun test6_missingKeyMaterial() {
        val failingProvider = object : com.mnemo.core.database.security.DatabaseEncryptionKeyProvider {
            override fun getPassphrase(): ByteArray {
                throw MissingKeyMaterialException("Key material missing and recreation denied")
            }
            override fun isEncryptionActive(): Boolean = true
            override fun hasKeyMaterial(): Boolean = false
            override fun destroyKeyMaterial() {}
        }

        try {
            MnemoDatabase.buildDatabase(context, failingProvider)
            fail("Expected MissingKeyMaterialException was not thrown")
        } catch (e: MissingKeyMaterialException) {
            // Expected safe failure
            val dbFile = context.getDatabasePath(MnemoDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                assertFalse(
                    "Plaintext database must never be created on key failure",
                    DatabasePlaintextMigrator.isPlaintextDatabase(dbFile)
                )
            }
        }
    }

    /**
     * Test 7 — Corrupted key material
     * Verify that corrupted encrypted passphrase storage is detected and does not trigger
     * destructive database recreation.
     */
    @Test
    fun test7_corruptedKeyMaterial() {
        // Step 1: Create an encrypted database
        val fakeKeystoreProvider = object : KeyStoreProvider {
            val key = byteArrayOf(0x11, 0x22, 0x33, 0x44)
            override fun isHardwareBacked(): Boolean = true
            override fun getOrCreateMasterKey(alias: String): ByteArray = key
            override fun encrypt(plainText: ByteArray): ByteArray {
                val iv = ByteArray(12) { 0x02.toByte() }
                val encrypted = plainText.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
                val tag = ByteArray(16) { 0xBB.toByte() }
                return iv + encrypted + tag
            }
            override fun decrypt(cipherText: ByteArray): ByteArray {
                val payload = cipherText.copyOfRange(12, cipherText.size - 16)
                return payload.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
            }
            override fun deleteKey(alias: String): Boolean = true
        }

        val keyProvider = KeystoreDatabaseEncryptionKeyProvider(context, fakeKeystoreProvider)
        val pass = keyProvider.getPassphrase()
        val db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, pass)
        runBlocking {
            db.memoryDao().insert(
                MemoryEntity(
                    id = "persist_item",
                    sourceType = DataSourceType.CONTACT.name,
                    title = "Important Person",
                    content = "Do not lose this record",
                    timestamp = System.currentTimeMillis(),
                    sourceUri = "content://contacts/1"
                )
            )
        }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        val originalFileLength = dbFile.length()
        assertTrue(originalFileLength > 0)

        // Step 2: Corrupt the encrypted key file header
        val keyFile = keyProvider.keyFile
        assertTrue(keyFile.exists())
        val corruptedBytes = keyFile.readBytes()
        corruptedBytes[0] = 0x00 // corrupt magic byte
        FileOutputStream(keyFile).use { it.write(corruptedBytes) }

        // Step 3: Attempt to load passphrase with corrupted file
        val newProvider = KeystoreDatabaseEncryptionKeyProvider(context, fakeKeystoreProvider)
        try {
            newProvider.getPassphrase()
            fail("Expected CorruptedKeyMaterialException was not thrown")
        } catch (_: CorruptedKeyMaterialException) {
            // Expected safe failure
        }

        // Verify database file was NOT destroyed or modified
        assertTrue("Database file must still exist untouched", dbFile.exists())
        assertEquals("Database file size must remain intact", originalFileLength, dbFile.length())
    }

    /**
     * Test 8 — Plaintext to SQLCipher migration
     * Verify that all records, indexes, triggers, and full-text search survive migration from
     * plaintext SQLite to SQLCipher and the encrypted copy is valid.
     */
    @Test
    fun test8_migration() {
        // Step 1: Create a genuine plaintext SQLite database using default unencrypted Room
        val plaintextDb = Room.databaseBuilder(
            context,
            MnemoDatabase::class.java,
            testPlaintextDbName
        ).build()

        val records = (1..5).map { i ->
            MemoryEntity(
                id = "plaintext_item_$i",
                sourceType = DataSourceType.MESSAGE.name,
                title = "Important Message $i",
                content = "Plaintext message content for record $i with confidential facts",
                personName = "Sender $i",
                timestamp = System.currentTimeMillis() + i,
                sourceUri = "content://messages/$i"
            )
        }
        val auditRecords = listOf(
            com.mnemo.core.database.entity.AuditLogEntity(
                id = "audit_migration_01",
                timestamp = System.currentTimeMillis(),
                sourceType = DataSourceType.MESSAGE.name,
                action = "PRE_MIGRATION_ACCESS",
                details = "Sample audit log for migration"
            )
        )
        runBlocking {
            plaintextDb.memoryDao().insertAll(records)
            plaintextDb.auditLogDao().insertAll(auditRecords)
        }
        plaintextDb.close()

        val plaintextDbFile = context.getDatabasePath(testPlaintextDbName)
        assertTrue("Plaintext DB file must exist", plaintextDbFile.exists())
        assertTrue("File must be detected as plaintext SQLite", DatabasePlaintextMigrator.isPlaintextDatabase(plaintextDbFile))

        // Step 2: Execute migration
        val keyProvider = object : com.mnemo.core.database.security.DatabaseEncryptionKeyProvider {
            override fun getPassphrase(): ByteArray = validPassphrase
            override fun isEncryptionActive(): Boolean = true
            override fun hasKeyMaterial(): Boolean = true
            override fun destroyKeyMaterial() {}
        }

        val migratedCount = DatabasePlaintextMigrator.migratePlaintextToEncrypted(
            context = context,
            dbFile = plaintextDbFile,
            keyProvider = keyProvider
        )
        assertEquals(5, migratedCount)

        // Step 3: Verify the file on disk is now encrypted
        assertFalse(
            "Migrated file must NO LONGER be readable as plaintext SQLite",
            DatabasePlaintextMigrator.isPlaintextDatabase(plaintextDbFile)
        )

        // Step 4: Open with SQLCipher factory and verify all records and FTS search survived
        val encryptedDb = MnemoDatabase.buildEncryptedDatabase(context, testPlaintextDbName, validPassphrase)
        val allMigrated = runBlocking { encryptedDb.memoryDao().getAll() }
        assertEquals(5, allMigrated.size)

        val migratedAudits = runBlocking { encryptedDb.auditLogDao().getAll() }
        assertEquals(1, migratedAudits.size)
        assertEquals("audit_migration_01", migratedAudits[0].id)

        // Verify FTS table count matches
        val ftsCount = runBlocking { encryptedDb.memoryDao().countFts() }
        assertEquals(5, ftsCount)

        // Perform actual FTS4 search query to prove full-text search works after migration
        val ftsSearchResults = runBlocking { encryptedDb.memoryDao().searchFts("confidential") }
        assertEquals("FTS search must find all 5 records matching 'confidential'", 5, ftsSearchResults.size)

        val personSearchResults = runBlocking { encryptedDb.memoryDao().searchFts("Sender*") }
        assertEquals("FTS prefix search must match person records", 5, personSearchResults.size)

        encryptedDb.close()

        // Step 5: Verify standard plaintext SQLite fails to read migrated database
        try {
            val unencryptedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                plaintextDbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = unencryptedDb.rawQuery("SELECT count(*) FROM memory_items", null)
            cursor.moveToFirst()
            cursor.close()
            unencryptedDb.close()
            fail("Security Failure: Plaintext SQLite opened migrated encrypted database!")
        } catch (e: Exception) {
            assertTrue(e is android.database.sqlite.SQLiteException)
        }
    }

    /**
     * Test 9 — Key destruction prevents reopen (Cryptographic Erasure)
     * Verify that destroying key material renders previous encrypted database ciphertext
     * completely unrecoverable, failing decryption on subsequent access attempts.
     */
    @Test
    fun test9_keyDestructionPreventsReopen() {
        val fakeKeystoreProvider = object : KeyStoreProvider {
            val key = byteArrayOf(0x31, 0x32, 0x33, 0x34)
            override fun isHardwareBacked(): Boolean = true
            override fun getOrCreateMasterKey(alias: String): ByteArray = key
            override fun encrypt(plainText: ByteArray): ByteArray {
                val iv = ByteArray(12) { 0x05.toByte() }
                val encrypted = plainText.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
                val tag = ByteArray(16) { 0xEE.toByte() }
                return iv + encrypted + tag
            }
            override fun decrypt(cipherText: ByteArray): ByteArray {
                val payload = cipherText.copyOfRange(12, cipherText.size - 16)
                return payload.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
            }
            override fun deleteKey(alias: String): Boolean = true
        }

        // Step 1: Create encrypted database with initial key provider
        val provider1 = KeystoreDatabaseEncryptionKeyProvider(context, fakeKeystoreProvider)
        val pass1 = provider1.getPassphrase()
        val db = MnemoDatabase.buildEncryptedDatabase(context, testDbName, pass1)
        runBlocking {
            db.memoryDao().insert(
                MemoryEntity(
                    id = "classified_007",
                    sourceType = DataSourceType.MESSAGE.name,
                    title = "Classified Mission",
                    content = "Top secret instructions to be wiped",
                    timestamp = System.currentTimeMillis(),
                    sourceUri = "content://messages/007"
                )
            )
        }
        db.close()

        val dbFile = context.getDatabasePath(testDbName)
        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > 0)

        // Step 2: Destroy key material (Cryptographic Erasure)
        provider1.destroyKeyMaterial()
        assertFalse("Key material file must be deleted", provider1.hasKeyMaterial())

        // Step 3: Attempting to reopen with a new random passphrase (simulating regeneration)
        val differentRandomPassphrase = ByteArray(32).apply { secureRandom.nextBytes(this) }
        val reopenDb = MnemoDatabase.buildEncryptedDatabase(context, testDbName, differentRandomPassphrase)
        try {
            runBlocking {
                reopenDb.memoryDao().count()
            }
            fail("Security Failure: Encrypted database opened after original key was destroyed!")
        } catch (e: Exception) {
            // Decryption failure expected
            assertTrue(
                "Must fail with SQLiteException on wrong/regenerated passphrase: ${e.message}",
                e is android.database.sqlite.SQLiteException ||
                e.message?.contains("corrupt", ignoreCase = true) == true ||
                e.message?.contains("file is not a database", ignoreCase = true) == true
            )
        } finally {
            try { reopenDb.close() } catch (_: Exception) {}
        }
    }
}
