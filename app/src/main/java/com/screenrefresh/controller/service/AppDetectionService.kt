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
    private var lastPkg = ""

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel("rate_service", "刷新率监控", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isRunning.value = true
        try { startForeground(1, notif("监控中")) } catch (_: Exception) {}
        scope.launch { RateController.scanModes() }
        daemonJob = scope.launch {
            while (isActive) {
                val pkg = getForegroundPackage()
                if (pkg != null && pkg != lastPkg) {
                    lastPkg = pkg
                    val entity = ScreenRefreshApp.instance.db.whitelistDao().getByPackage(pkg)
                    if (entity != null) {
                        notify(1, notif("${entity.appName} → ${entity.targetRate}Hz"))
                        stepTo(entity.targetRate)
                    } else {
                        stepperJob?.cancel()
                        RateController.resetTo120()
                        notify(1, notif("监控中"))
                    }
                }
                delay(2000)
            }
        }
    }

    private suspend fun getForegroundPackage(): String? {
        val r = RateController.suExec("dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|mFocusedActivity' | head -1")
        // Try multiple regex patterns
        for (pat in listOf(
            Regex("""u0\s+([\w.]+)/"""),       // u0 com.app/.Activity
            Regex("""\{[^}]*\s+([\w.]+)/"""), // {... com.app/...}
            Regex("""([\w.]+)/\."""),          // com.app/.
        )) {
            val m = pat.find(r.output)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    private suspend fun stepTo(targetHz: Int) {
        stepperJob?.cancel()
        val chain = Stepper.getChain(targetHz)
        stepperJob = scope.launch {
            try {
                for (rate in chain) {
                    RateController.setRate(rate)
                    if (rate != chain.last()) delay(2000)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {}
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        daemonJob?.cancel()
        stepperJob?.cancel()
        scope.launch { RateController.resetTo120() }
        _isRunning.value = false
        super.onDestroy()
    }

    private fun notify(id: Int, n: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(id, n)
    }

    private fun notif(text: String): Notification {
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
