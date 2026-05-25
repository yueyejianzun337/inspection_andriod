package com.eqm.inspection.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DraftListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val drafts: List<Map<String, Any>> = emptyList()
)

class DraftListViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DraftListUiState())
    val uiState: StateFlow<DraftListUiState> = _uiState

    init {
        loadDrafts()
    }

    fun loadDrafts() {
        viewModelScope.launch {
            _uiState.value = DraftListUiState(isLoading = true)
            try {
                val response = ApiClient.apiService.getDrafts()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val drafts = body.data.map { draft ->
                            mapOf(
                                "id" to draft.id,
                                "manufacturer_name" to (draft.manufacturerName ?: ""),
                                "production_line_name" to (draft.productionLineName ?: ""),
                                "model_name" to (draft.modelName ?: ""),
                                "inspector_name" to (draft.inspectorName ?: ""),
                                "reviewer_name" to (draft.reviewerName ?: ""),
                                "test_station_names" to (draft.testStationNames ?: ""),
                                "inspection_date" to (draft.inspectionDate ?: ""),
                                "created_at" to (draft.createdAt ?: ""),
                                "manufacturer_id" to (draft.manufacturerId ?: 0),
                                "production_line_id" to (draft.productionLineId ?: 0),
                                "model_id" to (draft.modelId ?: 0),
                                "reviewer_id" to (draft.reviewerId ?: 0),
                                "test_station_id" to (draft.testStationId ?: "")
                            )
                        }
                        _uiState.value = DraftListUiState(
                            isLoading = false,
                            drafts = drafts
                        )
                    } else {
                        _uiState.value = DraftListUiState(
                            isLoading = false,
                            error = body?.message ?: "加载草稿列表失败"
                        )
                    }
                } else {
                    _uiState.value = DraftListUiState(
                        isLoading = false,
                        error = "加载失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DraftListUiState(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }

    fun deleteDraft(draftId: Int) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.deleteDraft(draftId)
                if (response.isSuccessful) {
                    loadDrafts()
                }
            } catch (_: Exception) { }
        }
    }
}
