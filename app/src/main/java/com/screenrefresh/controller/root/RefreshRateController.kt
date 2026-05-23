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
            "settings get global oneplus_screen_refresh_rate",
            "settings get global miui_refresh_rate"
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

    suspend fun tryAllMethods(rate: Int): List<RootShell.ShellDebugEntry> {
        val entries = mutableListOf<RootShell.ShellDebugEntry>()
        Log.d(TAG, "=== Trying all methods to set $rate Hz ===")

        val settingsCommands = listOf(
            Triple("global", "peak_refresh_rate", "$rate"),
            Triple("global", "user_refresh_rate", "$rate"),
            Triple("global", "min_refresh_rate", "$rate"),
            Triple("global", "max_refresh_rate", "$rate"),
            Triple("global", "fps_constraint", "${rate}.0"),
            Triple("global", "oneplus_screen_refresh_rate", "$rate"),
            Triple("global", "miui_refresh_rate", "$rate"),
            Triple("system", "peak_refresh_rate", "$rate"),
            Triple("system", "user_refresh_rate", "$rate"),
            Triple("system", "min_refresh_rate", "$rate"),
            Triple("system", "max_refresh_rate", "$rate"),
            Triple("system", "fps_constraint", "${rate}.0"),
            Triple("secure", "peak_refresh_rate", "$rate"),
            Triple("secure", "user_refresh_rate", "$rate"),
        )

        for ((scope, key, value) in settingsCommands) {
            val cmd = "settings put $scope $key $value"
            entries.add(RootShell.executeCommandWithDebug("settings $scope $key", cmd))
        }

        val sysfsPaths = listOf(
            "/sys/class/graphics/fb0/fps",
            "/sys/devices/virtual/graphics/fb0/fps",
            "/sys/class/graphics/fb1/fps",
            "/sys/class/drm/card0-DSI-1/status",
            "/sys/class/drm/card0/crtc-0/active",
            "/sys/devices/platform/boe_fb.0/fps",
            "/sys/devices/platform/mtk_fb.0/fps",
            "/sys/devices/platform/display/fps",
            "/sys/kernel/debug/disp/fps",
            "/proc/graphics/fps"
        )

        for (path in sysfsPaths) {
            val cmd = "echo $rate > $path 2>/dev/null && cat $path 2>/dev/null || echo 'FAIL'"
            entries.add(RootShell.executeCommandWithDebug("sysfs $path", cmd))
        }

        val sfMethods = listOf(
            "service call SurfaceFlinger 1035 i32 $rate",
            "service call SurfaceFlinger 1036 i32 $rate",
            "service call SurfaceFlinger 1037 i32 $rate",
            "service call SurfaceFlinger 1002 i32 $rate"
        )
        for (cmd in sfMethods) {
            entries.add(RootShell.executeCommandWithDebug("SurfaceFlinger", cmd))
        }

        // setprop approach
        entries.add(RootShell.executeCommandWithDebug(
            "setprop", "setprop debug.egl.swapinterval $rate"
        ))

        // Try reading back the current rate
        val readCmds = listOf(
            "settings get global peak_refresh_rate",
            "settings get global user_refresh_rate",
            "dumpsys display | grep -i 'refreshRate' | head -5",
            "dumpsys display | grep -i 'modeId' | head -3"
        )
        for (cmd in readCmds) {
            entries.add(RootShell.executeCommandWithDebug("readback", cmd))
        }

        _debugLog.value = entries
        return entries
    }

    suspend fun setRefreshRate(rate: Int): Boolean {
        Log.d(TAG, "Setting refresh rate to ${rate}Hz")

        tryAllMethods(rate)

        val verify = RootShell.executeCommand(
            "settings get global peak_refresh_rate 2>/dev/null || " +
            "settings get global user_refresh_rate 2>/dev/null || echo 60"
        )
        val currentSetting = verify.output.trim().toFloatOrNull()?.toInt() ?: 60

        if (currentSetting == rate) {
            _currentRate.value = rate
            _lastError.value = null
            Log.d(TAG, "Verified: refresh rate set to ${rate}Hz")
            return true
        }

        _lastError.value = "Set to $rate but read back $currentSetting"
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

    fun clearDebugLog() {
        _debugLog.value = emptyList()
    }

    fun stopStepping() {
        steppingJob?.cancel()
        steppingJob = null
        _isStepping.value = false
    }
}
