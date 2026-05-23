package com.screenrefresh.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.root.RateController
import com.screenrefresh.controller.root.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

private val PROFILE_RATES = listOf(120, 132, 144, 156)
private val SET_OFFSET = mapOf(120 to 120, 132 to 144, 144 to 156, 156 to 165)
private val READ_REVERSE = mapOf(120 to 120, 144 to 132, 156 to 144, 165 to 156)

@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    var currentRate by remember { mutableIntStateOf(120) }
    var debug by remember { mutableStateOf<List<RootExecutor.DebugEntry>>(emptyList()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var shizukuOk by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = RootExecutor.isRootAvailable()
        shizukuOk = RootExecutor.isShizukuAvailable()
        val raw = RateController.getCurrentRate()
        currentRate = READ_REVERSE[raw] ?: raw
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header: current rate
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("当前刷新率", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text("${currentRate} Hz", fontSize = 56.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("Root: ${if(rootOk == true) "✅" else if(rootOk == false) "❌" else "…"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Shizuku: ${if(shizukuOk) "✅" else "❌"}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Profile rate buttons
        Text("步进方案: 120 → 132 → 144 → 156 → 165", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PROFILE_RATES.forEach { rate ->
                val setVal = SET_OFFSET[rate] ?: rate
                val isActive = rate == currentRate
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            RateController.setRate(setVal)
                            val raw = RateController.getCurrentRate()
                            currentRate = READ_REVERSE[raw] ?: raw
                            debug = RateController.lastDebugEntries
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (isActive) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) else ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${rate}Hz", fontSize = 13.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                        if (isActive) Text("当前", fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Tools row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    RateController.runDiagnostic()
                    debug = RateController.lastDebugEntries
                    showDebug = true
                }
            }, modifier = Modifier.weight(1f)) { Text("诊断") }
            FilledTonalButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val raw = RateController.getCurrentRate()
                    currentRate = READ_REVERSE[raw] ?: raw
                }
            }, modifier = Modifier.weight(1f)) { Text("刷新状态") }
            FilledTonalButton(onClick = {
                showDebug = !showDebug
                if(!showDebug) RateController.clearDebug()
            }, modifier = Modifier.weight(0.6f)) {
                Text(if(showDebug) "收起" else "日志")
            }
        }

        // Debug log
        if (showDebug && debug.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    debug.forEach { e ->
                        val icon = if(e.success) "✅" else "❌"
                        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text("$icon [${e.via}] ${e.method} ${e.elapsedMs}ms",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            Text("  ${e.command.take(70)}", fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(e.output != "(empty)" && e.output.isNotBlank()){
                                Text("  → ${e.output.take(200)}", fontSize = 7.sp,
                                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
