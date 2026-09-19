package com.yivi.perception

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.repo.PerceptionRepository
import com.yivi.perception.service.NotificationListener
import com.yivi.perception.service.PermissionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.ln

class NativeToolkit(
    private val context: Context,
    private val repo: PerceptionRepository
) {

    // ── 设备 ────────────────────────────────────────

    fun deviceInfo(): Map<String, String> = mapOf(
        "brand" to (Build.BRAND ?: ""),
        "model" to (Build.MODEL ?: ""),
        "device" to (Build.DEVICE ?: ""),
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString()
    )

    suspend fun battery(): Map<String, Any> = withContext(Dispatchers.IO) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        mapOf("ok" to true, "level" to level, "charging" to charging)
    }

    suspend fun network(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val info = runCatching { wm.connectionInfo }.getOrNull()
            val ssid = info?.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            mapOf(
                "ok" to true,
                "ssid" to (ssid ?: "读不到 WiFi 名（安卓 10 以上要定位权限）"),
                "rssi" to (info?.rssi ?: 0),
                @Suppress("DEPRECATION")
                "ip" to android.text.format.Formatter.formatIpAddress(info?.ipAddress ?: 0)
            )
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to (e.message ?: "读不到网络信息"))
        }
    }

    /** 最近一次定位；能拿到实时的就标 realtime=true，否则会说清是多久前的 */
    suspend fun location(): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            return@withContext mapOf("ok" to false, "error" to "没给定位权限，去应用设置里开一下")
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext mapOf("ok" to false, "error" to "这台设备没有定位服务")

        var loc: Location? = null
        var realtime = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                val got = currentLocation(lm, provider)
                if (got != null) {
                    loc = got
                    realtime = true
                    break
                }
            }
        }
        if (loc == null) {
            for (provider in listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )) {
                loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                if (loc != null) break
            }
        }

        val fix = loc ?: return@withContext mapOf("ok" to false, "error" to "没拿到定位（可能从来没定过位）")
        val ageMinutes = ((System.currentTimeMillis() - fix.time) / 60000L).toInt()
        mapOf(
            "ok" to true,
            "realtime" to realtime,
            "lat" to fix.latitude,
            "lng" to fix.longitude,
            "provider" to (fix.provider ?: ""),
            "age_minutes" to ageMinutes,
            "address" to reverseGeocode(fix.latitude, fix.longitude),
            "note" to if (realtime) "本次是实时定位" else "不是实时，是最近一次定位，约 $ageMinutes 分钟前"
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun currentLocation(lm: LocationManager, provider: String): Location? = try {
        if (lm.getProvider(provider) == null) {
            null
        } else {
            val latch = CountDownLatch(1)
            var result: Location? = null
            lm.getCurrentLocation(provider, null, context.mainExecutor) { l ->
                result = l
                latch.countDown()
            }
            latch.await(6, TimeUnit.SECONDS)
            result
        }
    } catch (e: Exception) {
        null
    }

    private fun reverseGeocode(lat: Double, lng: Double): String {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lng, 1)
            val addr = list?.firstOrNull()?.let { a ->
                listOfNotNull(a.adminArea, a.locality, a.subLocality, a.thoroughfare, a.subThoroughfare)
                    .joinToString("")
            }
            if (!addr.isNullOrBlank()) return addr
        } catch (e: Exception) {
        }
        val text = httpGet(
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lng&localityLanguage=zh",
            8000
        )
        val j = jsonObj(text) ?: return ""
        return listOfNotNull(
            j["countryName"].strOrNull(),
            j["principalSubdivision"].strOrNull(),
            j["city"].strOrNull(),
            j["locality"].strOrNull()
        ).distinct().joinToString(" ")
    }

    /** 天气：不给城市就用最近定位，数据来自 open-meteo（不用 key） */
    suspend fun weather(city: String?): Map<String, Any> = withContext(Dispatchers.IO) {
        var lat: Double
        var lng: Double
        var place = ""

        if (!city.isNullOrBlank()) {
            val geo = jsonObj(
                httpGet(
                    "https://geocoding-api.open-meteo.com/v1/search?name=${encode(city)}&count=1&language=zh&format=json",
                    10000
                )
            )
            val first = geo?.get("results")?.asArray()?.firstOrNull()?.jsonObject
                ?: return@withContext mapOf("ok" to false, "error" to "找不到城市「$city」")
            lat = first["latitude"].numOrNull() ?: return@withContext mapOf("ok" to false, "error" to "城市坐标没拿到")
            lng = first["longitude"].numOrNull() ?: 0.0
            place = listOfNotNull(first["name"].strOrNull(), first["admin1"].strOrNull()).distinct().joinToString(" ")
        } else {
            val fix = lastFix() ?: return@withContext mapOf(
                "ok" to false,
                "error" to "没给城市、也没定位过：要么传 city，要么先把定位权限和定位打开"
            )
            lat = fix.first
            lng = fix.second
            place = "当前位置"
        }

        val text = httpGet(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=3",
            12000
        )
        val root = jsonObj(text)
            ?: return@withContext weatherFallback(lat, lng, place)
                ?: mapOf("ok" to false, "error" to "天气接口都没通，检查一下手机能不能上网")
        val cur = root["current"]?.jsonObject
        val daily = root["daily"]?.jsonObject

        val days = mutableListOf<Map<String, Any>>()
        val dates = daily?.get("time")?.asArray()
        if (dates != null) {
            dates.forEachIndexed { i, d ->
                days.add(
                    mapOf(
                        "date" to (d.strOrNull() ?: ""),
                        "weather" to weatherText(daily["weather_code"].asArray()?.getOrNull(i)?.numOrNull()?.toInt()),
                        "min" to (daily["temperature_2m_min"].asArray()?.getOrNull(i)?.numOrNull() ?: 0.0),
                        "max" to (daily["temperature_2m_max"].asArray()?.getOrNull(i)?.numOrNull() ?: 0.0),
                        "rain_prob" to (daily["precipitation_probability_max"].asArray()?.getOrNull(i)?.numOrNull() ?: 0.0)
                    )
                )
            }
        }

        mapOf(
            "ok" to true,
            "place" to place,
            "now" to mapOf(
                "temp" to (cur?.get("temperature_2m")?.numOrNull() ?: 0.0),
                "feels_like" to (cur?.get("apparent_temperature")?.numOrNull() ?: 0.0),
                "humidity" to (cur?.get("relative_humidity_2m")?.numOrNull() ?: 0.0),
                "wind" to (cur?.get("wind_speed_10m")?.numOrNull() ?: 0.0),
                "weather" to weatherText(cur?.get("weather_code")?.numOrNull()?.toInt())
            ),
            "days" to days
        )
    }

    /** 备用天气源：wttr.in，也不用 key */
    private fun weatherFallback(lat: Double, lng: Double, place: String): Map<String, Any>? {
        val root = jsonObj(httpGet("https://wttr.in/$lat,$lng?format=j1", 12000)) ?: return null
        val cur = root["current_condition"]?.asArray()?.firstOrNull()?.jsonObject ?: return null
        val days = root["weather"]?.asArray()?.take(3)?.map { d ->
            val o = d.jsonObject
            val noon = o["hourly"].asArray()?.getOrNull(4)?.jsonObject
            mapOf(
                "date" to (o["date"].strOrNull() ?: ""),
                "weather" to (noon?.get("weatherDesc").asArray()?.firstOrNull()?.jsonObject?.get("value").strOrNull() ?: ""),
                "min" to ((o["mintempC"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "max" to ((o["maxtempC"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "rain_prob" to ((noon?.get("chanceofrain").strOrNull() ?: "0").toDoubleOrNull() ?: 0.0)
            )
        } ?: emptyList()

        return mapOf(
            "ok" to true,
            "place" to place,
            "source" to "wttr.in（备用源）",
            "now" to mapOf(
                "temp" to ((cur["temp_C"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "feels_like" to ((cur["FeelsLikeC"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "humidity" to ((cur["humidity"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "wind" to ((cur["windspeedKmph"].strOrNull() ?: "0").toDoubleOrNull() ?: 0.0),
                "weather" to (cur["weatherDesc"].asArray()?.firstOrNull()?.jsonObject?.get("value").strOrNull() ?: "")
            ),
            "days" to days
        )
    }

    private suspend fun lastFix(): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) return@withContext null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
        var loc: Location? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                val got = currentLocation(lm, provider)
                if (got != null) {
                    loc = got
                    break
                }
            }
        }
        if (loc == null) {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                if (loc != null) break
            }
        }
        loc?.let { it.latitude to it.longitude }
    }

    // ── 传感器 ──────────────────────────────────────

    fun sensors(): List<Map<String, String>> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        return sm.getSensorList(Sensor.TYPE_ALL).map {
            mapOf(
                "name" to (it.name ?: ""),
                "type" to it.type.toString(),
                "vendor" to (it.vendor ?: "")
            )
        }
    }

    /** 真的去读一次传感器：light / proximity / steps / direction / motion / all */
    suspend fun readSensor(kind: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@withContext mapOf("ok" to false, "error" to "这台设备没有传感器服务")
        val out = mutableMapOf<String, Any>()

        if (kind == "all" || kind == "light") {
            val v = readOnce(sm, Sensor.TYPE_LIGHT)
            if (v != null) {
                val lux = v[0]
                out["light_lux"] = lux
                out["light"] = when {
                    lux < 10 -> "很暗"
                    lux < 100 -> "偏暗"
                    lux < 1000 -> "正常"
                    else -> "很亮"
                }
            }
        }
        if (kind == "all" || kind == "proximity") {
            val v = readOnce(sm, Sensor.TYPE_PROXIMITY)
            if (v != null) {
                val max = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: 1f
                out["proximity"] = if (v[0] < max) "贴近（可能在口袋或耳边）" else "远离"
            }
        }
        if (kind == "all" || kind == "motion") {
            val v = readOnce(sm, Sensor.TYPE_ACCELEROMETER)
            if (v != null) {
                val g = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) / SensorManager.GRAVITY_EARTH
                out["motion_g"] = g
                out["motion"] = when {
                    g < 1.15f -> "放着没动"
                    g < 1.8f -> "轻轻动着"
                    else -> "在晃"
                }
            }
        }
        if (kind == "all" || kind == "direction") {
            val v = readOnce(sm, Sensor.TYPE_ROTATION_VECTOR)
            if (v != null) {
                val rm = FloatArray(9)
                val ori = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rm, v)
                SensorManager.getOrientation(rm, ori)
                val deg = ((Math.toDegrees(ori[0].toDouble()).toFloat() + 360f) % 360f).toInt()
                out["azimuth"] = deg
                out["direction"] = when {
                    deg < 23 || deg >= 338 -> "北"
                    deg < 68 -> "东北"
                    deg < 113 -> "东"
                    deg < 158 -> "东南"
                    deg < 203 -> "南"
                    deg < 248 -> "西南"
                    deg < 293 -> "西"
                    else -> "西北"
                }
            }
        }
        if (kind == "all" || kind == "steps") {
            if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION) && Build.VERSION.SDK_INT >= 29) {
                out["steps"] = "没给活动识别权限，读不了步数"
            } else {
                val v = readOnce(sm, Sensor.TYPE_STEP_COUNTER)
                if (v != null) out["steps_since_boot"] = v[0].toInt()
            }
        }

        if (out.isEmpty()) {
            mapOf("ok" to false, "error" to "这个传感器读不到（设备可能没有）")
        } else {
            mapOf("ok" to true).plus(out)
        }
    }

    private fun readOnce(sm: SensorManager, type: Int, timeoutMs: Long = 1500): FloatArray? {
        val sensor = sm.getDefaultSensor(type) ?: return null
        val latch = CountDownLatch(1)
        var values: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                values = event.values.clone()
                latch.countDown()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        return try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            sm.unregisterListener(listener)
            values
        } catch (e: Exception) {
            runCatching { sm.unregisterListener(listener) }
            null
        }
    }

    // ── 应用 ────────────────────────────────────────

    fun openApp(packageName: String): Map<String, Any> {
        if (packageName.isBlank()) return mapOf("ok" to false, "error" to "要传 packageName")
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                mapOf("ok" to true, "app" to appLabel(packageName), "package" to packageName)
            } else {
                mapOf("ok" to false, "error" to "没装这个应用，或者它没有启动入口：$packageName")
            }
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to (e.message ?: "打不开"))
        }
    }

    fun installedApps(): List<Map<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo?.packageName }
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                mapOf("name" to (info.loadLabel(pm)?.toString() ?: pkg), "package" to pkg)
            }
            .sortedBy { it["name"] }
            .take(300)
    }

    fun currentApp(): Map<String, Any> {
        val pkg = PermissionService.lastPackage
        if (pkg.isNullOrBlank()) {
            return mapOf("ok" to false, "error" to "没开无障碍，或者还没捕捉到前台应用")
        }
        return mapOf("ok" to true, "app" to appLabel(pkg), "package" to pkg)
    }

    /**
     * 读通知，两路互不覆盖：
     * kind=current 只看通知栏现在挂着的 / recent 只看最近收到的 / 其它（空、both）两边都给。
     * limit 每类最多几条，默认 20，最多 20。
     */
    fun notifications(kind: String?, limit: Int): Map<String, Any> {
        val k = (kind ?: "").lowercase()
        val n = limit.coerceIn(1, NotificationListener.MAX)
        val wantCurrent = k != "recent"
        val wantRecent = k != "current"

        val out = mutableMapOf<String, Any>("ok" to true)
        if (wantCurrent) {
            val current = NotificationListener.active(n)
            out["current"] = current
            out["current_count"] = current.size
        }
        if (wantRecent) {
            val recent = NotificationListener.recent().takeLast(n)
            out["recent"] = recent
            out["recent_count"] = recent.size
        }
        val total = (out["current_count"] as? Int ?: 0) + (out["recent_count"] as? Int ?: 0)
        if (total == 0) {
            return mapOf("ok" to false, "error" to "读不到通知：可能没开通知监听权限，或者两边都是空的")
        }
        return out
    }

    /** 录 3 秒环境音，估算分贝和是不是有人声 */
    suspend fun ambient(): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return@withContext mapOf("ok" to false, "error" to "没给麦克风权限")
        }
        var recorder: MediaRecorder? = null
        try {
            val file = File(context.cacheDir, "ambient_${System.currentTimeMillis()}.m4a")
            val r = MediaRecorder()
            recorder = r
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()

            val amps = mutableListOf<Int>()
            repeat(6) {
                delay(500)
                runCatching { r.maxAmplitude }.getOrElse { 0 }.let { if (it > 0) amps.add(it) }
            }
            runCatching { r.stop() }
            runCatching { r.release() }
            recorder = null
            file.delete()

            val peak = amps.maxOrNull() ?: 0
            val db = if (peak > 0) {
                (20 * ln(peak.toDouble() / 32767.0) / ln(10.0) + 90).toInt().coerceIn(20, 100)
            } else 0
            val env = when {
                db <= 0 -> "没录到声音"
                db < 40 -> "很安静"
                db < 70 -> "正常"
                else -> "有点吵"
            }
            val voice = db >= 45 && amps.size >= 3 &&
                ((amps.maxOrNull() ?: 0) - (amps.minOrNull() ?: 0)) > amps.average() * 0.4
            mapOf("ok" to true, "db" to db, "env" to env, "voice" to voice)
        } catch (e: Exception) {
            mapOf("ok" to false, "error" to (e.message ?: "录音失败"))
        } finally {
            runCatching { recorder?.release() }
        }
    }

    // ── 点歌：查网易云 API 拿 id，再跳本机网易云 ────────────

    /** 搜歌：返回歌名 + 歌手 + 专辑 + 歌曲 id（id 交给 play_song 用） */
    suspend fun searchSong(keyword: String, limit: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext mapOf("ok" to false, "error" to "要传歌名，或者歌名加歌手")
        val n = limit.coerceIn(1, 10)
        val text = httpGet(
            "https://music.163.com/api/search/get/web?s=${encode(keyword)}&type=1&limit=$n",
            10000,
            mapOf("Referer" to "https://music.163.com/")
        )
        val root = jsonObj(text)
            ?: return@withContext mapOf("ok" to false, "error" to "搜歌接口没通，检查手机能不能上网")
        val array = root["result"]?.jsonObject?.get("songs")?.asArray()
        if (array.isNullOrEmpty()) {
            return@withContext mapOf("ok" to false, "error" to "没搜到：$keyword")
        }
        val songs = array.take(n).map { item ->
            val o = item.jsonObject
            mapOf(
                "id" to (o["id"].numOrNull()?.toLong() ?: 0L),
                "name" to (o["name"].strOrNull() ?: ""),
                "artist" to (o["artists"].asArray()?.joinToString("/") { it.jsonObject["name"].strOrNull() ?: "" } ?: ""),
                "album" to (o["album"]?.jsonObject?.get("name").strOrNull() ?: "")
            )
        }
        mapOf("ok" to true, "keyword" to keyword, "count" to songs.size, "songs" to songs)
    }

    /** 点歌：按 id 跳本机网易云的歌曲页 */
    suspend fun playSong(id: Long, name: String): Map<String, Any> = withContext(Dispatchers.IO) {
        if (id <= 0L) {
            return@withContext mapOf("ok" to false, "error" to "要传歌曲 id：先用 search_song 搜出 id")
        }
        val opened = openNetEase(id)
        mapOf(
            "ok" to opened,
            "id" to id,
            "name" to name,
            "note" to if (opened) "已经跳本机网易云了" else "没打开：本机可能没装网易云，网页也没能打开"
        )
    }

    private fun openNetEase(id: Long): Boolean {
        val tries = listOf(
            "orpheus://song/$id",
            "https://music.163.com/song?id=$id"
        )
        for (uri in tries) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }

    // ── 日程 / 闹钟 ─────────────────────────────────

    suspend fun addSchedule(title: String, note: String, time: Long, remind: Boolean): Map<String, Any> {
        val event = com.yivi.perception.data.db.EventEntity(
            category = "行程", title = title, note = note, time = time, remind = remind
        )
        val id = repo.add(event)
        if (remind) AlarmScheduler.schedule(context, event.copy(id = id))
        return mapOf("ok" to true, "id" to id, "category" to "行程", "time" to time)
    }

    suspend fun addAlarm(title: String, note: String, time: Long, repeatDays: String): Map<String, Any> {
        val alarm = com.yivi.perception.data.db.EventEntity(
            category = "闹钟", title = title, note = note, time = time, remind = true, repeatDays = repeatDays
        )
        val id = repo.add(alarm)
        AlarmScheduler.schedule(context, alarm.copy(id = id))
        return mapOf("ok" to true, "id" to id, "category" to "闹钟", "time" to time, "repeatDays" to repeatDays)
    }

    suspend fun listSchedules(): List<Map<String, Any>> =
        repo.listByCategory("行程").map {
            mapOf(
                "id" to it.id, "title" to it.title, "note" to it.note,
                "time" to it.time, "remind" to it.remind
            )
        }

    suspend fun listAlarms(): List<Map<String, Any>> =
        repo.listByCategory("闹钟").map {
            mapOf(
                "id" to it.id, "title" to it.title, "note" to it.note,
                "time" to it.time, "repeatDays" to it.repeatDays, "enabled" to it.remind
            )
        }

    suspend fun updateEvent(id: Long, title: String?, note: String?, time: Long?, remind: Boolean?, repeatDays: String?): Map<String, Any> {
        val item = repo.all().find { it.id == id } ?: return mapOf("ok" to false, "error" to "没有 id=$id 这条")
        val updated = item.copy(
            title = title ?: item.title,
            note = note ?: item.note,
            time = time ?: item.time,
            remind = remind ?: item.remind,
            repeatDays = repeatDays ?: item.repeatDays
        )
        repo.update(updated)
        AlarmScheduler.cancel(context, item)
        if (updated.remind) AlarmScheduler.schedule(context, updated)
        return mapOf("ok" to true, "id" to id)
    }

    suspend fun deleteEvent(id: Long): Map<String, Any> {
        val item = repo.all().find { it.id == id } ?: return mapOf("ok" to false, "error" to "没有 id=$id 这条")
        AlarmScheduler.cancel(context, item)
        repo.deleteById(id)
        return mapOf("ok" to true, "id" to id, "deleted" to item.title)
    }

    // ── 小工具 ─────────────────────────────────────

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun appLabel(pkg: String): String = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(
        url: String,
        timeoutMs: Int = 10000,
        headers: Map<String, String> = emptyMap()
    ): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
        )
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    private fun jsonObj(text: String?): JsonObject? =
        text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private fun JsonElement?.strOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.numOrNull(): Double? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

    private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

    private fun weatherText(code: Int?): String = when (code) {
        null -> "未知"
        0 -> "晴"
        1 -> "基本晴"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "雪"
        77 -> "雪粒"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷阵雨带冰雹"
        else -> "未知($code)"
    }
}
