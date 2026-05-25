package com.eqm.inspection.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val totalRecords: Int = 0,
    val totalModels: Int = 0,
    val monthlyData: List<Map<String, Any>> = emptyList(),
    val modelData: List<Map<String, Any>> = emptyList(),
    val failRecords: List<Map<String, Any>> = emptyList()
)

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = DashboardUiState(isLoading = true)
            try {
                val response = ApiClient.apiService.getDashboard()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        _uiState.value = DashboardUiState(
                            isLoading = false,
                            totalRecords = data.totalRecords,
                            totalModels = data.totalModels,
                            monthlyData = data.monthlyData.map {
                                mapOf("month" to it.month, "count" to it.count)
                            },
                            modelData = data.modelData.map {
                                mapOf("model_name" to it.modelName, "count" to it.count)
                            },
                            failRecords = data.failRecords.map {
                                mapOf(
                                    "id" to it.id,
                                    "inspection_date" to it.inspectionDate,
                                    "model_name" to it.modelName,
                                    "line_name" to (it.lineName ?: ""),
                                    "status" to it.status
                                )
                            }
                        )
                    } else {
                        _uiState.value = DashboardUiState(
                            isLoading = false,
                            error = body?.message ?: "加载失败"
                        )
                    }
                } else {
                    _uiState.value = DashboardUiState(
                        isLoading = false,
                        error = "加载失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }
}
