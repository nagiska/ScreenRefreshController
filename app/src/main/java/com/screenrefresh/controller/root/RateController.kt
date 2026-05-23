package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    private var modeMap: Map<Int, Int> = emptyMap()

    // DTBO mapping: display Hz → setting value needed
    private val toSet = mapOf(120 to 120, 132 to 144, 144 to 156, 156 to 165)
    private val fromSet = mapOf(120 to 120, 144 to 132, 156 to 144, 165 to 156)

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        val raw = r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
        fromSet[raw] ?: raw
    }

    suspend fun setRate(displayHz: Int): Boolean = withContext(Dispatchers.IO) {
        val v = toSet[displayHz] ?: displayHz
        Log.d(TAG, "setRate $displayHz → $v")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        if (modeMap.isEmpty()) refreshModeMap()
        val modeId = modeMap[v]

        // Step 1: Delete conflicting globals FIRST
        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-mi", "settings delete global miui_refresh_rate"))

        // Step 2: Secure
        entries.add(RootExecutor.executeWithDebug("sec-miui", "settings put secure miui_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sec-peak", "settings put secure peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sec-user", "settings put secure user_refresh_rate $v"))

        // Step 3: System
        entries.add(RootExecutor.executeWithDebug("sys-min", "settings put system min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-max", "settings put system max_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-peak", "settings put system peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-user", "settings put system user_refresh_rate $v"))

        // Step 4: Global bounds
        entries.add(RootExecutor.executeWithDebug("glb-min", "settings put global min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-max", "settings put global max_refresh_rate $v"))

        // Step 5: SurfaceFlinger
        if (modeId != null)
            entries.add(RootExecutor.executeWithDebug("sf-1035", "service call SurfaceFlinger 1035 i32 $modeId"))

        // Verify
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
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("cur", "settings get secure miui_refresh_rate && settings get secure peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("modes", "dumpsys display 2>/dev/null | grep -iE '(fps=|DisplayMode)' | head -20"))
        lastDebugEntries = entries
    }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
