# Mnemo Testing Strategy & Verification Guide

This document defines testing standards, test suite architecture, and local and continuous integration verification procedures for **Mnemo**.

---

## 1. Testing Philosophy

Because Mnemo is an offline-first assistant dealing directly with private personal data:
- **Unit tests are mandatory for all shared contracts, parsers, and security logic.**
- Tests must never require an active internet connection.
- Tests must never mock external network calls since no external network calls exist.
- Test suites must be fast, deterministic, and runnable via command-line or Android Studio.

---

## 2. Test Architecture

```text
Mnemo/
├── core/
│   ├── model/src/test/         <- Contract validation tests
│   │   ├── MemoryItemTest.kt
│   │   └── AssistantContractTest.kt
│   │
│   ├── database/               <- Database encryption, concurrency & migration tests
│   │   ├── src/test/           <- Unit tests (mock Keystore / file integrity / concurrency)
│   │   │   ├── KeystoreDatabaseEncryptionKeyProviderTest.kt
│   │   │   ├── DatabasePlaintextMigratorTest.kt
│   │   │   ├── MnemoDatabaseTest.kt
│   │   │   └── DatabaseConcurrencyWipeTest.kt
│   │   └── src/androidTest/    <- Android Instrumentation Tests (SQLCipher on-device)
│   │       └── SqlCipherEncryptionTest.kt
│   │
│   └── security/src/test/      <- Keystore, key alias & audit logger tests
│       ├── LocalAuditLoggerTest.kt
│       └── KeyAliasConsistencyTest.kt
│
├── intelligence/src/test/      <- Intent recognition & router tests
│   ├── intent/
│   │   └── RuleBasedCommandParserTest.kt
│   └── router/
│       └── ToolRouterTest.kt
│
├── connectors/src/test/        <- Permission adapter & mapping tests
│   └── adapter/
│       └── PermissionAdapterTest.kt
│
└── app/src/test/               <- ViewModels and UI state unit tests
    └── ui/diagnostics/
        └── DiagnosticStateEvaluatorTest.kt
```

---

## 3. Running Unit Tests

### Execute All Tests Across All Modules
```powershell
.\gradlew.bat test
```

### Run Tests for a Specific Module
```powershell
# Core model contract tests
.\gradlew.bat :core:model:test

# Database unit tests (key provider, migration state machine, DB lifecycle, concurrency)
.\gradlew.bat :core:database:testDebugUnitTest

# Database SQLCipher instrumentation test suite (requires connected emulator/device)
.\gradlew.bat :core:database:connectedDebugAndroidTest

# Compile instrumentation test APK without running
.\gradlew.bat :core:database:assembleDebugAndroidTest

# Intelligence intent parser tests
.\gradlew.bat :intelligence:test

# Security and audit log tests
.\gradlew.bat :core:security:test

# Connectors tests
.\gradlew.bat :connectors:test

# App diagnostics tests
.\gradlew.bat :app:testDebugUnitTest
```

### View Test Reports
HTML test execution reports are generated at:
```text
<module-name>/build/reports/tests/testDebugUnitTest/index.html
```

---

## 4. Phase 0 Test Suite Coverage (47 Unit Tests)

### 4.1 `:core:model` Contract Tests (4 Tests)
- `testValidMemoryItemCreation`: Verifies construction, fields immutability, and property accessors.
- `testBlankIdThrowsException`: Validates defensive precondition on blank ID.
- `testBlankTitleAndContentThrowsException`: Validates requirement for meaningful content.
- `testToSourceReference`: Verifies conversion into user-facing source citations.
- `testAssistantCommandCreation`: Validates typed intent, arguments, entities, and confidence scores.
- `testAssistantResultSuccessFactory`: Validates success state, source references, and null error payload.
- `testAssistantResultFailureFactory`: Validates error encapsulation and permission denial codes.

### 4.2 `:intelligence` Intent Parser & Router Tests (10 Tests)
- `testFindContactCommand`: Validates query parsing for `"Find Rahul's phone number."` -> `FIND_CONTACT`, `person="Rahul"`.
- `testShowCalendarEventsCommand`: Validates `"Show my upcoming calendar events."` -> `SHOW_CALENDAR_EVENTS`.
- `testSearchMessagesCommand`: Validates `"Find the message where someone mentioned a meeting."` -> `SEARCH_MESSAGES`.
- `testOpenAppCommand`: Validates `"Open the calculator."` -> `OPEN_APPLICATION`, `appName="calculator."`.
- `testFindFilesCommand`: Validates `"Find my downloaded PDF files."` -> `FIND_FILES`, `extension="pdf"`.
- `testSummarizeTodayCommand`: Validates `"Give me a summary of today's important events."` -> `SUMMARIZE_TODAY`.
- `testExtractCommitmentsCommand`: Validates `"What commitments did I make this week?"` -> `EXTRACT_COMMITMENTS`.
- `testBlankQueryFallback`: Verifies fallback to `UNKNOWN` with 0.0 confidence.
- `testRouterExecutesRegisteredTool`: Verifies tool lookup and execution in `DefaultToolRouter`.
- `testRouterReturnsFailureForUnregisteredTool`: Verifies safe failure handling for unregistered intents.

