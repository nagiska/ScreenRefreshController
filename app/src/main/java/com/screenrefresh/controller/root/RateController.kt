package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    private val SF_CODES = listOf(1035, 1013, 1004, 1010, 1005)

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

        if (cachedModes.isEmpty()) {
            cachedModes = DisplayModes.scanModes()
        }

        val modeId = DisplayModes.findModeId(cachedModes, rate)

        // === Combined script ===
        val script = buildString {
            appendLine("settings put global peak_refresh_rate $rate")
            appendLine("settings put global user_refresh_rate $rate")
            appendLine("settings put system min_refresh_rate $rate")
            appendLine("settings put system peak_refresh_rate $rate")
            appendLine("settings put system user_refresh_rate $rate")
            appendLine("settings put secure miui_refresh_rate $rate")

            if (modeId != null) {
                for (code in SF_CODES) {
                    appendLine("service call SurfaceFlinger $code i32 $modeId")
                }
            }
            for (code in SF_CODES) {
                appendLine("service call SurfaceFlinger $code i32 $rate")
            }

            appendLine("echo $rate > /sys/class/graphics/fb0/fps")
            appendLine("echo $rate > /sys/devices/virtual/graphics/fb0/fps")

            appendLine("echo === verify ===")
            appendLine("settings get global peak_refresh_rate")
            appendLine("settings get system user_refresh_rate")
            appendLine("settings get secure miui_refresh_rate")
            appendLine("cat /sys/class/graphics/fb0/fps")
        }

        entries.add(RootExecutor.executeWithDebug("combined:all", script.trimEnd()))

        // === Individual (debug) ===
        entries.add(RootExecutor.executeWithDebug("put:secure:miui", "settings put secure miui_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("put:system:user", "settings put system user_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("put:system:peak", "settings put system peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("put:system:min", "settings put system min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("put:global:peak", "settings put global peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("put:global:user", "settings put global user_refresh_rate $rate"))

        if (modeId != null) {
            entries.add(RootExecutor.executeWithDebug("sf:1035:modeId", "service call SurfaceFlinger 1035 i32 $modeId"))
        }
        for (code in SF_CODES) {
            entries.add(RootExecutor.executeWithDebug("sf:$code:fps", "service call SurfaceFlinger $code i32 $rate"))
        }

        entries.add(RootExecutor.executeWithDebug("sysfs:fb0", "echo $rate > /sys/class/graphics/fb0/fps && cat /sys/class/graphics/fb0/fps"))

        // === Check success ===
        val combinedOk = entries.firstOrNull()?.let {
            it.success && it.output.contains(rate.toString())
        } ?: false
        val individualOk = entries.any { it.output.trim().toIntOrNull() == rate }

        lastDebugEntries = entries
        val ok = combinedOk || individualOk
        if (!ok) Log.w(TAG, "All methods failed for $rate Hz")
        ok
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("diag:id", "id"))
        entries.add(RootExecutor.executeWithDebug("diag:su", "echo SU_OK"))
        entries.add(RootExecutor.executeWithDebug("diag:dumpsys", "dumpsys display 2>/dev/null | head -100"))
        entries.add(RootExecutor.executeWithDebug("diag:fb0", "cat /sys/class/graphics/fb0/fps"))
        entries.add(RootExecutor.executeWithDebug("diag:global", "settings list global 2>/dev/null | grep -i refresh"))
        entries.add(RootExecutor.executeWithDebug("diag:system", "settings list system 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("diag:secure", "settings list secure 2>/dev/null | grep -i refresh"))
        entries.add(RootExecutor.executeWithDebug("diag:which", "which su"))
        lastDebugEntries = entries
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
