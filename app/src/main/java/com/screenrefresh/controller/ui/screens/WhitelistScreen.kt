package com.screenrefresh.controller.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screenrefresh.controller.ScreenRefreshApp
import com.screenrefresh.controller.data.WhitelistEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WhitelistScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { ScreenRefreshApp.instance.db }
    val items = remember { mutableStateListOf<WhitelistEntity>() }
    var showAppPicker by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<WhitelistEntity?>(null) }
    var showRatePicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WhitelistEntity?>(null) }

    LaunchedEffect(Unit) {
        db.whitelistDao().getAll().collect { list ->
            items.clear(); items.addAll(list)
        }
    }

    if (showAppPicker) {
        var search by remember { mutableStateOf("") }
        AppPickerDialog(
            search = search,
            onSearchChange = { search = it },
            onSelect = { pkg, name ->
                scope.launch(Dispatchers.IO) {
                    db.whitelistDao().insert(WhitelistEntity(pkg, name, 120))
                }
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }

    if (showRatePicker && selectedApp != null) {
        RatePickerDialog(
            app = selectedApp!!,
            onSelect = { rate ->
                scope.launch(Dispatchers.IO) {
                    db.whitelistDao().insert(selectedApp!!.copy(targetRate = rate))
                }
                showRatePicker = false
            },
            onDismiss = { showRatePicker = false }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移除") },
            text = { Text("确定从白名单移除 ${target.appName}？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        db.whitelistDao().delete(target)
                    }
                    deleteTarget = null
                }) { Text("确定", color = Color(0xFFFF4D4F)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAppPicker = true },
                containerColor = MiuiBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, "添加") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Text("刷新率设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MiuiText)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text("暂无白名单应用\n点击右下角 + 添加",
                    fontSize = 14.sp, color = MiuiGray, modifier = Modifier.padding(top = 40.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items.toList(), key = { it.packageName }) { entity ->
                    AppCard(
                        packageName = entity.packageName,
                        appName = entity.appName,
                        targetRate = entity.targetRate,
                        onRateClick = { selectedApp = entity; showRatePicker = true },
                        onDelete = { deleteTarget = entity }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun AppPickerDialog(search: String, onSearchChange: (String) -> Unit,
                    onSelect: (String, String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
                .map { AppInfo(it.packageName, it.loadLabel(ctx.packageManager).toString(), it.loadIcon(ctx.packageManager)) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val filtered = allApps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            Column {
                TextField(value = search, onValueChange = onSearchChange,
                    placeholder = { Text("搜索应用...") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF2F3F5),
                        unfocusedContainerColor = Color(0xFFF2F3F5),
                        focusedIndicatorColor = MiuiBlue
                    ))
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(360.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(Modifier.fillMaxWidth().clickable { onSelect(app.packageName, app.label) }
                            .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (app.icon != null) {
                                val bmp = iconFromDrawable(app.icon)
                                androidx.compose.foundation.Image(bmp, null,
                                    Modifier.size(36.dp).clip(CircleShape))
                            } else {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEEEEEF)),
                                    contentAlignment = Alignment.Center) {
                                    Text(app.label.firstOrNull()?.toString() ?: "?", color = MiuiGray)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 11.sp, color = MiuiGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun AppCard(packageName: String, appName: String, targetRate: Int, onRateClick: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var icon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val d = ctx.packageManager.getApplicationIcon(packageName)
                icon = iconFromDrawable(d)
            } catch (_: Exception) {}
        }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                androidx.compose.foundation.Image(icon!!, null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEEEF)),
                    contentAlignment = Alignment.Center) {
                    Text(appName.firstOrNull()?.toString() ?: "?", fontSize = 16.sp, color = MiuiGray)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(appName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(packageName, fontSize = 11.sp, color = MiuiGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(4.dp))
            Text("${targetRate}Hz", fontSize = 11.sp, color = MiuiBlue)
            IconButton(onClick = onRateClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ChevronRight, "选择", tint = MiuiGray, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, "删除", tint = Color(0xFFFF4D4F), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun RatePickerDialog(app: WhitelistEntity, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val rates = listOf(
        Triple(120, "120Hz", ""),
        Triple(144, "144Hz", "120→132→144"),
        Triple(165, "165Hz", "120→132→144→156→165"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${app.appName} 刷新率") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                rates.forEach { (rate, label, chain) ->
                    Card(Modifier.fillMaxWidth().clickable { onSelect(rate) }.padding(vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (rate == app.targetRate) MiuiBlue else Color(0xFFF2F3F5))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(label, fontSize = 16.sp,
                                fontWeight = if (rate == app.targetRate) FontWeight.Bold else FontWeight.Normal,
                                color = if (rate == app.targetRate) Color.White else MiuiText)
                            if (chain.isNotEmpty())
                                Text(chain, fontSize = 11.sp,
                                    color = if (rate == app.targetRate) Color(0xFFB8D4FF) else MiuiGray)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

data class AppInfo(val packageName: String, val label: String, val icon: Drawable?)

fun iconFromDrawable(d: Drawable): androidx.compose.ui.graphics.ImageBitmap {
    if (d is BitmapDrawable) return d.bitmap.asImageBitmap()
    val bmp = Bitmap.createBitmap(
        d.intrinsicWidth.coerceAtLeast(1),
        d.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)
    d.setBounds(0, 0, canvas.width, canvas.height)
    d.draw(canvas)
    return bmp.asImageBitmap()
}
