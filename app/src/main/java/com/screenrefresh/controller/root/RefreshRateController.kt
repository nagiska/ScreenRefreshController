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
        // Multiple SurfaceFlinger transaction codes to try
        private val SF_CODES = listOf(1035, 1013, 1004, 1010, 1005)
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

    private val _dumpOutput = MutableStateFlow<String?>(null)
    val dumpOutput: StateFlow<String?> = _dumpOutput

    private val defaultRate = MutableStateFlow(60)
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

    suspend fun runDiagnostic() {
        val script = buildString {
            appendLine("id")
            appendLine("echo ---SU_OK---")
            appendLine("which su")
            appendLine("echo ---WHICH_SU---")
            appendLine("dumpsys display 2>/dev/null | head -200")
            appendLine("echo ---DUMPSYS_END---")
            appendLine("cat /sys/class/graphics/fb0/fps 2>/dev/null || echo no_fb0_fps")
            appendLine("echo ---SYSFS_FPS---")
            appendLine("settings list global | grep -i refresh 2>/dev/null || echo no_global_refresh")
            appendLine("echo ---GLOBAL_REFRESH---")
            appendLine("settings list system | grep -i refresh 2>/dev/null || echo no_system_refresh")
            appendLine("echo ---SYSTEM_REFRESH---")
            appendLine("settings list secure | grep -i refresh 2>/dev/null || echo no_secure_refresh")
        }
        val entry = RootShell.executeCommandWithDebug("diagnostic", script.trimEnd())
        _debugLog.value = listOf(entry)
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

        if (!initialDumpsysChecked) {
            refreshDisplayModes()
            initialDumpsysChecked = true
        }

        val modeId = findModeIdForFps(rate)

        // Phase 1: Combined settings + SF commands
        val cmds = mutableListOf<String>()
        cmds.add("settings put global peak_refresh_rate $rate")
        cmds.add("settings put global user_refresh_rate $rate")
        cmds.add("settings put system min_refresh_rate $rate")
        cmds.add("settings put system peak_refresh_rate $rate")
        cmds.add("settings put system user_refresh_rate $rate")
        cmds.add("settings put secure miui_refresh_rate $rate")

        // Phase 2: Try SurfaceFlinger with modeId first, then raw fps
        if (modeId != null) {
            for (code in SF_CODES) {
                cmds.add("service call SurfaceFlinger $code i32 $modeId")
            }
        }
        for (code in SF_CODES) {
            cmds.add("service call SurfaceFlinger $code i32 $rate")
        }

        // Phase 3: sysfs
        cmds.add("echo $rate > /sys/class/graphics/fb0/fps 2>/dev/null; echo fb0:\$?")
        cmds.add("echo $rate > /sys/devices/virtual/graphics/fb0/fps 2>/dev/null; echo fb0_virt:\$?")

        // Verify
        cmds.add("echo === verify ===")
        cmds.add("settings get global peak_refresh_rate")
        cmds.add("settings get system user_refresh_rate")
        cmds.add("cat /sys/class/graphics/fb0/fps 2>/dev/null || echo no_fb0")

        val fullScript = cmds.joinToString(" && ")
        entries.add(RootShell.executeCommandWithDebug("combined:all", fullScript))

        // Phase 4: Shizuku (shell UID) - try same commands via Shizuku
        if (ShizukuShell.isAvailable()) {
            for (code in SF_CODES) {
                val target = modeId ?: rate
                val cmd = "service call SurfaceFlinger $code i32 $target"
                val result = ShizukuShell.executeCommand(cmd)
                entries.add(RootShell.ShellDebugEntry(
                    method = "shizuku:sf:$code",
                    command = cmd,
                    success = result.success,
                    output = result.output.ifEmpty { "(empty)" },
                    error = result.error.ifEmpty { "(none)" }
                ))
            }
            // Also try settings via Shizuku
            for (key in listOf("system min_refresh_rate", "system peak_refresh_rate",
                               "system user_refresh_rate", "secure miui_refresh_rate",
                               "global peak_refresh_rate", "global user_refresh_rate")) {
                val cmd = "settings put $key $rate"
                val result = ShizukuShell.executeCommand(cmd)
                entries.add(RootShell.ShellDebugEntry(
                    method = "shizuku:put:$key",
                    command = cmd,
                    success = result.success,
                    output = result.output.ifEmpty { "(empty)" },
                    error = result.error.ifEmpty { "(none)" }
                ))
            }
        } else {
            entries.add(RootShell.ShellDebugEntry(
                method = "shizuku",
                command = "Shizuku not available",
                success = false,
                output = "(skipped)",
                error = "Install Shizuku from GitHub"
            ))
        }

        // Phase 5: Individual attempts (for detailed debug)
        entries.add(RootShell.executeCommandWithDebug("put:secure:miui",
            "settings put secure miui_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:user",
            "settings put system user_refresh_rate $rate"))
        entries.add(RootShell.executeCommandWithDebug("put:system:peak",
            "settings put system peak_refresh_rate $rate"))

        if (modeId != null) {
            entries.add(RootShell.executeCommandWithDebug("sf:1035:modeId",
                "service call SurfaceFlinger 1035 i32 $modeId"))
        }
        for (code in SF_CODES) {
            entries.add(RootShell.executeCommandWithDebug("sf:$code:fps",
                "service call SurfaceFlinger $code i32 $rate"))
        }

        for (path in listOf("/sys/class/graphics/fb0/fps", "/sys/devices/virtual/graphics/fb0/fps")) {
            entries.add(RootShell.executeCommandWithDebug("sysfs",
                "echo $rate > $path && cat $path"))
        }

        // Check success
        val anySuccess = entry.output.contains(rate.toString()) ||
            entries.any { it.output.contains(rate.toString()) && it.success }

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
