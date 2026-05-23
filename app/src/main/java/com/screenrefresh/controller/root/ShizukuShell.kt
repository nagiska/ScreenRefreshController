package com.screenrefresh.controller.root

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object ShizukuShell {

    private const val TAG = "ShizukuShell"

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val clz = Class.forName("rikka.shizuku.Shizuku")
            val method = clz.getDeclaredMethod("pingBinder")
            method.invoke(null) as? Boolean ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku not available", e)
            false
        }
    }

    suspend fun executeCommand(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", Array<String>::class.java)
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command))

            val processClass = process.javaClass
            val inputStreamMethod = processClass.getDeclaredMethod("getInputStream")
            val inputStream = inputStreamMethod.invoke(process) as InputStream
            val output = inputStream.bufferedReader().readText().trim()

            val waitForMethod = processClass.getDeclaredMethod("waitFor")
            val exitCode = (waitForMethod.invoke(process) as Int)

            ShellResult(exitCode == 0, output, "")
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku exec failed: $command", e)
            ShellResult(false, "", e.message ?: "error")
        }
    }

    suspend fun execTransaction(serviceName: String, code: Int, dataBuilder: (Parcel) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            getServiceMethod.isAccessible = true
            val binder = getServiceMethod.invoke(null, serviceName) as IBinder
            if (binder == null || !binder.pingBinder()) return@withContext false

            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            dataBuilder(data)
            val result = binder.transact(code, data, reply, 0)
            data.recycle()
            reply.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Transaction failed: $serviceName code=$code", e)
            false
        }
    }
}
