# Mnemo — Private On-Device AI Assistant for Android

[![Android CI](https://github.com/LOKI25557/Mnemo/actions/workflows/android-build.yml/badge.svg)](https://github.com/LOKI25557/Mnemo/actions/workflows/android-build.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![Offline-First](https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen.svg)](docs/PRIVACY_SECURITY.md)

**Mnemo** is an offline-first, privacy-focused personal AI assistant for Android. It operates entirely on-device, enabling users to interact with their own authorized personal information through natural language and voice commands without transmitting personal data to the cloud.

---

## 1. Core Objectives & Privacy Principles

- **Offline-First Operation**: Runs 100% locally. Does not require or declare the Android `INTERNET` permission.
- **Zero Cloud APIs**: No Firebase, Supabase, Google Analytics, or external cloud LLM services.
- **Encrypted Local Storage**: Transparent 256-bit AES page encryption at rest via SQLCipher 4.6.1.
- **Hardware Root of Trust**: Cryptographic master keys are generated and protected by the hardware-backed Android Keystore.
- **Source-Linked Answers**: Every answer is verified with traceable on-device **Source References** pointing to the original local item.
- **User Sovereignty**: User-controlled retention and instantaneous multi-layer secure wipe with cryptographic erasure.

---

## 2. Technology Stack

| Area | Technology |
|---|---|
| **Language** | Kotlin 2.2.0 |
| **Platform** | Android (minSdk 26, compileSdk 35, targetSdk 35) |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | Clean Architecture + MVVM + Modular Multi-Project |
| **Dependency Injection** | Inversion of Control via Clean Service Container (`AppContainer`) |
| **Asynchronous I/O** | Kotlin Coroutines & Flow/StateFlow |
| **Local Database** | SQLite via Room + Genuine SQLCipher 4.6.1 at-rest encryption |
| **Search Engine** | SQLite FTS4 Full-Text Search |
| **Hardware Security** | Android Keystore System (AES-256 GCM) |
| **Biometrics** | AndroidX BiometricPrompt |
| **Background Processing** | Android WorkManager |
| **Testing** | JUnit 4, Coroutines Test, Espresso, AndroidX Test Runner |
| **Build System** | Gradle 9.1.0 (Kotlin DSL) + Version Catalog (`libs.versions.toml`) |

---

## 3. Four-Member Team Ownership

| Developer | Role | Assigned Modules & Scope |
|---|---|---|
| **Loki** | AI & Intelligence Lead | `:intelligence` (Rule-based intent parser, tool registry, router, retrieval orchestrator, evaluation datasets) |
| **Pravee** | Android UI & Application Experience Lead | `:app` (Jetpack Compose UI, Navigation, MainActivity, floating bubble overlay, voice UI, answer cards) |
| **Dadu** | Android System Connectors Lead | `:connectors` (Contacts Provider, Calendar Provider, Notification listener, Files, Installed apps, permission adapters) |
| **Nikhi** | Database & Security Lead | `:core:database`, `:core:security` (Room database, SQLCipher integration, DAOs, crash-safe migration, Keystore, BiometricPrompt, audit logging, secure wipe) |

For detailed responsibilities, see [docs/MODULE_OWNERSHIP.md](docs/MODULE_OWNERSHIP.md).

---

## 4. Repository Structure

```text
Mnemo/
├── app/                        <- Application module (Pravee)
│   ├── src/main/java/com/mnemo/app/
│   │   ├── MainActivity.kt     <- Compose host activity
│   │   ├── MnemoApplication.kt <- App entry point
│   │   ├── di/                 <- AppContainer (DI)
│   │   ├── navigation/         <- NavHost and Routes
│   │   ├── ui/                 <- Home, Diagnostics, Theme
│   │   └── feature/            <- Feature package placeholders
│   ├── res/                    <- App resources, launcher icons
│   └── AndroidManifest.xml     <- Zero network permissions
│
├── core/
│   ├── model/                  <- Shared domain contracts (All Members)
│   │   ├── DataSourceType.kt
│   │   ├── MemoryItem.kt
│   │   ├── AssistantCommand.kt
│   │   ├── AssistantResult.kt
│   │   └── interfaces/         <- Connector, repository, and tool contracts
│   ├── database/               <- Storage layer (Nikhi)
│   │   ├── MnemoDatabase.kt    <- SQLCipher 4.6.1 Room database with wiping lock
│   │   ├── migration/          <- DatabasePlaintextMigrator with crash recovery journal
│   │   ├── security/           <- KeystoreDatabaseEncryptionKeyProvider (MNEMO_KEY_V1)
│   │   ├── entity/             <- Room entities (MemoryEntity, AuditLogEntity, FtsMemoryEntity)
│   │   └── dao/                <- Room DAOs
│   └── security/               <- Security & Crypto layer (Nikhi)
│       ├── AndroidKeyStoreProvider.kt <- AES-256 GCM hardware-backed master key
│       ├── MnemoBiometricManager.kt
│       ├── LocalAuditLogger.kt
│       └── SecureEraserImpl.kt <- Multi-layer cryptographic erasure
│
├── intelligence/               <- AI & Tool Router (Loki)
│   ├── intent/                 <- RuleBasedCommandParser
│   ├── router/                 <- ToolRouter, ToolRegistry
│   ├── tools/                  <- FindContactTool, UpcomingEventsTool, etc.
│   └── retrieval/              <- RetrievalOrchestrator
│
├── connectors/                 <- Android System Connectors (Dadu)
│   ├── contacts/               <- AndroidContactsConnector
│   ├── calendar/               <- AndroidCalendarConnector
│   ├── notifications/          <- AndroidNotificationConnector
│   ├── files/                  <- AndroidFilesConnector
│   └── installedapps/          <- AndroidInstalledAppsConnector
│
├── docs/                       <- Complete Architectural Documentation
│   ├── ARCHITECTURE.md
│   ├── DATABASE_SCHEMA.md
│   ├── API_CONTRACTS.md
│   ├── MODULE_OWNERSHIP.md
│   ├── SETUP_GUIDE.md
│   ├── TESTING.md
│   └── PRIVACY_SECURITY.md
│
├── tools/                      <- AI Evaluation & Benchmarking (Loki)
├── gradle/
│   ├── libs.versions.toml      <- Centralized version catalog
│   └── wrapper/                <- Gradle 9.1.0 wrapper
├── .github/workflows/          <- GitHub Actions CI build & verification
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

---

## 5. Getting Started

### 1. Clone the repository
```powershell
git clone https://github.com/LOKI25557/Mnemo.git
cd Mnemo
```

### 2. Configure SDK Path
Ensure `local.properties` contains your Android SDK location:
```properties
sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```

### 3. Build & Run Tests
```powershell
# Run unit tests across all modules
.\gradlew.bat test

# Build debug APK
.\gradlew.bat assembleDebug

# Build instrumentation test APK
.\gradlew.bat :core:database:assembleDebugAndroidTest
```

---

## 6. Git Branching Strategy

```text
main           (Production Releases)
  ▲
develop        (Active Integration Branch)
  ▲
  ├── feature/loki-intelligence  (Loki)
  ├── feature/pravee-ui          (Pravee)
  ├── feature/dadu-connectors    (Dadu)
  └── feature/nikhi-storage      (Nikhi)
```

1. Branch from `develop`.
2. Commit only within your assigned module or package.
3. Verify tests with `.\gradlew.bat test` locally.
4. Submit PR to `develop`. CI automatically validates that no `INTERNET` permission exists.

---

## 7. Current Implementation Status (Phase 0 Completed)

- [x] Multi-module Gradle build with Kotlin DSL & Version Catalog.
- [x] Shared data contracts layer in `:core:model` (`MemoryItem`, `AssistantCommand`, `AssistantResult`, `MnemoTool`).
- [x] Rule-based natural language intent parser in `:intelligence` with unit tests.
- [x] Local tool registry and router in `:intelligence`.
- [x] Room database with FTS4 and access audit entities in `:core:database`.
- [x] Genuine SQLCipher 4.6.1 at-rest encryption via `SupportOpenHelperFactory`.
- [x] Android Keystore hardware-backed AES-256 GCM key provider (`MNEMO_KEY_V1` envelope format).
- [x] Crash-safe plaintext migration with durable migration state machine (`ISOLATED`, `ENCRYPTING`, `VERIFIED`).
- [x] Multi-layer secure wipe with concurrency locking (`setWiping`) and cryptographic erasure.
- [x] 9-point on-device instrumentation test suite (`SqlCipherEncryptionTest`).
- [x] Biometric authentication prompt manager abstraction in `:core:security`.
- [x] System connector adapters for Contacts, Calendar, Notifications, Files, and Apps in `:connectors`.
- [x] Jetpack Compose entry application with home screen & live diagnostic dashboard in `:app`.
- [x] Prohibited `INTERNET` permission verified absent across all manifests.
- [x] 100% passing unit tests across all modules.

---

## 8. Next Development Roadmap (Phase 1)

- **Loki:** Expand rule parser with fuzzy matching and entity date-time extractors; build evaluation benchmark harness.
- **Pravee:** Build interactive Assistant chat UI, floating bubble overlay window, and permission onboarding cards.
- **Dadu:** Implement live `ContentResolver` queries with pagination and `NotificationListenerService` event capture.
- **Nikhi:** Implement biometric prompt authorization on sensitive routes, retention policy worker, and automatic database vacuuming.
