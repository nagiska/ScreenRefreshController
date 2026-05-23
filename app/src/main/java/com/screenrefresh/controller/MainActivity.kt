package com.screenrefresh.controller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import com.screenrefresh.controller.service.AppDetectionService
import com.screenrefresh.controller.ui.screens.DashboardScreen
import com.screenrefresh.controller.ui.screens.MiuiBlue
import com.screenrefresh.controller.ui.screens.MiuiBg
import com.screenrefresh.controller.ui.screens.MiuiLightScheme
import com.screenrefresh.controller.ui.screens.WhitelistScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MiuiLightScheme) {
                Surface(Modifier.fillMaxSize(), color = MiuiBg) { MainContent() }
            }
        }
    }
}

@Composable
fun MainContent() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var currentRate by remember { mutableIntStateOf(120) }
    var kernelVer by remember { mutableStateOf("loading...") }
    val isServiceRunning by AppDetectionService.isRunning.collectAsState()

    LaunchedEffect(Unit) {
        currentRate = RateController.getCurrentRate()
        kernelVer = RateController.getKernelVersion()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, "主页") },
                    label = { Text("主页") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, "白名单") },
                    label = { Text("刷新率设置") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    currentRate = currentRate,
                    kernelVersion = kernelVer,
                    isServiceRunning = isServiceRunning,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                1 -> WhitelistScreen()
            }
        }
    }
}
