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
import androidx.core.content.ContextCompat
import com.yivi.perception.alarm.AlarmScheduler
import com.yivi.perception.data.SettingsRepository
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
    private val repo: PerceptionRepository,
    private val settings: SettingsRepository
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

    @Suppress("DEPRECATION")
    suspend fun network(): Map<String, Any> = withContext(Dispatchers.IO) {
        val out = mutableMapOf<String, Any>()

        // IP 从网卡读。别用 WifiManager.ipAddress：安卓 13 上它恒为 0，才显示成 0.0.0.0
        val ips = com.yivi.perception.service.NetworkUtils.allIps()
        out["ip"] = if (ips.isEmpty()) "没连到局域网（可能只有移动数据）" else ips.joinToString(" / ")

        val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = runCatching { wm.connectionInfo }.getOrNull()
            val ssid = info?.ssid?.replace("\"", "")?.trim()
            out["wifi_ssid"] = when {
                !fine -> "读不到 WiFi 名：要「定位」权限（安卓 10 起的规定）"
                ssid.isNullOrBlank() || ssid == "<unknown ssid>" -> "没连 WiFi（或者系统不给读）"
                else -> ssid
            }
            if (fine && !ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                out["wifi_signal"] = info?.rssi ?: 0
            }
        } catch (e: Exception) {
            out["wifi_ssid"] = "读不到：${e.message ?: "系统限制"}"
        }

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            out["type"] = when {
                caps == null -> "没网"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "移动数据"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线"
                else -> "其它"
            }
        } catch (_: Exception) {
        }

        mapOf("ok" to true).plus(out)
    }

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
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            val got = currentLocation(lm, provider)
            if (got != null) {
                loc = got
                realtime = true
                break
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

    @Suppress("DEPRECATION")
    private fun reverseGeocode(lat: Double, lng: Double): String {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
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
    suspend fun weather(city: String?, source: String?): Map<String, Any> = withContext(Dispatchers.IO) {
        var lat: Double
        var lng: Double
        var place = ""

        // 默认只给当前天气；传 forecast 才带上未来三天
        val wantForecast = (source ?: "").equals("forecast", ignoreCase = true)

        // 没指定城市就用设置里的默认城市；连默认城市都没有才用定位
        val wanted = city?.takeIf { it.isNotBlank() } ?: settings.weatherCity.value.takeIf { it.isNotBlank() }

        if (wanted != null) {
            val geo = jsonObj(
                httpGet(
                    "https://geocoding-api.open-meteo.com/v1/search?name=${encode(wanted)}&count=1&language=zh&format=json",
                    10000
                )
            )
            val first = geo?.get("results")?.asArray()?.firstOrNull()?.jsonObject
                ?: return@withContext mapOf("ok" to false, "error" to "找不到城市「$wanted」")
            lat = first["latitude"].numOrNull() ?: return@withContext mapOf("ok" to false, "error" to "城市坐标没拿到")
            lng = first["longitude"].numOrNull() ?: 0.0
            place = listOfNotNull(first["name"].strOrNull(), first["admin1"].strOrNull()).distinct().joinToString(" ")
        } else {
            val fix = lastFix() ?: return@withContext mapOf(
                "ok" to false,
                "error" to "没传城市、设置里也没有默认城市、又定位不到：三个里给一个就行"
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
            ?: return@withContext weatherFallback(lat, lng, place, wantForecast)
                ?: mapOf("ok" to false, "error" to "天气接口都没通，检查一下手机能不能上网")
        val cur = root["current"]?.jsonObject
        val daily = root["daily"]?.jsonObject

        val days = mutableListOf<Map<String, Any>>()
        val dates = daily?.get("time")?.asArray()
        if (dates != null && wantForecast) {
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
    private fun weatherFallback(lat: Double, lng: Double, place: String, wantForecast: Boolean): Map<String, Any>? {
        val root = jsonObj(httpGet("https://wttr.in/$lat,$lng?format=j1", 12000)) ?: return null
        val cur = root["current_condition"]?.asArray()?.firstOrNull()?.jsonObject ?: return null
        val days = if (!wantForecast) emptyList() else root["weather"]?.asArray()?.take(3)?.map { d ->
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
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            val got = currentLocation(lm, provider)
            if (got != null) {
                loc = got
                break
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

    /** 常见传感器在这台机器上有没有（只列这几个常用的，不再倒一长串杂项） */
    private fun commonSensorList(sm: SensorManager): List<Map<String, Any>> = listOf(
        Triple("light", "光线", Sensor.TYPE_LIGHT),
        Triple("proximity", "距离", Sensor.TYPE_PROXIMITY),
        Triple("motion", "动静（加速度）", Sensor.TYPE_ACCELEROMETER),
        Triple("direction", "朝向", Sensor.TYPE_ROTATION_VECTOR),
        Triple("steps", "步数", Sensor.TYPE_STEP_COUNTER)
    ).map { (kind, label, type) ->
        mapOf(
            "kind" to kind,
            "name" to label,
            "有" to (sm.getDefaultSensor(type) != null)
        )
    }

    /**
     * 读一次传感器。只保留每台手机基本都有的那几路：
     * light 光线 / proximity 距离 / motion 动静 / direction 朝向 / steps 步数；
     * kind="list" 看这台机器有哪几路；不传（或 all）就是全都读。
     */
    suspend fun readSensor(kind: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@withContext mapOf("ok" to false, "error" to "这台设备没有传感器服务")

        if (kind.equals("list", true)) {
            val list = commonSensorList(sm)
            return@withContext mapOf(
                "ok" to true,
                "sensors" to list,
                "note" to "有=false 就是这台机器没有那一路"
            )
        }

        val out = mutableMapOf<String, Any>()

        if (kind == "all" || kind == "light") {
            // 光感很多机器第一帧是 0，取中位数：开灯却报"很暗"就是这个坑
            val lux = readSamples(sm, Sensor.TYPE_LIGHT).map { it[0] }.sorted().let {
                if (it.isEmpty()) null else it[it.size / 2]
            }
            if (lux != null) {
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
            val v = readSamples(sm, Sensor.TYPE_PROXIMITY).lastOrNull()
            if (v != null) {
                val max = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.maximumRange ?: 1f
                out["proximity"] = if (v[0] < max) "贴近（可能在口袋或耳边）" else "远离"
            }
        }
        if (kind == "all" || kind == "motion") {
            // 加速度计：单看一帧判不出动静，要在一小段时间里看抖动
            val samples = readSamples(sm, Sensor.TYPE_ACCELEROMETER)
            if (samples.isNotEmpty()) {
                val gs = samples.map {
                    kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) / SensorManager.GRAVITY_EARTH
                }
                val mean = gs.average().toFloat()
                val jitter = (gs.max() - gs.min())
                out["motion_g"] = (Math.round(mean * 100) / 100.0)
                out["motion_wobble"] = (Math.round(jitter * 100) / 100.0)
                out["motion"] = when {
                    jitter < 0.05f -> "放着没动"
                    jitter < 0.40f -> "轻轻动着"
                    else -> "在晃"
                }
            }
        }
        if (kind == "all" || kind == "direction") {
            val rv = readSamples(sm, Sensor.TYPE_ROTATION_VECTOR).lastOrNull()
            val azimuth = if (rv != null) {
                val rm = FloatArray(9)
                val ori = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rm, rv)
                SensorManager.getOrientation(rm, ori)
                Math.toDegrees(ori[0].toDouble()).toFloat()
            } else {
                // 少数机器没有旋转矢量，退回老接口 TYPE_ORIENTATION（已废弃但还能用）
                readSamples(sm, Sensor.TYPE_ORIENTATION).lastOrNull()?.get(0)
            }
            if (azimuth != null) {
                val deg = ((azimuth + 360f) % 360f).toInt()
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
            if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
                out["steps"] = "没给活动识别权限，读不了步数"
            } else {
                val v = readSamples(sm, Sensor.TYPE_STEP_COUNTER).lastOrNull()
                if (v != null) out["steps_since_boot"] = v[0].toInt()
            }
        }

        if (out.isEmpty()) {
            mapOf(
                "ok" to false,
                "error" to "没读到 kind=$kind。能读的是 light 光线 / proximity 距离 / motion 动静 / direction 朝向 / steps 步数；" +
                    "读不到一般是这台机器没那路传感器，或者它只在变化时才上报（步数要边走边读）。kind=list 可以看这台机器有哪几路"
            )
        } else {
            mapOf("ok" to true).plus(out)
        }
    }

    /**
     * 采集一小段时间的传感器数据（不是只取第一帧）。
     * 光感/步数这类传感器有时只在变化或走路时上报，采不到就返回空，由调用方说明。
     */
    private fun readSamples(sm: SensorManager, type: Int, windowMs: Long = 700, maxSamples: Int = 24): List<FloatArray> {
        val sensor = sm.getDefaultSensor(type) ?: return emptyList()
        val samples = mutableListOf<FloatArray>()
        val lock = Object()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                synchronized(lock) {
                    if (samples.size < maxSamples) samples.add(event.values.clone())
                    lock.notifyAll()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        return try {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            val end = System.currentTimeMillis() + windowMs
            synchronized(lock) {
                while (samples.size < 3 && System.currentTimeMillis() < end) {
                    lock.wait(60)
                }
            }
            synchronized(lock) { samples.toList() }
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { sm.unregisterListener(listener) }
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
        val svc = PermissionService.current
        // 优先问无障碍服务"当前活动的那个窗口"，这才是实时的；lastPackage 只是最后一次窗口变化事件，会慢一拍
        val live = runCatching {
            svc?.windows?.firstOrNull { it.isActive && it.isFocused }
                ?: svc?.windows?.firstOrNull { it.isActive }
        }.getOrNull()
        val livePkg = runCatching { live?.root?.packageName?.toString() }.getOrNull()
        val pkg = livePkg?.takeIf { it.isNotBlank() } ?: PermissionService.lastPackage
        if (pkg.isNullOrBlank()) {
            return mapOf("ok" to false, "error" to "没开无障碍，或者还没捕捉到前台应用")
        }
        val out = mutableMapOf<String, Any>(
            "ok" to true,
            "app" to appLabel(pkg),
            "package" to pkg,
            "source" to if (livePkg != null) "实时（无障碍当前窗口）" else "最后一次窗口变化事件，可能慢一拍"
        )
        // 有输入法/系统弹窗盖在上面时，报的是它们——这不是 bug，但得说清楚
        val onTop = runCatching {
            svc?.windows
                ?.filter {
                    it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                        it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM
                }
                ?.mapNotNull { it.root?.packageName?.toString() }
        }.getOrNull()
        if (!onTop.isNullOrEmpty()) {
            out["盖在上面的窗口"] = onTop.distinct().joinToString(", ")
        }
        return out
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
            val listenerOn = runCatching {
                androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }.getOrDefault(false)
            return if (!listenerOn) {
                mapOf("ok" to false, "error" to "读不到通知：没开「通知监听」权限（设置 → 权限 → 通知监听）")
            } else {
                out + mapOf("note" to "通知监听是开着的，但两边现在都是空的")
            }
        }
        return out
    }

    /** 录 3 秒环境音，估算分贝和是不是有人声 */
    @Suppress("DEPRECATION")
    suspend fun ambient(): Map<String, Any> = withContext(Dispatchers.IO) {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            return@withContext mapOf("ok" to false, "error" to "没给麦克风权限")
        }
        var recorder: MediaRecorder? = null
        try {
            val file = File(context.cacheDir, "ambient_${System.currentTimeMillis()}.m4a")
            val r = MediaRecorder(context)
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

    // ── 音量 / 铃声模式 / 勿扰 ────────────────────────

    /** 现在的铃声模式、勿扰状态、四路音量 */
    @Suppress("DEPRECATION")
    fun soundState(): Map<String, Any> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return mapOf("ok" to false, "error" to "这台设备没有音频服务")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val mode = when (am.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT -> "静音"
            android.media.AudioManager.RINGER_MODE_VIBRATE -> "震动"
            else -> "响铃"
        }
        val dnd = when (nm?.currentInterruptionFilter) {
            android.app.NotificationManager.INTERRUPTION_FILTER_NONE -> "勿扰（全部屏蔽）"
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "勿扰（只放行优先）"
            android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS -> "只允许闹钟"
            android.app.NotificationManager.INTERRUPTION_FILTER_ALL -> "没开勿扰"
            else -> "读不到（可能没给勿扰权限）"
        }
        fun level(stream: Int): Int {
            val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            return am.getStreamVolume(stream) * 100 / max
        }
        return mapOf(
            "ok" to true,
            "mode" to mode,
            "dnd" to dnd,
            "volume" to mapOf(
                "music" to level(android.media.AudioManager.STREAM_MUSIC),
                "ring" to level(android.media.AudioManager.STREAM_RING),
                "notification" to level(android.media.AudioManager.STREAM_NOTIFICATION),
                "alarm" to level(android.media.AudioManager.STREAM_ALARM)
            ),
            "note" to run {
                val notif = level(android.media.AudioManager.STREAM_NOTIFICATION)
                if (notif == 0) "音量是百分比（0-100）。注意：通知音量现在是 0，来消息不会响" else "音量是百分比（0-100）"
            }
        )
    }

    /**
     * 改铃声模式 / 改音量。
     * mode：normal 响铃、vibrate 震动、silent 静音
     * dnd：true 开勿扰 / false 关勿扰（要勿扰权限；开着勿扰时系统会忽略铃声模式的改动）
     * level：0-100，配 stream 用（music / ring / notification / alarm，默认 music）
     */
    @Suppress("DEPRECATION")
    suspend fun setSound(mode: String?, dnd: Boolean?, level: Int?, stream: String?): Map<String, Any> = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@withContext mapOf("ok" to false, "error" to "这台设备没有音频服务")
        val done = mutableListOf<String>()

        val want = mode?.trim()?.lowercase()
        if (!want.isNullOrBlank()) {
            try {
                when (want) {
                    "silent", "静音", "mute" -> {
                        am.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                        done.add("铃声模式=静音")
                    }
                    "vibrate", "震动" -> {
                        am.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
                        done.add("铃声模式=震动")
                    }
                    "normal", "响铃", "ring" -> {
                        am.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
                        done.add("铃声模式=响铃")
                    }
                    "dnd", "勿扰" -> {
                        // 老写法：mode=dnd 等于开勿扰
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                        if (nm == null) {
                            return@withContext mapOf("ok" to false, "error" to "这台设备没有通知服务")
                        }
                        if (!nm.isNotificationPolicyAccessGranted) {
                            return@withContext mapOf(
                                "ok" to false,
                                "error" to "改勿扰要先给「勿扰权限」：设置 → 权限 → 勿扰权限，点一下去开"
                            )
                        }
                        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE)
                        done.add("已开勿扰")
                    }
                    else -> return@withContext mapOf("ok" to false, "error" to "mode 只认 normal / vibrate / silent / dnd")
                }
            } catch (e: Exception) {
                return@withContext mapOf("ok" to false, "error" to (e.message ?: "改铃声模式失败"))
            }
        }

        if (dnd != null) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (nm == null) {
                return@withContext mapOf("ok" to false, "error" to "这台设备没有通知服务")
            }
            if (!nm.isNotificationPolicyAccessGranted) {
                return@withContext mapOf(
                    "ok" to false,
                    "error" to "改勿扰要先给「勿扰权限」：设置 → 权限 → 勿扰权限，点一下去开"
                )
            }
            try {
                nm.setInterruptionFilter(
                    if (dnd) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                    else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                )
                done.add(if (dnd) "已开勿扰" else "已关勿扰")
            } catch (e: Exception) {
                return@withContext mapOf("ok" to false, "error" to (e.message ?: "改勿扰失败"))
            }
        }

        if (level != null) {
            val target = when (stream?.trim()?.lowercase()) {
                "ring", "铃声" -> android.media.AudioManager.STREAM_RING
                "notification", "通知" -> android.media.AudioManager.STREAM_NOTIFICATION
                "alarm", "闹钟" -> android.media.AudioManager.STREAM_ALARM
                else -> android.media.AudioManager.STREAM_MUSIC
            }
            try {
                run {
                    val max = am.getStreamMaxVolume(target).coerceAtLeast(1)
                    val v = (level.coerceIn(0, 100) * max + 50) / 100
                    am.setStreamVolume(target, v, 0)
                }
                // 通知那路如果能单独调就单独调（termux 那种四路都能调的机器都是独立的）
                if (am.getStreamMaxVolume(android.media.AudioManager.STREAM_NOTIFICATION) <= 0 &&
                    target == android.media.AudioManager.STREAM_NOTIFICATION
                ) {
                    done.add("这台机器没有独立的通知音量，跟着铃声走")
                }
                done.add("音量=${level.coerceIn(0, 100)}%")
            } catch (e: Exception) {
                return@withContext mapOf("ok" to false, "error" to (e.message ?: "改音量失败"))
            }
        }

        if (done.isEmpty()) {
            return@withContext mapOf("ok" to false, "error" to "没传要改的东西：mode / dnd / level 至少给一个")
        }
        val now = soundState().toMutableMap()
        now["ok"] = true
        now["changed"] = done
        now
    }

    // ── 搜歌 / 点歌 ─────────────────────────────────


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
        val how = openNetEase(id)
        mapOf(
            "ok" to (how != null),
            "id" to id,
            "name" to name,
            "route" to (how ?: "没跳成"),
            "note" to if (how != null) {
                "已经用「$how」打开这首歌了"
            } else {
                "没打开：这台手机没装网易云音乐，网页版也没调起来"
            }
        )
    }

    /**
     * 唤起网易云播这首歌，返回走的是哪条路。
     * 网易云的 deep link：orpheus://song/<id>/?autoplay=1（带上 autoplay 才会直接播）
     */
    private fun openNetEase(id: Long): String? {
        val tries = listOf(
            "orpheus://song/$id/?autoplay=1" to "网易云客户端（直接播放）",
            "orpheus://song/$id" to "网易云客户端（歌曲页）",
            "https://music.163.com/song?id=$id" to "网页版（没装客户端）"
        )
        for ((uri, how) in tries) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (uri.startsWith("orpheus://")) setPackage("com.netease.cloudmusic")
                }
                context.startActivity(intent)
                return how
            } catch (e: Exception) {
            }
        }
        return null
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
