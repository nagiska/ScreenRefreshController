package com.screenrefresh.controller.service

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var stepIntervalMs: Long
        get() = prefs.getLong(KEY_STEP_INTERVAL, DEFAULT_STEP_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_STEP_INTERVAL, value).apply()

    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var resetOnExit: Boolean
        get() = prefs.getBoolean(KEY_RESET_ON_EXIT, true)
        set(value) = prefs.edit().putBoolean(KEY_RESET_ON_EXIT, value).apply()

    var defaultRefreshRate: Int
        get() = prefs.getInt(KEY_DEFAULT_RATE, 60)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_RATE, value).apply()

    companion object {
        private const val PREFS_NAME = "screen_refresh_settings"
        private const val KEY_STEP_INTERVAL = "step_interval_ms"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_RESET_ON_EXIT = "reset_on_exit"
        private const val KEY_DEFAULT_RATE = "default_rate"
        const val DEFAULT_STEP_INTERVAL = 3000L
    }
}
