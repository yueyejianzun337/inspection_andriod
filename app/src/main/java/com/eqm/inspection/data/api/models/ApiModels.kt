package com.eqm.inspection.data.api.models

import com.google.gson.annotations.SerializedName

// ===== 通用响应包装 =====
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)

// ===== 登录 =====
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginData(
    val token: String,
    @SerializedName("expires_in") val expiresIn: Long,
    val user: UserInfo
)

data class UserInfo(
    val id: Int,
    val username: String,
    val role: String,
    val manufacturers: List<Int>? = null,
    val models: List<Int>? = null
)

// ===== 初始化数据 =====
data class InitData(
    @SerializedName("current_user") val currentUser: CurrentUserInfo,
    val manufacturers: List<IdNamePair>,
    val models: List<IdNamePair>,
    @SerializedName("test_stations") val testStations: List<IdNamePair>,
    val reviewers: List<IdNamePair>
)

data class CurrentUserInfo(
    val id: Int,
    val username: String,
    val role: String,
    @SerializedName("vendor_manufacturer_id") val vendorManufacturerId: Int? = null,
    @SerializedName("vendor_manufacturer_name") val vendorManufacturerName: String? = null,
    @SerializedName("manufacturer_ids") val manufacturerIds: List<Int>? = null
)

data class IdNamePair(
    val id: Int,
    val name: String,
    val username: String? = null  // reviewers 使用 username
)

// ===== 仪表板 =====
data class DashboardData(
    @SerializedName("total_records") val totalRecords: Int,
    @SerializedName("total_models") val totalModels: Int,
    @SerializedName("monthly_data") val monthlyData: List<MonthlyData>,
    @SerializedName("model_data") val modelData: List<ModelData>,
    @SerializedName("fail_records") val failRecords: List<FailRecord>
)

data class MonthlyData(
    val month: String,
    val count: Int
)

data class ModelData(
    @SerializedName("model_name") val modelName: String,
    val count: Int
)

data class FailRecord(
    val id: Int,
    @SerializedName("inspection_date") val inspectionDate: String,
    @SerializedName("model_name") val modelName: String,
    @SerializedName("line_name") val lineName: String?,
    val status: String
)

// ===== 巡检项目 =====
data class InspectionLoadResponse(
    @SerializedName("inspection_items") val inspectionItems: List<InspectionItem>,
    @SerializedName("project_details") val projectDetails: List<ProjectDetail>
)

data class InspectionItem(
    val id: Int,
    val name: String,
    @SerializedName("input_type") val inputType: String,
    val options: String? = null,
    @SerializedName("test_station_id") val testStationId: Int? = null,
    @SerializedName("test_station_name") val testStationName: String? = null,
    val details: List<ProjectDetail>? = null,
    // 用于草稿回填
    @SerializedName("default_value") var defaultValue: String? = null,
    @SerializedName("default_image_path") var defaultImagePath: String? = null,
    @SerializedName("default_remarks") var defaultRemarks: String? = null
)

data class ProjectDetail(
    val id: Int,
    val name: String,
    @SerializedName("input_type") val inputType: String,
    val options: String? = null,
    @SerializedName("inspection_item_id") val inspectionItemId: Int? = null,
    // 用于草稿回填
    @SerializedName("default_value") var defaultValue: String? = null,
    @SerializedName("default_image_path") var defaultImagePath: String? = null,
    @SerializedName("default_remarks") var defaultRemarks: String? = null
)

// ===== 提交 =====
data class SubmitRequest(
    @SerializedName("production_line_id") val productionLineId: Int,
    @SerializedName("model_id") val modelId: Int,
    @SerializedName("reviewer_id") val reviewerId: Int,
    @SerializedName("inspection_date") val inspectionDate: String,
    @SerializedName("test_station_ids") val testStationIds: List<Int>,
    @SerializedName("skip_test_station_ids") val skipTestStationIds: List<Int> = emptyList(),
    @SerializedName("is_draft") val isDraft: Boolean,
    @SerializedName("manufacturer_id") val manufacturerId: Int? = null,
    @SerializedName("draft_id") val draftId: Int? = null,
    @SerializedName("inspector_name") val inspectorName: String = "",
    val items: List<SubmitItem>
)

