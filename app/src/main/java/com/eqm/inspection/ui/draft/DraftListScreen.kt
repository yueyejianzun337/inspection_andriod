package com.eqm.inspection.ui.draft

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eqm.inspection.ui.components.*
import com.eqm.inspection.ui.theme.StatusDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onNavigateToInspection: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: DraftListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "暫存管理",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.loadDrafts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(modifier = Modifier.padding(padding))
            uiState.error != null -> ErrorMessage(
                message = uiState.error!!,
                onRetry = { viewModel.loadDrafts() },
                modifier = Modifier.padding(padding)
            )
            uiState.drafts.isEmpty() -> EmptyState(
                message = "暫無暫存記錄",
                modifier = Modifier.padding(padding)
            )
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.drafts) { draft ->
                        DraftCard(
                            draft = draft,
                            onEdit = {
                                val id = draft["id"] as? Int ?: return@DraftCard
                                onNavigateToInspection(id)
                            },
                            onDelete = {
                                val id = draft["id"] as? Int ?: return@DraftCard
                                showDeleteDialog = id
                            }
                        )
                    }
                }
            }
        }
    }

    // 删除确认对话框
    showDeleteDialog?.let { draftId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("確認刪除") },
            text = { Text("確定要刪除此暫存記錄嗎？刪除後無法恢復。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draftId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun DraftCard(
    draft: Map<String, Any>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = draft["model_name"] as? String ?: "",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = StatusDraft,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "暫存",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${draft["manufacturer_name"]} / ${draft["production_line_name"]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "檢驗員: ${draft["inspector_name"]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "審核員: ${draft["reviewer_name"]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "日期: ${draft["inspection_date"]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 測試站
            val testStations = draft["test_station_names"] as? String ?: ""
            if (testStations.isNotBlank()) {
                Text(
                    text = "測試站: $testStations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("繼續編輯", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
