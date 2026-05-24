package com.screenrefresh.controller.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.screenrefresh.controller.R
import com.screenrefresh.controller.ScreenRefreshApp
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.Stepper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppDetectionService : AccessibilityService() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stepperJob: Job? = null
    private var daemonJob: Job? = null
    private var currentPkg: String = ""
    private var steppingActive = false

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        createNotificationChannel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        try { startForeground(1, buildNotification("监控中")) }
        catch (_: Exception) {}
        scope.launch { RateController.scanModes() }
        startDaemon()
    }

    private fun startDaemon() {
        daemonJob?.cancel()
        daemonJob = scope.launch {
            while (isActive) {
                val pkg = getForegroundPackage()
                if (pkg != null && pkg != currentPkg) {
                    currentPkg = pkg
                    notify(1, buildNotification("监控: $pkg"))
                    checkApp(pkg)
                }
                delay(1500)
            }
        }
    }

    private suspend fun getForegroundPackage(): String? {
        try {
            val result = RateController.suExec("dumpsys activity activities 2>/dev/null | grep mResumedActivity")
            // Output: "mResumedActivity: ActivityRecord{... u0 com.example.app/.MainActivity t123}"
            val line = result.output.trim()
            val match = Regex("""u0\s+([\w.]+)/""").find(line)
            return match?.groupValues?.get(1)
        } catch (_: Exception) { return null }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun checkApp(pkg: String) {
        scope.launch {
            try {
                val entity = ScreenRefreshApp.instance.db.whitelistDao().getByPackage(pkg)
                if (entity != null) {
                    notify(1, buildNotification("${entity.appName} → ${entity.targetRate}Hz 步进中"))
                    startStepping(entity.targetRate)
                } else {
                    stopStepping()
                    RateController.resetTo120()
                    notify(1, buildNotification("监控中"))
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun startStepping(targetRate: Int) {
        stepperJob?.cancel()
        steppingActive = true
        val chain = Stepper.getChain(targetRate)
        stepperJob = scope.launch {
            var step = 0
            try {
                for (rate in chain) {
                    RateController.setRate(rate)
                    step++
                    if (rate != chain.last()) {
                        val remaining = chain.size - step
                        notify(1, buildNotification("${currentPkg} → ${rate}Hz (${remaining}步剩余)"))
                        delay(2000)
                    }
                }
                notify(1, buildNotification("${targetRate}Hz 已就绪"))
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (e: Exception) {
                notify(1, buildNotification("步进失败: ${e.message}"))
            } finally { steppingActive = false }
        }
    }

    private fun stopStepping() {
        stepperJob?.cancel()
        stepperJob = null
        steppingActive = false
    }

    override fun onDestroy() {
        daemonJob?.cancel()
        stopStepping()
        scope.launch { RateController.resetTo120() }
        _isRunning.value = false
        super.onDestroy()
    }

    private fun notify(id: Int, n: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel("rate_service", "刷新率监控", NotificationManager.IMPORTANCE_LOW)
            c.description = "监控白名单应用并自动调节刷新率"
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = android.app.PendingIntent.getActivity(this, 0,
            Intent(this, com.screenrefresh.controller.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, "rate_service").setContentTitle("刷新率控制")
                .setContentText(text).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi).setOngoing(true).build()
        else @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("刷新率控制")
                .setContentText(text).setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi).setOngoing(true).build()
    }
}
