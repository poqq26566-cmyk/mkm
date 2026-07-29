package com.ivarna.mkm.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.data.model.CpuCluster
import com.ivarna.mkm.data.model.CpuCore
import com.ivarna.mkm.ui.components.*
import com.ivarna.mkm.ui.components.ThermalCard
import com.ivarna.mkm.ui.viewmodel.CpuViewModel
import com.ivarna.mkm.utils.ShellUtils
import com.ivarna.mkm.ui.components.BootToggleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuScreen(viewModel: CpuViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val cpuStatus by viewModel.cpuStatus.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedClusterForGovernor by remember { mutableStateOf<CpuCluster?>(null) }
    var selectedClusterForMaxFreq by remember { mutableStateOf<CpuCluster?>(null) }
    var selectedClusterForMinFreq by remember { mutableStateOf<CpuCluster?>(null) }
    
    var selectedCoreForSettings by remember { mutableStateOf<com.ivarna.mkm.data.model.CpuCore?>(null) }
    var showCoreGovernorSheet by remember { mutableStateOf(false) }
    var showCoreMaxFreqSheet by remember { mutableStateOf(false) }
    var showCoreMinFreqSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                     IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
                title = {
                    Column {
                        Text(
                            cpuStatus.cpuName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "CPU 管理",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        PullToRefreshWrapper(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    top = 8.dp, // padding consumed by wrapper, just need top spacing
                    bottom = 32.dp,
                    start = 16.dp,
                    end = 16.dp
                )
            ) {
            item {
                HeroUsageCard(
                    title = "整体利用率",
                    usage = cpuStatus.overallUsage,
                    mainValue = "${(cpuStatus.overallUsage * 100).toInt()}%",
                    subValue = "${cpuStatus.totalCores} 个处理器运行中"
                )
            }

            // Thermal Status
            item {
                val thermalStatus by viewModel.thermalStatus.collectAsState()
                // Show card if we have data OR if we are refreshing (to show loading state)
                if (thermalStatus.zones.isNotEmpty() || isRefreshing) {
                    ThermalCard(
                        status = thermalStatus,
                        isLoading = isRefreshing && thermalStatus.zones.isEmpty(), // Only show specific loading if empty
                        onSetLimit = { limit -> viewModel.setThermalLimit(limit) },
                        onDisableThrottling = { viewModel.disableThrottling() }
                    )
                }
            }


            item {
                Spacer(modifier = Modifier.height(16.dp))
                BootToggleCard(
                    enabled = bootEnabled,
                    onToggle = { viewModel.toggleBootEnabled(it) }
                )
            }

            item {
                SectionHeader("CPU 集群")
            }

            items(cpuStatus.clusters) { cluster ->
                CpuClusterCard(
                    cluster = cluster,
                    onGovernorClick = { selectedClusterForGovernor = cluster },
                    onMaxFreqClick = { selectedClusterForMaxFreq = cluster },
                    onMinFreqClick = { selectedClusterForMinFreq = cluster }
                )
            }

            item {
                SectionHeader("核心状态监控")
            }

            item {
                CoreStatusGrid(
                    cores = cpuStatus.clusters.flatMap { it.cores },
                    onCoreClick = { selectedCoreForSettings = it }
                )
            }
            }
        }

        // Selection sheets
        selectedClusterForGovernor?.let { cluster ->
            SelectionBottomSheet(
                title = "选择调速器",
                items = cluster.availableGovernors,
                selectedItem = cluster.governor,
                onDismiss = { selectedClusterForGovernor = null },
                onItemSelected = {
                    viewModel.setGovernor(cluster.id, it)
                    selectedClusterForGovernor = null
                }
            )
        }

        selectedClusterForMaxFreq?.let { cluster ->
            SelectionBottomSheet(
                title = "选择最高频率",
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMaxFreq,
                onDismiss = { selectedClusterForMaxFreq = null },
                onItemSelected = {
                    viewModel.setFrequency(cluster.id, it, true)
                    selectedClusterForMaxFreq = null
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
            )
        }

        selectedClusterForMinFreq?.let { cluster ->
            SelectionBottomSheet(
                title = "选择最低频率",
                items = cluster.availableFrequencies,
                selectedItem = cluster.rawMinFreq,
                onDismiss = { selectedClusterForMinFreq = null },
                onItemSelected = {
                    viewModel.setFrequency(cluster.id, it, false)
                    selectedClusterForMinFreq = null
                },
                itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
            )
        }

        selectedCoreForSettings?.let { core ->
            ModalBottomSheet(
                onDismissRequest = { selectedCoreForSettings = null },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "核心 ${core.id} 设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SettingRow(
                            label = "调速器",
                            value = core.governor,
                            onClick = { showCoreGovernorSheet = true }
                        )
                        SettingRow(
                            label = "最高频率",
                            value = core.maxFreq,
                            onClick = { showCoreMaxFreqSheet = true }
                        )
                        SettingRow(
                            label = "最低频率",
                            value = core.minFreq,
                            onClick = { showCoreMinFreqSheet = true }
                        )
                    }
                }
            }

            if (showCoreGovernorSheet) {
                SelectionBottomSheet(
                    title = "核心 ${core.id} 调速器",
                    items = core.availableGovernors,
                    selectedItem = core.governor,
                    onDismiss = { showCoreGovernorSheet = false },
                    onItemSelected = {
                        viewModel.setGovernorForCore(core.id, it)
                        showCoreGovernorSheet = false
                    }
                )
            }

            if (showCoreMaxFreqSheet) {
                SelectionBottomSheet(
                    title = "核心 ${core.id} 最高频率",
                    items = core.availableFrequencies,
                    selectedItem = core.rawMaxFreq,
                    onDismiss = { showCoreMaxFreqSheet = false },
                    onItemSelected = {
                        viewModel.setFrequencyForCore(core.id, it, true)
                        showCoreMaxFreqSheet = false
                    },
                    itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
                )
            }

            if (showCoreMinFreqSheet) {
                SelectionBottomSheet(
                    title = "核心 ${core.id} 最低频率",
                    items = core.availableFrequencies,
                    selectedItem = core.rawMinFreq,
                    onDismiss = { showCoreMinFreqSheet = false },
                    onItemSelected = {
                        viewModel.setFrequencyForCore(core.id, it, false)
                        showCoreMinFreqSheet = false
                    },
                    itemLabel = { ShellUtils.formatFreq(it.toLongOrNull() ?: 0L) }
                )
            }
        }
    }
}

@Composable
fun CpuClusterCard(
    cluster: CpuCluster,
    onGovernorClick: () -> Unit,
    onMaxFreqClick: () -> Unit,
    onMinFreqClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "集群 ${cluster.id}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "核心 ${cluster.coreRange.first}-${cluster.coreRange.last}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingRow(
                    label = "调速器",
                    value = cluster.governor,
                    onClick = onGovernorClick
                )
                SettingRow(
                    label = "最高频率",
                    value = cluster.maxFreq,
                    onClick = onMaxFreqClick
                )
                SettingRow(
                    label = "最低频率",
                    value = cluster.minFreq,
                    onClick = onMinFreqClick
                )
                InfoRow(
                    label = "当前主频",
                    value = cluster.currentFreq
                )
            }
        }
    }
}

@Composable
fun CoreStatusGrid(cores: List<CpuCore>, onCoreClick: (CpuCore) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cores.chunked(2).forEach { rowCores ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCores.forEach { core ->
                    CoreMiniCard(
                        core = core,
                        onClick = { onCoreClick(core) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowCores.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

