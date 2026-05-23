package com.screenrefresh.controller.service

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var stepIntervalMs: Long
        get() = prefs.getLong(KEY_STEP_INTERVAL, DEFAULT_STEP_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_STEP_INTERVAL, value).apply()

    var resetOnExit: Boolean
        get() = prefs.getBoolean(KEY_RESET_ON_EXIT, true)
        set(value) = prefs.edit().putBoolean(KEY_RESET_ON_EXIT, value).apply()

    var selectedProfileId: String
        get() = prefs.getString(KEY_PROFILE, "120_144") ?: "120_144"
        set(value) = prefs.edit().putString(KEY_PROFILE, value).apply()

    var customRates: List<Int>
        get() {
            val str = prefs.getString(KEY_CUSTOM_RATES, "120,144,165") ?: "120,144,165"
            return str.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_RATES, value.joinToString(",")).apply()
        }

    companion object {
        private const val PREFS_NAME = "screen_refresh_settings"
        private const val KEY_STEP_INTERVAL = "step_interval_ms"
        private const val KEY_RESET_ON_EXIT = "reset_on_exit"
        private const val KEY_PROFILE = "selected_profile"
        private const val KEY_CUSTOM_RATES = "custom_rates"
        const val DEFAULT_STEP_INTERVAL = 3000L
    }
}
