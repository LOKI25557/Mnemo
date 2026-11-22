package com.mnemo.app.background.workers

/**
 * Scheduled background worker placeholder for periodic local index maintenance.
 * Phase 1 Implementation Plan:
 * Uses Android WorkManager with setRequiresCharging(true) and setRequiresBatteryNotLow(true)
 * to refresh local search tokens without impacting user battery.
 */
object IndexSyncWorkerContract {
    const val WORK_NAME = "mnemo_local_index_sync"
}
