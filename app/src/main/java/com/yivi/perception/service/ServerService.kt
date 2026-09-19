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
import com.yivi.perception.MainActivity
import com.yivi.perception.PerceptionApp
import com.yivi.perception.R

class ServerService : Service() {

    companion object {
        const val ACTION_START = "com.yivi.perception.START"
        const val ACTION_STOP = "com.yivi.perception.STOP"
        const val PORT = 9001
    }

    private var stayAliveView: android.view.View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeStayAliveOverlay()
                PerceptionApp.instance.mcpServer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        PerceptionApp.instance.mcpServer.start(PORT) { }
        // 有悬浮窗权限就挂一个 1dp 的透明悬浮层：进程被系统清的时候会客气很多
        addStayAliveOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        removeStayAliveOverlay()
        super.onDestroy()
    }

    /** 1dp 透明悬浮层，只为让服务更稳；没权限就跳过 */
    private fun addStayAliveOverlay() {
        if (stayAliveView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val view = android.view.View(this)
            val lp = android.view.WindowManager.LayoutParams(
                1, 1,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = 0
                y = 0
            }
            wm.addView(view, lp)
            stayAliveView = view
        } catch (e: Exception) {
            stayAliveView = null
        }
    }

    private fun removeStayAliveOverlay() {
        val view = stayAliveView ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(view)
        } catch (_: Exception) {
        }
        stayAliveView = null
    }

    private fun startForegroundCompat() {
        val stopIntent = android.app.PendingIntent.getService(
            this, 99,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("perception_server", "Perception 服务", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, "perception_server")
            .setContentTitle("Perception · MCP 服务运行中")
            .setContentText("局域网地址 http://${NetworkUtils.localIp()}:$PORT/mcp（点通知可回到 APP）")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this, 100, Intent(this, MainActivity::class.java),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    } else {
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
            )
            .addAction(0, "停止服务", stopIntent)
            .build()
        startForeground(10, notification)
    }
}
