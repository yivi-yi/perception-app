package com.yivi.perception

import android.app.Application
import androidx.room.Room
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.SettingsRepository
import com.yivi.perception.data.db.PerceptionDatabase
import com.yivi.perception.data.repo.PerceptionRepository
import com.yivi.perception.mcp.HttpMcpServer
import com.yivi.perception.mcp.McpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PerceptionApp : Application() {

    lateinit var database: PerceptionDatabase
        private set
    lateinit var repository: PerceptionRepository
        private set
    lateinit var toolkit: NativeToolkit
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var mcpServer: HttpMcpServer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsRepository(this)
        database = Room.databaseBuilder(this, PerceptionDatabase::class.java, "perception.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = PerceptionRepository(database.dao())
        toolkit = NativeToolkit(this, repository, settings)
        mcpServer = HttpMcpServer(McpEngine(toolkit, settings))

        // 每次启动把闹钟重新排一遍：换过手机时间、被杀过、升过级，都能自动接上
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AlarmScheduler.rescheduleAll(this@PerceptionApp) }
        }
    }

    companion object {
        lateinit var instance: PerceptionApp
            private set
    }
}