### 4.3 `:core:security` Audit Logger & Key Tests (3 Tests)
- `testLogAccessAndRetrieveRecent`: Verifies logging, timestamping, and reverse chronological ordering.
- `testLogCapacityBounding`: Verifies FIFO bounding to avoid unbounded memory growth.
- `testAuthoritativeKeyAliasConsistency`: Verifies that `KeyStoreProvider.DEFAULT_KEY_ALIAS` evaluates to `"mnemo_database_master_key"` and legacy aliases are documented for safe backward compatibility and wipe sanitization.

### 4.4 `:connectors` Permission Adapter Tests (1 Test)
- `testRequiredPermissionsMapping`: Verifies mapping between features and platform permissions.

### 4.5 `:core:database` Unit Test Suites (25 Tests)

#### `KeystoreDatabaseEncryptionKeyProviderTest` (12 Tests)
- `testFirstPassphraseGenerationAndStorage`: Verifies creation of a 32-byte (256-bit) cryptographically random passphrase, encryption via mock Keystore AES-GCM cipher, persistence to disk, and exact byte decryption on subsequent retrieval.
- `testPassphrasePersistenceAcrossCalls`: Verifies that independent provider instances reading the same persisted `mnemo_db_passphrase.bin` produce identical decrypted passphrases.
- `testCorruptedMagicHeaderThrowsException`: Simulates disk corruption by mutating the magic bytes (`0x00, 0x00`); asserts `CorruptedKeyMaterialException` is thrown.
- `testTruncatedFileThrowsException`: Simulates an interrupted write with only 5 bytes; asserts `CorruptedKeyMaterialException` is thrown.
- `testSecureDestroyKeyMaterial`: Verifies `destroyKeyMaterial()` securely wipes and deletes the persisted key file, resetting state so subsequent calls generate fresh key material.
- `testPlaintextHeaderDetection`: Verifies helper correctly recognizes standard SQLite magic bytes (`SQLite format 3\0`) vs encrypted SQLCipher headers.
- `testUnsupportedFormatVersionThrowsException`: Mutates version field at offset 12..15 to 99; asserts `CorruptedKeyMaterialException` is thrown.
- `testInvalidIvLengthThrowsException`: Mutates IV length field at offset 16..19 to 7; asserts `CorruptedKeyMaterialException` is thrown.
- `testAuthenticationFailureThrowsException`: Simulates AEADBadTagException during decryption; asserts `KeyAuthenticationException` is thrown.
- `testConcurrentPassphraseAccess`: Spawns 16 concurrent threads accessing `getPassphrase()` simultaneously; verifies thread safety and identical keys with zero race conditions.
- `testExcessiveFileSizeThrowsException`: Writes 2048 bytes exceeding `MAX_KEY_FILE_SIZE` (1024 bytes); asserts `CorruptedKeyMaterialException` is thrown.
- `testTrailingBytesInEnvelopeThrowsException`: Appends unexpected trailing garbage bytes after valid envelope; asserts `CorruptedKeyMaterialException` is thrown.

#### `DatabasePlaintextMigratorTest` (6 Tests)
- `testIsPlaintextDatabase_validHeader`: Validates accurate identification of genuine SQLite 3 headers.
- `testIsPlaintextDatabase_encryptedOrRandom`: Confirms ciphertext or non-SQLite files are rejected as plaintext.
- `testIsPlaintextDatabase_shortOrEmptyFile`: Validates graceful false return on files < 16 bytes or missing.
- `testHasInterruptedMigration_detection`: Verifies detection of stale state files and pending backups.
- `testRecovery_whenVerifiedStatePresent_safelyWipesBackupAndKeepsTarget`: Proves crash recovery after verification NEVER deletes the verified target database and safely completes backup cleanup.
- `testRecovery_whenEncryptingInterrupted_restoresPlaintextBackup`: Proves crash recovery during encryption restores the untouched plaintext backup and discards partial ciphertext.
- `testRecovery_neverOverwritesWhenBackupCorrupted_unrecoverableState`: Proves that when backup is corrupted, recovery halts safely without deleting or overwriting the target, preserving files for forensic investigation.
- `testWipeAndDelete_overwritesAndUnlinks`: Verifies chunked zero overwrite and complete unlinking of files.

