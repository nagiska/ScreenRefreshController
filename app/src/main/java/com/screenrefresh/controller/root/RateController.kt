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
        when (raw) { 144 -> 132; 156 -> 144; 165 -> 156; else -> raw }
    }

    suspend fun setRate(displayHz: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "setRate($displayHz)")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // 165Hz: DTBO max mode, only reachable via SurfaceFlinger modeId
        if (displayHz == 165) {
            if (modeMap.isEmpty()) {
                entries.add(RootExecutor.executeWithDebug("scan-dumpsys", "dumpsys display 2>/dev/null | grep 'DisplayModeRecord'"))
                refreshModeMap()
            }
            val modeId = modeMap[165] ?: modeMap.entries.maxByOrNull { it.key }?.value
            if (modeId != null) {
                // Try multiple SF codes
                for (code in listOf(1035, 1013, 1004)) {
                    entries.add(RootExecutor.executeWithDebug("sf-$code-165",
                        "service call SurfaceFlinger $code i32 $modeId"))
                }
            }
            // Also try settings as fallback
            entries.add(RootExecutor.executeWithDebug("miui-raw", "settings put secure miui_refresh_rate 165"))
            entries.add(RootExecutor.executeWithDebug("peak-raw", "settings put secure peak_refresh_rate 165"))
            lastDebugEntries = entries
            return@withContext entries.any { it.success }
        }

        // Other rates: use settings with DTBO offset mapping
        val v = when (displayHz) {
            120 -> 120; 132 -> 144; 144 -> 156; 156 -> 165; else -> displayHz
        }

        if (modeMap.isEmpty()) refreshModeMap()
        val modeId = modeMap[v]

        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))

        entries.add(RootExecutor.executeWithDebug("sec-miui", "settings put secure miui_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sec-peak", "settings put secure peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sec-user", "settings put secure user_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-min", "settings put system min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-max", "settings put system max_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-peak", "settings put system peak_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("sys-user", "settings put system user_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-min", "settings put global min_refresh_rate $v"))
        entries.add(RootExecutor.executeWithDebug("glb-max", "settings put global max_refresh_rate $v"))

        if (modeId != null)
            entries.add(RootExecutor.executeWithDebug("sf-1035", "service call SurfaceFlinger 1035 i32 $modeId"))

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
