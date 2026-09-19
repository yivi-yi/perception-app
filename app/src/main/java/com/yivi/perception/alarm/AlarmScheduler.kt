package com.yivi.perception.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yivi.perception.MainActivity
import com.yivi.perception.PerceptionApp
import com.yivi.perception.data.db.EventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 闹钟排班。用 setAlarmClock 交给系统当"闹钟"处理：最不容易被省电策略杀掉，
 * 状态栏也会显示闹钟图标。
 */
object AlarmScheduler {

    const val EXTRA_ID = "alarm_id"
    const val EXTRA_TITLE = "alarm_title"

    /** 下一次该响/该提醒的时间；不会再响（一次性且已经过）返回 null */
    fun nextTrigger(alarm: EventEntity, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val time: LocalTime = Instant.ofEpochMilli(alarm.time).atZone(ZoneId.systemDefault()).toLocalTime()
        val days = alarm.repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }

        if (days.isEmpty()) {
            val at = Instant.ofEpochMilli(alarm.time).atZone(ZoneId.systemDefault()).toLocalDateTime()
            return if (at.isAfter(now)) at else null
        }

        var candidate = LocalDate.now().atTime(time)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        var guard = 0
        while (candidate.dayOfWeek.value !in days && guard < 8) {
            candidate = candidate.toLocalDate().plusDays(1).atTime(time)
            guard++
        }
        return if (candidate.dayOfWeek.value in days) candidate else null
    }

    fun schedule(context: Context, alarm: EventEntity) {
        val trigger = nextTrigger(alarm) ?: run { cancel(context, alarm); return }
        val millis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (alarm.category == "闹钟") {
                val showIntent = PendingIntent.getActivity(
                    context,
                    alarm.id.toInt(),
                    Intent(context, MainActivity::class.java),
                    flags()
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent), operation(context, alarm))
            } else {
                // 日程提醒：准点叫一次就行，不用占系统"下一个闹钟"的位置
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation(context, alarm))
            }
        } catch (e: SecurityException) {
            // 没给"精确闹钟"权限就退回普通闹钟，一样会响，只是可能不差秒
            try {
                am.set(AlarmManager.RTC_WAKEUP, millis, operation(context, alarm))
            } catch (_: Exception) {
            }
        }
    }

    fun cancel(context: Context, alarm: EventEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(operation(context, alarm))
        } catch (_: Exception) {
        }
    }

    /** 开机、或应用启动时，把所有开着的闹钟重新装一遍 */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext as? PerceptionApp ?: return
        app.repository.listByCategory("闹钟").filter { it.remind }.forEach { schedule(context, it) }
    }

    private fun operation(context: Context, alarm: EventEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, alarm.id)
            putExtra(EXTRA_TITLE, alarm.title)
        }
        return PendingIntent.getBroadcast(context, alarm.id.toInt(), intent, flags())
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
