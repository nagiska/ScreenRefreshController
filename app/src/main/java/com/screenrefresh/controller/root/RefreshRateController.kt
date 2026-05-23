package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class RefreshRateController {

    companion object {
        private const val TAG = "RefreshRateController"
    }

    private var steppingJob: Job? = null

    private val _currentRate = MutableStateFlow(60)
    val currentRate: StateFlow<Int> = _currentRate

    private val _isStepping = MutableStateFlow(false)
    val isStepping: StateFlow<Boolean> = _isStepping

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val defaultRate = MutableStateFlow(60)

    // All rate-setting methods tried in order
    private enum class SetMethod { SETTINGS_GLOBAL, SETTINGS_SYSTEM, SYSFS, SURFACEFLINGER }

    suspend fun initDefaultRate() {
        val result = RootShell.executeCommand("settings get global user_refresh_rate 2>/dev/null || settings get global peak_refresh_rate 2>/dev/null || echo 60")
        val rate = result.output.trim().toFloatOrNull()?.toInt() ?: 60
        defaultRate.value = rate
        _currentRate.value = rate
    }

    suspend fun setRefreshRate(rate: Int): Boolean {
        Log.d(TAG, "Setting refresh rate to ${rate}Hz")

        val commands = listOf(
            "settings put global peak_refresh_rate $rate",
            "settings put global user_refresh_rate $rate",
            "settings put system peak_refresh_rate $rate",
            "settings put system user_refresh_rate $rate"
        )

        val results = commands.map { RootShell.executeCommand(it) }
        val allOk = results.any { it.success }

        if (allOk) {
            _currentRate.value = rate
            _lastError.value = null
            Log.d(TAG, "Successfully set refresh rate to ${rate}Hz")
        } else {
            val sysfsOk = trySysfsWrite(rate)
            if (sysfsOk) {
                _currentRate.value = rate
                _lastError.value = null
                return true
            }
            _lastError.value = "Failed to set refresh rate"
            Log.e(TAG, "All methods failed to set refresh rate to ${rate}Hz")
        }

        return allOk
    }

    private suspend fun trySysfsWrite(rate: Int): Boolean {
        val paths = listOf(
            "/sys/class/graphics/fb0/fps",
            "/sys/devices/virtual/graphics/fb0/fps",
            "/sys/class/drm/card0-DSI-1/status"
        )
        for (path in paths) {
            val cmd = "echo $rate > $path 2>/dev/null && cat $path"
            val result = RootShell.executeCommand(cmd)
            if (result.success) {
                val value = result.output.trim().toIntOrNull()
                if (value != null && value > 0) return true
            }
        }
        return false
    }

    suspend fun resetToDefault() {
        val def = defaultRate.value
        if (def > 0) {
            setRefreshRate(def)
        }
    }

    fun startStepping(
        scope: CoroutineScope,
        availableRates: List<Int>,
        stepIntervalMs: Long = 3000L,
        onStep: (Int) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        stopStepping()
        _isStepping.value = true

        steppingJob = scope.launch {
            val startRate = _currentRate.value
            val sortedRates = availableRates.sorted()
            val startIndex = sortedRates.indexOfFirst { it >= startRate }.coerceAtLeast(0)
            val targetRates = sortedRates.drop(startIndex)

            for (rate in targetRates) {
                if (!isActive) break
                if (rate <= startRate) continue

                val success = setRefreshRate(rate)
                if (!success) {
                    val err = "Failed to set $rate Hz"
                    _lastError.value = err
                    onError(err)
                    break
                }

                onStep(rate)

                if (rate != targetRates.last()) {
                    delay(stepIntervalMs)
                }
            }

            _isStepping.value = false
            onComplete()
        }
    }

    fun stopStepping() {
        steppingJob?.cancel()
        steppingJob = null
        _isStepping.value = false
    }
}
