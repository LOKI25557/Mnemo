package com.mnemo.connectors.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.connector.NotificationsConnector
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concrete notification message connector.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class AndroidNotificationConnector(
    private val context: Context
) : NotificationsConnector {

    // In-memory buffer for local captured notification messages (starter store)
    private val capturedMessages = CopyOnWriteArrayList<MemoryItem>()

    override suspend fun hasPermission(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    override suspend fun getRecentMessages(limit: Int): List<MemoryItem> {
        return capturedMessages.take(limit)
    }

    override suspend fun searchMessages(query: String): List<MemoryItem> {
        if (query.isBlank()) return emptyList()
        return capturedMessages.filter {
            it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
        }
    }

    /**
     * Called by NotificationListenerService when a new notification is posted.
     */
    fun recordNotification(id: String, sender: String, text: String, pkg: String) {
        val item = MemoryItem(
            id = "notif_$id",
            sourceType = DataSourceType.MESSAGE,
            title = sender,
            content = text,
            personName = sender,
            sourceUri = "package:$pkg"
        )
        capturedMessages.add(0, item)
    }
}
