# Mnemo Module Ownership & Team Responsibilities

This document defines the clear boundaries, responsibilities, and workflows for our four-person engineering team.

All members collaborate on the **same repository** (`https://github.com/LOKI25557/Mnemo.git`), using documented shared interfaces in `:core:model` to build independently without touching or breaking each other's code.

---

## 1. Member 1 — LOKI

**Role:** AI & Intelligence Lead
**Primary Module:** `:intelligence`
**Shared Contact Area:** `:core:model` (AI & tool schemas)

### Responsibilities:
- Natural-language intent recognition and rule-based query parser (`RuleBasedCommandParser`).
- Intent classification and entity resolution (dates, times, person names, keywords).
- Tool router, tool registry, and tool execution orchestration (`DefaultToolRouter`, `ToolRegistry`).
- Local tool implementations (`FindContactTool`, `UpcomingEventsTool`, `OpenAppTool`, `SearchMessagesTool`).
- Retrieval orchestration across multi-source memory items (`RetrievalOrchestrator`).
- Synthesis of source-linked answers linking directly to local provenance (`SourceReference`).
- Local on-device LLM evaluation, prompt engineering, and offline inference integration in Phase 2.
- Local commitment and promise extraction from messages in Phase 2.
- Accuracy testing, benchmark datasets, and evaluation tooling in `tools/`.

### Directory Ownership:
```text
intelligence/
├── src/main/kotlin/com/mnemo/intelligence/
│   ├── intent/          <- Rule-based parser, entity matchers
│   ├── router/          <- Tool router, registry
│   ├── tools/           <- Local tool implementations
│   ├── retrieval/       <- Multi-source retrieval orchestrator
│   └── AssistantEngine.kt
└── src/test/kotlin/...  <- Parser and router tests
tools/
├── datasets/            <- Evaluation datasets
├── benchmarks/          <- Latency & memory benchmarks
└── evaluation/          <- Intent classification test harness
```

---

## 2. Member 2 — PRAVEE

**Role:** Android UI & Application Experience Lead
**Primary Module:** `:app`
**Shared Contact Area:** `:core:model` (Presentation models)

### Responsibilities:
- `MainActivity` and `MnemoApplication` entry points.
- Jetpack Compose design foundation, typography, Material 3 Dark/Light themes.
- Compose Navigation (`MnemoNavHost`, `Screen`).
- Floating assistant bubble and window overlay controller (`feature/bubble`).
- Voice interaction UI state and audio wave animations (`feature/voice`).
- Assistant chat interaction screen, query inputs, and source citation cards (`feature/assistant`).
- Permission onboarding flows and transparent permission request UI.
- Privacy Dashboard UI and system diagnostics screens (`ui/diagnostics`).
- User settings UI (`feature/settings`).
- App-level state management and ViewModel integration.

### Directory Ownership:
```text
app/
├── src/main/java/com/mnemo/app/
│   ├── MainActivity.kt
│   ├── MnemoApplication.kt
│   ├── di/              <- Dependency injection & AppContainer
│   ├── navigation/      <- Compose navigation graph & routes
│   ├── ui/
│   │   ├── home/        <- Placeholder home screen & roster
│   │   ├── diagnostics/ <- Live privacy & permission audit screen
│   │   └── theme/       <- Material 3 Theme, Color, Type
│   └── feature/
│       ├── bubble/      <- Floating overlay bubble controller
│       ├── voice/       <- Voice interaction UI & state
│       ├── assistant/   <- Assistant chat & answer cards
│       ├── privacy/     <- Privacy dashboard state & UI
│       └── settings/    <- App settings & retention controls
```

---

## 3. Member 3 — DADU

**Role:** Android System Connectors Lead
**Primary Module:** `:connectors`
**Shared Contact Area:** `:core:model` (Connector contracts)

