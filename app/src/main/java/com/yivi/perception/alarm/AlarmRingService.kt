package com.yivi.perception.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yivi.perception.MainActivity
import com.yivi.perception.PerceptionApp
import com.yivi.perception.R
import com.yivi.perception.data.db.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 响铃：前台服务 + 系统铃声循环 + 震动，通知里带「暂停 / 关闭」。
 * 一直响到你点为止，和系统闹钟一个行为。
 */
class AlarmRingService : Service() {

    companion object {
        const val ACTION_SNOOZE = "com.yivi.perception.action.SNOOZE"
        const val ACTION_STOP = "com.yivi.perception.action.STOP"
        const val EXTRA_ID = "ring_id"
        const val EXTRA_TITLE = "ring_title"
        private const val CHANNEL_ID = "perception_alarm"
        private const val NOTI_ID = 4101
        private const val SNOOZE_MINUTES = 5L

        fun start(context: Context, id: Long, title: String) {
            val intent = Intent(context, AlarmRingService::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmId = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseAll()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val id = alarmId
                releaseAll()
                snooze(id)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        alarmId = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "闹钟"
        startForeground(NOTI_ID, buildNotification(title))
        startRing()
        return START_STICKY
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }

    private fun startRing() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                setWakeMode(this@AlarmRingService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        } catch (e: Exception) {
            player = null
        }

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "perception:alarm")?.apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }

        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0))
        } catch (_: Exception) {
        }
    }

    private fun releaseAll() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    /** 暂停：5 分钟后再响一次（不动原来那条重复闹钟） */
    private fun snooze(id: Long) {
        if (id <= 0) return
        val app = applicationContext as? PerceptionApp ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val origin = app.repository.all().firstOrNull { it.id == id } ?: return@launch
            val again = EventEntity(
                category = "闹钟",
                title = origin.title.ifBlank { "闹钟" },
                note = origin.note,
                time = System.currentTimeMillis() + SNOOZE_MINUTES * 60 * 1000,
                remind = true,
                repeatDays = ""
            )
            val newId = app.repository.add(again)
            AlarmScheduler.schedule(applicationContext, again.copy(id = newId))
        }
    }

    private fun buildNotification(title: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "闹钟", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "闹钟到点提醒"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), piFlags()
        )
        val snoozeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_SNOOZE),
            piFlags()
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP),
            piFlags()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏰ $title")
            .setContentText("点「暂停」$SNOOZE_MINUTES 分钟后再响，点「关闭」就停")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .addAction(0, "暂停", snoozeIntent)
            .addAction(0, "关闭", stopIntent)
            .build()
    }

    private fun piFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
}
