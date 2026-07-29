package com.ivarna.mkm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Storage // Might not exist
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivarna.mkm.ui.components.InfoRow
import com.ivarna.mkm.ui.components.PullToRefreshWrapper
import com.ivarna.mkm.ui.components.StatCard
import com.ivarna.mkm.ui.components.HeroUsageCard
import com.ivarna.mkm.ui.components.UfsTuningCard
import com.ivarna.mkm.ui.viewmodel.StorageViewModel
import com.ivarna.mkm.ui.components.BootToggleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(viewModel: StorageViewModel = viewModel(), onOpenDrawer: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val bootEnabled by viewModel.bootEnabled.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                    Text(
                        "存储管理",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PullToRefreshWrapper(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                uiState?.let { state ->
                    BootToggleCard(
                        enabled = bootEnabled,
                        onToggle = { viewModel.toggleBootEnabled(it) }
                    )

                    val internalStorage = state.partitions.find { it.mountPoint == "/data" }
                    val systemStorage = state.partitions.find { it.mountPoint == "/system" }

                    if (internalStorage != null) {
                        HeroUsageCard(
                            title = "内部存储",
                            usage = internalStorage.usagePercent / 100f,
                            mainValue = "${internalStorage.usagePercent.toInt()}%",
                            subValue = "已用 ${internalStorage.used} / 共 ${internalStorage.total}"
                        )
                    }

                    if (systemStorage != null) {
                         StatCard(
                            title = "系统分区",
                            value = systemStorage.used,
                            subValue = "已用 / 共 ${systemStorage.total}",
                            progress = systemStorage.usagePercent / 100f,
                            icon = Icons.Default.SdStorage,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    UfsTuningCard(
                        ufs = state.ufsStatus,
                        onGovernorSelected = { path, gov -> viewModel.setUfsGovernor(path, gov) },
                        onMinFreqSelected = { path, freq -> viewModel.setUfsMinFreq(path, freq) },
                        onMaxFreqSelected = { path, freq -> viewModel.setUfsMaxFreq(path, freq) }
                    )

                    Card(
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "存储信息",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            InfoRow(label = "类型", value = state.type)
                            InfoRow(label = "分区数量", value = state.partitions.size.toString())
                            
                            internalStorage?.let {
                                InfoRow(label = "内部总容量", value = it.total)
                                InfoRow(label = "内部剩余", value = it.free)
                                InfoRow(label = "块大小", value = "${it.blockSize} 字节")
                            }
                        }
                    }
                }
            }
        }
    }
}
