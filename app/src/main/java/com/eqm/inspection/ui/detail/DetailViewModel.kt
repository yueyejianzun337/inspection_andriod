package com.eqm.inspection.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.util.ReportDownloader
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val record: Map<String, Any>? = null,
    val stationGroups: List<StationGroupUi> = emptyList()
)

data class StationGroupUi(
    val stationName: String,
    val items: List<DetailItemUi>
)

data class DetailItemUi(
    val itemName: String,
    val inputType: String?,
    val value: String?,
    val imagePath: String?,
    val remarks: String?,
    val details: List<DetailSubItemUi>
)

data class DetailSubItemUi(
    val detailName: String,
    val value: String?,
    val imagePath: String?,
    val remarks: String?
)

class DetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState

    fun loadDetail(recordId: Int) {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val response = ApiClient.apiService.getDetail(recordId)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        val record = mapOf(
                            "id" to data.record.id,
                            "manufacturer_name" to (data.record.manufacturerName ?: ""),
                            "production_line_name" to (data.record.productionLineName ?: ""),
                            "model_name" to (data.record.modelName ?: ""),
                            "test_station_names" to (data.record.testStationNames ?: emptyList<String>()),
                            "inspection_date" to (data.record.inspectionDate ?: ""),
                            "inspector_name" to (data.record.inspectorName ?: ""),
                            "reviewer_name" to (data.record.reviewerName ?: ""),
                            "review_status" to (data.record.reviewStatus ?: ""),
                            "is_draft" to (data.record.isDraft ?: 0),
                            "created_at" to (data.record.createdAt ?: "")
                        )

                        val stationGroups = data.inspectionData.map { group ->
                            StationGroupUi(
                                stationName = group.testStationName,
                                items = group.items.map { item ->
                                    DetailItemUi(
                                        itemName = item.itemName,
                                        inputType = item.inputType,
                                        value = item.value,
                                        imagePath = item.imagePath,
                                        remarks = item.remarks,
                                        details = item.details.map { detail ->
                                            DetailSubItemUi(
                                                detailName = detail.detailName,
                                                value = detail.value,
                                                imagePath = detail.imagePath,
                                                remarks = detail.remarks
                                            )
                                        }
                                    )
                                }
                            )
                        }

                        _uiState.value = DetailUiState(
                            isLoading = false,
                            record = record,
                            stationGroups = stationGroups
                        )
                    } else {
                        _uiState.value = DetailUiState(
                            isLoading = false,
                            error = body?.message ?: "加载详情失败"
                        )
                    }
                } else {
                    _uiState.value = DetailUiState(
                        isLoading = false,
                        error = "加载失败 (${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = "网络错误: ${e.localizedMessage}"
                )
            }
        }
    }

    fun downloadReport(context: Context, recordId: Int) {
        ReportDownloader.download(context, recordId, ApiClient.BASE_URL)
    }
}

/**
 * 为 DetailScreen 提供的状态和回调
 */
data class DetailScreenState(
    val uiState: DetailUiState = DetailUiState(),
    val onDownloadReport: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {}
)
