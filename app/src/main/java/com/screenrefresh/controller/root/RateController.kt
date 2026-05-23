package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"

    private var cachedModes: List<DisplayMode> = emptyList()
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    suspend fun refreshModes() {
        cachedModes = DisplayModes.scanModes()
        Log.d(TAG, "cached ${cachedModes.size} modes: ${cachedModes.map { it.fps }}")
    }

    suspend fun getModeInfo(): List<DisplayMode> = cachedModes

    suspend fun setRate(rate: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== setRate($rate) ===")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        if (cachedModes.isEmpty()) cachedModes = DisplayModes.scanModes()
        val modeId = DisplayModes.findModeId(cachedModes, rate)

        // Build script with semicolons so one failure doesn't block the rest
        val script = buildString {
            appendLine("settings put global peak_refresh_rate $rate")
            appendLine("settings put global user_refresh_rate $rate")
            appendLine("settings put system min_refresh_rate $rate")
            appendLine("settings put system peak_refresh_rate $rate")
            appendLine("settings put system user_refresh_rate $rate")
            appendLine("settings put secure miui_refresh_rate $rate")
            val target = modeId ?: rate
            appendLine("service call SurfaceFlinger 1035 i32 $target")
            appendLine("echo $rate > /sys/class/graphics/fb0/fps 2>/dev/null; echo fb0=\$?")
            appendLine("echo $rate > /sys/devices/virtual/graphics/fb0/fps 2>/dev/null; echo fb0v=\$?")
            appendLine("echo ===VERIFY===")
            appendLine("settings get global peak_refresh_rate")
            appendLine("settings get secure miui_refresh_rate")
            appendLine("settings get system user_refresh_rate")
            appendLine("cat /sys/class/graphics/fb0/fps 2>/dev/null || echo no_fb0")
            appendLine("echo ===DUMP_MODES===")
            appendLine("dumpsys display 2>/dev/null | grep 'DisplayModeRecord'")
        }
        entries.add(RootExecutor.executeWithDebug("combined", script.trimIndent()))

        // Individual attempts for detailed logging
        entries.add(RootExecutor.executeWithDebug("miui", "settings put secure miui_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys:user", "settings put system user_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys:peak", "settings put system peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys:min", "settings put system min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("glb:peak", "settings put global peak_refresh_rate $rate"))
        val target = modeId ?: rate
        entries.add(RootExecutor.executeWithDebug("sf:1035", "service call SurfaceFlinger 1035 i32 $target"))
        entries.add(RootExecutor.executeWithDebug("fb0", "echo $rate > /sys/class/graphics/fb0/fps && cat /sys/class/graphics/fb0/fps"))

        // Success check
        val anyOk = entries.any { it.success && (it.output.contains(rate.toString()) || it.output.trim().toIntOrNull() == rate) }
        lastDebugEntries = entries
        if (!anyOk) Log.w(TAG, "All methods failed for $rate Hz")
        anyOk
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-full", "dumpsys display 2>/dev/null"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-modes", "dumpsys display 2>/dev/null | grep 'DisplayModeRecord'"))
        entries.add(RootExecutor.executeWithDebug("fb0-fps", "cat /sys/class/graphics/fb0/fps 2>/dev/null || echo NOT_FOUND"))
        entries.add(RootExecutor.executeWithDebug("set-global", "settings list global 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("set-system", "settings list system 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("set-secure", "settings list secure 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("test-put-sys", "settings put system user_refresh_rate 120 && settings get system user_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("test-sf-120", "service call SurfaceFlinger 1035 i32 2"))
        entries.add(RootExecutor.executeWithDebug("which-su", "which su"))
        lastDebugEntries = entries
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
