package com.mnemo.core.security

import com.mnemo.core.model.security.KeyStoreProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyAliasConsistencyTest {

    @Test
    fun testAuthoritativeKeyAliasConsistency() {
        // Assert that the authoritative master key alias is exactly "mnemo_database_master_key"
        assertEquals(
            "Authoritative master key alias must be 'mnemo_database_master_key'",
            "mnemo_database_master_key",
            KeyStoreProvider.DEFAULT_KEY_ALIAS
        )

        // Assert legacy aliases are documented and recognized for safe backward compatibility and wipe sanitization
        assertTrue(
            KeyStoreProvider.LEGACY_KEY_ALIASES.contains("mnemo_master_database_key")
        )
        assertTrue(
            KeyStoreProvider.LEGACY_KEY_ALIASES.contains("mnemo_db_master_key")
        )
    }
}
