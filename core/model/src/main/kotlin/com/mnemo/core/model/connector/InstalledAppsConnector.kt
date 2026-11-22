package com.mnemo.core.model.connector

import com.mnemo.core.model.MemoryItem

/**
 * Android system connector interface for installed application discovery and launching.
 * Implemented in `:connectors` (Dadu's ownership).
 */
interface InstalledAppsConnector {
    /**
     * Lists all launchable user applications currently installed on device.
     */
    suspend fun getInstalledApps(): List<MemoryItem>

    /**
     * Finds matching application by human name (e.g. "calculator", "notes").
     */
    suspend fun findAppByName(appName: String): MemoryItem?

    /**
     * Attempts to launch an installed application by package name.
     *
     * @return True if intent was dispatched successfully, false otherwise.
     */
    suspend fun openApp(packageName: String): Boolean
}
