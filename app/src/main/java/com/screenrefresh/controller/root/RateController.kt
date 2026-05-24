package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RateController {

    private const val TAG = "RateCtrl"
    var lastDebugEntries: List<RootExecutor.DebugEntry> = emptyList()
        private set

    private var hzToModeId: Map<Int, Int> = emptyMap()

    fun getAvailableRates(): List<Int> = hzToModeId.keys.sorted()

    suspend fun scanModes() = withContext(Dispatchers.IO) {
        val result = suExec("dumpsys display | grep 'DisplayModeRecord'")
        val pattern = Regex("""id=(\d+),\s*width=(\d+),\s*height=(\d+),\s*fps=([\d.]+)""")
        val map = mutableMapOf<Int, Int>()
        result.output.lines().forEach { line ->
            pattern.find(line)?.let { m ->
                val id  = m.groupValues[1].toIntOrNull() ?: return@let
                val fps = m.groupValues[4].toFloatOrNull()?.toInt() ?: return@let
                if (fps in 30..300) map[fps] = id
            }
        }
        hzToModeId = map
        Log.d(TAG, "modeMap: $map")
    }

    suspend fun getCurrentRate(ctx: android.content.Context): Int {
        return try {
            val dm = ctx.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            dm.getDisplay(0)?.refreshRate?.toInt() ?: 120
        } catch (_: Exception) { 120 }
    }

    /** Execute via su pipe — works for dumpsys, uname, etc. */
    suspend fun suExec(command: String): RootExecutor.Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin  = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
            stdin.writeBytes("$command\nexit\n")
            stdin.flush(); stdin.close()
            val out = stdout.readText().trim()
            val err = stderr.readText().trim()
            process.waitFor()
            RootExecutor.Result(out.isNotEmpty() || err.isEmpty(), out, err, "su")
        } catch (e: Exception) {
            RootExecutor.Result(false, "", e.message ?: "err", "su")
        }
    }

    /** Execute via Shizuku (shell UID) — works for settings put on Xiaomi */
    private suspend fun shizukuExec(command: String): RootExecutor.Result = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            val proc = m.invoke(null, arrayOf("sh", "-c", command))
            val pCls = proc::class.java
            val out = (pCls.getDeclaredMethod("getInputStream").invoke(proc) as java.io.InputStream)
                .bufferedReader().readText().trim()
            val err = (pCls.getDeclaredMethod("getErrorStream").invoke(proc) as java.io.InputStream)
                .bufferedReader().readText().trim()
            val code = pCls.getDeclaredMethod("waitFor").invoke(proc) as Int
            RootExecutor.Result(code == 0, out, err, "shizuku")
        } catch (e: Exception) {
            RootExecutor.Result(false, "", e.message ?: "err", "shizuku")
        }
    }

    private suspend fun isShizukuAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("pingBinder")
            m.invoke(null) as? Boolean ?: false
        } catch (_: Exception) { false }
    }

    /** Set refresh rate — tries Shizuku first (works on Xiaomi KSU), su as fallback */
    suspend fun setRate(targetHz: Int): Boolean = withContext(Dispatchers.IO) {
        val modeId = hzToModeId[targetHz]
        val entries = mutableListOf<RootExecutor.DebugEntry>()

        val script = buildString {
            if (modeId != null) {
                appendLine("service call SurfaceFlinger 1035 i32 ${modeId - 1}")
            }
            appendLine("settings put system peak_refresh_rate ${targetHz}.0")
            appendLine("settings put system min_refresh_rate ${targetHz}.0")
            appendLine("settings put system user_refresh_rate $targetHz")
            appendLine("settings put secure miui_refresh_rate $targetHz")
        }

        // Try Shizuku first (works on Xiaomi with KSU), fallback to su
        val useShizuku = isShizukuAvailable()
        val result = if (useShizuku) shizukuExec(script.trimEnd()) else suExec(script.trimEnd())

        entries.add(RootExecutor.DebugEntry(
            "setRate", script.replace("\n", " → ").take(200),
            result.success, result.output.ifEmpty { "(empty)" },
            result.error.ifEmpty { "(none)" }, 0, if (useShizuku) "shizuku" else "su"
        ))
        lastDebugEntries = entries
        result.success
    }

    suspend fun runDiagnostic() {
        val entries = mutableListOf<RootExecutor.DebugEntry>()
        scanModes()
        entries.add(RootExecutor.DebugEntry("modes", "dumpsys scan", hzToModeId.isNotEmpty(),
            hzToModeId.toString().ifEmpty { "(empty)" }, "", 0, ""))
        entries.add(RootExecutor.DebugEntry("shizuku", "available", isShizukuAvailable(),
            if (isShizukuAvailable()) "YES" else "NO", "", 0, ""))
        lastDebugEntries = entries
    }

    suspend fun resetTo120() { setRate(120) }

    suspend fun getKernelVersion(): String = withContext(Dispatchers.IO) {
        suExec("uname -r").output.trim().ifEmpty { "unknown" }
    }

    fun clearDebug() { lastDebugEntries = emptyList() }
}
