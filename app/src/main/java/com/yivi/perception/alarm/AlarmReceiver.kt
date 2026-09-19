package com.yivi.perception.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yivi.perception.PerceptionApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 闹钟到点：拉起响铃服务，再把下一次排上（重复的排下一次，一次性的响过就关掉） */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        val app = context.applicationContext as? PerceptionApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val item = app.repository.all().firstOrNull { it.id == id } ?: return@launch
                if (!item.remind) return@launch

                if (item.category == "闹钟") {
                    AlarmRingService.start(context, id, item.title.ifBlank { "闹钟" })
                    if (item.repeatDays.isBlank()) {
                        // 一次性的响过就自己关掉，列表里不会留一条"还开着"的假象
                        app.repository.update(item.copy(remind = false))
                    } else {
                        AlarmScheduler.schedule(context, item)
                    }
                } else {
                    // 日程：提醒过一次就完事
                    ReminderNotifier.show(context, item)
                    app.repository.update(item.copy(remind = false))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
