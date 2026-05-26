package com.eqm.inspection.ui.query

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eqm.inspection.data.api.models.IdNamePair
import com.eqm.inspection.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryScreen(
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToReview: ((Int) -> Unit)? = null,
    onBack: () -> Unit,
    viewModel: QueryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initData by viewModel.initData.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "巡檢查詢",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 筛选条件面板
            FilterPanel(
                initData = initData,
                selectedManufacturerId = uiState.selectedManufacturerId,
                selectedModelId = uiState.selectedModelId,
                selectedStatus = uiState.selectedStatus,
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onManufacturerChange = { viewModel.setManufacturer(it) },
                onModelChange = { viewModel.setModel(it) },
                onStatusChange = { viewModel.setStatus(it) },
                onDateRangeChange = { start, end -> viewModel.setDateRange(start, end) },
                onSearch = { viewModel.refresh() }
            )

            // 结果列表
            when {
                uiState.isLoading -> LoadingIndicator()
                uiState.error != null -> ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.refresh() }
                )
                uiState.records.isEmpty() -> EmptyState(message = "暫無巡檢記錄")
                else -> {
                    // 总记录数
                    Text(
                        text = "共 ${uiState.total} 條記錄",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.records) { record ->
                            RecordCard(
                                record = record,
                                onClick = {
                                    val id = record["id"] as? Int ?: return@RecordCard
                                    val reviewStatus = record["review_status"] as? String
                                    val currentRole = initData?.get("current_user_role") as? String ?: ""
                                    if (reviewStatus == "pending" && currentRole in listOf("user", "admin")) {
                                        onNavigateToReview?.let { it(id) }
                                    } else {
                                        onNavigateToDetail(id)
                                    }
                                }
                            )
                        }

                        // 分页
                        if (uiState.totalPages > 1) {
                            item {
                                PaginationBar(
                                    currentPage = uiState.page,
                                    totalPages = uiState.totalPages,
                                    onPageChange = { viewModel.goToPage(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FilterPanel(
    initData: Map<String, Any>?,
    selectedManufacturerId: String?,
    selectedModelId: String?,
    selectedStatus: String?,
    startDate: String?,
    endDate: String?,
    onManufacturerChange: (String?) -> Unit,
    onModelChange: (String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onDateRangeChange: (String?, String?) -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val manufacturers = (initData?.get("manufacturers") as? List<*>)?.filterIsInstance<IdNamePair>() ?: emptyList()
    val models = (initData?.get("models") as? List<*>)?.filterIsInstance<IdNamePair>() ?: emptyList()
    val isVendor = initData?.get("is_vendor") as? Boolean ?: false
    val vendorManufacturerIds = (initData?.get("vendor_manufacturer_ids") as? List<*>)
        ?.filterIsInstance<Int>() ?: emptyList()
    // 廠商用戶有多個廠商時也要顯示下拉選項
    val showManufacturerFilter = !isVendor || vendorManufacturerIds.size > 1

    var manufacturerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }

    // 日期選擇器
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    if (showStartDatePicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                cal.set(year, month, day)
                onDateRangeChange(fmt.format(cal.time), endDate)
                showStartDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    if (showEndDatePicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                cal.set(year, month, day)
                onDateRangeChange(startDate, fmt.format(cal.time))
                showEndDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("篩選條件", style = MaterialTheme.typography.titleSmall)

            // 厂商（非廠商用戶顯示全部，多廠商廠商用戶顯示其管理的廠商）
            if (showManufacturerFilter) {
                ExposedDropdownMenuBox(
                    expanded = manufacturerExpanded,
                    onExpandedChange = { manufacturerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = manufacturers.find { it.id.toString() == selectedManufacturerId }?.name ?: "全部廠商",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("廠商") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = manufacturerExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = manufacturerExpanded,
                        onDismissRequest = { manufacturerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部廠商") },
                            onClick = {
                                onManufacturerChange(null)
                                manufacturerExpanded = false
                            }
                        )
                        manufacturers.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    onManufacturerChange(m.id.toString())
                                    manufacturerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 型号
            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = models.find { it.id.toString() == selectedModelId }?.name ?: "全部型號",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("型號") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部型號") },
                        onClick = {
                            onModelChange(null)
                            modelExpanded = false
                        }
                    )
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.name) },
                            onClick = {
                                onModelChange(m.id.toString())
                                modelExpanded = false
                            }
                        )
                    }
                }
            }

            // 状态
            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (selectedStatus) {
                        "pending" -> "待審核"
                        "confirmed" -> "已審核"
                        else -> "全部狀態"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("審核狀態") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部狀態") },
                        onClick = {
                            onStatusChange(null)
                            statusExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("待審核") },
                        onClick = {
                            onStatusChange("pending")
                            statusExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("已審核") },
                        onClick = {
                            onStatusChange("confirmed")
                            statusExpanded = false
                        }
                    )
                }
            }

            // 日期范围
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startDate ?: "",
                    onValueChange = {},
                    label = { Text("開始日期") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showStartDatePicker = true },
                    readOnly = true,
                    enabled = true,
                    trailingIcon = {
                        TextButton(onClick = { showStartDatePicker = true }) {
                            Text("選擇")
                        }
                    }
                )
                OutlinedTextField(
                    value = endDate ?: "",
                    onValueChange = {},
                    label = { Text("結束日期") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showEndDatePicker = true },
                    readOnly = true,
                    enabled = true,
                    trailingIcon = {
                        TextButton(onClick = { showEndDatePicker = true }) {
                            Text("選擇")
                        }
                    }
                )
            }

            // 查詢按鈕
            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查詢")
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: Map<String, Any>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record["model_name"] as? String ?: "",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = statusColor(record["review_status"] as? String),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusLabel(record["review_status"] as? String),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${record["manufacturer_name"]} / ${record["production_line_name"]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "檢驗員: ${record["inspector_name"]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "審核員: ${record["reviewer_name"]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "日期: ${record["inspection_date"]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            // 測試站
            val testStations = record["test_station_names"] as? String ?: ""
            if (testStations.isNotBlank()) {
                Text(
                    text = "測試站: $testStations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = { if (currentPage > 1) onPageChange(currentPage - 1) },
            enabled = currentPage > 1
        ) {
            Text("上一頁")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$currentPage / $totalPages",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = { if (currentPage < totalPages) onPageChange(currentPage + 1) },
            enabled = currentPage < totalPages
        ) {
            Text("下一頁")
        }
    }
}
