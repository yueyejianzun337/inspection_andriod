package com.eqm.inspection.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recordId: Int,
    role: String = "",
    onBack: () -> Unit,
    onNavigateToReview: ((Int) -> Unit)? = null,
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        viewModel.loadDetail(recordId)
    }

    // 監聽審核成功
    LaunchedEffect(uiState.reviewSuccess) {
        uiState.reviewSuccess?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessages()
        }
    }

    // 監聽刪除成功
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            snackbarHostState.showSnackbar("刪除成功", duration = SnackbarDuration.Short)
            viewModel.clearMessages()
            onBack()
        }
    }

    // 監聽錯誤
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "巡檢詳情",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.downloadReport(context, recordId) }) {
                        Icon(Icons.Default.Download, contentDescription = "下載報告")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorMessage(
                message = uiState.error!!,
                onRetry = { viewModel.loadDetail(recordId) },
                modifier = Modifier.padding(padding)
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 基本信息
                    uiState.record?.let { record ->
                        item {
                            RecordInfoCard(record)
                        }
                    }

                    // 各测试站结果
                    items(uiState.stationGroups) { group ->
                        StationGroupCard(group)
                    }

                    // 審核/刪除按鈕（非 vendor 角色）
                    if (role != "vendor") {
                        uiState.record?.let { record ->
                            val reviewStatus = record["review_status"] as? String
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (role in listOf("user", "admin") && reviewStatus == "pending") {
                                        Button(
                                            onClick = {
                                                onNavigateToReview?.let { it(recordId) }
                                            },
                                            enabled = !uiState.isReviewing,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4CAF50)
                                            )
                                        ) {
                                            if (uiState.isReviewing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text("確認審核")
                                        }
                                    }
                                    if (role == "admin") {
                                        OutlinedButton(
                                            onClick = { showDeleteDialog = true },
                                            enabled = !uiState.isDeleting,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color.Red
                                            )
                                        ) {
                                            if (uiState.isDeleting) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("刪除記錄")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 刪除確認對話框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("確認刪除") },
            text = { Text("確定要刪除此記錄嗎？此操作不可恢復。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRecord(recordId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun parseImagePaths(path: String): List<String> {
    if (path.isBlank()) return emptyList()
    return path.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
}

@Composable
private fun RecordInfoCard(record: Map<String, Any>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "基本資訊",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow("廠商", record["manufacturer_name"] as? String ?: "")
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            InfoRow("產線", record["production_line_name"] as? String ?: "")
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            InfoRow("型號", record["model_name"] as? String ?: "")
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            InfoRow("檢驗員", record["inspector_name"] as? String ?: "")

            val reviewerName = record["reviewer_name"] as? String
            if (!reviewerName.isNullOrBlank()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                InfoRow("審核員", reviewerName)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            InfoRow("日期", record["inspection_date"] as? String ?: "")

            // 审核状态
            val status = record["review_status"] as? String
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "審核狀態",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = statusColor(status),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusLabel(status),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 测试站
            val stationNames = record["test_station_names"] as? List<*>
            if (!stationNames.isNullOrEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "測試站:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stationNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun StationGroupCard(group: StationGroupUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 测试站标题
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = group.stationName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            group.items.forEachIndexed { index, item ->
                DetailItemRow(item)
                if (index < group.items.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(item: DetailItemUi) {
    Column {
        // 主项 - 名称和值
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(12.dp))
            // 值徽章
            val resultValue = when {
                item.value == "NA" || item.value == "PASS" || item.value == "FAIL" -> item.value
                else -> item.value ?: "-"
            }
            val bgColor = when (item.value) {
                "FAIL" -> MaterialTheme.colorScheme.errorContainer
                "NA" -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            }
            val textColor = when (item.value) {
                "FAIL" -> MaterialTheme.colorScheme.onErrorContainer
                "NA" -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
            if (item.value in listOf("PASS", "FAIL", "NA")) {
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = resultValue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (item.value == "FAIL") FontWeight.Bold else FontWeight.Normal
                    )
                }
            } else {
                Text(
                    text = resultValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (item.value == "FAIL") FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // 图片
        val imagePaths = parseImagePaths(item.imagePath ?: "")
        if (imagePaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imagePaths.forEach { path ->
                    DetailImage(
                        imageUrl = "${ApiClient.BASE_URL.trimEnd('/')}$path",
                        modifier = Modifier
                            .width(140.dp)
                            .height(105.dp)
                    )
                }
            }
        }

        // 备注
        if (!item.remarks.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = item.remarks,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }

        // 细项
        if (item.details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            // 细项标题
            Text(
                text = "細項檢查",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    item.details.forEachIndexed { idx, detail ->
                        DetailSubItemRow(detail)
                        if (idx < item.details.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSubItemRow(detail: DetailSubItemUi) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = detail.detailName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            val detailBg = when (detail.value) {
                "FAIL" -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            val detailColor = when (detail.value) {
                "FAIL" -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            if (detail.value in listOf("PASS", "FAIL", "NA")) {
                Surface(
                    color = detailBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = detail.value ?: "-",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = detailColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (detail.value == "FAIL") FontWeight.Bold else FontWeight.Normal
                    )
                }
            } else {
                Text(
                    text = detail.value ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = detailColor
                )
            }
        }

        // 子项图片
        val subImagePaths = parseImagePaths(detail.imagePath ?: "")
        if (subImagePaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                subImagePaths.forEach { path ->
                    DetailImage(
                        imageUrl = "${ApiClient.BASE_URL.trimEnd('/')}$path",
                        modifier = Modifier
                            .width(100.dp)
                            .height(75.dp)
                    )
                }
            }
        }

        // 子项备注
        if (!detail.remarks.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "備註: ${detail.remarks}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = "檢查圖片",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
