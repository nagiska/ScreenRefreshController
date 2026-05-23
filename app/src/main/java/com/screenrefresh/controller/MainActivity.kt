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
import com.screenrefresh.controller.root.DisplayMode
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

@Composable
fun MainScreen() {
    val scope = rememberCoroutineScope()
    var modes by remember { mutableStateOf<List<DisplayMode>>(emptyList()) }
    var currentRate by remember { mutableIntStateOf(60) }
    var debug by remember { mutableStateOf<List<RootExecutor.DebugEntry>>(emptyList()) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var shizukuOk by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rootOk = RootExecutor.isRootAvailable()
        shizukuOk = RootExecutor.isShizukuAvailable()
        RateController.refreshModes()
        modes = RateController.getModeInfo()
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("当前: $currentRate Hz", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Root: ${when(rootOk) { true -> "✅"; false -> "❌"; else -> "…" }}")
                    Text("Shizuku: ${if(shizukuOk) "✅" else "❌"}")
                }
                Text("模式: ${modes.size}个 — ${modes.map{"${it.fps}Hz(m${it.id})"}.joinToString(" ")}",
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (modes.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                modes.distinctBy{it.fps}.sortedBy{it.fps}.forEach{ m ->
                    FilledTonalButton(onClick={
                        scope.launch(Dispatchers.IO){
                            RateController.setRate(m.fps)
                            debug = RateController.lastDebugEntries
                        }
                    }, modifier=Modifier.weight(1f)){ Text("${m.fps}Hz", fontSize=10.sp) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(4.dp)){
            listOf(60,90,120,144,165).forEach{ r->
                FilledTonalButton(onClick={
                    scope.launch(Dispatchers.IO){
                        RateController.setRate(r)
                        debug = RateController.lastDebugEntries
                    }
                }, modifier=Modifier.weight(1f)){ Text("${r}Hz", fontSize=10.sp) }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilledTonalButton(onClick={
                scope.launch(Dispatchers.IO){ RateController.runDiagnostic(); debug=RateController.lastDebugEntries }
            }, modifier=Modifier.weight(1f)){ Text("诊断", fontSize=12.sp) }
            FilledTonalButton(onClick={
                scope.launch(Dispatchers.IO){ RateController.refreshModes(); modes=RateController.getModeInfo() }
            }, modifier=Modifier.weight(1f)){ Text("扫描", fontSize=12.sp) }
            FilledTonalButton(onClick={ RateController.clearDebug(); debug=emptyList() },
                modifier=Modifier.weight(0.6f)){ Text("清空", fontSize=12.sp) }
        }

        if(debug.isNotEmpty()){
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(8.dp)){
                Column(Modifier.fillMaxWidth().padding(10.dp)){
                    Text("日志", fontWeight=FontWeight.Bold, fontSize=12.sp)
                    Spacer(Modifier.height(4.dp))
                    debug.forEach{ e->
                        val icon = if(e.success) "✅" else "❌"
                        val via = if(e.via.isNotEmpty()) "[${e.via}] " else ""
                        Column(Modifier.fillMaxWidth().padding(vertical=1.dp)){
                            Text("$icon $via${e.method} ${e.elapsedMs}ms",
                                fontSize=9.sp, fontFamily=FontFamily.Monospace)
                            Text("  ${e.command.take(70)}", fontSize=7.sp,
                                fontFamily=FontFamily.Monospace, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(e.output.isNotEmpty() && e.output!="(empty)"){
                                Text("  → ${e.output.take(300)}", fontSize=7.sp,
                                    fontFamily=FontFamily.Monospace, color=MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
