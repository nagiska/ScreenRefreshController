package com.screenrefresh.controller.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootExecutor {

    private const val TAG = "RootExec"

    data class Result(val success: Boolean, val output: String, val error: String)
    data class DebugEntry(
        val method: String,
        val command: String,
        val success: Boolean,
        val output: String,
        val error: String,
        val elapsedMs: Long
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

    // Execute via persistent su shell (write to stdin)
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
            val exitCode = exitMarker.toIntOrNull() ?: -1
            val cleanOutput = output.substringBeforeLast("__EXIT__").trim()

            Result(exitCode == 0, cleanOutput, error)
        } catch (e: Exception) {
            Result(false, "", e.message ?: "su error")
        }
    }

    // Execute via Shizuku
    private suspend fun shizukuExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            val proc = m.invoke(null, arrayOf("sh", "-c", script))
            val pCls = proc::class.java
            val ism = pCls.getDeclaredMethod("getInputStream")
            val `is` = ism.invoke(proc) as java.io.InputStream
            val output = `is`.bufferedReader().readText().trim()
            val wfm = pCls.getDeclaredMethod("waitFor")
            val exitCode = wfm.invoke(proc) as Int
            Result(exitCode == 0, output, "")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "shizuku error")
        }
    }

    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        val result = suExec(command)
        if (result.success) return@withContext result
        if (isShizukuAvailable()) shizukuExec(command) else result
    }

    suspend fun executeWithDebug(label: String, command: String): DebugEntry = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = execute(command)
        val elapsed = System.currentTimeMillis() - start
        DebugEntry(label, command, result.success, result.output.ifEmpty { "(empty)" }, result.error.ifEmpty { "(none)" }, elapsed)
    }
}
