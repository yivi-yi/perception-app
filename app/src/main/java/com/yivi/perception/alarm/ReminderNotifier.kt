package com.yivi.perception.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yivi.perception.MainActivity
import com.yivi.perception.R
import com.yivi.perception.data.db.EventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 日程提醒：只弹一条通知带声音，不会一直响 */
object ReminderNotifier {

    private const val CHANNEL_ID = "perception_reminder"
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun show(context: Context, event: EventEntity) {
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "日程提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "日程到点提醒"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val open = PendingIntent.getActivity(
            context, event.id.toInt(), Intent(context, MainActivity::class.java), flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📌 ${event.title.ifBlank { "日程提醒" }}")
            .setContentText(event.note.ifBlank { timeText(event.time) })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        try {
            manager.notify(event.id.toInt() + 1000, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun timeText(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFmt)
}
