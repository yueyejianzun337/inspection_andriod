package com.eqm.inspection.ui.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QueryUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val records: List<Map<String, Any>> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val totalPages: Int = 1,
    // 筛选条件
    val selectedManufacturerId: String? = null,
    val selectedModelId: String? = null,
    val selectedReviewerId: String? = null,
    val selectedStatus: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val perPage: Int = 20
)

class QueryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QueryUiState())
    val uiState: StateFlow<QueryUiState> = _uiState

    private val _initData = MutableStateFlow<Map<String, Any>?>(null)
    val initData: StateFlow<Map<String, Any>?> = _initData

    init {
        loadInitData()
        search()
    }

    private fun loadInitData() {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getInitData()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        _initData.value = mapOf(
                            "manufacturers" to data.manufacturers,
                            "models" to data.models,
                            "reviewers" to data.reviewers,
                            "current_user_role" to data.currentUser.role,
                            "is_vendor" to (data.currentUser.role == "vendor"),
                            "vendor_manufacturer_ids" to (data.currentUser.manufacturerIds ?: emptyList<Int>())
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun setManufacturer(id: String?) {
        _uiState.value = _uiState.value.copy(selectedManufacturerId = id, page = 1)
        search()
    }

    fun setModel(id: String?) {
        _uiState.value = _uiState.value.copy(selectedModelId = id, page = 1)
        search()
    }

    fun setReviewer(id: String?) {
        _uiState.value = _uiState.value.copy(selectedReviewerId = id, page = 1)
        search()
    }

    fun setStatus(status: String?) {
        _uiState.value = _uiState.value.copy(selectedStatus = status, page = 1)
        search()
    }

    fun setDateRange(start: String?, end: String?) {
        _uiState.value = _uiState.value.copy(startDate = start, endDate = end, page = 1)
        search()
    }

    fun goToPage(page: Int) {
        _uiState.value = _uiState.value.copy(page = page)
        search()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(page = 1)
        search()
    }

    fun search() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val response = ApiClient.apiService.queryRecords(
                    page = state.page,
                    perPage = state.perPage,
                    manufacturerId = state.selectedManufacturerId,
                    modelId = state.selectedModelId,
                    reviewerId = state.selectedReviewerId,
                    reviewStatus = state.selectedStatus,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    isDraft = "0"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val records = body.data.map { record ->
                            mapOf(
                                "id" to record.id,
                                "manufacturer_name" to (record.manufacturerName ?: ""),
                                "production_line_name" to (record.productionLineName ?: ""),
                                "model_name" to (record.modelName ?: ""),
                                "inspector_name" to (record.inspectorName ?: ""),
                                "reviewer_name" to (record.reviewerName ?: ""),
                                "test_station_names" to (record.testStationNames ?: ""),
                                "inspection_date" to (record.inspectionDate ?: ""),
                                "review_status" to (record.reviewStatus ?: ""),
                                "created_at" to (record.createdAt ?: "")
                            )
                        }
                        val totalPages = if (body.total > 0)
                            (body.total + state.perPage - 1) / state.perPage else 1
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            records = records,
                            total = body.total,
                            totalPages = totalPages,
                            page = body.page
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "查询失败 (${response.code()})"
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
}
