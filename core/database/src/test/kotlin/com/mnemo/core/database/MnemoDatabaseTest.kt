package com.mnemo.core.database

import android.content.Context
import android.content.ContextWrapper
import com.mnemo.core.database.security.DatabaseEncryptionKeyProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MnemoDatabaseTest {

    private val fakeKeyProvider = object : DatabaseEncryptionKeyProvider {
        override fun getPassphrase(): ByteArray = ByteArray(32) { 0x01 }
        override fun isEncryptionActive(): Boolean = true
        override fun hasKeyMaterial(): Boolean = true
        override fun destroyKeyMaterial() {}
    }

    private val fakeContext: Context = ContextWrapper(null)

    @Before
    fun setUp() {
        MnemoDatabase.setWiping(false)
        MnemoDatabase.closeInstance()
    }

    @After
    fun tearDown() {
        MnemoDatabase.setWiping(false)
        MnemoDatabase.closeInstance()
    }

    @Test
    fun testWipingFlagBlocksDatabaseOperations() {
        assertFalse(MnemoDatabase.isWiping())

        MnemoDatabase.setWiping(true)
        assertTrue(MnemoDatabase.isWiping())

        // getInstance must throw IllegalStateException when wiping
        assertThrows(IllegalStateException::class.java) {
            MnemoDatabase.getInstance(fakeContext, fakeKeyProvider)
        }

        // buildDatabase must throw IllegalStateException when wiping
        assertThrows(IllegalStateException::class.java) {
            MnemoDatabase.buildDatabase(fakeContext, fakeKeyProvider)
        }

        MnemoDatabase.setWiping(false)
        assertFalse(MnemoDatabase.isWiping())
    }

    @Test
    fun testCloseInstanceSafeWhenNoActiveInstance() {
        // Must not throw when called repeatedly or without active DB
        MnemoDatabase.closeInstance()
        MnemoDatabase.closeInstance()
    }
}
