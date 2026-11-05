package com.moto.files.data

import android.content.Context
import android.content.SharedPreferences
import com.moto.files.model.Settings
import com.moto.files.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted app settings with a reactive stream so the theme/switches apply instantly. */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val flow = MutableStateFlow(load())

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, _ -> flow.value = load() }
    }

    fun observe(): Flow<Settings> = flow.asStateFlow()

    fun load(): Settings = Settings(
        theme = ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!),
        gridColumns = prefs.getInt("cols", 4),
        showHidden = prefs.getBoolean("hidden", false),
        binDays = prefs.getInt("bin_days", 30),
        sharePort = prefs.getInt("port", 8080)
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putInt("cols", s.gridColumns)
            .putBoolean("hidden", s.showHidden)
            .putInt("bin_days", s.binDays)
            .putInt("port", s.sharePort)
            .apply()
        flow.value = s
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
