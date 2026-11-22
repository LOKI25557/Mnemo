package com.mnemo.core.database.security

import android.content.Context
import com.mnemo.core.model.security.KeyStoreProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Arrays

/**
 * Production implementation of [DatabaseEncryptionKeyProvider] utilizing Android Keystore
 * envelope encryption.
 *
 * Security Model:
 * 1. 256-bit (32-byte) random passphrase generated via [SecureRandom].
 * 2. Passphrase encrypted using an AES-256-GCM key inside hardware-backed Android Keystore.
 * 3. Only the encrypted envelope (IV + ciphertext + auth tag) and file header are written to app-private storage.
 * 4. Passphrase is decrypted into memory only when requested to open the SQLCipher database.
 * 5. Corrupted files or authentication tag mismatches fail safely without silently recreating or falling back.
 */
class KeystoreDatabaseEncryptionKeyProvider(
    private val filesDir: File,
    private val keyStoreProvider: KeyStoreProvider,
    private val keyFileName: String = DEFAULT_KEY_FILE_NAME
) : DatabaseEncryptionKeyProvider {

    constructor(
        context: Context,
        keyStoreProvider: KeyStoreProvider,
        keyFileName: String = DEFAULT_KEY_FILE_NAME
    ) : this(context.filesDir, keyStoreProvider, keyFileName)

    private val lock = Any()

    private val keyDir: File
        get() = File(filesDir, KEY_DIR_NAME)

    val keyFile: File
        get() = File(keyDir, keyFileName)

    override fun isEncryptionActive(): Boolean = true

    override fun hasKeyMaterial(): Boolean {
        synchronized(lock) {
            return keyFile.exists() && keyFile.length() > 0
        }
    }

    override fun getPassphrase(): ByteArray {
        synchronized(lock) {
            if (!keyFile.exists() || keyFile.length() == 0L) {
                return generateAndPersistNewPassphrase()
            }
            return loadAndDecryptPassphrase()
        }
    }

    override fun destroyKeyMaterial() {
        synchronized(lock) {
            if (keyFile.exists()) {
                val size = keyFile.length().toInt()
                if (size > 0) {
                    val zeros = ByteArray(size)
                    try {
                        FileOutputStream(keyFile).use { it.write(zeros) }
                    } catch (_: Exception) {}
                }
                keyFile.delete()
            }
        }
    }

    /**
     * Rotates the SQLCipher database passphrase, persisting the newly encrypted key envelope atomically.
     */
    fun rotatePassphrase(newPassphrase: ByteArray) {
        require(newPassphrase.size == PASSPHRASE_BYTE_LENGTH) {
            "Passphrase must be exactly $PASSPHRASE_BYTE_LENGTH bytes (256 bits)"
        }
        synchronized(lock) {
            val encryptedBytes = keyStoreProvider.encrypt(newPassphrase)
            writeKeyFileAtomically(encryptedBytes)
        }
    }

    private fun generateAndPersistNewPassphrase(): ByteArray {
        val secureRandom = SecureRandom()
        val rawPassphrase = ByteArray(PASSPHRASE_BYTE_LENGTH)
        secureRandom.nextBytes(rawPassphrase)

        try {
            val encryptedPayload = keyStoreProvider.encrypt(rawPassphrase)
            writeKeyFileAtomically(encryptedPayload)
            return rawPassphrase.copyOf()
        } catch (e: Exception) {
            Arrays.fill(rawPassphrase, 0.toByte())
            throw DatabaseSecurityException("Failed to generate and encrypt initial database passphrase", e)
        }
    }

    private fun loadAndDecryptPassphrase(): ByteArray {
        if (keyFile.length() > MAX_KEY_FILE_SIZE) {
            throw CorruptedKeyMaterialException(
                "Key envelope file exceeds maximum permitted size: ${keyFile.length()} bytes (max: $MAX_KEY_FILE_SIZE bytes)"
            )
        }

        val fileBytes = try {
            FileInputStream(keyFile).use { it.readBytes() }
        } catch (e: Exception) {
            throw MissingKeyMaterialException("Could not read encrypted key file: ${e.message}")
        }

        val magicBytes = KEY_FILE_MAGIC.toByteArray(Charsets.UTF_8)
        if (fileBytes.size < magicBytes.size + 16) {
            throw CorruptedKeyMaterialException("Key file too small to contain valid metadata and ciphertext")
        }

        // Verify magic header
        val header = fileBytes.copyOfRange(0, magicBytes.size)
        if (!header.contentEquals(magicBytes)) {
            throw CorruptedKeyMaterialException("Key file magic header mismatch. File may be corrupted or tampered.")
        }

        // Parse binary envelope format
        val encryptedPayload: ByteArray
        if (fileBytes.size >= ENVELOPE_V1_MIN_LENGTH) {
            var offset = magicBytes.size
            val version = readInt(fileBytes, offset)
            offset += 4
            if (version != FORMAT_VERSION_1) {
                throw CorruptedKeyMaterialException("Unsupported key envelope format version: $version. Expected: $FORMAT_VERSION_1")
            }

            val ivLength = readInt(fileBytes, offset)
            offset += 4
            if (ivLength != GCM_IV_LENGTH) {
                throw CorruptedKeyMaterialException("Invalid IV length in key envelope: $ivLength. Expected: $GCM_IV_LENGTH")
            }

            val iv = ByteArray(ivLength)
            System.arraycopy(fileBytes, offset, iv, 0, ivLength)
            offset += ivLength

            val ciphertextLength = readInt(fileBytes, offset)
            offset += 4
            if (ciphertextLength <= 0 || fileBytes.size < offset + ciphertextLength) {
                throw CorruptedKeyMaterialException(
                    "Invalid or truncated ciphertext in key envelope: expected $ciphertextLength bytes, file has ${fileBytes.size - offset}"
                )
            }

            // Verify no unexpected trailing bytes exist after the authenticated payload
            if (fileBytes.size != offset + ciphertextLength) {
                throw CorruptedKeyMaterialException(
                    "Unexpected trailing bytes detected in key envelope: file has ${fileBytes.size} bytes, expected ${offset + ciphertextLength}"
                )
            }

            val ciphertextWithTag = ByteArray(ciphertextLength)
            System.arraycopy(fileBytes, offset, ciphertextWithTag, 0, ciphertextLength)

            // Reconstruct encrypted payload [IV + CiphertextWithTag] for KeyStoreProvider.decrypt
            encryptedPayload = ByteArray(ivLength + ciphertextLength)
            System.arraycopy(iv, 0, encryptedPayload, 0, ivLength)
            System.arraycopy(ciphertextWithTag, 0, encryptedPayload, ivLength, ciphertextLength)
        } else {
            // Legacy unversioned envelope fallback: magic (12) + payload (60) = 72 bytes
            if (fileBytes.size != magicBytes.size + GCM_IV_LENGTH + 48) {
                throw CorruptedKeyMaterialException("Unexpected length for legacy key envelope: ${fileBytes.size} bytes")
            }
            encryptedPayload = fileBytes.copyOfRange(magicBytes.size, fileBytes.size)
        }

        val decrypted = try {
            keyStoreProvider.decrypt(encryptedPayload)
        } catch (e: Exception) {
            throw KeyAuthenticationException(
                "Failed to decrypt database passphrase with Keystore. Key invalid or data corrupted: ${e.message}",
                e
            )
        }

        if (decrypted.size != PASSPHRASE_BYTE_LENGTH) {
            Arrays.fill(decrypted, 0.toByte())
            throw CorruptedKeyMaterialException(
                "Decrypted passphrase length mismatch: expected $PASSPHRASE_BYTE_LENGTH bytes, got ${decrypted.size}"
            )
        }

        return decrypted
    }

    private fun writeKeyFileAtomically(encryptedBytes: ByteArray) {
        require(encryptedBytes.size >= GCM_IV_LENGTH + 16) {
            "Encrypted payload too short to contain IV and ciphertext tag"
        }
        if (!keyDir.exists()) {
            keyDir.mkdirs()
        }
        val tempFile = File(keyDir, "$keyFileName.tmp")
        val magicBytes = KEY_FILE_MAGIC.toByteArray(Charsets.UTF_8)
        val ivLength = GCM_IV_LENGTH
        val ciphertextLength = encryptedBytes.size - ivLength

        val fullData = ByteArray(magicBytes.size + 4 + 4 + ivLength + 4 + ciphertextLength)
        var offset = 0

        // 1. Magic bytes (12 bytes)
        System.arraycopy(magicBytes, 0, fullData, offset, magicBytes.size)
        offset += magicBytes.size

        // 2. Format version (4 bytes)
        writeInt(fullData, offset, FORMAT_VERSION_1)
        offset += 4

        // 3. IV length (4 bytes)
        writeInt(fullData, offset, ivLength)
        offset += 4

        // 4. IV bytes (12 bytes)
        System.arraycopy(encryptedBytes, 0, fullData, offset, ivLength)
        offset += ivLength

        // 5. Ciphertext length (4 bytes)
        writeInt(fullData, offset, ciphertextLength)
        offset += 4

        // 6. Ciphertext + GCM Tag (48 bytes)
        System.arraycopy(encryptedBytes, ivLength, fullData, offset, ciphertextLength)

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(fullData)
                fos.flush()
                fos.fd.sync()
            }
        } finally {
            Arrays.fill(fullData, 0.toByte())
        }

        if (!tempFile.renameTo(keyFile)) {
            tempFile.copyTo(keyFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value ushr 24).toByte()
        buffer[offset + 1] = (value ushr 16).toByte()
        buffer[offset + 2] = (value ushr 8).toByte()
        buffer[offset + 3] = value.toByte()
    }

    private fun readInt(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)
    }

    companion object {
        const val DEFAULT_KEY_FILE_NAME = "mnemo_db_passphrase.bin"
        private const val KEY_DIR_NAME = "security"
        private const val KEY_FILE_MAGIC = "MNEMO_KEY_V1"
        const val PASSPHRASE_BYTE_LENGTH = 32 // 256 bits for AES-256 SQLCipher
        private const val FORMAT_VERSION_1 = 1
        private const val GCM_IV_LENGTH = 12
        private const val ENVELOPE_V1_MIN_LENGTH = 12 + 4 + 4 + 12 + 4 + 16 // 52 bytes minimum
        const val MAX_KEY_FILE_SIZE = 1024L // 1 KB max to prevent malicious excessive allocations
    }
}
