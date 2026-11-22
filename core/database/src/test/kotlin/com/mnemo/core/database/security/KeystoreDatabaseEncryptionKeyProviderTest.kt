package com.mnemo.core.database.security

import android.content.Context
import com.mnemo.core.database.migration.DatabasePlaintextMigrator
import com.mnemo.core.model.security.KeyStoreProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Proxy

class KeystoreDatabaseEncryptionKeyProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeKeyStoreProvider: KeyStoreProvider
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")

        // In-memory fake KeyStoreProvider modeling AndroidKeyStoreProvider (IV + Ciphertext + Tag)
        fakeKeyStoreProvider = object : KeyStoreProvider {
            private val key = byteArrayOf(0x5A.toByte(), 0xA5.toByte(), 0x3C.toByte(), 0xC3.toByte())

            override fun isHardwareBacked(): Boolean = true

            override fun getOrCreateMasterKey(alias: String): ByteArray = key.copyOf()

            override fun encrypt(plainText: ByteArray): ByteArray {
                val fakeIv = ByteArray(12) { 0x12.toByte() }
                val encrypted = plainText.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
                val fakeTag = ByteArray(16) { 0xFE.toByte() }
                return fakeIv + encrypted + fakeTag
            }

            override fun decrypt(cipherText: ByteArray): ByteArray {
                require(cipherText.size >= 12 + 16) { "Ciphertext too short" }
                val encrypted = cipherText.copyOfRange(12, cipherText.size - 16)
                return encrypted.mapIndexed { i, b -> (b.toInt() xor key[i % key.size].toInt()).toByte() }.toByteArray()
            }

            override fun deleteKey(alias: String): Boolean = true
        }
    }

    @Test
    fun testFirstPassphraseGenerationAndStorage() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertFalse(provider.hasKeyMaterial())

        val passphrase = provider.getPassphrase()
        assertNotNull(passphrase)
        assertEquals(32, passphrase.size)
        assertTrue(provider.hasKeyMaterial())

        val keyFile = provider.keyFile
        assertTrue(keyFile.exists())
        val bytes = keyFile.readBytes()
        val magic = "MNEMO_KEY_V1".toByteArray(Charsets.UTF_8)
        assertArrayEquals(magic, bytes.copyOfRange(0, magic.size))
    }

    @Test
    fun testPassphrasePersistenceAcrossCalls() {
        val provider1 = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        val pass1 = provider1.getPassphrase()

        // Recreate provider from same storage
        val provider2 = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        val pass2 = provider2.getPassphrase()

        assertArrayEquals(pass1, pass2)
    }

    @Test
    fun testCorruptedMagicHeaderThrowsException() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        provider.getPassphrase() // create it

        // Overwrite header with garbage
        val keyFile = provider.keyFile
        val bytes = keyFile.readBytes()
        bytes[0] = 0x00
        bytes[1] = 0x00
        FileOutputStream(keyFile).use { it.write(bytes) }

        val reloadedProvider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            reloadedProvider.getPassphrase()
        }
    }

    @Test
    fun testTruncatedFileThrowsException() {
        val keyDir = File(filesDir, "security")
        keyDir.mkdirs()
        val keyFile = File(keyDir, KeystoreDatabaseEncryptionKeyProvider.DEFAULT_KEY_FILE_NAME)
        keyFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            provider.getPassphrase()
        }
    }

    @Test
    fun testSecureDestroyKeyMaterial() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        provider.getPassphrase()
        assertTrue(provider.hasKeyMaterial())

        provider.destroyKeyMaterial()
        assertFalse(provider.hasKeyMaterial())
        assertFalse(provider.keyFile.exists())
    }

    @Test
    fun testPlaintextHeaderDetection() {
        val plaintextFile = tempFolder.newFile("plaintext.db")
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        plaintextFile.writeBytes(sqliteHeader + ByteArray(100))

        assertTrue(DatabasePlaintextMigrator.isPlaintextDatabase(plaintextFile))

        val encryptedFile = tempFolder.newFile("encrypted.db")
        val randomEncryptedBytes = ByteArray(128) { it.toByte() }
        encryptedFile.writeBytes(randomEncryptedBytes)

        assertFalse(DatabasePlaintextMigrator.isPlaintextDatabase(encryptedFile))
    }

    @Test
    fun testUnsupportedFormatVersionThrowsException() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        provider.getPassphrase() // create envelope

        val keyFile = provider.keyFile
        val bytes = keyFile.readBytes()
        // Format version is at offset 12..15. Overwrite with version 99
        bytes[12] = 0x00
        bytes[13] = 0x00
        bytes[14] = 0x00
        bytes[15] = 99.toByte()
        FileOutputStream(keyFile).use { it.write(bytes) }

        val reloadedProvider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            reloadedProvider.getPassphrase()
        }
    }

    @Test
    fun testInvalidIvLengthThrowsException() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        provider.getPassphrase()

        val keyFile = provider.keyFile
        val bytes = keyFile.readBytes()
        // IV length is at offset 16..19. Overwrite with 7
        bytes[16] = 0x00
        bytes[17] = 0x00
        bytes[18] = 0x00
        bytes[19] = 7.toByte()
        FileOutputStream(keyFile).use { it.write(bytes) }

        val reloadedProvider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            reloadedProvider.getPassphrase()
        }
    }

    @Test
    fun testAuthenticationFailureThrowsException() {
        // Step 1: Create a valid envelope using fake provider
        val setupProvider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        setupProvider.getPassphrase()

        // Step 2: Reload using a provider that simulates AEADBadTagException during decrypt
        val tamperingProvider = object : KeyStoreProvider {
            override fun isHardwareBacked(): Boolean = true
            override fun getOrCreateMasterKey(alias: String): ByteArray = byteArrayOf(1, 2, 3, 4)
            override fun encrypt(plainText: ByteArray): ByteArray = ByteArray(60) { 0x55.toByte() }
            override fun decrypt(cipherText: ByteArray): ByteArray {
                throw javax.crypto.AEADBadTagException("GCM authentication tag mismatch - ciphertext modified!")
            }
            override fun deleteKey(alias: String): Boolean = true
        }

        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, tamperingProvider)
        assertThrows(KeyAuthenticationException::class.java) {
            provider.getPassphrase()
        }
    }

    @Test
    fun testConcurrentPassphraseAccess() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        val tasks = (1..16).map {
            java.util.concurrent.Callable { provider.getPassphrase() }
        }

        val results = executor.invokeAll(tasks).map { it.get() }
        executor.shutdown()

        assertEquals(16, results.size)
        val first = results[0]
        results.forEach { pass ->
            assertArrayEquals(first, pass)
        }
    }

    @Test
    fun testExcessiveFileSizeThrowsException() {
        val keyDir = File(filesDir, "security")
        keyDir.mkdirs()
        val keyFile = File(keyDir, KeystoreDatabaseEncryptionKeyProvider.DEFAULT_KEY_FILE_NAME)
        // Write 2048 bytes (exceeds MAX_KEY_FILE_SIZE = 1024)
        keyFile.writeBytes(ByteArray(2048) { 0x41 })

        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            provider.getPassphrase()
        }
    }

    @Test
    fun testTrailingBytesInEnvelopeThrowsException() {
        val provider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        provider.getPassphrase()

        val keyFile = provider.keyFile
        val bytes = keyFile.readBytes()
        // Append 16 unexpected trailing garbage bytes
        FileOutputStream(keyFile).use { it.write(bytes + ByteArray(16) { 0xFF.toByte() }) }

        val reloadedProvider = KeystoreDatabaseEncryptionKeyProvider(filesDir, fakeKeyStoreProvider)
        assertThrows(CorruptedKeyMaterialException::class.java) {
            reloadedProvider.getPassphrase()
        }
    }
}
