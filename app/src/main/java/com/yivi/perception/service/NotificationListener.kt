package com.yivi.perception.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var lastNotifications: MutableList<String> = mutableListOf()

        @Volatile
        private var service: NotificationListener? = null

        /** 通知栏里现在挂着的通知 */
        fun active(): List<Map<String, String>> {
            val s = service ?: return emptyList()
            return try {
                s.activeNotifications?.mapNotNull { sbn: StatusBarNotification ->
                    val extras = sbn.notification?.extras ?: return@mapNotNull null
                    mapOf(
                        "app" to sbn.packageName,
                        "title" to (extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""),
                        "text" to (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""),
                        "time" to sbn.postTime.toString()
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        service = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        synchronized(lastNotifications) {
            lastNotifications.add("${sbn.packageName}: $title $text")
            if (lastNotifications.size > 20) lastNotifications.removeAt(0)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        if (service === this) service = null
        super.onDestroy()
    }
}
