package com.ivarna.mkm.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.R
import com.ivarna.mkm.service.BatteryMonitorService
import com.ivarna.mkm.service.BatterySessionTracker
import com.ivarna.mkm.shell.ShellManager
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.viewmodel.AppTheme
import com.ivarna.mkm.ui.viewmodel.PowerViewModel
import com.ivarna.mkm.ui.viewmodel.SettingsViewModel
import com.ivarna.mkm.utils.BatteryStatsResetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    powerViewModel: PowerViewModel = viewModel(),
    onOpenDrawer: () -> Unit = {},
    onRequestShizukuPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {}
) {
    val theme by viewModel.theme.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }

    // --- Power Calibration state ---
    val savedMultiplier by powerViewModel.calibrationMultiplier.collectAsState()
    val powerStatus by powerViewModel.powerStatus.collectAsState()
    var calibrationMultiplierText by remember { mutableStateOf(savedMultiplier.toString()) }
    var userHasEdited by remember { mutableStateOf(false) }
    LaunchedEffect(savedMultiplier) {
        if (!userHasEdited) calibrationMultiplierText = savedMultiplier.toString()
    }
    var calibrationSaveError by remember { mutableStateOf(false) }

    // --- Battery Notification state ---
    val batteryPrefs = remember { context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE) }
    var batteryNotificationEnabled by remember {
        mutableStateOf(batteryPrefs.getBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, false))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setBatteryNotification(context, true)
            batteryNotificationEnabled = true
        }
    }

    val updateIntervalOptions = remember {
        listOf(
            "5秒" to 5_000L,
            "10秒" to 10_000L,
            "30秒" to 30_000L,
            "1分钟" to 60_000L,
            "5分钟" to 300_000L,
            "10分钟" to 600_000L
        )
    }
    var selectedIntervalMs by remember {
        mutableStateOf(batteryPrefs.getLong("battery_update_interval_ms", BatterySessionTracker.DEFAULT_UPDATE_INTERVAL_MS))
    }
    var showIntervalMenu by remember { mutableStateOf(false) }

    // --- Battery Stats Reset state (T1) ---
    var resetOnUnplug by remember {
        mutableStateOf(BatteryStatsResetPrefs.isOnUnplug(context))
    }
    var resetOnFull by remember {
        mutableStateOf(BatteryStatsResetPrefs.isOnFull(context))
    }
    var resetOnBoot by remember {
        mutableStateOf(BatteryStatsResetPrefs.isOnBoot(context))
    }
    var resetMethod by remember { mutableStateOf("checking") }
    LaunchedEffect(Unit) {
        resetMethod = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    ShellManager.hasShizuku() -> "shizuku"
                    ShellManager.hasRoot() -> "root"
                    else -> "unavailable"
                }
            }.getOrDefault("unavailable")
        }
    }
    var lastResetTick by remember { mutableStateOf(0) }
    val lastResetDisplay = remember(lastResetTick) {
        val current = BatteryStatsResetPrefs.getLastReset(context)
        current?.let { (at, trigger) ->
            val agoMs = System.currentTimeMillis() - at
            val mins = agoMs / 60_000
            val rel = when {
                mins < 1L -> "刚刚"
                mins < 60L -> "${mins}分钟前"
                mins < 1440L -> "${mins / 60L}小时前"
                else -> "${mins / 1440L}天前"
            }
            val triggerZh = when (trigger) {
                "manual" -> "手动"
                "unplug" -> "拔出充电器"
                "full" -> "充满100%"
                "boot" -> "重启"
                else -> trigger
            }
            "上次重置：$rel（触发方式：$triggerZh）"
        } ?: "上次重置：从未"
    }

    fun toggleBatteryNotification(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            setBatteryNotification(context, enabled)
            batteryNotificationEnabled = enabled
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                     IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                title = {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item {
                AppInfoCard(
                    appName = "极简内核管理器",
                    version = "v1.7",
                    buildDate = "2026年6月16日"
                )
            }

            // Access Method Card - NEW for v1.1
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AccessMethodCard(
                    onRequestShizukuPermission = onRequestShizukuPermission
                )
            }

            item {
                SettingsSection(title = "外观") {
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "主题",
                        subtitle = when(theme) {
                            AppTheme.SYSTEM -> "跟随系统"
                            AppTheme.DYNAMIC -> "动态取色（Material You）"
                            AppTheme.LIGHT -> "浅色"
                            AppTheme.DARK -> "深色"
                            AppTheme.AMOLED -> "纯黑（AMOLED）"
                            AppTheme.NORD -> "Nord 主题"
                            AppTheme.NORD_LIGHT -> "Nord 浅色"
                            AppTheme.DRACULA -> "Dracula"
                            AppTheme.MONOKAI -> "Monokai"
                            AppTheme.GRUVBOX -> "Gruvbox"
                            AppTheme.GRUVBOX_LIGHT -> "Gruvbox 浅色"
                            AppTheme.SOLARIZED -> "Solarized 深色"
                            AppTheme.SOLARIZED_LIGHT -> "Solarized 浅色"
                            AppTheme.SYNTHWAVE -> "Synthwave（霓虹）"
                            AppTheme.ONE_LIGHT -> "One Light"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // Power Calibration - moved from Overlay page
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "功率校准") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val liveCalibrated = powerStatus.powerW * savedMultiplier
                        val polaritySign = if (powerStatus.isCharging) "+" else "-"
                        val polarityColor = if (powerStatus.isCharging)
                            androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.error
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "原始功率",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${polaritySign}%.3f W".format(powerStatus.powerW),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = polarityColor
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "校准后 (${savedMultiplier}×)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${polaritySign}%.3f W".format(liveCalibrated),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "校准系数",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = calibrationMultiplierText,
                                onValueChange = {
                                    calibrationMultiplierText = it
                                    userHasEdited = true
                                    calibrationSaveError = false
                                },
                                modifier = Modifier.weight(1f),
                                label = { Text("系数") },
                                placeholder = { Text("例如 1.1") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                isError = calibrationSaveError,
                                supportingText = if (calibrationSaveError) {
                                    { Text("数字无效", color = MaterialTheme.colorScheme.error) }
                                } else null
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    val normalised = calibrationMultiplierText.trim().replace(',', '.')
                                    val v = normalised.toFloatOrNull()
                                    if (v != null && v > 0f) {
                                        powerViewModel.saveCalibrationMultiplier(v)
                                        userHasEdited = false
                                        calibrationSaveError = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("系数已保存：${v}×")
                                        }
                                    } else {
                                        calibrationSaveError = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "保存系数")
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    powerViewModel.saveCalibrationMultiplier(1.0f)
                                    userHasEdited = false
                                    calibrationSaveError = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("已重置为 1.0×")
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.SettingsBackupRestore, contentDescription = "重置为 1×")
                            }
                        }
                        Text(
                            text = "已保存：${savedMultiplier}×  •  请与外部功率计对齐。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Battery Notification Toggle
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "通知") {
                    SettingsItem(
                        icon = if (batteryNotificationEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        title = "电池监控",
                        subtitle = if (batteryNotificationEnabled) "常驻通知已启用" else "点击启用电池通知",
                        onClick = { toggleBatteryNotification(!batteryNotificationEnabled) },
                        trailing = {
                            Switch(
                                checked = batteryNotificationEnabled,
                                onCheckedChange = { toggleBatteryNotification(it) }
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Navigate to full notification customise screen
                    SettingsItem(
                        icon = Icons.Default.Tune,
                        title = "自定义通知",
                        subtitle = "配置充电或放电时显示的内容",
                        onClick = onOpenNotificationSettings
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "刷新间隔",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box {
                            val currentLabel = updateIntervalOptions.find { it.second == selectedIntervalMs }?.first ?: "30秒"
                            AssistChip(
                                onClick = { showIntervalMenu = true },
                                label = { Text(currentLabel) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenu(
                                expanded = showIntervalMenu,
                                onDismissRequest = { showIntervalMenu = false }
                            ) {
                                updateIntervalOptions.forEach { (label, ms) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedIntervalMs = ms
                                            batteryPrefs.edit().putLong("battery_update_interval_ms", ms).apply()
                                            showIntervalMenu = false
                                        },
                                        leadingIcon = {
                                            if (ms == selectedIntervalMs) {
                                                Icon(
                                                    Icons.Default.Timer,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "间隔越长越省电，默认30秒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Battery Stats Reset (T1)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(title = "电池统计重置") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "自动重置系统电池统计（dumpsys batterystats --reset），以获得全新的唤醒锁/UID分析数据。需要 Root 或 Shizuku 权限。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "将使用：" + when (resetMethod) {
                                "shizuku" -> "Shizuku"
                                "root" -> "Root"
                                "unavailable" -> "不可用"
                                else -> "检测中"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (resetMethod == "unavailable")
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = lastResetDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        SwitchRow(
                            label = "拔出充电器时重置",
                            checked = resetOnUnplug,
                            onCheckedChange = {
                                resetOnUnplug = it
                                BatteryStatsResetPrefs.setOnUnplug(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SwitchRow(
                            label = "充满100%时重置",
                            checked = resetOnFull,
                            onCheckedChange = {
                                resetOnFull = it
                                BatteryStatsResetPrefs.setOnFull(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SwitchRow(
                            label = "重启时重置",
                            checked = resetOnBoot,
                            onCheckedChange = {
                                resetOnBoot = it
                                BatteryStatsResetPrefs.setOnBoot(context, it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(
                                enabled = resetMethod != "unavailable",
                                onClick = {
                                    coroutineScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            ShellManager.exec("dumpsys batterystats --reset")
                                        }
                                        if (result.isSuccess) {
                                            BatteryStatsResetPrefs.recordReset(context, "manual")
                                            lastResetTick++
                                            snackbarHostState.showSnackbar("电池统计已重置")
                                        } else {
                                            snackbarHostState.showSnackbar("重置失败：${result.stderr.ifBlank { "退出码 ${result.exitCode}" }}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("立即重置")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AboutMeCard(
                    name = "Abhay Raj",
                    handle = "@abhay-byte",
                    bio = "热衷于软件开发、硬件探索，以及一切与 Linux 相关的事物。"
                )
            }

            item {
                SettingsSection(title = "联系我") {
                    SocialItem(
                        icon = Icons.Default.Code,
                        label = "GitHub",
                        description = "查看我的仓库和项目",
                        onClick = { uriHandler.openUri("https://github.com/abhay-byte") }
                    )
                    SocialItem(
                        icon = Icons.Default.Group,
                        label = "LinkedIn",
                        description = "职业社交，欢迎联系",
                        onClick = { uriHandler.openUri("https://www.linkedin.com/in/abhay-byte") }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    onClick = { uriHandler.openUri("https://github.com/abhay-byte/mkm") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    ListItem(
                        headlineContent = { Text("在 GitHub 上点个 Star", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("MKM —— 如果你喜欢这个项目，请给它一个 Star！") },
                        leadingContent = {
                            Icon(Icons.Default.Star, contentDescription = null)
                        },
                        modifier = Modifier.padding(8.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    onClick = { uriHandler.openUri("https://discord.gg/tAj45MjRkU") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF5865F2),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    ListItem(
                        headlineContent = { Text("加入 MKM 社区", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("在 Discord 上与其他 MKM 用户和开发者交流") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_discord),
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                        },
                        modifier = Modifier.padding(8.dp),
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            headlineColor = androidx.compose.ui.graphics.Color.White,
                            supportingColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            leadingIconColor = androidx.compose.ui.graphics.Color.White,
                            trailingIconColor = androidx.compose.ui.graphics.Color.White
                        )
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        SelectionBottomSheet(
            title = "应用主题",
            items = AppTheme.values().map { it.name },
            selectedItem = theme.name,
            onDismiss = { showThemeDialog = false },
            onItemSelected = {
                viewModel.setTheme(AppTheme.valueOf(it))
                showThemeDialog = false
            },
            itemLabel = {
                when(AppTheme.valueOf(it)) {
                    AppTheme.SYSTEM -> "跟随系统"
                    AppTheme.DYNAMIC -> "动态取色（Material You）"
                    AppTheme.LIGHT -> "浅色"
                    AppTheme.DARK -> "深色"
                    AppTheme.AMOLED -> "纯黑（AMOLED）"
                    AppTheme.NORD -> "Nord 主题"
                    AppTheme.NORD_LIGHT -> "Nord 浅色"
                    AppTheme.DRACULA -> "Dracula"
                    AppTheme.MONOKAI -> "Monokai"
                    AppTheme.GRUVBOX -> "Gruvbox"
                    AppTheme.GRUVBOX_LIGHT -> "Gruvbox 浅色"
                    AppTheme.SOLARIZED -> "Solarized 深色"
                    AppTheme.SOLARIZED_LIGHT -> "Solarized 浅色"
                    AppTheme.SYNTHWAVE -> "Synthwave（霓虹）"
                    AppTheme.ONE_LIGHT -> "One Light"
                }
            }
        )
    }
}

private fun setBatteryNotification(context: Context, enabled: Boolean) {
    context.getSharedPreferences(BatteryMonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BatteryMonitorService.PREF_NOTIFICATION_ENABLED, enabled)
        .apply()

    val intent = Intent(context, BatteryMonitorService::class.java).apply {
        action = if (enabled) BatteryMonitorService.ACTION_START else BatteryMonitorService.ACTION_STOP
    }
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
