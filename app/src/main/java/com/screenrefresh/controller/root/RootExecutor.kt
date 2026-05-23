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

    // Method 1: su -c (traditional, works on most Magisk)
    private suspend fun suCmdExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exit = process.waitFor()
            Result(exit == 0, out, err, "su-c")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "su-c err", "su-c")
        }
    }

    // Method 2: sh (shell) via su, script on stdin
    private suspend fun suPipeExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val stdin = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            stdin.writeBytes("$script\n")
            stdin.writeBytes("echo ___EXIT___\$?\n")
            stdin.writeBytes("exit\n")
            stdin.flush()
            stdin.close()

            val output = StringBuilder()
            var line: String?
            while (stdout.readLine().also { line = it } != null) output.appendLine(line)
            val error = stderr.readText().trim()
            process.waitFor()

            val raw = output.toString().trim()
            val marker = "___EXIT___"
            val exitIdx = raw.lastIndexOf(marker)
            val exitCode = if (exitIdx >= 0) {
                raw.substring(exitIdx + marker.length).trim().toIntOrNull() ?: -1
            } else -1
            val clean = if (exitIdx >= 0) raw.substring(0, exitIdx).trim() else raw
            Result(exitCode == 0, clean, error, "su-pipe")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "su-pipe err", "su-pipe")
        }
    }

    // Shizuku fallback
    private suspend fun shizukuExec(script: String): Result = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val m = clz.getDeclaredMethod("newProcess", Array<String>::class.java)
            val proc = m.invoke(null, arrayOf("sh", "-c", script))
            val pCls = proc::class.java
            val ism = pCls.getDeclaredMethod("getInputStream")
            val esm = pCls.getDeclaredMethod("getErrorStream")
            val out = (ism.invoke(proc) as java.io.InputStream).bufferedReader().readText().trim()
            val err = (esm.invoke(proc) as java.io.InputStream).bufferedReader().readText().trim()
            val wfm = pCls.getDeclaredMethod("waitFor")
            val exitCode = wfm.invoke(proc) as Int
            Result(exitCode == 0, out, err, "shizuku")
        } catch (e: Exception) {
            Result(false, "", e.message ?: "shizuku err", "shizuku")
        }
    }

    // Execute: try su-c → su-pipe → shizuku
    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        val r1 = suCmdExec(command)
        if (r1.success) return@withContext r1

        val r2 = suPipeExec(command)
        if (r2.success) return@withContext r2

        if (isShizukuAvailable()) shizukuExec(command)
        else r2
    }

    suspend fun executeWithDebug(label: String, command: String): DebugEntry = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = execute(command)
        val elapsed = System.currentTimeMillis() - start
        DebugEntry(label, command, result.success, result.output.ifEmpty { "(empty)" }, result.error.ifEmpty { "(none)" }, elapsed, result.via)
    }
}
