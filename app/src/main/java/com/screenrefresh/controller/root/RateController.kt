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

        // Step 1: Clear global conflicting settings (null might not work, use delete)
        entries.add(RootExecutor.executeWithDebug("delete-glb-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("delete-glb-ur", "settings delete global user_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("delete-glb-mi", "settings delete global miui_refresh_rate"))

        // Step 2: Set secure miui_refresh_rate (primary key that working APK uses)
        entries.add(RootExecutor.executeWithDebug("miui-refresh", "settings put secure miui_refresh_rate $rate"))

        // Step 3: Min/max bounds - must allow the target rate
        entries.add(RootExecutor.executeWithDebug("sec-peak", "settings put secure peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sec-user", "settings put secure user_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-min", "settings put system min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-max", "settings put system max_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-peak", "settings put system peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-user", "settings put system user_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("glb-min", "settings put global min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("glb-max", "settings put global max_refresh_rate $rate"))

        // Step 5: SurfaceFlinger with mode ID (if we have it)
        val target = modeId ?: rate
        entries.add(RootExecutor.executeWithDebug("sf-1035", "service call SurfaceFlinger 1035 i32 $target"))

        // Step 6: Verify
        val verify = buildString {
            appendLine("settings get secure miui_refresh_rate")
            appendLine("settings get secure peak_refresh_rate")
            appendLine("settings get global peak_refresh_rate")
        }
        entries.add(RootExecutor.executeWithDebug("verify", verify.trimEnd()))

        val anyOk = entries.any { it.success && it.output.contains(rate.toString()) }
        lastDebugEntries = entries
        if (!anyOk) Log.w(TAG, "All methods failed for $rate Hz")
        anyOk
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-modes", "dumpsys display 2>/dev/null | grep -A2 'DisplayModeRecord'"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-rate", "dumpsys display 2>/dev/null | grep -i -E '(mDesired|mActive|refreshRate|fps=)'"))
        entries.add(RootExecutor.executeWithDebug("curr-settings", "settings get secure miui_refresh_rate && settings get secure peak_refresh_rate && settings get global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("list-global", "settings list global 2>/dev/null | grep -i ref | head -10"))
        entries.add(RootExecutor.executeWithDebug("list-secure", "settings list secure 2>/dev/null | grep -i ref | head -10"))
        entries.add(RootExecutor.executeWithDebug("list-system", "settings list system 2>/dev/null | grep -i ref | head -10"))
        lastDebugEntries = entries
    }

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val keys = listOf(
            "settings get secure miui_refresh_rate",
            "settings get secure user_refresh_rate",
            "settings get secure peak_refresh_rate",
        )
        for (cmd in keys) {
            val r = RootExecutor.execute("$cmd 2>/dev/null || echo 0")
            val rate = r.output.trim().toFloatOrNull()?.toInt()
            if (rate != null && rate in 30..300) return@withContext rate
        }
        120
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