#### `MnemoDatabaseTest` (2 Tests)
- `testWipingFlagBlocksDatabaseOperations`: Validates that `MnemoDatabase.setWiping(true)` atomically blocks calls to `getInstance()` and `buildDatabase()`, throwing `IllegalStateException`.
- `testCloseInstanceSafeWhenNoActiveInstance`: Validates safe idempotent execution of `closeInstance()`.

#### `DatabaseConcurrencyWipeTest` (5 Tests)
- `testQueryInProgressWhenWipingStarts_waitsForCompletion`: Asserts wipe waits for active operations to drain.
- `testNewQueryArrivingDuringWiping_isRejected`: Asserts new operations during wipe throw `IllegalStateException`.
- `testTwoSimultaneousWipeRequests_secondRejectedSafely`: Asserts duplicate simultaneous wipe requests return false without deadlock.
- `testFailedWipe_resetsGateSafely`: Asserts gate is released if operation drain times out.
- `testReopeningAfterSuccessfulWipe_initializesCleanly`: Asserts clean re-initialization after wipe.

### 4.6 `:app` Unit Test Suites (4 Tests)

#### `DiagnosticStateEvaluatorTest` (4 Tests)
- `testFactoryConfiguredWhenDatabaseFileMissing`: Validates `SQLCIPHER CONFIGURED` and `RUNTIME VERIFICATION PENDING` when database file does not exist.
- `testDatabaseInitializedWhenFileMissingButOpen`: Validates `DB INITIALIZED` when open helper is active.
- `testCiphertextAtRestWhenDatabaseFileExistsAndNotPlaintext`: Validates `CIPHERTEXT AT REST` on encrypted file.
- `testEncryptionUnavailableWhenPlaintextDatabaseDetected`: Validates `ENCRYPTION UNAVAILABLE` when plaintext SQLite header detected.
- `testRuntimeEncryptionVerifiedOnlyWhenInstrumentationExplicitlyPassed`: Proves that a test suite compiling or existing NEVER counts as verified; strictly requires runtime execution flag.

---

## 5. Android Instrumentation Test Suite (`SqlCipherEncryptionTest`)

Located at: `:core:database:src/androidTest/kotlin/com/mnemo/core/database/SqlCipherEncryptionTest.kt`.

9 non-negotiable on-device security verification tests:
1. `test1_firstDatabaseCreation`: Verifies initial creation with `SupportOpenHelperFactory` produces an active encrypted database file with non-zero size on disk, inserts synthetic record, and checks count.
2. `test2_dataPersistence`: Verifies closing the database and reopening with the valid passphrase retains all data with exact field matches.
3. `test3_encryptionAtRest`: Reads the raw database file from disk using standard unencrypted Android SQLite (`SQLiteDatabase.openDatabase`). Verifies that reading fails with `SQLiteException` ("file is not a database") and confirms the header does NOT contain `SQLite format 3`.
4. `test4_wrongKeyFailsDecryption`: Attempts to open the encrypted database using an incorrect passphrase (`0xEE...`). Verifies that opening throws an authentication exception and does NOT reveal data.
5. `test5_keystorePersistence`: Verifies that the hardware Keystore AES key persists across provider instances and can decrypt the passphrase across app lifecycles.
6. `test6_missingKeyMaterial`: Deletes `mnemo_db_passphrase.bin` and configures provider not to recreate keys. Verifies that database initialization fails safely with `MissingKeyMaterialException` without creating an unencrypted replacement.
7. `test7_corruptedKeyMaterial`: Writes corrupted bytes into `mnemo_db_passphrase.bin`. Verifies that provider fails with `CorruptedKeyMaterialException` and refuses to fall back to plaintext SQLite.
8. `test8_migration`: Creates a genuine unencrypted plaintext SQLite database with memory and audit records, triggers `DatabasePlaintextMigrator`, verifies migration into an encrypted SQLCipher database, checks row counts for all tables, validates FTS4 search queries across migrated data, verifies target schema objects, indexes, and triggers, and asserts standard unencrypted SQLite cannot read the migrated file.
9. `test9_keyDestructionPreventsReopen`: Verifies cryptographic erasure: destroying the passphrase envelope and attempting to reopen with a regenerated random key fails with `SQLiteException`, guaranteeing that former ciphertext is permanently unrecoverable.

### Instrumentation Verification Status
- **Test APK Status:** Compiled successfully at `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` (23.54 MB).
- **Runtime Execution Status:** **NOT RUN** (No physical device or running Android emulator was attached during local test execution; `adb devices` returned 0 attached devices).
- **Execution Command (Requires Connected Hardware or Running Emulator):**
  ```powershell
  .\gradlew.bat :core:database:connectedDebugAndroidTest
  ```
