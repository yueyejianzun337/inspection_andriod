# 廠商巡檢 Android APP 開發指南

## 專案概述

廠商巡檢 Android APP 是一個使用 Jetpack Compose + MVVM 架構的移動端應用，用於供應商（廠商）的品質巡檢填寫、查詢、審核等操作。後端為 Flask + SQLAlchemy + SQLite。

## 技術棧

| 類別 | 技術 | 版本 |
|------|------|------|
| 語言 | Kotlin | 1.9.x |
| UI | Jetpack Compose + Material3 | BOM 2024.02.00 |
| 導航 | Navigation Compose | 2.7.5 |
| 網路 | Retrofit2 + OkHttp4 | 2.9.0 / 4.12.0 |
| JSON | Gson | 2.10.1 |
| 圖片加載 | Coil Compose | 2.5.0 |
| 持久化 | DataStore Preferences | 1.0.0 |
| 異步 | Kotlin Coroutines | 1.7.3 |
| 編譯 SDK | 34 | minSdk 26 |
| Java | 17 | Kotlin JVM 17 |

## 專案結構

```
app/src/main/java/com/eqm/inspection/
├── MainActivity.kt              # 入口 Activity + NavHost
├── VendorInspectionApp.kt       # Application，初始化 TokenManager
├── data/
│   ├── SettingsDataStore.kt     # 服務器地址持久化
│   └── api/
│       ├── ApiClient.kt         # Retrofit 單例 + Token 攔截器
│       ├── ApiService.kt        # Retrofit API 介面定義
│       ├── TokenManager.kt      # JWT Token 本地持久化
│       └── models/
│           └── ApiModels.kt     # 所有 API 請求和響應數據類
├── ui/
│   ├── components/
│   │   └── CommonComponents.kt  # 共用組件（TopBar/加載/錯誤/空狀態/狀態色）
│   ├── navigation/
│   │   └── NavGraph.kt          # 路由定義物件 Routes
│   ├── login/
│   │   ├── LoginScreen.kt       # 登入畫面
│   │   └── LoginViewModel.kt    # 登入邏輯
│   ├── dashboard/
│   │   ├── DashboardScreen.kt   # 儀表板（統計+圖表+FAIL記錄）
│   │   └── DashboardViewModel.kt
│   ├── inspection/
│   │   ├── InspectionScreen.kt  # 巡檢填寫（表單+項目+圖片上傳）
│   │   └── InspectionViewModel.kt # 提交/暫存/驗證/NA聯動/可不測
│   ├── query/
│   │   ├── QueryScreen.kt       # 查詢列表（篩選面板+分頁）
│   │   └── QueryViewModel.kt
│   ├── detail/
│   │   ├── DetailScreen.kt      # 詳情（三層結構：測試站→項目→細項+圖片）
│   │   └── DetailViewModel.kt
│   ├── draft/
│   │   ├── DraftListScreen.kt   # 暫存管理（列表+刪除確認）
│   │   └── DraftViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt    # 設置（用戶信息+服務器地址+登出）
│   │   └── SettingsViewModel.kt
│   └── theme/
│       ├── Color.kt             # 色彩定義（主色/狀態色/背景色）
│       └── Theme.kt             # Material3 主題配置
└── util/
    └── ReportDownloader.kt      # 報告下載（DownloadManager）
```

## 架構模式：MVVM

```
View (Composable) ──collectAsStateWithLifecycle()──> ViewModel ──Coroutine──> API Service
     │                                                      │                    │
     │  Callback (onValueChange, onClick)                    │  Retrofit          │
     └──────────────────────────────────────────────────────┘  OkHttp            │
                                                                                │
                                                    MutableStateFlow ◄─── ApiClient ◄── 後端
```

**核心原則：**
- **ViewModel** 持有 `MutableStateFlow<UiState>`，UI 通過 `collectAsStateWithLifecycle()` 訂閱
- **Screen** 只負責渲染，不包含業務邏輯
- **ViewModel** 通過 `viewModelScope.launch` 執行非同步操作
- **ApiService** 使用 Retrofit suspend function

## 導航流程

```
Login ──> Dashboard ──> Inspection (填寫)
    │         ├──> Query (查詢列表) ──> Detail (詳情)
    │         ├──> Drafts (暫存管理) ──> Inspection?draftId= (編輯草稿)
    │         └──> Settings (設置)
    │
    └──<── 返回登入 (登出)
```

路由定義在 `Routes` 物件中：
- `Routes.LOGIN = "login"`
- `Routes.INSPECTION = "inspection?draftId={draftId}"`
- `Routes.QUERY = "query"`
- `Routes.DETAIL = "detail/{recordId}"`
- `Routes.DRAFTS = "drafts"`
- `Routes.SETTINGS = "settings"`

## 關鍵數據模型 (ApiModels.kt)

### API 響應通用包裝
```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)
```

### 初始化數據
```kotlin
data class InitData(
    val currentUser: CurrentUserInfo,   // 當前用戶 id/username/role/manufacturerIds
    val manufacturers: List<IdNamePair>,
    val models: List<IdNamePair>,
    val testStations: List<IdNamePair>,
    val reviewers: List<IdNamePair>
)
```

### 提交請求
```kotlin
data class SubmitRequest(
    val productionLineId: Int,
    val modelId: Int,
    val reviewerId: Int,
    val inspectionDate: String,
    val testStationIds: List<Int>,
    val skipTestStationIds: List<Int> = emptyList(),
    val isDraft: Boolean,
    val manufacturerId: Int? = null,
    val draftId: Int? = null,
    val items: List<SubmitItem>   // 包含主項 + details (細項)
)
```

