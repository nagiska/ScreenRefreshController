package com.screenrefresh.controller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.screenrefresh.controller.root.DeviceConfig
import com.screenrefresh.controller.root.RefreshRateController
import com.screenrefresh.controller.root.StepProfiles
import com.screenrefresh.controller.service.AppDetectionAccessibilityService
import com.screenrefresh.controller.ui.screens.DashboardScreen
import com.screenrefresh.controller.ui.screens.SettingsScreen
import com.screenrefresh.controller.ui.screens.WhitelistScreen
import com.screenrefresh.controller.ui.theme.ScreenRefreshTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app by lazy { application as ScreenRefreshApp }
    private val refreshController = RefreshRateController()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScreenRefreshTheme {
                MainContent(
                    app = app,
                    refreshController = refreshController,
                    onToggleService = { toggleAccessibilityService() }
                )
            }
        }

        scope.launch {
            refreshController.initDefaultRate()
        }
    }

    private fun toggleAccessibilityService() {
        if (AppDetectionAccessibilityService.isRunning.value) {
            stopService(Intent(this, AppDetectionAccessibilityService::class.java))
        } else {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}

@Composable
private fun MainContent(
    app: ScreenRefreshApp,
    refreshController: RefreshRateController,
    onToggleService: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val isServiceRunning by AppDetectionAccessibilityService.isRunning.collectAsState()
    val isRootAvailable by app.isRootAvailable.collectAsState()
    val debugEntries by refreshController.debugLog.collectAsState()

    val whitelistItems by app.database.whitelistDao().getAllFlow().collectAsState(initial = emptyList())
    var supportedRates by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentRate by remember { mutableIntStateOf(60) }
    var currentProfileName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val config = withContext(Dispatchers.IO) {
            DeviceConfig.detectRefreshRates(app)
        }
        val profile = StepProfiles.getById(app.settingsManager.selectedProfileId)
        currentProfileName = profile.name
        supportedRates = StepProfiles.getAvailableRates(profile, config.supportedRates)
        currentRate = config.currentRate.toInt()
    }

    LaunchedEffect(Unit) {
        refreshController.currentRate.collect { rate ->
            currentRate = rate
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("白名单") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    isServiceRunning = isServiceRunning,
                    isRootAvailable = isRootAvailable,
                    currentRate = currentRate,
                    isStepping = false,
                    supportedRates = supportedRates,
                    profileName = currentProfileName,
                    debugEntries = debugEntries,
                    onToggleService = onToggleService,
                    onManualSetRate = { rate ->
                        CoroutineScope(Dispatchers.IO).launch {
                            refreshController.setRefreshRate(rate)
                        }
                    },
                    onClearDebug = { refreshController.clearDebugLog() }
                )
                1 -> WhitelistScreen(
                    whitelist = whitelistItems,
                    onAdd = { entity ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.database.whitelistDao().insert(entity)
                        }
                    },
                    onRemove = { entity ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.database.whitelistDao().delete(entity)
                        }
                    }
                )
                2 -> SettingsScreen(
                    settingsManager = app.settingsManager
                )
            }
        }
    }
}
