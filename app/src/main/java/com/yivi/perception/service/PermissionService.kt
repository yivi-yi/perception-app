package com.yivi.perception.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class PermissionService : AccessibilityService() {

    companion object {
        @Volatile var lastPackage: String? = null

        @Volatile
        private var service: PermissionService? = null

        /** 外面拿当前连上的这个无障碍服务（没连上就是 null） */
        val current: PermissionService? get() = service

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
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (service === this) service = null
        super.onDestroy()
    }
}
