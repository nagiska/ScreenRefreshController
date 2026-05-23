package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    // Display Hz → SurfaceFlinger modeId (from dumpsys)
    var hzToModeId: Map<Int, Int> = emptyMap()
        private set

    /** Scan dumpsys for ALL DisplayModeRecords, build Hz→modeId map */
    suspend fun scanAllModes() = withContext(Dispatchers.IO) {
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // Try multiple grep patterns to catch mode records
        for (grep in listOf(
            "dumpsys display 2>/dev/null | grep -E '(DisplayModeRecord|id=.*fps=)'",
            "dumpsys display 2>/dev/null | grep -B1 'fps='",
            "dumpsys display 2>/dev/null | grep -E '(fps=[0-9]|DisplayMode)'"
        )) {
            val r = RootExecutor.execute(grep)
            if (!r.success || r.output.isBlank()) continue

            val pat = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
            val map = mutableMapOf<Int, Int>()
            r.output.lines().forEach { line ->
                val f = pat.find(line) ?: return@forEach
                val id = f.groupValues[1].toIntOrNull() ?: return@forEach
                val fps = f.groupValues[4].toFloatOrNull()?.toInt() ?: return@forEach
                if (fps in 30..300) map[fps] = id
            }
            if (map.isNotEmpty()) {
                hzToModeId = map
                entries.add(RootExecutor.executeWithDebug("mode-ids", "echo $map"))
                Log.d(TAG, "Found mode IDs: $map")
                break
            }
        }
        lastDebugEntries = entries
    }

    suspend fun getCurrentRate(): Int = withContext(Dispatchers.IO) {
        val r = RootExecutor.execute("settings get secure miui_refresh_rate 2>/dev/null || echo 0")
        r.output.trim().toFloatOrNull()?.toInt()?.takeIf { it in 30..300 } ?: 120
    }

    /** Works like the reference APK: settings put + service call SurfaceFlinger */
    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "setRate($targetHz)")
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        // Scan modes if needed
        if (hzToModeId.isEmpty()) scanAllModes()
        val modeId = hzToModeId[targetHz]

        // 1. Delete conflicting globals
        entries.add(RootExecutor.executeWithDebug("del-pk", "settings delete global peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("del-ur", "settings delete global user_refresh_rate"))

        // 2. Settings put (like reference APK)
        entries.add(RootExecutor.executeWithDebug("miui",  "settings put secure miui_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("peak",  "settings put secure peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("user",  "settings put secure user_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-min", "settings put system min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-max", "settings put system max_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-pk",  "settings put system peak_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("s-ur",  "settings put system user_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("g-min", "settings put global min_refresh_rate $targetHz"))
        entries.add(RootExecutor.executeWithDebug("g-max", "settings put global max_refresh_rate $targetHz"))

        // 3. SurfaceFlinger with modeId (like reference APK)
        if (modeId != null) {
            entries.add(RootExecutor.executeWithDebug("sf-1035",
                "service call SurfaceFlinger 1035 i32 $modeId"))
        } else {
            entries.add(RootExecutor.executeWithDebug("sf-missing",
                "echo 'modeId not found for ${targetHz}Hz in dumpsys'"))
        }

        // 4. Verify
        entries.add(RootExecutor.executeWithDebug("verify",
            "settings get secure miui_refresh_rate"))

        val ok = entries.any { it.success && it.output.contains(targetHz.toString()) }
        lastDebugEntries = entries
        ok
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        entries.add(RootExecutor.executeWithDebug("whoami", "id"))
        entries.add(RootExecutor.executeWithDebug("dumpsys-modes",
            "dumpsys display 2>/dev/null | grep -E '(DisplayModeRecord|id=.*fps=)'"))
        entries.add(RootExecutor.executeWithDebug("cur-miui",
            "settings get secure miui_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("cur-peak",
            "settings get secure peak_refresh_rate"))
        entries.add(RootExecutor.executeWithDebug("mode-map",
            "echo $hzToModeId"))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        RootExecutor.execute("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
