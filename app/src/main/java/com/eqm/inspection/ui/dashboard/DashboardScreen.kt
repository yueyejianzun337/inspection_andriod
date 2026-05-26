package com.eqm.inspection.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eqm.inspection.ui.components.*
import com.eqm.inspection.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    role: String = "",
    onNavigateToInspection: (() -> Unit)? = null,
    onNavigateToQuery: () -> Unit,
    onNavigateToDrafts: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 每次畫面顯示時自動刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "儀表板",
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                role = role,
                selected = 0,
                onInspection = onNavigateToInspection,
                onQuery = onNavigateToQuery,
                onDrafts = onNavigateToDrafts,
                onSettings = onNavigateToSettings
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ErrorMessage(
                        message = uiState.error!!,
                        onRetry = { viewModel.loadDashboard() },
                        modifier = Modifier
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("前往設置檢查服務器地址")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 统计卡片
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                label = "巡檢總數",
                                value = uiState.totalRecords.toString(),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary
                            )
                            StatCard(
                                label = "型號總數",
                                value = uiState.totalModels.toString(),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // 月统计柱状图
                    if (uiState.monthlyData.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("近6個月巡檢趨勢", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    MonthlyBarChart(data = uiState.monthlyData)
                                }
                            }
                        }
                    }

                    // 型号分布
                    if (uiState.modelData.isNotEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("型號分布", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ModelDistributionChart(data = uiState.modelData)
                                }
                            }
                        }
                    }

                    // FAIL记录
                    if (uiState.failRecords.isNotEmpty()) {
                        item {
                            Text("最近FAIL記錄", style = MaterialTheme.typography.titleMedium)
                        }
                        items(uiState.failRecords) { record ->
                            FailRecordCard(
                                record = record,
                                onClick = {
                                    val id = record["id"] as? Int ?: return@FailRecordCard
                                    onNavigateToDetail(id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyBarChart(data: List<Map<String, Any>>) {
    val barColors = listOf(
        Color(0xFF2196F3), // 蓝
        Color(0xFF4CAF50), // 绿
        Color(0xFFFF9800), // 橙
        Color(0xFFE91E63), // 粉红
        Color(0xFF9C27B0), // 紫
        Color(0xFF00BCD4)  // 青
    )
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (data.isEmpty()) return@Canvas
        val maxCount = data.maxOf { (it["count"] as? Number)?.toFloat() ?: 1f }
        val barWidth = size.width / (data.size * 2f)
        val gap = barWidth

        data.forEachIndexed { index, item ->
            val count = (item["count"] as? Number)?.toFloat() ?: 0f
            val chartAreaHeight = size.height - 30f
            val barHeight = if (maxCount > 0f) (count / maxCount) * chartAreaHeight else 0f
            val x = index * (barWidth + gap) + gap / 2

            // 画柱（每个月份不同颜色）
            drawRect(
                color = barColors[index % barColors.size],
                topLeft = Offset(x, size.height - barHeight - 20f),
                size = Size(barWidth, barHeight)
            )

            // 数值标签（柱顶）
            val countStr = count.toInt().toString()
            val countLayout = textMeasurer.measure(
                text = countStr,
                style = TextStyle(fontSize = 12.sp, color = barColors[index % barColors.size])
            )
            drawText(
                countLayout,
                topLeft = Offset(
                    x + (barWidth - countLayout.size.width) / 2,
                    size.height - barHeight - 20f - countLayout.size.height - 4f
                )
            )

            // 月份标签（柱底）
            val month = (item["month"] as? String)?.takeLast(2) ?: ""
            val monthLayout = textMeasurer.measure(
                text = month,
                style = TextStyle(fontSize = 10.sp)
            )
            drawText(
                monthLayout,
                topLeft = Offset(x + (barWidth - monthLayout.size.width) / 2, size.height - 18f)
            )
        }
    }
}

@Composable
private fun ModelDistributionChart(data: List<Map<String, Any>>) {
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.take(5).forEachIndexed { index, item ->
            val name = item["model_name"] as? String ?: ""
            val count = (item["count"] as? Number)?.toInt() ?: 0
            val maxCount = data.maxOf { (it["count"] as? Number)?.toInt() ?: 0 }
            val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    modifier = Modifier.width(120.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.05f, 1f)),
                        color = colors[index % colors.size],
                        shape = MaterialTheme.shapes.small
                    ) {}
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FailRecordCard(
    record: Map<String, Any>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record["model_name"] as? String ?: "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = record["inspection_date"] as? String ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = StatusFail,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "FAIL",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    role: String = "",
    selected: Int,
    onInspection: (() -> Unit)?,
    onQuery: () -> Unit,
    onDrafts: (() -> Unit)?,
    onSettings: () -> Unit
) {
    val showFullTabs = role in listOf("vendor", "admin")

    NavigationBar {
        NavigationBarItem(
            selected = selected == 0,
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("首頁") }
        )
        if (showFullTabs) {
            NavigationBarItem(
                selected = selected == 1,
                onClick = onInspection ?: {},
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                label = { Text("填寫") }
            )
        }
        NavigationBarItem(
            selected = if (showFullTabs) selected == 2 else selected == 1,
            onClick = onQuery,
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("查詢") }
        )
        if (showFullTabs) {
            NavigationBarItem(
                selected = selected == 3,
                onClick = onDrafts ?: {},
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                label = { Text("暫存") }
            )
        }
        NavigationBarItem(
            selected = if (showFullTabs) selected == 4 else selected == 2,
            onClick = onSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("設置") }
        )
    }
}
