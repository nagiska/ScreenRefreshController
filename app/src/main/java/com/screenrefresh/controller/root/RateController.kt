package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    private var modeMap: Map<Int, Int> = emptyMap()

    // DTBO overclock mapping: display Hz → actual setting value needed
    private val displayToSetting = mapOf(
        120 to 120, 132 to 144, 144 to 156, 156 to 165, 165 to 165
    )
    private val settingToDisplay = mapOf(
        120 to 120, 144 to 132, 156 to 144, 165 to 156
    )

    suspend fun refreshModeMap() = withContext(Dispatchers.IO) {
        // Get full dumpsys, grep for mode records
        val r = RootExecutor.execute("dumpsys display 2>/dev/null | grep 'DisplayModeRecord'")
        if (!r.success || r.output.isBlank()) return@withContext

        val pattern = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        val map = mutableMapOf<Int, Int>()
        for (line in r.output.lines()) {
            val m = pattern.find(line) ?: continue
            val id = m.groupValues[1].toIntOrNull() ?: continue
            val fps = m.groupValues[4].toFloatOrNull()?.toInt() ?: continue
            if (fps in 30..300) map[fps] = id
        }
        if (map.isNotEmpty()) {
            modeMap = map
            Log.d(TAG, "modeMap: $map")
        }
    }

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        val raw = r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
        settingToDisplay[raw] ?: raw
    }

    suspend fun setRate(displayRate: Int): Boolean = withContext(Dispatchers.IO) {
        val rate = displayToSetting[displayRate] ?: displayRate
        Log.d(TAG, "=== setRate(display=${displayRate} → setting=${rate}) ===")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // Refresh mode map if empty
        if (modeMap.isEmpty()) refreshModeMap()
        val modeId = modeMap[rate]

        // Step 1: Delete conflicting global settings
        entries.add(RootExecutor.executeWithDebug("del-glb-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-glb-ur", "settings delete global user_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-glb-mi", "settings delete global miui_refresh_rate"))

        // Step 2: Secure settings (primary)
        entries.add(RootExecutor.executeWithDebug("secure-miui", "settings put secure miui_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("secure-peak", "settings put secure peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("secure-user", "settings put secure user_refresh_rate $rate"))

        // Step 3: System settings
        entries.add(RootExecutor.executeWithDebug("sys-min", "settings put system min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-max", "settings put system max_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-peak", "settings put system peak_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("sys-user", "settings put system user_refresh_rate $rate"))

        // Step 4: Global bounds
        entries.add(RootExecutor.executeWithDebug("glb-min", "settings put global min_refresh_rate $rate"))
        entries.add(RootExecutor.executeWithDebug("glb-max", "settings put global max_refresh_rate $rate"))

        // Step 5: SurfaceFlinger with modeId (crucial for DTBO oc)
        if (modeId != null) {
            entries.add(RootExecutor.executeWithDebug("sf-modeId", "service call SurfaceFlinger 1035 i32 $modeId"))
        } else {
            entries.add(RootExecutor.executeWithDebug("sf-raw", "service call SurfaceFlinger 1035 i32 $rate"))
        }

        // Verify
        entries.add(RootExecutor.executeWithDebug("verify", "settings get secure miui_refresh_rate && settings get secure peak_refresh_rate"))

        val anyOk = entries.any { it.success && it.output.contains(rate.toString()) }
        lastDebugEntries = entries
        if (!anyOk) Log.w(TAG, "All methods failed for $rate Hz")
        anyOk
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-modes", "dumpsys display 2>/dev/null | grep -A3 'DisplayModeRecord'"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-rate", "dumpsys display 2>/dev/null | grep -E '(mDesired|mActive|fps=|refreshRate)' | head -20"))
        entries.add(RootExecutor.executeWithDebug("curr-secure", "settings get secure miui_refresh_rate && settings get secure peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("curr-system", "settings get system min_refresh_rate && settings get system peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("list-global-r", "settings list global 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("list-secure-r", "settings list secure 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("list-system-r", "settings list system 2>/dev/null | grep -i ref"))
        entries.add(RootExecutor.executeWithDebug("modeMap", "echo $modeMap"))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() {
        setRate(120)
    }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("uname -r")
        r.output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
