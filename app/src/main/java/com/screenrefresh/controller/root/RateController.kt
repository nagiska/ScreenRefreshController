package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    var hzToModeId: Map<Int, Int> = emptyMap()
        private set

    /** Scan dumpsys for modeId→Hz mapping. Xiaomi format: "modeId N renderFrameRate X.Y" */
    suspend fun scanAllModes() = withContext(Dispatchers.IO) {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        val raw = RootExecutor.execute("dumpsys display 2>/dev/null | grep -E 'modeId|DisplayModeRecord|renderFrameRate|supportedModes'")
        entries.add(RootExecutor.executeWithDebug("dumpsys-filtered", raw.output.ifBlank { "(empty)" }))

        val map = mutableMapOf<Int, Int>()
        // Xiaomi/HyperOS: "modeId N ... renderFrameRate X.Y"
        val pat1 = Regex("""modeId\s+(\d+).*renderFrameRate\s+([\d.]+)""")
        // AOSP: "id=N, width=W, height=H, fps=X.Y"  
        val pat2 = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")

        raw.output.lines().forEach { line ->
            pat1.find(line)?.let { m ->
                val id = m.groupValues[1].toIntOrNull() ?: return@forEach
                val hz = m.groupValues[2].toFloatOrNull()?.toInt() ?: return@forEach
                if (hz in 30..300) map[hz] = id
            }
        }
        if (map.isEmpty()) {
            raw.output.lines().forEach { line ->
                pat2.find(line)?.let { m ->
                    val id = m.groupValues[1].toIntOrNull() ?: return@forEach
                    val hz = m.groupValues[4].toFloatOrNull()?.toInt() ?: return@forEach
                    if (hz in 30..300) map[hz] = id
                }
            }
        }
        if (map.isNotEmpty()) { hzToModeId = map; entries.add(RootExecutor.executeWithDebug("mode-ok", "map=$map")) }
        else entries.add(RootExecutor.executeWithDebug("mode-fail", "pattern mismatch"))
        lastDebugEntries = entries
    }

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
    }

    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "setRate($targetHz)")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        if (hzToModeId.isEmpty()) scanAllModes()
        val modeId = hzToModeId[targetHz]

        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))

        entries.add(RootExecutor.executeWithDebug("miui",  "settings put secure miui_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("peak",  "settings put secure peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("user",  "settings put secure user_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-min", "settings put system min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-max", "settings put system max_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-pk",  "settings put system peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-ur",  "settings put system user_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("g-min", "settings put global min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("g-max", "settings put global max_refresh_rate $targetHz"))

        if (modeId != null) {
            entries.add(RootExecutor.executeWithDebug("sf",
                "service call SurfaceFlinger 1035 i32 0 i32 $modeId"))
        } else {
            entries.add(RootExecutor.executeWithDebug("sf-skip",
                "echo 'no modeId for ${targetHz}Hz; try 132-144-156-165 as mode IDs'"))
            // Try targeting Hz as potential modeId
            entries.add(RootExecutor.executeWithDebug("sf-guess",
                "service call SurfaceFlinger 1035 i32 0 i32 $targetHz"))
        }

        entries.add(RootExecutor.executeWithDebug("verify", "settings get secure miui_refresh_rate"))
        val ok = entries.any { it.success && it.output.contains(targetHz.toString()) }
        lastDebugEntries = entries
        ok
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("cur-miui", "settings get secure miui_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-modes",
            "dumpsys display 2>/dev/null | grep -E 'modeId|DisplayModeRecord|renderFrameRate'"))
        entries.add(RootExecutor.executeWithDebug("mode-map", "echo $hzToModeId"))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }
    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }
    fun clearDebug() { lastDebugEntries = emptyList() }
}