### Responsibilities:
- Android Contacts Provider integration via `ContentResolver` (`AndroidContactsConnector`).
- Android Calendar Provider integration for events and schedules (`AndroidCalendarConnector`).
- Android `NotificationListenerService` integration for offline notification capture (`AndroidNotificationConnector`).
- Local filesystem discovery and document search for PDFs/downloads (`AndroidFilesConnector`).
- Installed application querying and intent launching via `PackageManager` (`AndroidInstalledAppsConnector`).
- Safe runtime permission adapters (`PermissionAdapter`, checking permissions before querying, zero crashes on denial).
- Android system data mapping into unified domain `MemoryItem` records.
- Unit and integration tests for all system connectors.

### Directory Ownership:
```text
connectors/
├── src/main/kotlin/com/mnemo/connectors/
│   ├── contacts/        <- Android Contacts Provider adapter
│   ├── calendar/        <- Android Calendar Provider adapter
│   ├── notifications/   <- Notification listener & capture store
│   ├── files/           <- Local document & PDF search
│   ├── installedapps/   <- PackageManager query & intent launcher
│   └── adapter/         <- Permission adapters & checkers
└── src/test/kotlin/...  <- Connector tests
```

---

## 4. Member 4 — NIKHI

**Role:** Database & Security Lead
**Primary Modules:** `:core:database`, `:core:security`
**Shared Contact Area:** `:core:model` (Repository & Security contracts)

### Responsibilities:
- Local database architecture using Room (`MnemoDatabase`).
- Database entities (`MemoryEntity`, `AuditLogEntity`, `FtsMemoryEntity`).
- Data Access Objects (`MemoryDao`, `AuditLogDao`).
- FTS4/FTS5 full-text search indexing and tokenization.
- Database migrations and schema evolution in `core/database/migrations/`.
- SQLCipher transparent disk encryption integration (`DatabaseEncryptionKeyProvider`).
- Hardware-backed Android Keystore key management (AES-256 GCM) (`AndroidKeyStoreProvider`).
- Biometric authentication prompt integration (`MnemoBiometricManager`).
- Transparent local access audit logging (`LocalAuditLogger`).
- Cryptographic secure wipe and zero data remanence deletion (`SecureEraserImpl`).
- Safe permission state diagnostics (`PermissionStateHelper`).

### Directory Ownership:
```text
core/database/
├── src/main/kotlin/com/mnemo/core/database/
│   ├── MnemoDatabase.kt
│   ├── entity/          <- MemoryEntity, AuditLogEntity, FtsMemoryEntity
│   ├── dao/             <- MemoryDao, AuditLogDao
│   ├── repository/      <- RoomMemoryRepository
│   ├── security/        <- DatabaseEncryptionKeyProvider
│   └── migrations/      <- Future schema migrations
core/security/
├── src/main/kotlin/com/mnemo/core/security/
│   ├── AndroidKeyStoreProvider.kt <- Hardware-backed Keystore
│   ├── MnemoBiometricManager.kt   <- BiometricPrompt adapter
│   ├── LocalAuditLogger.kt        <- Local access audit logging
│   ├── SecureEraserImpl.kt        <- Zero-remanence deletion
│   └── PermissionStateHelper.kt   <- Runtime permission state
```

---

## 5. Team Collaboration & Git Branching Strategy

```text
main
    Stable release branch (production-ready)
    ▲
    │ Pull Request (Review required, all CI checks must pass)
develop
    Integration branch for active phase
    ▲
    ├───────────────────────┼───────────────────────┼───────────────────────┐
feature/loki-intelligence  feature/pravee-ui    feature/dadu-connectors  feature/nikhi-storage
    (Loki)                  (Pravee)                (Dadu)                  (Nikhi)
```

### Workflow Rules:
1. **Never commit directly to `main` or `develop`.**
2. Branch out from latest `develop`: `git checkout -b feature/<your-name>-<feature>`.
3. Work **strictly** within your assigned module or package.
4. If you need a change in `:core:model`, discuss it with the team first and open a separate contract PR.
5. Always run `./gradlew test` locally before opening a Pull Request.
6. CI automatically runs tests and audits for prohibited `INTERNET` permission on every PR.
7. Merge into `develop` only after peer review and passing CI.
