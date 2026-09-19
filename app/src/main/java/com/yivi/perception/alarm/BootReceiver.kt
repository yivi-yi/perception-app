package com.yivi.perception.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yivi.perception.PerceptionApp
import com.yivi.perception.service.ServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机（或重启后）把闹钟重新排一遍；开着"开机自启"顺带把 MCP 服务也拉起来 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val arrived = intent.action ?: return
        if (arrived != Intent.ACTION_BOOT_COMPLETED &&
            arrived != "android.intent.action.QUICKBOOT_POWERON" &&
            arrived != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext as? PerceptionApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (app.settings.bootStart.value) {
                    val svc = Intent(context, ServerService::class.java).apply { setAction(ServerService.ACTION_START) }
                    try {
                        ContextCompat.startForegroundService(context, svc)
                    } catch (_: Exception) {
                    }
                }
                AlarmScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
