package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootExecutor {

    private const val TAG = "RootExec"

    data class Result(val success: Boolean, val output: String, val error: String, val via: String)
    data class DebugEntry(
        val method: String,
        val command: String,
        val success: Boolean,
        val output: String,
        val error: String,
        val elapsedMs: Long,
        val via: String = ""
    )

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val r = BufferedReader(InputStreamReader(p.inputStream))
            val line = r.readLine()
            p.waitFor()
            !line.isNullOrBlank()
        } catch (e: Exception) { false }
    }

    suspend fun isShizukuAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("pingBinder")
            m.invoke(null) as? Boolean ?: false
        } catch (e: Exception) { false }
    }

    // Shizuku — runs as shell user (UID 2000), which SurfaceFlinger accepts on Xiaomi
    private suspend fun shizukuExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            val proc = m.invoke(null, arrayOf("sh", "-c", script))
            val pCls = proc::class.java
            val ism = pCls.getDeclaredMethod("getInputStream")
            val esm = pCls.getDeclaredMethod("getErrorStream")
            val `is` = ism.invoke(proc) as java.io.InputStream
            val `es` = esm.invoke(proc) as java.io.InputStream
            val output = `is`.bufferedReader().readText().trim()
            val error  = `es`.bufferedReader().readText().trim()
            val wfm = pCls.getDeclaredMethod("waitFor")
            val exitCode = wfm.invoke(proc) as Int
            Result(exitCode == 0, output, error, "shizuku")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "shizuku error", "shizuku")
        }
    }

    // su — fallback
    private suspend fun suExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            stdin.writeBytes("$script\n")
            stdin.writeBytes("echo __EXIT__$?\n")
            stdin.writeBytes("exit\n")
            stdin.flush()
            stdin.close()

            val output = stdout.readText().trim()
            val error = stderr.readText().trim()
            process.waitFor()

            val exitMarker = output.substringAfterLast("__EXIT__").trim()
            val exitCode = exitMarker.toIntOrNull() ?: 1
            val cleanOutput = output.substringBeforeLast("__EXIT__").trim()

            Result(exitCode == 0, cleanOutput, error, "su")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "su error", "su")
        }
    }

    // Shizuku FIRST, then su fallback
    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        if (isShizukuAvailable()) {
            val r = shizukuExec(command)
            if (r.success || r.error.contains("access denied").not()) return@withContext r
        }
        suExec(command)
    }

    suspend fun executeWithDebug(label: String, command: String): DebugEntry = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = execute(command)
        val elapsed = System.currentTimeMillis() - start
        DebugEntry(label, command, result.success, result.output.ifEmpty { "(empty)" }, result.error.ifEmpty { "(none)" }, elapsed, result.via)
    }
}
