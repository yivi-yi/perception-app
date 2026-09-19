package com.yivi.perception.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PermissionService : AccessibilityService() {

    companion object {
        @Volatile var lastPackage: String? = null
        @Volatile var lastScreenText: String = ""

        @Volatile
        private var service: PermissionService? = null

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        service = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (pkg != null && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastPackage = pkg
        }
        val text = event.text?.joinToString(" ") { it.toString() }
        if (!text.isNullOrBlank()) {
            lastScreenText = text.take(3000)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (service === this) service = null
        super.onDestroy()
    }
}