### 查詢記錄
```kotlin
data class InspectionRecord(
    val id: Int,
    val manufacturerName: String?,
    val productionLineName: String?,
    val modelName: String?,
    val inspectorName: String?,
    val reviewerName: String?,
    val testStationNames: String?,     // 逗號分隔的多測試站名稱
    val inspectionDate: String?,
    val reviewStatus: String?,         // pending/approved/rejected
    val isDraft: Int? = null,
    val createdAt: String? = null
)
```

## 共用組件 (CommonComponents.kt)

| 組件 | 用途 |
|------|------|
| `AppTopBar` | 頂部導航欄（可選返回按鈕 + 操作圖標） |
| `LoadingIndicator` | 載入中動畫 |
| `ErrorMessage` | 錯誤提示（可選重試按鈕） |
| `EmptyState` | 空列表提示 |
| `StatCard` | 統計數字卡片 |
| `statusColor()` | 審核狀態→顏色映射 |
| `statusLabel()` | 審核狀態→中文標籤映射 |

## 功能模塊詳解

### 1. 登入模塊
- `LoginViewModel.login()`：調用 `POST /api/auth/login`，成功後通過回調傳遞 token/username/role
- `TokenManager`：使用 DataStore 持久化 `jwt_token` / `username` / `role`
- 啟動時自動檢查已保存 Token，有效則跳過登入

### 2. 儀表板模塊
- `DashboardViewModel.loadDashboard()`：調用 `GET /api/vendor/dashboard`
- 展示：巡檢總數、型號總數、近6月趨勢柱狀圖、型號分布條形圖、FAIL記錄列表
- 底層導航欄：首頁 / 填寫 / 查詢 / 暫存 / 設置

### 3. 巡檢填寫模塊（核心）
- **表單欄位**：廠商 / 產線 / 型號 / 審核員 / 日期 / 測試站（多選）
- **「加載巡檢項目」按鈕**：點擊後調用 `POST /api/vendor/load_items`
- **動態項目渲染**：
  - `select/dropdown` → ExposedDropdownMenu
  - `checkbox/radio` → RadioButton 組
  - `text/number` → OutlinedTextField
- **圖片上傳**：通過 `uploadImage()` 先上傳到 temp，得到靜態路徑後保存
- **圖片縮略圖**：使用 Coil AsyncImage 顯示，可刪除
- **NA 聯動**：主項選 NA → 所有細項自動設為 NA
- **「可不測」開關**：每個測試站有 Switch，開啟後折疊項目，提交時後端自動以 NA 處理
- **驗證**：正式提交時檢查所有非 checkbox/radio 項目的值是否為空（跳過「可不測」的測試站）
- **暫存/提交**：`submit(isDraft)` 共用入口，後端根據 `is_draft` 標誌區分

### 4. 查詢模塊
- 篩選條件面板始終顯示：廠商 / 型號 / 狀態 / 日期範圍 + 查詢按鈕
- 結果列表：顯示型號、狀態標籤、廠商/產線、檢驗員/審核員、日期、測試站
- 分頁導航：上一頁 / 下一頁
- 支援即時搜索（條件變更自動觸發查詢）

### 5. 詳情模塊
- 三層結構：測試站 → 檢測項目 → 細項
- 顯示圖片縮略圖（支援逗號/分號分隔的多圖片路徑）
- 顯示審核狀態徽章
- 報告下載按鈕（使用 DownloadManager）

### 6. 暫存管理
- 列表顯示草稿記錄（含審核員、測試站信息）
- 繼續編輯 → 傳遞 `draftId` 到填寫頁面
- 刪除 → 確認對話框
- 填寫頁面 `loadDraft()` 回填所有已填數據

### 7. 設置
- 顯示當前用戶名和角色
- 服務器地址配置（DataStore 持久化）
- 退出登入

## 關鍵開發模式

### API 調用模式
```kotlin
// ViewModel 中
fun loadData() {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        try {
            val response = ApiClient.apiService.someEndpoint(...)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    // 更新狀態
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "網絡錯誤: ...")
        }
    }
}
```

### UI 狀態管理
```kotlin
data class ExampleUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    val data: ... = ...
)
```

### 圖片處理模式
```kotlin
// 解析（支援逗號和分號分隔）
private fun parseImagePaths(path: String): List<String> =
    path.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }

// 多張時用逗號拼接（Android 慣例）
private fun joinImagePaths(paths: List<String>): String =
    paths.joinToString(",")

// 顯示
AsyncImage(
    model = ImageRequest.Builder(context)
        .data("${ApiClient.BASE_URL.trimEnd('/')}$path")
        .crossfade(true)
        .build(),
    contentDescription = "圖片",
    modifier = Modifier.clip(RoundedCornerShape(4.dp)),
    contentScale = ContentScale.Crop
)
```

## 多廠商支援說明

- `CurrentUserInfo.manufacturerIds: List<Int>?` — 廠商用戶關聯的多個廠商 ID
- 查詢頁面：多廠商時顯示廠商下拉過濾器
- 填寫頁面：自動選擇第一個廠商，多廠商時可切換
- 後端：使用 `IN` 子句查詢

## 圖片路徑規範

- **Android APP 上傳**：逗號 `,` 分隔（`joinImagePaths`）
- **Web 頁面上傳**：分號 `;` 分隔
- **解析**：`path.split(",", ";")` 同時支援兩種分隔符
- **URL**：`${ApiClient.BASE_URL}$path`（path 以 `/static/...` 開頭）

## 依賴配置參考

```kotlin
// build.gradle.kts 關鍵依賴
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.material:material-icons-extended")
implementation("androidx.navigation:navigation-compose:2.7.5")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("io.coil-kt:coil-compose:2.5.0")
implementation("androidx.datastore:datastore-preferences:1.0.0")
```
