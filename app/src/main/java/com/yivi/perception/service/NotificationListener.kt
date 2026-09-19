package com.yivi.perception.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 读通知：要开"通知监听"权限。
 * 两路互不覆盖：active = 通知栏现在挂着的；history = 最近收到的（最多 20 条，新的在后）。
 */
class NotificationListener : NotificationListenerService() {

    companion object {
        const val MAX = 20

        @Volatile
        private var history: MutableList<String> = mutableListOf()

        @Volatile
        private var service: NotificationListener? = null

        /** 最近收到的，最多 20 条 */
        fun recent(): List<String> = synchronized(history) { history.toList() }

        /** 通知栏现在挂着的 */
        fun active(limit: Int = MAX): List<Map<String, String>> {
            val s = service ?: return emptyList()
            return try {
                s.activeNotifications?.mapNotNull { sbn: StatusBarNotification ->
                    val extras = sbn.notification?.extras ?: return@mapNotNull null
                    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
                    if (title.isBlank() && text.isBlank()) return@mapNotNull null
                    if (isNoise(sbn.packageName, "$title $text")) return@mapNotNull null
                    mapOf(
                        "app" to sbn.packageName,
                        "title" to title,
                        "text" to text,
                        "time" to humanTime(sbn.postTime)
                    )
                }?.takeLast(limit.coerceIn(1, MAX)) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        /** 毫秒时间戳换成"14:32（3 分钟前）"，别把一串数字丢给模型 */
        fun humanTime(millis: Long): String {
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val minutes = ((System.currentTimeMillis() - millis) / 60000L).coerceAtLeast(0)
            val ago = when {
                minutes < 1 -> "刚刚"
                minutes < 60 -> "${minutes} 分钟前"
                minutes < 24 * 60 -> "${minutes / 60} 小时前"
                else -> "${minutes / (24 * 60)} 天前"
            }
            return fmt.format(java.util.Date(millis)) + "（$ago）"
        }

        private fun isNoise(pkg: String?, joined: String): Boolean {
            val p = pkg?.lowercase() ?: ""
            if ("clipboard" in p || "复制到剪贴板" in joined || "已复制" in joined) return true
            if ("flow" in p || "fluid" in p) return true
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        service = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val joined = listOf(title, text).filter { it.isNotBlank() }.joinToString(" - ")
        if (joined.isBlank() || isNoise(sbn.packageName, joined)) return
        synchronized(history) {
            history.add("${sbn.packageName}: $joined")
            while (history.size > MAX) history.removeAt(0)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        if (service === this) service = null
        super.onDestroy()
    }
}
