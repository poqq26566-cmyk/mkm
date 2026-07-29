package com.ivarna.mkm.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivarna.mkm.shell.ShizukuManager
import com.ivarna.mkm.shell.SHIZUKU_REQUEST_CODE
import rikka.shizuku.Shizuku

/**
 * Permission status enum for UI state
 */
enum class PermissionStatus {
    Checking,
    NotInstalled,
    Hidden,          // Not visible in PackageManager but binder is alive
    NotRunning,      // Installed but service not running
    NotGranted,
    Granted,
    Denied
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequestScreen(
    onNavigateBack: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionStatus = remember { mutableStateOf<PermissionStatus>(PermissionStatus.Checking) }
    
    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                permissionStatus.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    PermissionStatus.Granted
                } else {
                    PermissionStatus.Denied
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        
        // Check current status with granular states
        when {
            ShizukuManager.hasPermission() -> {
                permissionStatus.value = PermissionStatus.Granted
                kotlinx.coroutines.delay(500)
                onPermissionGranted()
            }
            ShizukuManager.isHidden() -> {
                // Binder alive but package hidden (Issue #6)
                permissionStatus.value = PermissionStatus.Hidden
            }
            !ShizukuManager.isInstalled() -> {
                permissionStatus.value = PermissionStatus.NotInstalled
            }
            !ShizukuManager.isRunning() -> {
                permissionStatus.value = PermissionStatus.NotRunning
            }
            else -> {
                permissionStatus.value = PermissionStatus.NotGranted
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shizuku 权限") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (permissionStatus.value) {
                PermissionStatus.NotInstalled -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku 未安装",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "请安装 Shizuku 以在无 Root 权限的情况下使用此功能。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://github.com/RikkaApps/Shizuku/releases")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("下载 Shizuku")
                    }
                }

                PermissionStatus.Hidden -> {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku 已隐藏",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shizuku 似乎已从系统中隐藏，但其服务仍在运行。你仍可以为 MKM 授权。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { ShizukuManager.requestPermission() }
                    ) {
                        Text("授予权限")
                    }
                }
                
                PermissionStatus.NotRunning -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Shizuku 未运行",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Shizuku 已安装但服务未运行。请打开 Shizuku 并启动服务。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            // Try to open Shizuku app
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                // Fallback to Play Store
                                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("market://details?id=moe.shizuku.privileged.api")
                                }
                                context.startActivity(playStoreIntent)
                            }
                        }
                    ) {
                        Text("打开 Shizuku")
                    }
                }
                
                PermissionStatus.NotGranted -> {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "需要权限",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "MKM 需要 Shizuku 权限才能管理系统设置。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { ShizukuManager.requestPermission() }
                    ) {
                        Text("授予权限")
                    }
                }
                
                PermissionStatus.Granted -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "权限已授予",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "正在设置…",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                }
                
                PermissionStatus.Denied -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "权限被拒绝",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "没有 Shizuku 权限，MKM 将无法正常工作。如果可用，你仍可以使用 Root 权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("取消")
                        }
                        Button(onClick = { ShizukuManager.requestPermission() }) {
                            Text("重试")
                        }
                    }
                }
                
                PermissionStatus.Checking -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在检查状态…")
                }
            }
        }
    }
}

/**
 * Card component for showing access method status in settings
 */
@Composable
fun AccessMethodCard(
    onRequestShizukuPermission: () -> Unit
) {
    val accessMethod = remember { com.ivarna.mkm.shell.ShellManager.getAvailableMethod() }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "访问方式",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "当前方式：",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    when (accessMethod) {
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.ROOT -> "Root"
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.LOCAL -> "无"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (accessMethod) {
                        com.ivarna.mkm.shell.ShellManager.AccessMethod.ROOT -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Shizuku status
            val shizukuHidden = ShizukuManager.isHidden()
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = if (shizukuHidden) "Shizuku（已隐藏）" else "Shizuku",
                status = when {
                    ShizukuManager.hasPermission() -> "已启用"
                    shizukuHidden -> "已隐藏 — 点击授权"
                    !ShizukuManager.isInstalled() -> "未安装"
                    !ShizukuManager.isRunning() -> "未运行"
                    !ShizukuManager.hasPermission() -> "未授权"
                    else -> "已启用"
                },
                statusColor = when {
                    ShizukuManager.hasPermission() -> MaterialTheme.colorScheme.primary
                    shizukuHidden -> MaterialTheme.colorScheme.tertiary
                    !ShizukuManager.isInstalled() -> MaterialTheme.colorScheme.error
                    !ShizukuManager.isRunning() -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.tertiary
                },
                onClick = when {
                    ShizukuManager.hasPermission() -> null
                    shizukuHidden -> onRequestShizukuPermission
                    !ShizukuManager.isInstalled() -> null
                    !ShizukuManager.isRunning() -> null
                    else -> onRequestShizukuPermission
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Root status
            AccessMethodItem(
                icon = Icons.Default.Security,
                title = "Root",
                status = if (com.topjohnwu.superuser.Shell.getShell().isRoot) "已启用" else "不可用",
                statusColor = if (com.topjohnwu.superuser.Shell.getShell().isRoot) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                onClick = null
            )
        }
    }
}

@Composable
fun AccessMethodItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    statusColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = statusColor
                )
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
    }
}
