package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
    }

    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "setRate($targetHz)")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // 1. Delete conflicting globals FIRST (before setting)
        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))

        // 2. Set secure settings (primary)
        entries.add(RootExecutor.executeWithDebug("miui", "settings put secure miui_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("peak", "settings put secure peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("user", "settings put secure user_refresh_rate $targetHz"))

        // 3. Set system settings
        entries.add(RootExecutor.executeWithDebug("s-mi", "settings put system min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-mx", "settings put system max_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-pk", "settings put system peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-ur", "settings put system user_refresh_rate $targetHz"))

        // 4. Set global bounds
        entries.add(RootExecutor.executeWithDebug("g-mi", "settings put global min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("g-mx", "settings put global max_refresh_rate $targetHz"))

        entries.add(RootExecutor.executeWithDebug("verify", "settings get secure miui_refresh_rate"))
        val ok = entries.any { it.success }
        lastDebugEntries = entries
        ok
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("cur", "settings get secure miui_refresh_rate"))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }
    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }
    fun clearDebug() { lastDebugEntries = emptyList() }
}
