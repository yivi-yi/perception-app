package com.yivi.perception.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PermissionService : AccessibilityService() {

    companion object {
        @Volatile var lastPackage: String? = null
        @Volatile var lastScreenText: String = ""

        @Volatile
        private var service: PermissionService? = null

        /** 现在屏幕上有什么字（读当前窗口的节点树，最多 4000 字） */
        fun dumpScreen(): String? {
            val s = service ?: return null
            val root = try {
                s.rootInActiveWindow
            } catch (e: Exception) {
                null
            } ?: return null
            val sb = StringBuilder()
            s.walk(root, sb, 0)
            return sb.toString().trim().ifBlank { null }
        }
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        node ?: return
        if (sb.length > 4000 || depth > 40) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val label = text.ifEmpty { desc }
        if (label.isNotEmpty()) {
            if (node.isClickable) sb.append("[可点] ")
            sb.append(label).append('\n')
        }
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            walk(child, sb, depth + 1)
        }
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
