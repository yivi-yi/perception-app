package com.yivi.perception.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yivi.perception.PerceptionApp
import com.yivi.perception.R

class ServerService : Service() {

    companion object {
        const val ACTION_START = "com.yivi.perception.START"
        const val ACTION_STOP = "com.yivi.perception.STOP"
        const val PORT = 9001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PerceptionApp.instance.mcpServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        PerceptionApp.instance.mcpServer.start(PORT) { }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("perception_server", "Perception 服务", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, "perception_server")
            .setContentTitle("Perception")
            .setContentText("MCP 服务运行中 · ${NetworkUtils.localIp()}:$PORT")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
        startForeground(10, notification)
    }
}
