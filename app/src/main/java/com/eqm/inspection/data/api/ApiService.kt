package com.eqm.inspection.data.api

import com.eqm.inspection.data.api.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// 请求体包装类
class TestStationIdsBody(val test_station_ids: List<Int>)

/**
 * Retrofit API 接口定义
 * 对应后端所有厂商巡检相关路由
 */
interface ApiService {

    // ===== 登录 (无Token) =====
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginData>>

    // ===== 初始化数据 =====
    @GET("api/vendor/init_data")
    suspend fun getInitData(): Response<ApiResponse<InitData>>

    // ===== 仪表板 =====
    @GET("api/vendor/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardData>>

    // ===== 产线 =====
    @GET("api/vendor/production_lines/{manufacturerId}")
    suspend fun getProductionLines(
        @Path("manufacturerId") manufacturerId: Int
    ): Response<List<IdNamePair>>

    // ===== 加载巡检项目 =====
    @POST("api/vendor/load_items")
    suspend fun loadInspectionItems(
        @Body body: TestStationIdsBody
    ): Response<InspectionLoadResponse>

    // ===== 提交巡检 =====
    @POST("api/vendor/submit")
    suspend fun submitInspection(
        @Body request: SubmitRequest
    ): Response<SubmitResponse>

    // ===== 图片上传 (multipart) =====
    @Multipart
    @POST("api/vendor/upload_image")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("record_id") recordId: RequestBody
    ): Response<ImageUploadResponse>

    // ===== 查询 =====
    @GET("api/vendor/query")
    suspend fun queryRecords(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 20,
        @Query("manufacturer_id") manufacturerId: String? = null,
        @Query("model_id") modelId: String? = null,
        @Query("reviewer_id") reviewerId: String? = null,
        @Query("review_status") reviewStatus: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("is_draft") isDraft: String = "0"
    ): Response<QueryData>

    // ===== 详情 =====
    @GET("api/vendor/detail/{recordId}")
    suspend fun getDetail(
        @Path("recordId") recordId: Int
    ): Response<ApiResponse<DetailData>>

    // ===== 审核 =====
    @POST("api/vendor/review/{recordId}")
    suspend fun reviewRecord(
        @Path("recordId") recordId: Int,
        @Body body: Map<String, String>
    ): Response<ApiResponse<Any>>

    // ===== 删除记录 =====
    @POST("api/vendor/delete_record/{recordId}")
    suspend fun deleteRecord(
        @Path("recordId") recordId: Int
    ): Response<ApiResponse<Any>>

    // ===== 草稿列表 =====
    @GET("api/vendor/drafts")
    suspend fun getDrafts(): Response<ApiResponse<List<DraftRecord>>>

    // ===== 草稿数据 =====
    @GET("api/vendor/draft_data/{draftId}")
    suspend fun getDraftData(
        @Path("draftId") draftId: Int
    ): Response<ApiResponse<DraftDataResponse>>

    // ===== 审核数据（已提交记录的编辑数据） =====
    @GET("api/vendor/review_data/{recordId}")
    suspend fun getReviewData(
        @Path("recordId") recordId: Int
    ): Response<ApiResponse<DraftDataResponse>>

    // ===== 删除草稿 =====
    @POST("api/vendor/delete_draft/{draftId}")
    suspend fun deleteDraft(
        @Path("draftId") draftId: Int
    ): Response<ApiResponse<Any>>

    // ===== 修改密码 =====
    @POST("api/vendor/change_password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<ApiResponse<Any>>

    // ===== 报告下载 =====
    @GET("api/vendor/download_report/{recordId}")
    @Streaming
    suspend fun downloadReport(
        @Path("recordId") recordId: Int
    ): Response<okhttp3.ResponseBody>
}
