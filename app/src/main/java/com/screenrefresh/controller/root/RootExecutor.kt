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

    // Method 1: su -c (standard)
    private suspend fun suCmdExec(command: String): Result = withContext(Dispatchers.IO) {
        try {
            val cmd = "exec sh -c '$command'"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exit = process.waitFor()
            Result(exit == 0, out, err, "su-c")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "err", "su-c")
        }
    }

    // Method 2: su pipe (stdin)
    private suspend fun suPipeExec(command: String): Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            stdin.writeBytes("$command\n")
            stdin.writeBytes("exit\n")
            stdin.flush()
            stdin.close()

            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            process.waitFor()
            Result(out.isNotEmpty() || err.isEmpty(), out, err, "su-pipe")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "err", "su-pipe")
        }
    }

    // Method 3: Shizuku
    private suspend fun shizukuExec(command: String): Result = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            val proc = m.invoke(null, arrayOf("sh", "-c", command))
            val pCls = proc::class.java
            val ism = pCls.getDeclaredMethod("getInputStream")
            val esm = pCls.getDeclaredMethod("getErrorStream")
            val wfm = pCls.getDeclaredMethod("waitFor")
            val out = (ism.invoke(proc) as java.io.InputStream).bufferedReader().readText().trim()
            val err = (esm.invoke(proc) as java.io.InputStream).bufferedReader().readText().trim()
            val code = wfm.invoke(proc) as Int
            Result(code == 0, out, err, "shizuku")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "err", "shizuku")
        }
    }

    // Primary: su-c, fallback: su-pipe, last: Shizuku
    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        // Try su -c
        val r1 = suCmdExec(command)
        if (r1.success) return@withContext r1

        // Try su pipe
        val r2 = suPipeExec(command)
        if (r2.success) return@withContext r2

        // Try Shizuku
        if (isShizukuAvailable()) {
            val r3 = shizukuExec(command)
            return@withContext r3
        }

        // Return last failure with error info
        r2
    }

    suspend fun executeWithDebug(label: String, command: String): DebugEntry = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val r = execute(command)
        val elapsed = System.currentTimeMillis() - start
        val out = r.output.ifEmpty { "(empty)" }
        val err = r.error.ifEmpty { "(none)" }
        DebugEntry(label, command, r.success, if (r.success) out else "$out | $err", err, elapsed, r.via)
    }
}
