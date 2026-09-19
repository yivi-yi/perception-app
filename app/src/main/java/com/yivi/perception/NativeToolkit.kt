package com.yivi.perception

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.repo.PerceptionRepository

class NativeToolkit(private val context: Context, private val repo: PerceptionRepository) {

    fun deviceInfo(): Map<String, String> = mapOf(
        "model" to (Build.MODEL ?: ""),
        "brand" to (Build.BRAND ?: ""),
        "sdk" to Build.VERSION.SDK_INT.toString(),
        "release" to Build.VERSION.RELEASE,
        "device" to (Build.DEVICE ?: "")
    )

    suspend fun battery(): Map<String, Any> = withContext(Dispatchers.IO) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        mapOf("level" to level, "charging" to charging)
    }

    suspend fun storage(): Map<String, Any> = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val avail = stat.availableBytes
        mapOf("total" to total, "available" to avail, "used" to (total - avail))
    }

    fun sensors(): List<Map<String, String>> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getSensorList(Sensor.TYPE_ALL).map {
            mapOf("name" to (it.name ?: ""), "type" to it.type.toString(), "vendor" to (it.vendor ?: ""))
        }
    }

    suspend fun lastLocation(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var out = mapOf<String, Any>("ok" to false)
            for (p in providers) {
                val loc = try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null }
                if (loc != null) {
                    out = mapOf("ok" to true, "provider" to p, "lat" to loc.latitude, "lng" to loc.longitude, "time" to loc.time)
                    break
                }
            }
            out
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to (e.message ?: "no permission"))
        }
    }

    suspend fun network(): Map<String, String> = withContext(Dispatchers.IO) {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo
        mapOf("ssid" to (info?.ssid ?: ""), "rssi" to (info?.rssi?.toString() ?: ""), "ip" to android.text.format.Formatter.formatIpAddress(info?.ipAddress ?: 0))
    }

    fun openApp(packageName: String): Map<String, String> {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                mapOf("ok" to "true", "app" to packageName)
            } else mapOf("ok" to "false", "error" to "not found")
        } catch (e: Exception) {
            mapOf("ok" to "false", "error" to (e.message ?: ""))
        }
    }

    fun installedApps(): List<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
    }

    suspend fun addSchedule(title: String, note: String, time: Long, remind: Boolean): Map<String, Any> {
        val id = repo.add(com.yivi.perception.data.db.EventEntity(category = "行程", title = title, note = note, time = time, remind = remind))
        return mapOf("ok" to true, "id" to id, "category" to "行程")
    }

    suspend fun addAlarm(title: String, note: String, time: Long, repeatDays: String): Map<String, Any> {
        val alarm = com.yivi.perception.data.db.EventEntity(
            category = "闹钟", title = title, note = note, time = time, remind = true, repeatDays = repeatDays
        )
        val id = repo.add(alarm)
        // AI 加进来的闹钟也要真的排上，不然只是一条数据
        AlarmScheduler.schedule(context, alarm.copy(id = id))
        return mapOf("ok" to true, "id" to id, "category" to "闹钟")
    }

    suspend fun listSchedules(): List<Map<String, Any>> =
        repo.listByCategory("行程").map { mapOf("id" to it.id, "title" to it.title, "note" to it.note, "time" to it.time, "remind" to it.remind) }

    suspend fun listAlarms(): List<Map<String, Any>> =
        repo.listByCategory("闹钟").map { mapOf("id" to it.id, "title" to it.title, "note" to it.note, "time" to it.time, "repeatDays" to it.repeatDays) }

    suspend fun updateEvent(id: Long, title: String?, note: String?, time: Long?, remind: Boolean?, repeatDays: String?): Map<String, Any> {
        val all = repo.all()
        val item = all.find { it.id == id } ?: return mapOf("ok" to false, "error" to "not found")
        val updated = item.copy(
            title = title ?: item.title,
            note = note ?: item.note,
            time = time ?: item.time,
            remind = remind ?: item.remind,
            repeatDays = repeatDays ?: item.repeatDays
        )
        repo.update(updated)
        if (updated.category == "闹钟") {
            AlarmScheduler.cancel(context, item)
            if (updated.remind) AlarmScheduler.schedule(context, updated)
        }
        return mapOf("ok" to true, "id" to id)
    }

    suspend fun deleteEvent(id: Long): Map<String, Any> {
        repo.all().firstOrNull { it.id == id && it.category == "闹钟" }?.let { AlarmScheduler.cancel(context, it) }
        repo.deleteById(id)
        return mapOf("ok" to true, "id" to id)
    }
}
