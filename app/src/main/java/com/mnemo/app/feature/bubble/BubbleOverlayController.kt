package com.mnemo.app.feature.bubble

/**
 * Interface for the floating assistant overlay bubble.
 * Assigned to Pravee (Android UI & Application Experience Lead).
 *
 * Phase 1 Implementation Plan:
 * Uses Android WindowManager with TYPE_APPLICATION_OVERLAY to render a lightweight
 * floating bubble when enabled by the user in settings.
 */
interface BubbleOverlayController {
    fun showOverlay()
    fun hideOverlay()
    fun isOverlayVisible(): Boolean
}
