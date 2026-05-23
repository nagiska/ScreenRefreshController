package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    // DTBO mapping: display Hz → setting value needed
    private val map = mapOf(120 to 120, 132 to 144, 144 to 156, 156 to 165)
    private val rev = mapOf(120 to 120, 144 to 132, 156 to 144, 165 to 156)

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        val raw = r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
        rev[raw] ?: raw
    }

    suspend fun setRate(displayHz: Int): Boolean = withContext(Dispatchers.IO) {
        val v = map[displayHz] ?: displayHz
        Log.d(TAG, "setRate $displayHz → $v")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        entries.add(RootExecutor.executeWithDebug("miui", "settings put secure miui_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("peak", "settings put secure peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("user", "settings put secure user_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-pk", "settings put system peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-min", "settings put system min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-max", "settings put system max_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-min", "settings put global min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-max", "settings put global max_refresh_rate $v"))

        // Also delete conflicting globals
        RootExecutor.execute("settings delete global peak_refresh_rate")
        RootExecutor.execute("settings delete global user_refresh_rate")

        var ok = false
        for (e in entries) if (e.success) ok = true
        lastDebugEntries = entries
        ok
    }

    suspend fun resetTo120() { setRate(120) }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("cur-secure", "settings get secure miui_refresh_rate && settings get secure peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("modes", "dumpsys display 2>/dev/null | grep -E '(fps=|DisplayMode)' | head -20"))
        lastDebugEntries = entries
    }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
