package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RefreshRateController {

    companion object {
        private const val TAG = "RefreshRateController"
        private val SAFE_KEYS = listOf(
            // All non-sysfs keys tried in order
            Triple("global", "peak_refresh_rate", false),
            Triple("global", "user_refresh_rate", false),
            Triple("system", "peak_refresh_rate", false),
            Triple("system", "user_refresh_rate", false),
            Triple("secure", "peak_refresh_rate", false),
            Triple("secure", "user_refresh_rate", false),
            Triple("global", "min_refresh_rate", false),
            Triple("global", "max_refresh_rate", false),
            Triple("global", "oneplus_screen_refresh_rate", false),
            Triple("global", "miui_refresh_rate", false),
            Triple("system", "min_refresh_rate", false),
            Triple("system", "max_refresh_rate", false),
        )
        private val SF_METHODS = listOf(1035, 1002, 1036, 1037)
    }

    private var steppingJob: Job? = null

    private val _currentRate = MutableStateFlow(60)
    val currentRate: StateFlow<Int> = _currentRate

    private val _isStepping = MutableStateFlow(false)
    val isStepping: StateFlow<Boolean> = _isStepping

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _debugLog = MutableStateFlow<List<RootShell.ShellDebugEntry>>(emptyList())
    val debugLog: StateFlow<List<RootShell.ShellDebugEntry>> = _debugLog

    private val defaultRate = MutableStateFlow(60)

    // Which method succeeded - saved for reuse
    private var preferredMethod: String? = null

    suspend fun initDefaultRate() {
        val readCmds = listOf(
            "settings get global user_refresh_rate",
            "settings get global peak_refresh_rate",
            "settings get system peak_refresh_rate",
            "settings get global oneplus_screen_refresh_rate",
            "settings get global miui_refresh_rate"
        )
        for (cmd in readCmds) {
            val result = RootShell.executeCommand("$cmd 2>/dev/null || true")
            val rate = result.output.trim().toFloatOrNull()?.toInt()
            if (rate != null && rate > 0) {
                defaultRate.value = rate
                _currentRate.value = rate
                return
            }
        }
        defaultRate.value = 60
        _currentRate.value = 60
    }

    private suspend fun getCurrentSettingRate(): Int {
        val cmds = listOf(
            "settings get global user_refresh_rate",
            "settings get global peak_refresh_rate",
            "settings get system peak_refresh_rate"
        )
        for (cmd in cmds) {
            val r = RootShell.executeCommand("$cmd 2>/dev/null || true")
            val rate = r.output.trim().toFloatOrNull()?.toInt()
            if (rate != null && rate > 0) return rate
        }
        return 0
    }

    suspend fun setRefreshRate(rate: Int): Boolean {
        Log.d(TAG, "Setting refresh rate to ${rate}Hz")
        val entries = mutableListOf<RootShell.ShellDebugEntry>()

        // 1. Try preferred method first (if we found one before)
        if (preferredMethod != null) {
            val parts = preferredMethod!!.split("|")
            if (parts.size == 2) {
                val cmd = parts[1].replace("{rate}", rate.toString())
                val entry = RootShell.executeCommandWithDebug("preferred", cmd)
                entries.add(entry)
                if (entry.success) {
                    val actual = getCurrentSettingRate()
                    if (actual == rate) {
                        _currentRate.value = rate
                        _lastError.value = null
                        _debugLog.value = entries
                        return true
                    }
                }
            }
        }

        // 2. Try all settings keys
        for ((scope, key, _) in SAFE_KEYS) {
            val cmd = "settings put $scope $key $rate"
            val entry = RootShell.executeCommandWithDebug("$scope.$key", cmd)
            entries.add(entry)
        }

        val actualAfterSettings = getCurrentSettingRate()
        if (actualAfterSettings == rate) {
            _currentRate.value = rate
            _lastError.value = null
            _debugLog.value = entries
            return true
        }

        // 3. Try SurfaceFlinger service calls
        for (code in SF_METHODS) {
            val cmd = "service call SurfaceFlinger $code i32 $rate"
            val entry = RootShell.executeCommandWithDebug("sf::$code", cmd)
            entries.add(entry)
        }

        val actualAfterSf = getCurrentSettingRate()
        if (actualAfterSf == rate) {
            _currentRate.value = rate
            _lastError.value = null
            preferredMethod = "sf|service call SurfaceFlinger 1035 i32 {rate}"
            _debugLog.value = entries
            return true
        }

        // 4. Read back final state
        entries.add(RootShell.executeCommandWithDebug("read_final",
            "settings get global peak_refresh_rate && settings get global user_refresh_rate && dumpsys display | grep -i refreshRate | head -3"))

        _lastError.value = "Tried all methods, rate stayed at $actualAfterSf"
        _debugLog.value = entries
        return false
    }

    suspend fun resetToDefault() {
        val def = defaultRate.value
        if (def > 0) setRefreshRate(def)
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
                if (rate != targetRates.last()) delay(stepIntervalMs)
            }

            _isStepping.value = false
            onComplete()
        }
    }

    fun clearPreferredMethod() { preferredMethod = null }

    fun clearDebugLog() { _debugLog.value = emptyList() }

    fun stopStepping() {
        steppingJob?.cancel()
        steppingJob = null
        _isStepping.value = false
    }
}
