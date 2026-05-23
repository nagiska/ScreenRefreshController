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
    private var cachedModes: List<DisplayModeInfo> = emptyList()

    private val _currentRate = MutableStateFlow(60)
    val currentRate: StateFlow<Int> = _currentRate

    private val _isStepping = MutableStateFlow(false)
    val isStepping: StateFlow<Boolean> = _isStepping

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _debugLog = MutableStateFlow<List<RootShell.ShellDebugEntry>>(emptyList())
    val debugLog: StateFlow<List<RootShell.ShellDebugEntry>> = _debugLog

    private val _availableModes = MutableStateFlow<List<DisplayModeInfo>>(emptyList())
    val availableModes: StateFlow<List<DisplayModeInfo>> = _availableModes

    private val defaultRate = MutableStateFlow(60)

    // Initial state
    private var initialDumpsysChecked = false

    suspend fun initDefaultRate() {
        val cmds = listOf(
            "settings get global user_refresh_rate",
            "settings get global peak_refresh_rate",
            "settings get system peak_refresh_rate",
        )
        for (cmd in cmds) {
            val result = RootShell.executeCommand("$cmd 2>/dev/null || echo 0")
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

    suspend fun refreshDisplayModes() {
        val modes = DeviceConfig.scanDisplayModesFromDumpsys()
        if (modes.isNotEmpty()) {
            cachedModes = modes
            _availableModes.value = modes
            Log.d(TAG, "Found ${modes.size} display modes: ${modes.map { it.fps }}")
        } else {
            Log.d(TAG, "No display modes found from dumpsys")
        }
    }

    private fun findModeIdForFps(targetFps: Int): Int? {
        return cachedModes.firstOrNull { it.fps == targetFps }?.id
    }

    suspend fun setRefreshRate(rate: Int): Boolean {
        Log.d(TAG, "=== setRefreshRate($rate) ===")
        val entries = mutableListOf<RootShell.ShellDebugEntry>()

        // Refresh display modes if not yet checked
        if (!initialDumpsysChecked) {
            refreshDisplayModes()
            initialDumpsysChecked = true
        }

        val modeId = findModeIdForFps(rate)
        if (modeId != null) {
            Log.d(TAG, "Found modeId=$modeId for ${rate}Hz")
        } else {
            Log.d(TAG, "No modeId for ${rate}Hz, cached fps: ${cachedModes.map { it.fps }}")
        }

        // Build combined script: all commands in ONE su session (like the working APK)
        val cmds = mutableListOf<String>()

        // Settings keys
        cmds.add("settings put global peak_refresh_rate $rate")
        cmds.add("settings put global user_refresh_rate $rate")
        cmds.add("settings put system min_refresh_rate $rate")
        cmds.add("settings put system peak_refresh_rate $rate")
        cmds.add("settings put system user_refresh_rate $rate")
        cmds.add("settings put secure miui_refresh_rate $rate")

        // SurfaceFlinger with modeId (if found)
        if (modeId != null) {
            cmds.add("service call SurfaceFlinger 1035 i32 $modeId")
        } else {
            cmds.add("service call SurfaceFlinger 1035 i32 $rate")
        }

        // sysfs fb0/fps
        cmds.add("echo $rate > /sys/class/graphics/fb0/fps 2>/dev/null; echo \$?")
        cmds.add("echo $rate > /sys/devices/virtual/graphics/fb0/fps 2>/dev/null; echo \$?")

        // Verify
        val verifyCmds = listOf(
            "echo === verify ===",
            "settings get global peak_refresh_rate",
            "settings get system user_refresh_rate",
            "cat /sys/class/graphics/fb0/fps 2>/dev/null || echo not_found",
            "settings get secure miui_refresh_rate 2>/dev/null || echo not_found",
        )

        val fullScript = (cmds + verifyCmds).joinToString(" && ")
        val entry = RootShell.executeCommandWithDebug("combined:all", fullScript)
        entries.add(entry)

        // Also try individual attempts for detailed debug
        entries.add(RootShell.executeCommandWithDebug("put:secure:miui",
            "settings put secure miui_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:user",
            "settings put system user_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:peak",
            "settings put system peak_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:min",
            "settings put system min_refresh_rate $rate"))

        if (modeId != null) {
            entries.add(RootShell.executeCommandWithDebug("sf:1035:modeId",
                "service call SurfaceFlinger 1035 i32 $modeId"))
        } else {
            entries.add(RootShell.executeCommandWithDebug("sf:1035:fps",
                "service call SurfaceFlinger 1035 i32 $rate"))
        }

        val fpsPaths = listOf(
            "/sys/class/graphics/fb0/fps",
            "/sys/devices/virtual/graphics/fb0/fps"
        )
        for (path in fpsPaths) {
            entries.add(RootShell.executeCommandWithDebug("sysfs:fps",
                "echo $rate > $path && cat $path"))
        }

        // Check if any method succeeded
        val anySuccess = entry.output.contains(rate.toString()) ||
            entries.any { it.output.trim() == rate.toString() }

        if (anySuccess) {
            _currentRate.value = rate
            _lastError.value = null
        } else {
            _lastError.value = "All methods failed for $rate Hz"
        }

        _debugLog.value = entries
        return anySuccess
    }

    suspend fun resetToDefault() {
        val def = defaultRate.value
        if (def > 0) setRefreshRate(def)
    }

    fun startStepping(
        scope: CoroutineScope,
        availableRates: List<Int>,
        stepIntervalMs: Long = 2000L,
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
    }
}
