package com.eqm.inspection.ui.inspection

import android.app.DatePickerDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eqm.inspection.ui.components.*
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.data.api.models.IdNamePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    draftId: Int? = null,
    reviewRecordId: Int? = null,
    onBack: () -> Unit,
    onSubmitted: (recordId: Int) -> Unit,
    onDraftSaved: () -> Unit,
    viewModel: InspectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ===== 共享图片上传 Launcher（避免在 LazyColumn 中重复创建导致崩溃） =====
    var pendingImageCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    // 相册选择
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingImageCallback?.let { callback ->
                uploadImage(context, scope, it) { path -> callback(path) }
            }
        }
    }

    // 拍照
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && photoUri != null) {
            pendingImageCallback?.let { callback ->
                uploadImage(context, scope, photoUri!!) { path -> callback(path) }
            }
        }
    }

    // Camera 权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            pendingImageCallback?.let { callback ->
                val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                photoUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                pendingImageCallback = callback
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                }
                cameraLauncher.launch(intent)
            }
        } else {
            pendingImageCallback = null
        }
    }

    fun takePhoto(callback: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
            photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingImageCallback = callback
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            }
            cameraLauncher.launch(intent)
        } else {
            pendingImageCallback = callback
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun pickFromGallery(callback: (String) -> Unit) {
        pendingImageCallback = callback
        imagePickerLauncher.launch("image/*")
    }
    // ==================================================================

    // 加载草稿
    LaunchedEffect(draftId) {
        if (draftId != null && draftId > 0) {
            viewModel.loadDraft(draftId)
        }
    }

    // 加载审核数据
    LaunchedEffect(reviewRecordId) {
        if (reviewRecordId != null && reviewRecordId > 0) {
            viewModel.loadReviewData(reviewRecordId)
        }
    }

    // 处理成功
    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null && uiState.submittedRecordId != null) {
            if (uiState.isDraft) {
                onDraftSaved()
            } else {
                onSubmitted(uiState.submittedRecordId!!)
            }
        }
    }

    // 验证错误时弹出 Snackbar（確保底部也能看到提示，不受滾動位置影響）
    LaunchedEffect(uiState.error, uiState.errorVersion) {
        if (uiState.error != null) {
            // 短暫延遲確保 LazyColumn 已顯示頂部 Card 後再彈 Snackbar
            kotlinx.coroutines.delay(200)
            snackbarHostState.showSnackbar(
                message = uiState.error!!,
                duration = SnackbarDuration.Long
            )
        }
    }

    // 巡檢員本地記憶（SharedPreferences）— 在 init 完成後才恢復
    val prefs = remember { context.getSharedPreferences("vendor_inspection", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(uiState.initData) {
        if (uiState.initData != null && uiState.form.inspectorName.isEmpty()) {
            val savedName = prefs.getString("inspector_name", "") ?: ""
            if (savedName.isNotEmpty()) {
                viewModel.updateInspectorName(savedName)
            }
        }
    }

    // 日期选择器
    if (showDatePicker) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                cal.set(year, month, day)
                viewModel.updateDate(fmt.format(cal.time))
                showDatePicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when {
                    reviewRecordId != null -> "審核記錄"
                    draftId != null -> "編輯草稿"
                    else -> "廠商巡檢填寫"
                },
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading && uiState.initData == null -> {
                LoadingIndicator(modifier = Modifier.padding(padding))
            }
            uiState.error != null && uiState.initData == null -> {
                ErrorMessage(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadInitData() },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 错误提示
                    if (uiState.error != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = uiState.error!!,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // 基本表单
                    item { FormSection(title = "基本資訊") }

                    // 厂商
                    uiState.initData?.let { data ->
                        val isVendor = data.currentUser.role == "vendor"
                        val mfrIds = data.currentUser.manufacturerIds
                        val vendorHasMultiMfr = isVendor && mfrIds != null && mfrIds.size > 1
                        val vendorMfrName = if (isVendor && !vendorHasMultiMfr) {
                            data.currentUser.vendorManufacturerName
                        } else null

                        item {
                            DropdownField(
                                label = "廠商",
                                value = if (isVendor && vendorMfrName != null) vendorMfrName
                                    else data.manufacturers.find { it.id == uiState.form.manufacturerId }?.name ?: "",
                                enabled = !isVendor || vendorHasMultiMfr,
                                placeholder = "選擇廠商",
                                options = data.manufacturers.map { it.name to it.id },
                                onSelected = { id -> viewModel.loadProductionLines(id) }
                            )
                        }

                        // 产线
                        item {
                            DropdownField(
                                label = "產線",
                                value = uiState.productionLines.find { it.id == uiState.form.productionLineId }?.name ?: "",
                                placeholder = "請先選擇廠商",
                                options = uiState.productionLines.map { it.name to it.id },
                                onSelected = { id -> viewModel.updateProductionLine(id) },
                                enabled = uiState.productionLines.isNotEmpty()
                            )
                        }

                        // 型号
                        item {
                            DropdownField(
                                label = "型號",
                                value = data.models.find { it.id == uiState.form.modelId }?.name ?: "",
                                placeholder = "選擇型號",
                                options = data.models.map { it.name to it.id },
                                onSelected = { id -> viewModel.updateModel(id) }
                            )
                        }

                        // 审核员
                        item {
                            DropdownField(
                                label = "審核員",
                                value = data.reviewers.find { it.id == uiState.form.reviewerId }?.let {
                                    it.username ?: it.name
                                } ?: "",
                                placeholder = "選擇審核員",
                                options = data.reviewers.map {
                                    (it.username ?: it.name) to it.id
                                },
                                onSelected = { id -> viewModel.updateReviewer(id) }
                            )
                        }

                        // 巡檢員
                        item {
                            OutlinedTextField(
                                value = uiState.form.inspectorName,
                                onValueChange = {
                                    viewModel.clearMessages()
                                    viewModel.updateInspectorName(it)
                                    // 輸入時即時保存到本地（更穩健）
                                    prefs.edit().putString("inspector_name", it).apply()
                                },
                                label = { Text("巡檢員") },
                                placeholder = { Text("輸入巡檢員名稱") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        // 日期
                        item {
                            OutlinedTextField(
                                value = uiState.form.inspectionDate,
                                onValueChange = {},
                                label = { Text("巡檢日期") },
                                readOnly = true,
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { showDatePicker = true }) {
                                        Text("選擇")
                                    }
                                }
                            )
                        }

                        // 测试站 (多选)
                        item {
                            MultiSelectField(
                                label = "測試站",
                                selectedIds = uiState.form.selectedTestStationIds,
                                options = data.testStations,
                                onSelectionChanged = { ids ->
                                    viewModel.updateTestStations(ids)
                                }
                            )
                        }

                        // 加载巡检项目按钮
                        item {
                            Button(
                                onClick = {
                                    // 保存巡檢員到本地記憶
                                    prefs.edit().putString("inspector_name", uiState.form.inspectorName).apply()
                                    viewModel.loadInspectionItems()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState.form.selectedTestStationIds.isNotEmpty()
                            ) {
                                Text("加載巡檢項目")
                            }
                        }
                    }

                    // 巡检项目列表
                    if (uiState.inspectionItems.isNotEmpty()) {
                        item { FormSection(title = "巡檢項目") }

                        // 按测试站分组
                        val groupedItems = uiState.inspectionItems.groupBy { it.testStationName ?: "未分類" }
                        groupedItems.forEach { (stationName, items) ->
                            // 获取该测试站的ID
                            val stationId = items.firstOrNull()?.testStationId
                            val isSkipped = stationId != null && stationId in uiState.form.skipTestStationIds

                            // 测试站标题行 + 可不测开关
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSkipped)
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "【測試站】$stationName",
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (stationId != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "可不測",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Switch(
                                                    checked = isSkipped,
                                                    onCheckedChange = { viewModel.toggleSkipTestStation(stationId) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isSkipped) {
                                // 跳过的测试站显示提示信息
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        )
                                    ) {
                                        Text(
                                            text = "此測試站已設為「可不測」，檢測項目將全部以 NA 提交",
                                            modifier = Modifier.padding(12.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items.forEachIndexed { _, item ->
                                    // 找到全局索引
                                    val actualIndex = uiState.inspectionItems.indexOfFirst { it.itemId == item.itemId }
                                    item {
                                        InspectionItemCard(
                                            item = item,
                                            onValueChange = { v -> viewModel.updateItemValue(actualIndex, v) },
                                            onImageChange = { p -> viewModel.updateItemImage(actualIndex, p) },
                                            onRemoveImage = { p -> viewModel.removeItemImage(actualIndex, p) },
                                            onRemarksChange = { r -> viewModel.updateItemRemarks(actualIndex, r) },
                                            onPickFromGallery = { cb -> pickFromGallery(cb) },
                                            onTakePhoto = { cb -> takePhoto(cb) }
                                        )
                                    }

                                    // 细项
                                    item.details.forEachIndexed { detailIndex, detail ->
                                        item {
                                            DetailItemCard(
                                                detail = detail,
                                                onValueChange = { v ->
                                                    viewModel.updateDetailValue(actualIndex, detailIndex, v)
                                                },
                                                onImageChange = { p ->
                                                    viewModel.updateDetailImage(actualIndex, detailIndex, p)
                                                },
                                                onRemoveImage = { p ->
                                                    viewModel.removeDetailImage(actualIndex, detailIndex, p)
                                                },
                                                onRemarksChange = { r ->
                                                    viewModel.updateDetailRemarks(actualIndex, detailIndex, r)
                                                },
                                                onPickFromGallery = { cb -> pickFromGallery(cb) },
                                                onTakePhoto = { cb -> takePhoto(cb) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 提交按钮
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!uiState.isReviewMode) {
                                OutlinedButton(
                                    onClick = { viewModel.submit(isDraft = true) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isSubmitting && uiState.inspectionItems.isNotEmpty()
                                ) {
                                    Text("暫存")
                                }
                            }
                            val submitLabel = if (uiState.isReviewMode) "確認審核" else "提交"
                            val submitColor = if (uiState.isReviewMode) Color(0xFF4CAF50) else null
                            Button(
                                onClick = { viewModel.submit(isDraft = false) },
                                modifier = Modifier.weight(1f),
                                enabled = !uiState.isSubmitting && uiState.inspectionItems.isNotEmpty(),
                                colors = if (submitColor != null) {
                                    ButtonDefaults.buttonColors(containerColor = submitColor)
                                } else {
                                    ButtonDefaults.buttonColors()
                                }
                            ) {
                                if (uiState.isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(submitLabel)
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FormSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DropdownField(
    label: String,
    value: String,
    enabled: Boolean = true,
    placeholder: String,
    options: List<Pair<String, Int>>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (name, id) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MultiSelectField(
    label: String,
    selectedIds: List<Int>,
    options: List<IdNamePair>,
    onSelectionChanged: (List<Int>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedNames = options.filter { it.id in selectedIds }.joinToString(", ") { it.name }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedNames.ifEmpty { "選擇測試站 (可多選)" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                val isSelected = option.id in selectedIds
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.name)
                        }
                    },
                    onClick = {
                        val newIds = if (isSelected) {
                            selectedIds - option.id
                        } else {
                            selectedIds + option.id
                        }
                        onSelectionChanged(newIds)
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun InspectionItemCard(
    item: FillItem,
    onValueChange: (String) -> Unit,
    onImageChange: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onPickFromGallery: (callback: (String) -> Unit) -> Unit,
    onTakePhoto: (callback: (String) -> Unit) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 检查站名称
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 根据 input_type 渲染不同控件
            when (item.inputType) {
                "select", "dropdown" -> {
                    val options = parseOptions(item.options)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = item.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("結果") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onValueChange(opt)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                "checkbox", "radio" -> {
                    val options = parseOptions(item.options)
                    Column {
                        options.forEach { opt ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = item.value == opt,
                                    onClick = { onValueChange(opt) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(opt, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                else -> { // text, number, etc.
                    OutlinedTextField(
                        value = item.value,
                        onValueChange = onValueChange,
                        label = { Text("結果") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = item.inputType != "textarea"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 图片上传按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPickFromGallery(onImageChange) },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("相冊", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onTakePhoto(onImageChange) },
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拍照", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 已上传图片缩略图
            val imagePaths = parseImagePaths(item.imagePath)
            if (imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已上傳圖片 (${imagePaths.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 使用 FlowRow 包裹多张图片
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imagePaths.forEach { path ->
                        Box(modifier = Modifier.width(100.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("${ApiClient.BASE_URL.trimEnd('/')}$path")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "圖片",
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(75.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            // 删除按钮
                            IconButton(
                                onClick = { onRemoveImage(path) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "刪除圖片",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 备注
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = item.remarks,
                onValueChange = onRemarksChange,
                label = { Text("備註") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DetailItemCard(
    detail: FillDetailItem,
    onValueChange: (String) -> Unit,
    onImageChange: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onPickFromGallery: (callback: (String) -> Unit) -> Unit,
    onTakePhoto: (callback: (String) -> Unit) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "  ${detail.detailName}",
                style = MaterialTheme.typography.bodySmall
            )

            when (detail.inputType) {
                "select", "dropdown" -> {
                    val options = parseOptions(detail.options)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = detail.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("結果") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        onValueChange(opt)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                "checkbox", "radio" -> {
                    val options = parseOptions(detail.options)
                    Row {
                        options.forEach { opt ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = detail.value == opt,
                                    onClick = { onValueChange(opt) }
                                )
                                Text(opt, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = detail.value,
                        onValueChange = onValueChange,
                        label = { Text("結果") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 图片上传按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onPickFromGallery(onImageChange) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("相冊", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onTakePhoto(onImageChange) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("拍照", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 已上传图片缩略图
            val imagePaths = parseImagePaths(detail.imagePath)
            if (imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "圖片 (${imagePaths.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    imagePaths.forEach { path ->
                        Box(modifier = Modifier.width(80.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("${ApiClient.BASE_URL.trimEnd('/')}$path")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "細項圖片",
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { onRemoveImage(path) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "刪除圖片",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = detail.remarks,
                onValueChange = onRemarksChange,
                label = { Text("備註") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

private fun parseOptions(options: String?): List<String> {
    if (options.isNullOrBlank()) return emptyList()
    return options.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun parseImagePaths(path: String): List<String> {
    if (path.isBlank()) return emptyList()
    return path.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun uploadImage(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: Uri,
    onSuccess: (String) -> Unit
) {
    scope.launch {
        try {
            // ===== 壓縮圖片（拍照原圖可能很大，縮小到 1920px 以內） =====
            val compressedFile = withContext(Dispatchers.IO) {
                compressImage(context, uri)
            }

            val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaType())
            val part = okhttp3.MultipartBody.Part.createFormData(
                "file", compressedFile.name, requestBody
            )
            val recordIdPart = "temp".toRequestBody("text/plain".toMediaType())

            val response = ApiClient.apiService.uploadImage(part, recordIdPart)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && body.filepath != null) {
                    onSuccess(body.filepath)
                }
            }
            compressedFile.delete()
        } catch (_: Exception) {}
    }
}

/**
 * 壓縮圖片：降解析度到 maxDimension px 以內，JPEG 品質 75%
 * 將照片從 3~10MB 壓縮到約 200~500KB
 */
private fun compressImage(context: android.content.Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    // 先讀取圖片尺寸（不載入 pixel data）
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(inputStream, null, opts)
    inputStream?.close()

    val origWidth = opts.outWidth
    val origHeight = opts.outHeight

    // 計算縮放比例，使最長邊不超過 1920px
    val maxDimension = 1920
    var scale = 1
    while (origWidth / scale > maxDimension || origHeight / scale > maxDimension) {
        scale *= 2
    }

    // 讀取縮放後的 Bitmap
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = scale
    }
    val inputStream2 = context.contentResolver.openInputStream(uri)
    val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOpts)
    inputStream2?.close()

    // 若取不到 bitmap，回退直接複製檔案
    if (bitmap == null) {
        val fallbackFile = java.io.File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            fallbackFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return fallbackFile
    }

    // 將壓縮後的 bitmap 寫入暫存檔
    val compressedFile = java.io.File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
    FileOutputStream(compressedFile).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
    }
    bitmap.recycle()

    return compressedFile
}
