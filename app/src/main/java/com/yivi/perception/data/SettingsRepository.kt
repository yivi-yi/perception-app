package com.yivi.perception.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("perception_settings", Context.MODE_PRIVATE)

    private val _accent = MutableStateFlow(prefs.getString("accent", "pink") ?: "pink")
    val accent: StateFlow<String> get() = _accent

    private val _dark = MutableStateFlow(prefs.getBoolean("dark", true))
    val dark: StateFlow<Boolean> get() = _dark

    private val _bgUri = MutableStateFlow(prefs.getString("bgUri", "") ?: "")
    val bgUri: StateFlow<String> get() = _bgUri

    private val _annivText = MutableStateFlow(prefs.getString("annivText", "纪念日") ?: "纪念日")
    val annivText: StateFlow<String> get() = _annivText

    private val _annivType = MutableStateFlow(prefs.getString("annivType", "正数") ?: "正数")
    val annivType: StateFlow<String> get() = _annivType

    private val _annivDate = MutableStateFlow(prefs.getLong("annivDate", 0L))
    val annivDate: StateFlow<Long> get() = _annivDate

    private val _bootStart = MutableStateFlow(prefs.getBoolean("bootStart", true))
    val bootStart: StateFlow<Boolean> get() = _bootStart

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> get() = _logs

    fun setAccent(v: String) { _accent.value = v; prefs.edit().putString("accent", v).apply() }
    fun setDark(v: Boolean) { _dark.value = v; prefs.edit().putBoolean("dark", v).apply() }
    fun setBgUri(v: String) { _bgUri.value = v; prefs.edit().putString("bgUri", v).apply() }
    fun setAnnivText(v: String) { _annivText.value = v; prefs.edit().putString("annivText", v).apply() }
    fun setAnnivType(v: String) { _annivType.value = v; prefs.edit().putString("annivType", v).apply() }
    fun setAnnivDate(v: Long) { _annivDate.value = v; prefs.edit().putLong("annivDate", v).apply() }
    fun setBootStart(v: Boolean) { _bootStart.value = v; prefs.edit().putBoolean("bootStart", v).apply() }

    /** 恢复出厂：设置项全回到默认（壁纸文件由界面那边删） */
    fun clearAll() {
        prefs.edit().clear().apply()
        _accent.value = "pink"
        _dark.value = true
        _bgUri.value = ""
        _annivText.value = "纪念日"
        _annivType.value = "正数"
        _annivDate.value = 0L
        _bootStart.value = true
        _logs.value = emptyList()
    }

    fun addLog(line: String) {
        val s = "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}  $line"
        _logs.value = (_logs.value + s).takeLast(200)
    }
}
