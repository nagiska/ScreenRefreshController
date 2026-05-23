package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    private var modeMap: Map<Int, Int> = emptyMap()

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        val raw = r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
        when (raw) {
            144 -> 132
            156 -> 144
            165 -> 156
            166 -> 165
            else -> raw
        }
    }

    suspend fun setRate(displayHz: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "setRate($displayHz)")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // Determine what settings value to use
        val v = when (displayHz) {
            120 -> 120
            132 -> 144
            144 -> 156
            156 -> 165
            165 -> 166   // separate from 156's 165
            else -> displayHz
        }

        if (modeMap.isEmpty()) refreshModeMap()
        val modeId = modeMap[displayHz]

        // Delete conflicting globals first
        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))

        // Set settings
        entries.add(RootExecutor.executeWithDebug("miui", "settings put secure miui_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("peak", "settings put secure peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("user", "settings put secure user_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-pk", "settings put system peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-mi", "settings put system min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-mx", "settings put system max_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-ur", "settings put system user_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-mi", "settings put global min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-mx", "settings put global max_refresh_rate $v"))

        // SurfaceFlinger with modeId if available
        if (modeId != null) {
            entries.add(RootExecutor.executeWithDebug("sf-1035", "service call SurfaceFlinger 1035 i32 $modeId"))
        }

        entries.add(RootExecutor.executeWithDebug("verify", "settings get secure miui_refresh_rate"))
        val ok = entries.any { it.success && it.output.contains(v.toString()) }
        lastDebugEntries = entries
        ok
    }

    suspend fun refreshModeMap() = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("dumpsys display 2>/dev/null | grep 'DisplayModeRecord'")
        if (!r.success || r.output.isBlank()) return@withContext
        val pat = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        val m = mutableMapOf<Int, Int>()
        r.output.lines().forEach { line ->
            val f = pat.find(line) ?: return@forEach
            val id = f.groupValues[1].toIntOrNull() ?: return@forEach
            val fps = f.groupValues[4].toFloatOrNull()?.toInt() ?: return@forEach
            if (fps in 30..300) m[fps] = id
        }
        if (m.isNotEmpty()) { modeMap = m; Log.d(TAG, "modeMap: $m") }
    }

    suspend fun resetTo120() { setRate(120) }
    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("cur", "settings get secure miui_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("modes", "dumpsys display 2>/dev/null | grep -iE '(fps=|DisplayMode)' | head -20"))
        lastDebugEntries = entries
    }
    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }
    fun clearDebug() { lastDebugEntries = emptyList() }
}
