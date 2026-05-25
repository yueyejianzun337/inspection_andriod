package com.eqm.inspection.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.data.api.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class InspectionFormData(
    val manufacturerId: Int? = null,
    val productionLineId: Int? = null,
    val modelId: Int? = null,
    val reviewerId: Int? = null,
    val inspectionDate: String = "",
    val selectedTestStationIds: List<Int> = emptyList(),
    val skipTestStationIds: List<Int> = emptyList(),
    val inspectorName: String = ""
)

data class FillItem(
    val itemId: Int,
    val itemName: String,
    val inputType: String,
    val options: String?,
    val testStationId: Int?,
    val testStationName: String?,
    var value: String = "",
    var imagePath: String = "",
    var remarks: String = "",
    val details: MutableList<FillDetailItem> = mutableListOf()
)

data class FillDetailItem(
    val itemId: String,
    val detailId: Int,
    val detailName: String,
    val inputType: String,
    val options: String?,
    var value: String = "",
    var imagePath: String = "",
    var remarks: String = ""
)

data class InspectionUiState(
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val errorVersion: Int = 0,  // 遞增確保 LaunchedEffect key 每次不同
    val successMessage: String? = null,
    val submittedRecordId: Int? = null,
    // Init data
    val initData: InitData? = null,
    // Form
    val form: InspectionFormData = InspectionFormData(),
    // Production lines for selected manufacturer
    val productionLines: List<IdNamePair> = emptyList(),
    // Inspection items
    val inspectionItems: List<FillItem> = emptyList(),
    val isDraft: Boolean = false,
    val draftId: Int? = null
)

class InspectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState

    // 缓存回填结果
    private var draftResultMap: Map<String, DraftResult> = emptyMap()

    init {
        loadInitData()
    }

    fun loadInitData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = ApiClient.apiService.getInitData()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        // 設置默認日期為今天
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        // 如果是编辑草稿（draftId 已设置），保留已有的 form 数据
                        val existingForm = _uiState.value.form
                        val isEditingDraft = _uiState.value.draftId != null
                        val form = if (isEditingDraft && existingForm.inspectionDate.isNotBlank()) {
                            existingForm
                        } else {
                            // 如果是vendor角色，自动选择厂商（有多個時選第一個）
                            val vendorMfrId = data.currentUser.manufacturerIds?.firstOrNull()
                                ?: data.currentUser.vendorManufacturerId
                            var newForm = InspectionFormData(inspectionDate = today)
                            if (vendorMfrId != null) {
                                newForm = newForm.copy(manufacturerId = vendorMfrId)
                                // 加载该厂商的产线
                                loadProductionLines(vendorMfrId)
                            }
                            newForm
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            initData = data,
                            form = form
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.message ?: "加载初始化数据失败"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }

    fun loadProductionLines(manufacturerId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getProductionLines(manufacturerId)
                if (response.isSuccessful) {
                    val lines = response.body() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        form = _uiState.value.form.copy(manufacturerId = manufacturerId),
                        productionLines = lines
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateForm(form: InspectionFormData) {
        _uiState.value = _uiState.value.copy(form = form)
    }

    fun updateProductionLine(id: Int?) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(productionLineId = id)
        )
    }

    fun updateModel(id: Int?) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(modelId = id)
        )
    }

    fun updateReviewer(id: Int?) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(reviewerId = id)
        )
    }

    fun updateDate(date: String) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(inspectionDate = date)
        )
    }

    fun updateTestStations(ids: List<Int>) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(selectedTestStationIds = ids)
        )
    }

    fun updateInspectorName(name: String) {
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(inspectorName = name)
        )
    }

    fun toggleSkipTestStation(stationId: Int) {
        val current = _uiState.value.form.skipTestStationIds
        val newIds = if (stationId in current) current - stationId else current + stationId
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(skipTestStationIds = newIds)
        )
    }

    fun loadInspectionItems() {
        val state = _uiState.value
        val testStationIds = state.form.selectedTestStationIds

        // 清除舊錯誤，確保底部 Snackbar 能重複觸發
        _uiState.value = state.copy(error = null)

        if (testStationIds.isEmpty()) {
            setError("請先選擇測試站")
            return
        }
        if (state.form.productionLineId == null) {
            setError("請先選擇產線")
            return
        }
        if (state.form.modelId == null) {
            setError("請先選擇型號")
            return
        }
        if (state.form.reviewerId == null) {
            setError("請先選擇審核員")
            return
        }
        if (state.form.inspectionDate.isBlank()) {
            setError("請先選擇日期")
            return
        }
        if (state.form.inspectorName.isBlank()) {
            setError("請填寫巡檢員")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = ApiClient.apiService.loadInspectionItems(
                    com.eqm.inspection.data.api.TestStationIdsBody(testStationIds)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val items = body.inspectionItems.map { item ->
                            FillItem(
                                itemId = item.id,
                                itemName = item.name,
                                inputType = item.inputType,
                                options = item.options,
                                testStationId = item.testStationId,
                                testStationName = item.testStationName,
                                value = item.defaultValue ?: "",
                                imagePath = item.defaultImagePath ?: "",
                                remarks = item.defaultRemarks ?: "",
                                details = body.projectDetails
                                    .filter { it.inspectionItemId == item.id }
                                    .map { detail ->
                                        FillDetailItem(
                                            itemId = "detail_${detail.id}",
                                            detailId = detail.id,
                                            detailName = detail.name,
                                            inputType = detail.inputType,
                                            options = detail.options,
                                            value = detail.defaultValue ?: "",
                                            imagePath = detail.defaultImagePath ?: "",
                                            remarks = detail.defaultRemarks ?: ""
                                        )
                                    }.toMutableList()
                            )
                        }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            inspectionItems = items
                        )
                        // 回填草稿数据
                        applyDraftResults()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "加载巡检项目失败"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载巡检项目失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }

    fun loadDraft(draftId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, draftId = draftId)
            try {
                val response = ApiClient.apiService.getDraftData(draftId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        val record = data.draftRecord

                        // 构建草稿结果映射
                        draftResultMap = data.draftResults.associateBy { it.itemId ?: "" }

                        // 回填表单
                        var form = InspectionFormData(
                            inspectionDate = record.inspectionDate ?: "",
                            reviewerId = record.reviewerId,
                            selectedTestStationIds = parseTestStationIds(record.testStationId),
                            inspectorName = record.inspectorName ?: ""
                        )

                        // 设置厂商（如果有）
                        if (record.manufacturerId != null) {
                            form = form.copy(manufacturerId = record.manufacturerId)
                            loadProductionLines(record.manufacturerId)
                        }
                        if (record.modelId != null) {
                            form = form.copy(modelId = record.modelId)
                        }
                        if (record.productionLineId != null) {
                            form = form.copy(productionLineId = record.productionLineId)
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            form = form,
                            isDraft = true
                        )

                        // 加载巡检项目（回填数据会在加载后自动应用）
                        loadInspectionItems()
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = body?.error ?: "加载草稿失败"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "加载草稿失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun parseTestStationIds(testStationId: String?): List<Int> {
        if (testStationId.isNullOrBlank()) return emptyList()
        return testStationId.split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it != 0 }
    }

    private fun parseImagePaths(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        return path.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun joinImagePaths(paths: List<String>): String {
        return paths.joinToString(",")
    }

    private fun applyDraftResults() {
        if (draftResultMap.isEmpty()) return
        val items = _uiState.value.inspectionItems.toMutableList()
        for (i in items.indices) {
            val item = items[i]
            // 查找主项结果
            val mainResult = draftResultMap[item.itemId.toString()]
            if (mainResult != null) {
                items[i] = item.copy(
                    value = mainResult.value ?: "",
                    imagePath = mainResult.imagePath ?: "",
                    remarks = mainResult.remarks ?: ""
                )
            }
            // 查找细项结果
            val details = item.details.toMutableList()
            for (j in details.indices) {
                val detail = details[j]
                val detailResult = draftResultMap[detail.itemId]
                if (detailResult != null) {
                    details[j] = detail.copy(
                        value = detailResult.value ?: "",
                        imagePath = detailResult.imagePath ?: "",
                        remarks = detailResult.remarks ?: ""
                    )
                }
            }
            items[i] = items[i].copy(details = details)
        }
        _uiState.value = _uiState.value.copy(inspectionItems = items)
        draftResultMap = emptyMap()
    }

    fun updateItemValue(itemIndex: Int, value: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val item = items[itemIndex]
            items[itemIndex] = item.copy(value = value)

            // NA联动：如果主项选NA，将所有细项也设为NA
            if (value == "NA") {
                val details = item.details.toMutableList()
                for (j in details.indices) {
                    if (details[j].inputType != "checkbox" && details[j].inputType != "radio") {
                        details[j] = details[j].copy(value = "NA")
                    }
                }
                items[itemIndex] = items[itemIndex].copy(details = details)
            }

            _uiState.value = _uiState.value.copy(inspectionItems = items)
        }
    }

    fun updateItemImage(itemIndex: Int, imagePath: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val item = items[itemIndex]
            val existingPaths = parseImagePaths(item.imagePath)
            val newPaths = existingPaths + imagePath
            items[itemIndex] = item.copy(imagePath = joinImagePaths(newPaths))
            _uiState.value = _uiState.value.copy(inspectionItems = items)
        }
    }

    fun removeItemImage(itemIndex: Int, imagePathToRemove: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val item = items[itemIndex]
            val existingPaths = parseImagePaths(item.imagePath)
            val newPaths = existingPaths.filter { it != imagePathToRemove }
            items[itemIndex] = item.copy(imagePath = joinImagePaths(newPaths))
            _uiState.value = _uiState.value.copy(inspectionItems = items)
        }
    }

    fun updateItemRemarks(itemIndex: Int, remarks: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            items[itemIndex] = items[itemIndex].copy(remarks = remarks)
            _uiState.value = _uiState.value.copy(inspectionItems = items)
        }
    }

    fun updateDetailValue(itemIndex: Int, detailIndex: Int, value: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val details = items[itemIndex].details.toMutableList()
            if (detailIndex in details.indices) {
                details[detailIndex] = details[detailIndex].copy(value = value)
                items[itemIndex] = items[itemIndex].copy(details = details)
                _uiState.value = _uiState.value.copy(inspectionItems = items)
            }
        }
    }

    fun updateDetailImage(itemIndex: Int, detailIndex: Int, imagePath: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val details = items[itemIndex].details.toMutableList()
            if (detailIndex in details.indices) {
                val detail = details[detailIndex]
                val existingPaths = parseImagePaths(detail.imagePath)
                val newPaths = existingPaths + imagePath
                details[detailIndex] = detail.copy(imagePath = joinImagePaths(newPaths))
                items[itemIndex] = items[itemIndex].copy(details = details)
                _uiState.value = _uiState.value.copy(inspectionItems = items)
            }
        }
    }

    fun removeDetailImage(itemIndex: Int, detailIndex: Int, imagePathToRemove: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val details = items[itemIndex].details.toMutableList()
            if (detailIndex in details.indices) {
                val detail = details[detailIndex]
                val existingPaths = parseImagePaths(detail.imagePath)
                val newPaths = existingPaths.filter { it != imagePathToRemove }
                details[detailIndex] = detail.copy(imagePath = joinImagePaths(newPaths))
                items[itemIndex] = items[itemIndex].copy(details = details)
                _uiState.value = _uiState.value.copy(inspectionItems = items)
            }
        }
    }

    fun updateDetailRemarks(itemIndex: Int, detailIndex: Int, remarks: String) {
        val items = _uiState.value.inspectionItems.toMutableList()
        if (itemIndex in items.indices) {
            val details = items[itemIndex].details.toMutableList()
            if (detailIndex in details.indices) {
                details[detailIndex] = details[detailIndex].copy(remarks = remarks)
                items[itemIndex] = items[itemIndex].copy(details = details)
                _uiState.value = _uiState.value.copy(inspectionItems = items)
            }
        }
    }

    fun submit(isDraft: Boolean) {
        val state = _uiState.value
        val form = state.form

        // 清除舊錯誤，確保底部 Snackbar 能重複觸發
        _uiState.value = state.copy(error = null)

        // 验证厂商
        if (form.manufacturerId == null) {
            setError("請選擇廠商")
            return
        }

        // 验证必填字段
        if (form.productionLineId == null) {
            setError("請選擇產線")
            return
        }
        if (form.modelId == null) {
            setError("請選擇型號")
            return
        }
        if (form.reviewerId == null) {
            setError("請選擇審核員")
            return
        }
        if (form.inspectionDate.isBlank()) {
            setError("請選擇日期")
            return
        }
        if (form.selectedTestStationIds.isEmpty()) {
            setError("請選擇測試站")
            return
        }
        if (form.inspectorName.isBlank()) {
            setError("請填寫巡檢員")
            return
        }

        // 验证检测项目（仅正式提交时需要）
        if (!isDraft) {
            if (state.inspectionItems.isEmpty()) {
                setError("請先加載並填寫巡檢項目")
                return
            }
            val skipIds = form.skipTestStationIds.toSet()
            for (item in state.inspectionItems) {
                val isSkipped = item.testStationId != null && item.testStationId in skipIds

                // 验证主项目
                if (!isSkipped && item.inputType != "checkbox" && item.inputType != "radio") {
                    if (item.value.isBlank()) {
                        setError("請填寫「${item.itemName}」的檢查結果")
                        return
                    }
                }

                // 验证细项
                for (detail in item.details) {
                    if (!isSkipped && detail.inputType != "checkbox" && detail.inputType != "radio") {
                        if (detail.value.isBlank()) {
                            setError("請填寫「${detail.detailName}」的檢查結果")
                            return
                        }
                    }
                }
            }
        }

        // 暫存時也檢查是否有項目（沒有項目無法暫存）
        if (isDraft && state.inspectionItems.isEmpty()) {
            setError("請先加載巡檢項目")
            return
        }

        val request = SubmitRequest(
            productionLineId = form.productionLineId,
            modelId = form.modelId,
            reviewerId = form.reviewerId,
            inspectionDate = form.inspectionDate,
            testStationIds = form.selectedTestStationIds,
            skipTestStationIds = form.skipTestStationIds,
            isDraft = isDraft,
            manufacturerId = form.manufacturerId,
            draftId = state.draftId,
            inspectorName = form.inspectorName,
            items = state.inspectionItems.map { item ->
                SubmitItem(
                    itemId = item.itemId,
                    value = item.value,
                    imagePath = item.imagePath,
                    remarks = item.remarks,
                    testStationId = item.testStationId,
                    details = item.details.map { detail ->
                        SubmitDetailItem(
                            itemId = detail.itemId,
                            value = detail.value,
                            imagePath = detail.imagePath,
                            remarks = detail.remarks
                        )
                    }
                )
            }
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            try {
                val response = ApiClient.apiService.submitInspection(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            successMessage = if (isDraft) "草稿保存成功" else "提交成功",
                            submittedRecordId = body.recordId,
                            isDraft = isDraft
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isSubmitting = false)
                        setError(body?.message ?: "提交失败")
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isSubmitting = false)
                    setError("提交失败 (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
                setError("网络错误: ${e.localizedMessage}")
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }

    private fun setError(message: String) {
        val current = _uiState.value
        _uiState.value = current.copy(error = message, errorVersion = current.errorVersion + 1)
    }

    fun reset() {
        _uiState.value = InspectionUiState()
        draftResultMap = emptyMap()
        loadInitData()
    }
}
