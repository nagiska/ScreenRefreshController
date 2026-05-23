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

    suspend fun initDefaultRate() {
        val cmds = listOf(
            "settings get global user_refresh_rate",
            "settings get global peak_refresh_rate",
            "settings get system peak_refresh_rate",
        )
        for (cmd in cmds) {
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

    suspend fun setRefreshRate(rate: Int): Boolean {
        Log.d(TAG, "=== setRefreshRate($rate) ===")
        val entries = mutableListOf<RootShell.ShellDebugEntry>()
        var success = false

        // Phase 1: settings put (safe, works on most devices)
        entries.add(RootShell.executeCommandWithDebug("put:global:peak",
            "settings put global peak_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:global:user",
            "settings put global user_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:peak",
            "settings put system peak_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:user",
            "settings put system user_refresh_rate $rate"))

        // Phase 2: sysfs fb0/fps (for DTBO-overclocked devices like Xiaomi)
        val fpsPaths = listOf(
            "/sys/class/graphics/fb0/fps",
            "/sys/devices/virtual/graphics/fb0/fps"
        )
        for (path in fpsPaths) {
            val cmd = "echo $rate > $path && cat $path"
            val entry = RootShell.executeCommandWithDebug("sysfs:fps", cmd)
            entries.add(entry)
            if (entry.success && entry.output.trim() == rate.toString()) {
                success = true
            }
        }

        // Phase 3: SurfaceFlinger service calls
        entries.add(RootShell.executeCommandWithDebug("sf:1035",
            "service call SurfaceFlinger 1035 i32 $rate"))

        // Verify: read back current rate
        val verifyEntry = RootShell.executeCommandWithDebug("verify",
            "settings get global peak_refresh_rate && " +
            "cat /sys/class/graphics/fb0/fps 2>/dev/null || true")
        entries.add(verifyEntry)

        if (success) {
            _currentRate.value = rate
            _lastError.value = null
        } else {
            _lastError.value = "All methods attempted for $rate Hz"
        }

        _debugLog.value = entries
        return success
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

    fun clearDebugLog() { _debugLog.value = emptyList() }
    fun stopStepping() {
        steppingJob?.cancel()
        steppingJob = null
        _isStepping.value = false
    }
}
