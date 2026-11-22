package com.mnemo.connectors.installedapps

import android.content.Context
import android.content.Intent
import com.mnemo.core.model.DataSourceType
import com.mnemo.core.model.MemoryItem
import com.mnemo.core.model.connector.InstalledAppsConnector

/**
 * Concrete installed application discovery and launcher connector.
 * Implemented by Dadu (Android System Connectors Lead).
 */
class AndroidInstalledAppsConnector(
    private val context: Context
) : InstalledAppsConnector {

    private val packageManager = context.packageManager

    override suspend fun getInstalledApps(): List<MemoryItem> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)

        return resolveInfoList.map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(packageManager).toString()
            MemoryItem(
                id = pkg,
                sourceType = DataSourceType.INSTALLED_APP,
                title = label,
                content = "Installed application package: $pkg",
                sourceUri = "package:$pkg"
            )
        }
    }

    override suspend fun findAppByName(appName: String): MemoryItem? {
        val apps = getInstalledApps()
        return apps.firstOrNull { it.title.equals(appName, ignoreCase = true) }
            ?: apps.firstOrNull { it.title.contains(appName, ignoreCase = true) }
    }

    override suspend fun openApp(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
