package com.mnemo.app.di

import android.content.Context
import com.mnemo.connectors.adapter.PermissionAdapter
import com.mnemo.connectors.calendar.AndroidCalendarConnector
import com.mnemo.connectors.contacts.AndroidContactsConnector
import com.mnemo.connectors.files.AndroidFilesConnector
import com.mnemo.connectors.installedapps.AndroidInstalledAppsConnector
import com.mnemo.connectors.notifications.AndroidNotificationConnector
import com.mnemo.core.database.MnemoDatabase
import com.mnemo.core.database.repository.RoomMemoryRepository
import com.mnemo.core.database.security.DatabaseEncryptionKeyProvider
import com.mnemo.core.database.security.KeystoreDatabaseEncryptionKeyProvider
import com.mnemo.core.model.connector.CalendarConnector
import com.mnemo.core.model.connector.ContactsConnector
import com.mnemo.core.model.connector.FilesConnector
import com.mnemo.core.model.connector.InstalledAppsConnector
import com.mnemo.core.model.connector.NotificationsConnector
import com.mnemo.core.model.repository.MemoryRepository
import com.mnemo.core.model.security.AuditLogger
import com.mnemo.core.model.security.BiometricAuthManager
import com.mnemo.core.model.security.KeyStoreProvider
import com.mnemo.core.model.security.SecureEraser
import com.mnemo.core.security.AndroidKeyStoreProvider
import com.mnemo.core.security.LocalAuditLogger
import com.mnemo.core.security.MnemoBiometricManager
import com.mnemo.core.security.PermissionStateHelper
import com.mnemo.core.security.SecureEraserImpl
import com.mnemo.intelligence.AssistantEngine
import com.mnemo.intelligence.intent.RuleBasedCommandParser
import com.mnemo.intelligence.retrieval.RetrievalOrchestrator
import com.mnemo.intelligence.router.DefaultToolRouter
import com.mnemo.intelligence.router.ToolRegistry
import com.mnemo.intelligence.router.ToolRouter
import com.mnemo.intelligence.tools.FindContactTool
import com.mnemo.intelligence.tools.OpenAppTool
import com.mnemo.intelligence.tools.SearchMessagesTool
import com.mnemo.intelligence.tools.UpcomingEventsTool

/**
 * Dependency injection container for Mnemo.
 * Supplies singletons and instantiated dependencies across presentation and feature layers.
 * Follows Clean Architecture and inversion of control principles.
 */
class AppContainer(private val context: Context) {

    // 2. Security Layer (Nikhi)
    val keyStoreProvider: KeyStoreProvider by lazy {
        AndroidKeyStoreProvider()
    }

    val databaseKeyProvider: DatabaseEncryptionKeyProvider by lazy {
        KeystoreDatabaseEncryptionKeyProvider(context, keyStoreProvider)
    }

    // 1. Database & Storage Layer (Nikhi)
    val database: MnemoDatabase by lazy {
        MnemoDatabase.getInstance(context, databaseKeyProvider)
    }

    val memoryRepository: MemoryRepository by lazy {
        RoomMemoryRepository(database.memoryDao())
    }

    val biometricAuthManager: BiometricAuthManager by lazy {
        MnemoBiometricManager(context)
    }

    val auditLogger: AuditLogger by lazy {
        LocalAuditLogger()
    }

    val secureEraser: SecureEraser by lazy {
        SecureEraserImpl(
            context = context,
            memoryRepository = memoryRepository,
            keyStoreProvider = keyStoreProvider,
            startWipeCoordinator = { MnemoDatabase.startWipe() },
            onBeforeWipe = { MnemoDatabase.closeInstance() },
            finishWipeCoordinator = { MnemoDatabase.finishWipe() }
        )
    }

    val permissionHelper: PermissionStateHelper by lazy {
        PermissionStateHelper(context)
    }

    // 3. System Connectors (Dadu)
    val contactsConnector: ContactsConnector by lazy {
        AndroidContactsConnector(context)
    }

    val calendarConnector: CalendarConnector by lazy {
        AndroidCalendarConnector(context)
    }

    val notificationsConnector: NotificationsConnector by lazy {
        AndroidNotificationConnector(context)
    }

    val filesConnector: FilesConnector by lazy {
        AndroidFilesConnector(context)
    }

    val installedAppsConnector: InstalledAppsConnector by lazy {
        AndroidInstalledAppsConnector(context)
    }

    val permissionAdapter: PermissionAdapter by lazy {
        PermissionAdapter(context)
    }

    // 4. Intelligence & Tools Layer (Loki)
    val commandParser: RuleBasedCommandParser by lazy {
        RuleBasedCommandParser()
    }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry().apply {
            register(FindContactTool(contactsConnector))
            register(UpcomingEventsTool(calendarConnector))
            register(OpenAppTool(installedAppsConnector))
            register(SearchMessagesTool(notificationsConnector))
        }
    }

    val toolRouter: ToolRouter by lazy {
        DefaultToolRouter(toolRegistry)
    }

    val retrievalOrchestrator: RetrievalOrchestrator by lazy {
        RetrievalOrchestrator(memoryRepository)
    }

    val assistantEngine: AssistantEngine by lazy {
        AssistantEngine(commandParser, toolRouter, retrievalOrchestrator)
    }
}
