package com.eqm.inspection.data.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * API 客户端单例
 * 使用 OkHttp + Retrofit 封装网络请求
 */
object ApiClient {

    // 默认后端地址 - 开发时修改为实际IP
    var BASE_URL = "http://192.168.16.226:8000/"  // 默认服务器地址

    // 当前有效的 JWT Token
    var currentToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    /** Token 拦截器：自动附加 Authorization header */
    private val tokenInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val request = if (currentToken != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $currentToken")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(tokenInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private var _retrofit: Retrofit = createRetrofit()
    private var _apiService: ApiService = _retrofit.create(ApiService::class.java)

    val apiService: ApiService
        get() = _apiService

    /** 创建带 Token 的 Retrofit 实例 */
    fun createAuthClient(token: String): ApiService {
        val authClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** 更新后端地址，并重建 Retrofit 实例 */
    fun updateBaseUrl(url: String) {
        BASE_URL = url.trimEnd('/') + "/"
        _retrofit = createRetrofit()
        _apiService = _retrofit.create(ApiService::class.java)
    }
}
