# Mnemo API Contracts & Shared Data Specification

**Package:** `com.mnemo.core.model`
**Module:** `:core:model`
**Status:** Phase 0 Active Baseline

---

## 1. Overview

The contract layer in `:core:model` establishes the foundational shared language between all four team members:
- **Loki** consumes models and invokes connectors/tools.
- **Pravee** displays models in Compose UI cards and triggers assistant commands.
- **Dadu** maps Android platform data into `MemoryItem` entities.
- **Nikhi** persists, encrypts, and indexes `MemoryItem` records.

All contracts are strictly typed, immutable, and thread-safe.

---

## 2. Core Data Models

### 2.1 `DataSourceType`

```kotlin
package com.mnemo.core.model

enum class DataSourceType {
    CONTACT,
    CALENDAR,
    MESSAGE,
    FILE,
    INSTALLED_APP
}
```

### 2.2 `MemoryItem`

```kotlin
package com.mnemo.core.model

data class MemoryItem(
    val id: String,
    val sourceType: DataSourceType,
    val title: String,
    val content: String,
    val personName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceUri: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val indexedAt: Long = System.currentTimeMillis()
)
```

- **Validation:** `id` must not be blank; either `title` or `content` must be non-blank.
- **Helper:** `toSourceReference()` produces user-facing citation badges.

### 2.3 `SourceReference`

```kotlin
package com.mnemo.core.model

data class SourceReference(
    val sourceType: DataSourceType,
    val title: String,
    val sourceUri: String,
    val snippet: String? = null
)
```

### 2.4 `AssistantCommand`

```kotlin
package com.mnemo.core.model

data class AssistantCommand(
    val rawQuery: String,
    val intent: AssistantIntent,
    val arguments: Map<String, String> = emptyMap(),
    val confidenceScore: Float = 1.0f,
    val entities: List<EntityMatch> = emptyList(),
    val requiresConfirmation: Boolean = false
)
```

### 2.5 `AssistantResult`

```kotlin
package com.mnemo.core.model

data class AssistantResult(
    val isSuccess: Boolean,
    val message: String,
    val sources: List<SourceReference> = emptyList(),
    val error: AssistantError? = null,
    val requiresConfirmation: Boolean = false,
    val data: List<MemoryItem> = emptyList()
)
```

- **Factories:** `AssistantResult.success(...)`, `AssistantResult.failure(...)`, `AssistantResult.confirmationRequired(...)`.

---

## 3. Tool Contracts (`com.mnemo.core.model.tool`)

### `MnemoTool`

```kotlin
package com.mnemo.core.model.tool

interface MnemoTool {
    val id: String
    val name: String
    val description: String
    val parameters: List<ToolParameter>

    suspend fun execute(arguments: Map<String, String>): AssistantResult
}
```

---

## 4. Connector Contracts (`com.mnemo.core.model.connector`)

### 4.1 `ContactsConnector`
```kotlin
interface ContactsConnector {
    suspend fun hasPermission(): Boolean
    suspend fun searchContacts(query: String): List<MemoryItem>
    suspend fun getContactById(id: String): MemoryItem?
}
```

### 4.2 `CalendarConnector`
```kotlin
interface CalendarConnector {
    suspend fun hasPermission(): Boolean
    suspend fun getEvents(startTime: Long, endTime: Long): List<MemoryItem>
    suspend fun searchEvents(query: String): List<MemoryItem>
}
```

### 4.3 `NotificationsConnector`
```kotlin
interface NotificationsConnector {
    suspend fun hasPermission(): Boolean
    suspend fun getRecentMessages(limit: Int = 20): List<MemoryItem>
    suspend fun searchMessages(query: String): List<MemoryItem>
}
```

### 4.4 `FilesConnector`
```kotlin
interface FilesConnector {
    suspend fun hasPermission(): Boolean
    suspend fun searchFiles(query: String, extensions: List<String>? = null): List<MemoryItem>
}
```

### 4.5 `InstalledAppsConnector`
```kotlin
interface InstalledAppsConnector {
    suspend fun getInstalledApps(): List<MemoryItem>
    suspend fun findAppByName(appName: String): MemoryItem?
    suspend fun openApp(packageName: String): Boolean
}
```

---

## 5. Storage Contract (`com.mnemo.core.model.repository`)

### `MemoryRepository`
```kotlin
interface MemoryRepository {
    suspend fun insert(item: MemoryItem)
    suspend fun insertAll(items: List<MemoryItem>)
    suspend fun search(query: String, limit: Int = 20): List<MemoryItem>
    suspend fun getById(id: String): MemoryItem?
    suspend fun getBySource(sourceType: DataSourceType, limit: Int = 50): List<MemoryItem>
    suspend fun deleteById(id: String): Boolean
    suspend fun deleteBySource(sourceType: DataSourceType): Int
    suspend fun deleteAll()
    suspend fun count(): Int
    fun observeCount(): Flow<Int>
}
```

---

## 6. Voice Contracts (`com.mnemo.core.model.speech`)

### 6.1 `SpeechRecognizer`
```kotlin
interface SpeechRecognizer {
    fun isAvailable(): Boolean
    fun startListening(): Flow<String>
    suspend fun stopListening()
}
```

### 6.2 `SpeechSpeaker`
```kotlin
interface SpeechSpeaker {
    fun isAvailable(): Boolean
    suspend fun speak(text: String)
    fun stop()
}
```

---

## 7. Security Contracts (`com.mnemo.core.model.security`)

### 7.1 `KeyStoreProvider`
```kotlin
interface KeyStoreProvider {
    fun isHardwareBacked(): Boolean
    fun getOrCreateMasterKey(alias: String = DEFAULT_KEY_ALIAS): ByteArray
    fun encrypt(plainText: ByteArray): ByteArray
    fun decrypt(cipherText: ByteArray): ByteArray
    fun deleteKey(alias: String = DEFAULT_KEY_ALIAS): Boolean
}
```

### 7.2 `BiometricAuthManager`
```kotlin
interface BiometricAuthManager {
    fun canAuthenticate(): Boolean
    fun getAvailabilityStatus(): String
    suspend fun authenticate(title: String, subtitle: String): Boolean
}
```

### 7.3 `AuditLogger`
```kotlin
interface AuditLogger {
    suspend fun logAccess(sourceType: DataSourceType, action: String, details: String)
    suspend fun getRecentAuditRecords(limit: Int = 100): List<AuditRecord>
}
```

### 7.4 `SecureEraser`
```kotlin
interface SecureEraser {
    suspend fun secureWipeAll(): Boolean
}
```
