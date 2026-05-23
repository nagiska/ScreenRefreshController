package com.screenrefresh.controller

import android.app.Application
import com.screenrefresh.controller.data.AppDatabase
import com.screenrefresh.controller.root.RootShell
import com.screenrefresh.controller.service.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScreenRefreshApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsManager: SettingsManager by lazy { SettingsManager(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isRootAvailable = MutableStateFlow<Boolean?>(null)
    val isRootAvailable: StateFlow<Boolean?> = _isRootAvailable

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch {
            _isRootAvailable.value = RootShell.isRootAvailable()
        }
    }

    companion object {
        @Volatile
        private var instance: ScreenRefreshApp? = null

        fun getInstance(): ScreenRefreshApp {
            return instance ?: throw IllegalStateException("App not initialized")
        }
    }
}