data class SubmitItem(
    @SerializedName("item_id") val itemId: Int,
    val value: String,
    @SerializedName("image_path") val imagePath: String = "",
    val remarks: String = "",
    @SerializedName("test_station_id") val testStationId: Int? = null,
    val details: List<SubmitDetailItem> = emptyList()
)

data class SubmitDetailItem(
    @SerializedName("item_id") val itemId: String,  // "detail_XXX" 格式
    val value: String,
    @SerializedName("image_path") val imagePath: String = "",
    val remarks: String = ""
)

data class SubmitResponse(
    val success: Boolean,
    val message: String? = null,
    @SerializedName("record_id") val recordId: Int? = null,
    @SerializedName("is_draft") val isDraft: Boolean? = null,
    @SerializedName("draft_id") val draftId: Int? = null
)

// ===== 查询 =====
data class QueryData(
    val data: List<InspectionRecord>,
    val total: Int,
    val page: Int,
    @SerializedName("per_page") val perPage: Int
)

data class InspectionRecord(
    val id: Int,
    @SerializedName("manufacturer_id") val manufacturerId: Int? = null,
    @SerializedName("manufacturer_name") val manufacturerName: String? = null,
    @SerializedName("production_line_name") val productionLineName: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("inspector_name") val inspectorName: String? = null,
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    @SerializedName("test_station_names") val testStationNames: String? = null,
    @SerializedName("inspection_date") val inspectionDate: String? = null,
    @SerializedName("review_status") val reviewStatus: String? = null,
    @SerializedName("is_draft") val isDraft: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ===== 详情 =====
data class DetailData(
    val record: DetailRecord,
    @SerializedName("inspection_data") val inspectionData: List<StationGroup>
)

data class DetailRecord(
    val id: Int,
    @SerializedName("manufacturer_name") val manufacturerName: String? = null,
    @SerializedName("production_line_name") val productionLineName: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("test_station_names") val testStationNames: List<String>? = null,
    @SerializedName("inspection_date") val inspectionDate: String? = null,
    @SerializedName("inspector_name") val inspectorName: String? = null,
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    @SerializedName("is_draft") val isDraft: Int? = null,
    @SerializedName("review_status") val reviewStatus: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class StationGroup(
    @SerializedName("test_station_id") val testStationId: Int,
    @SerializedName("test_station_name") val testStationName: String,
    val items: List<DetailItem>
)

data class DetailItem(
    @SerializedName("item_id") val itemId: Int,
    @SerializedName("item_name") val itemName: String,
    @SerializedName("input_type") val inputType: String? = null,
    val options: String? = null,
    val value: String? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    val remarks: String? = null,
    val details: List<DetailSubItem> = emptyList()
)

data class DetailSubItem(
    @SerializedName("detail_id") val detailId: Int,
    @SerializedName("detail_name") val detailName: String,
    val value: String? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    val remarks: String? = null
)

// ===== 草稿 =====
data class DraftListData(
    val data: List<DraftRecord>
)

data class DraftRecord(
    val id: Int,
    @SerializedName("manufacturer_name") val manufacturerName: String? = null,
    @SerializedName("production_line_name") val productionLineName: String? = null,
    @SerializedName("model_name") val modelName: String? = null,
    @SerializedName("inspector_name") val inspectorName: String? = null,
    @SerializedName("reviewer_name") val reviewerName: String? = null,
    @SerializedName("test_station_names") val testStationNames: String? = null,
    @SerializedName("inspection_date") val inspectionDate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("manufacturer_id") val manufacturerId: Int? = null,
    @SerializedName("production_line_id") val productionLineId: Int? = null,
    @SerializedName("model_id") val modelId: Int? = null,
    @SerializedName("reviewer_id") val reviewerId: Int? = null,
    @SerializedName("test_station_id") val testStationId: String? = null,
    @SerializedName("inspector_id") val inspectorId: Int? = null
)

data class DraftDataResponse(
    @SerializedName("draft_record") val draftRecord: DraftRecord,
    @SerializedName("draft_results") val draftResults: List<DraftResult>
)

data class DraftResult(
    val id: Int? = null,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("item_type") val itemType: String,
    val value: String? = null,
    @SerializedName("image_path") val imagePath: String? = null,
    val remarks: String? = null
)

// ===== 修改密码 =====
data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String,
    val confirm_password: String
)

// ===== 图片上传 =====
data class ImageUploadResponse(
    val success: Boolean,
    val filepath: String? = null,
    val filename: String? = null,
    val error: String? = null
)
