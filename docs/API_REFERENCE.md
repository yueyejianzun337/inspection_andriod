# 廠商巡檢 Server API 參考文檔

> 本文檔供開發其他平台（iOS / React Native / Flutter / Web 等）的 APP 時參考。
> 後端基於 Flask + JWT 認證 + SQLite，所有 API 返回 JSON 格式。

---

## 目錄

1. [基本資訊](#1-基本資訊)
2. [認證機制](#2-認證機制)
3. [API 端點一覽](#3-api-端點一覽)
4. [端點詳解](#4-端點詳解)
   - 4.1 [登入](#41-登入-post-apiauthlogin)
   - 4.2 [初始化數據](#42-初始化數據-get-apivendorinit_data)
   - 4.3 [儀表板](#43-儀表板-get-apivendordashboard)
   - 4.4 [產線列表](#44-產線列表-get-apivendorproduction_linesmanufacturerid)
   - 4.5 [加載巡檢項目](#45-加載巡檢項目-post-apivendorload_items)
   - 4.6 [圖片上傳](#46-圖片上傳-post-apivendorupload_image)
   - 4.7 [提交巡檢](#47-提交巡檢-post-apivendorsubmit)
   - 4.8 [查詢記錄](#48-查詢記錄-get-apivendorquery)
   - 4.9 [記錄詳情](#49-記錄詳情-get-apivendordetailrecordid)
   - 4.10 [審核記錄](#410-審核記錄-post-apivendorreviewrecordid)
   - 4.11 [刪除記錄](#411-刪除記錄-post-apivendordelete_recordrecordid)
   - 4.12 [草稿列表](#412-草稿列表-get-apivendordrafts)
   - 4.13 [草稿數據](#413-草稿數據-get-apivendordraft_datadraftid)
   - 4.14 [刪除草稿](#414-刪除草稿-post-apivendordelete_draftdraftid)
   - 4.15 [下載報告](#415-下載報告-get-apivendordownload_reportrecordid)
5. [數據模型](#5-數據模型)
6. [權限對照表](#6-權限對照表)
7. [常見錯誤處理](#7-常見錯誤處理)

---

## 1. 基本資訊

| 項目 | 說明 |
|------|------|
| **Base URL** | `http://<server>:8000`（可在 Android APP 設置中修改） |
| **API 前綴** | 所有端點在 `api/` 路徑下 |
| **認證方式** | JWT Bearer Token |
| **數據格式** | JSON (application/json) |
| **圖片上傳** | multipart/form-data |
| **字符編碼** | UTF-8 |

---

## 2. 認證機制

### JWT Token 獲取
- 調用 `POST /api/auth/login` 獲得 token
- Token 有效期：24 小時（由 `JWT_ACCESS_TOKEN_EXPIRES` 設定）

### 請求頭
所有受保護的端點需要在 Header 中攜帶：
```
Authorization: Bearer <token>
Content-Type: application/json
```

### 角色定義

| 角色 | 說明 | 權限範圍 |
|------|------|----------|
| `vendor` | 廠商用戶 | 僅自己關聯的廠商數據（可綁定多個廠商） |
| `user` | 普通用戶（審核員） | 所有廠商數據（只讀+審核） |
| `admin` | 管理員 | 所有權限 |
| `readonly` | 只讀用戶 | 查閱權限 |

### Token 驗證回傳

| HTTP Status | 說明 |
|-------------|------|
| 401 | Token 缺失或過期，需重新登入 |
| 403 | Token 有效但無權訪問該資源 |

---

## 3. API 端點一覽

| 序號 | 方法 | 端點 | 認證 | 角色限制 | 說明 |
|------|------|------|------|----------|------|
| 1 | POST | `/api/auth/login` | 否 | 全部 | 登入取得 Token |
| 2 | GET | `/api/vendor/init_data` | 是 | vendor/admin | 初始化下拉選項 |
| 3 | GET | `/api/vendor/dashboard` | 是 | vendor/admin | 儀表板統計 |
| 4 | GET | `/api/vendor/production_lines/<id>` | 是 | vendor/admin | 產線列表 |
| 5 | POST | `/api/vendor/load_items` | 是 | vendor/admin | 加載巡檢項目 |
| 6 | POST | `/api/vendor/upload_image` | 是 | vendor/admin | 上傳圖片 |
| 7 | POST | `/api/vendor/submit` | 是 | vendor/admin | 提交/暫存巡檢 |
| 8 | GET | `/api/vendor/query` | 是 | vendor/admin/user | 查詢記錄 |
| 9 | GET | `/api/vendor/detail/<id>` | 是 | vendor/admin/user | 記錄詳情 |
| 10 | POST | `/api/vendor/review/<id>` | 是 | user/admin | 審核記錄 |
| 11 | POST | `/api/vendor/delete_record/<id>` | 是 | admin/user | 刪除記錄 |
| 12 | GET | `/api/vendor/drafts` | 是 | vendor/admin | 草稿列表 |
| 13 | GET | `/api/vendor/draft_data/<id>` | 是 | vendor/admin | 草稿數據 |
| 14 | POST | `/api/vendor/delete_draft/<id>` | 是 | vendor/admin | 刪除草稿 |
| 15 | GET | `/api/vendor/download_report/<id>` | 是 | vendor/admin/user | 下載報告 |

---

## 4. 端點詳解

### 4.1 登入 `POST /api/auth/login`

**不需要 Token**，不需要 `api/vendor` 前綴。

**Request:**
```json
{
    "username": "vendor1",
    "password": "123456"
}
```

**Response (200):**
```json
{
    "success": true,
    "message": "登录成功",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIs...",
        "expires_in": 86400,
        "user": {
            "id": 5,
            "username": "vendor1",
            "role": "vendor",
            "manufacturers": [6, 7, 8],
            "models": null
        }
    }
}
```

**Response (401):**
```json
{
    "success": false,
    "message": "用户名或密码错误"
}
```

**欄位說明：**
| 字段 | 類型 | 說明 |
|------|------|------|
| `token` | string | JWT Token，後續請求需在 Authorization header 中攜帶 |
| `expires_in` | int | 有效期（秒） |
| `user.manufacturers` | List<int> \| null | 該用戶關聯的廠商 ID 列表（vendor 角色專用） |
| `user.models` | List<int> \| null | 該用戶關聯的型號 ID 列表 |

---

### 4.2 初始化數據 `GET /api/vendor/init_data`

**用途：** 返回填寫頁面所需的所有下拉選項（廠商、型號、測試站、審核員）。

**Response (200):**
```json
{
    "success": true,
    "data": {
        "manufacturers": [
            {"id": 6, "name": "智易越南"},
            {"id": 7, "name": "T&W"},
            {"id": 8, "name": "T&W2"}
        ],
        "models": [
            {"id": 1, "name": "AI_SERVER"},
            {"id": 2, "name": "SWITCH"}
        ],
        "test_stations": [
            {"id": 1, "name": "第一站"},
            {"id": 2, "name": "第二站"}
        ],
        "reviewers": [
            {"id": 2, "username": "user1"},
            {"id": 3, "username": "user2"}
        ],
        "current_user": {
            "id": 5,
            "username": "vendor1",
            "role": "vendor",
            "manufacturer_ids": [6, 7, 8]
        }
    }
}
```

**權限過濾規則：**
- `vendor` 角色：`manufacturers` 僅返回用戶關聯的廠商，`models` 按 `user.models` 過濾
- `admin` 角色：返回全部數據
- `reviewers` 始終返回 `role = 'user'` 的用戶

---

### 4.3 儀表板 `GET /api/vendor/dashboard`

**Query Parameters:**
| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `manufacturer_id` | int | 否 | 指定廠商；不傳時 vendor 角色返回**所有綁定廠商**的聚合數據（IN 子句） |

**統計邏輯：**
- vendor 角色：默認查詢所有綁定廠商（`IN (...)` 子句），支援 `manufacturer_id` 參數指定單個廠商
- admin 角色：不傳參數時返回空，需指定 `manufacturer_id`
- 僅統計 `is_draft = 0` 的非草稿記錄
- 不按 `inspector_id` 過濾（因 Web GUI 提交的記錄無 inspector_id）

**Response (200):**
```json
{
    "success": true,
    "data": {
        "total_records": 13,
        "total_models": 2,
        "monthly_data": [
            {"month": "2026-05", "count": 5},
            {"month": "2026-06", "count": 8}
        ],
        "model_data": [
            {"model_name": "AI_SERVER", "count": 10},
            {"model_name": "SWITCH", "count": 3}
        ],
        "fail_records": [
            {
                "id": 12,
                "inspection_date": "2026-05-24",
                "model_name": "AI_SERVER",
                "line_name": "產線A",
                "status": "pending"
            }
        ]
    }
}
```

**欄位說明：**
| 字段 | 類型 | 說明 |
|------|------|------|
| `monthly_data` | array | 近6個月按月統計（`inspection_date >= 180天前`） |
| `model_data` | array | 按型號分佈統計（近6個月） |
| `fail_records` | array | 最近5條 FAIL 記錄 |

---

### 4.4 產線列表 `GET /api/vendor/production_lines/<manufacturerId>`

**Response (200):**
```json
[
    {"id": 9, "name": "SMT"},
    {"id": 10, "name": "DIP"}
]
```

**注意：** 此端點返回純陣列（非 `ApiResponse` 包裝）。

---

### 4.5 加載巡檢項目 `POST /api/vendor/load_items`

**Request:**
```json
{
    "test_station_ids": [1, 2]
}
```

**Response (200):**
```json
{
    "inspection_items": [
        {
            "id": 1,
            "name": "外觀檢查",
            "input_type": "select",
            "options": "PASS,FAIL,NA",
            "test_station_id": 1,
            "test_station_name": "第一站",
            "details": [
                {
                    "id": 1,
                    "name": "外觀-細項A",
                    "input_type": "text",
                    "options": null,
                    "inspection_item_id": 1
                }
            ]
        }
    ],
    "project_details": [
        {
            "id": 1,
            "name": "外觀-細項A",
            "input_type": "text",
            "options": null,
            "inspection_item_id": 1
        }
    ]
}
```

**`input_type` 取值說明：**
| 值 | 表現形式 | 驗證規則 |
|----|----------|----------|
| `text` | 文字輸入框 | 正式提交時值不可為空 |
| `number` | 數字輸入框 | 正式提交時值不可為空 |
| `select` | 下拉選擇 | 正式提交時值不可為空 |
| `dropdown` | 下拉選擇（同 select） | 正式提交時值不可為空 |
| `checkbox` | 單選按鈕 | 跳過空值驗證 |
| `radio` | 單選按鈕（同 checkbox） | 跳過空值驗證 |
| `textarea` | 多行輸入 | 正式提交時值不可為空 |

---

### 4.6 圖片上傳 `POST /api/vendor/upload_image`

**Request (multipart/form-data):**
| 字段 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `file` | File | 是 | 圖片文件（JPG/PNG） |
| `record_id` | string | 否 | 預設 `temp`，提交時使用實際 record_id |

**Response (200):**
```json
{
    "success": true,
    "filepath": "/static/Supplier_uploads/temp/20260524_100741_001.png",
    "filename": "20260524_100741_001.png"
}
```

**存檔規則：**
- 圖片上傳到 `static/Supplier_uploads/{record_id}/` 目錄
- 文件名格式：`{yyyyMMdd_HHmmss}_{序號:03d}.{ext}`
- 路徑以 `"/static/Supplier_uploads/..."` 開頭
- 靜態文件通過 Flask 的 `static` 路由提供

---

### 4.7 提交巡檢 `POST /api/vendor/submit`

**Request:**
```json
{
    "production_line_id": 9,
    "model_id": 1,
    "reviewer_id": 2,
    "inspection_date": "2026-05-25",
    "test_station_ids": [1, 2],
    "skip_test_station_ids": [2],
    "is_draft": false,
    "manufacturer_id": 6,
    "draft_id": null,
    "inspector_name": "T&W",
    "items": [
        {
            "item_id": 1,
            "value": "PASS",
            "image_path": "/static/Supplier_uploads/temp/20260524_100741_001.png,/static/Supplier_uploads/temp/20260524_100741_002.png",
            "remarks": "",
            "test_station_id": 1,
            "details": [
                {
                    "item_id": "detail_1",
                    "value": "OK",
                    "image_path": "",
                    "remarks": ""
                }
            ]
        }
    ]
}
```

**Response (200 正式提交):**
```json
{
    "success": true,
    "message": "提交成功",
    "record_id": 15,
    "is_draft": false,
    "draft_id": null
}
```

**Response (200 暫存):**
```json
{
    "success": true,
    "message": "草稿保存成功",
    "record_id": 15,
    "is_draft": true,
    "draft_id": 15
}
```

**關鍵邏輯：**
1. `skip_test_station_ids` — 標記為「可不測」的測試站，其所有項目自動存為 `"NA"`
2. `is_draft = true` 時 — 以暫存方式保存，狀態為 `is_draft = 1`
3. `is_draft = false` 時 — 正式提交，狀態為 `is_draft = 0, status = 'pending'`
4. `draft_id` 不為空時 — 更新已有草稿（先刪除舊結果再插入）
5. `inspector_name` 為文字輸入框，Android APP 使用 SharedPreferences 本地記憶
6. 後端 INSERT 和 UPDATE 都會自動寫入 `inspector_id = user.id`

**圖片路徑處理規則：**
- Android APP 上傳時：多張圖片路徑用**逗號 `,`** 分隔
- Web 頁面上傳時：多張圖片路徑用**分號 `;`** 分隔
- 解析時：APP 端 `split(",", ";")` 同時支援兩種格式

---

### 4.8 查詢記錄 `GET /api/vendor/query`

**Query Parameters:**
| 參數 | 類型 | 必填 | 預設 | 說明 |
|------|------|------|------|------|
| `page` | int | 否 | 1 | 頁碼 |
| `per_page` | int | 否 | 20 | 每頁筆數 |
| ``manufacturer_id` | string | 否 | - | 廠商 ID（傳 `"all"` 或省略為全部，vendor 不傳時查所有綁定廠商） |
| `model_id` | string | 否 | - | 型號 ID |
| `reviewer_id` | string | 否 | - | 審核員 ID |
| `review_status` | string | 否 | - | 審核狀態：`pending`（待審核）/ `confirmed`（已審核） |
| `start_date` | string | 否 | - | 開始日期: `yyyy-MM-dd` |
| `end_date` | string | 否 | - | 結束日期: `yyyy-MM-dd` |
| `is_draft` | string | 否 | "0" | `"0"`正式記錄 / `"1"`草稿 |

**Response (200):**
```json
{
    "success": true,
    "data": [
        {
            "id": 12,
            "manufacturer_name": "智易越南",
            "production_line_name": "SMT",
            "model_name": "AI_SERVER",
            "inspector_name": "vendor1",
            "reviewer_name": "user1",
            "test_station_names": "第一站,第二站",
            "inspection_date": "2026-05-24",
            "review_status": "pending",
            "is_draft": 0,
            "created_at": "2026-05-24 10:07:41"
        }
    ],
    "total": 1,
    "page": 1,
    "per_page": 20
}
```

---

### 4.9 記錄詳情 `GET /api/vendor/detail/<recordId>`

**Response (200):**
```json
{
    "success": true,
    "data": {
        "record": {
            "id": 12,
            "manufacturer_name": "智易越南",
            "production_line_name": "SMT",
            "model_name": "AI_SERVER",
            "test_station_names": ["第一站", "第二站"],
            "inspection_date": "2026-05-24",
            "inspector_name": "vendor1",
            "reviewer_name": "user1",
            "review_status": "pending",
            "is_draft": 0,
            "created_at": "2026-05-24 10:07:41"
        },
        "inspection_data": [
            {
                "test_station_id": 1,
                "test_station_name": "第一站",
                "items": [
                    {
                        "item_id": 1,
                        "item_name": "外觀檢查",
                        "input_type": "select",
                        "options": "PASS,FAIL,NA",
                        "value": "PASS",
                        "image_path": "/static/Supplier_uploads/12/20260524_100741_001.png;/static/Supplier_uploads/12/20260524_100741_002.png",
                        "remarks": "",
                        "details": [
                            {
                                "detail_id": 1,
                                "detail_name": "外觀-細項A",
                                "value": "OK",
                                "image_path": "",
                                "remarks": ""
                            }
                        ]
                    }
                ]
            }
        ]
    }
}
```

**三層結構：** 測試站（`StationGroup`）→ 檢測項目（`DetailItem`）→ 細項（`DetailSubItem`）

---

### 4.10 審核記錄 `POST /api/vendor/review/<recordId>`

**Request:**
```json
{
    "status": "approved"
}
```

**`status` 取值：** `approved` / `rejected`

**Response (200):**
```json
{
    "success": true,
    "message": "審核完成"
}
```

---

### 4.11 刪除記錄 `POST /api/vendor/delete_record/<recordId>`

**Requires:** `admin` 或 `user` 角色（vendor 無權刪除）

**Request Body:** 無需傳入內容

**Response (200):**
```json
{
    "success": true,
    "message": "刪除成功"
}
```

---

### 4.12 草稿列表 `GET /api/vendor/drafts`

**Response (200):**
```json
{
    "success": true,
    "data": [
        {
            "id": 14,
            "manufacturer_name": "智易越南",
            "production_line_name": "SMT",
            "model_name": "AI_SERVER",
            "inspector_name": "vendor1",
            "reviewer_name": "user1",
            "test_station_names": "第一站,第二站",
            "inspection_date": "2026-05-25",
            "created_at": "2026-05-25 09:34:18",
            "manufacturer_id": 6,
            "production_line_id": 9,
            "model_id": 1,
            "reviewer_id": 2,
            "inspector_id": 5,
            "test_station_id": "1,2"
        }
    ]
}
```

**注意：** `test_station_id` 是逗號分隔的字串，如 `"1,2"`。

---

### 4.13 草稿數據 `GET /api/vendor/draft_data/<draftId>`

**Response (200):**
```json
{
    "success": true,
    "data": {
        "draft_record": {
            "id": 14,
            "manufacturer_id": 6,
            "production_line_id": 9,
            "model_id": 1,
            "test_station_id": "1,2",
            "inspection_date": "2026-05-25",
            "reviewer_id": 2,
            "inspector_id": 5
        },
        "draft_results": [
            {
                "id": 41,
                "item_id": "1",
                "item_type": "main",
                "value": "PASS",
                "image_path": "",
                "remarks": ""
            },
            {
                "id": 42,
                "item_id": "detail_1",
                "item_type": "detail",
                "value": "OK",
                "image_path": "",
                "remarks": ""
            }
        ]
    }
}
```

**`item_id` 格式：**
- main（主項）：數字字串，如 `"1"`, `"2"`
- detail（細項）：`"detail_{XXX}"` 格式，如 `"detail_1"`, `"detail_2"`

---

### 4.14 刪除草稿 `POST /api/vendor/delete_draft/<draftId>`

**Response (200):**
```json
{
    "success": true,
    "message": "刪除成功"
}
```

**注意：** vendor 角色只能刪除自己的草稿（inspector_id 匹配）。

---

### 4.15 下載報告 `GET /api/vendor/download_report/<recordId>`

**用途：** 下載 Excel 格式的巡檢報告。

**Response (200):** 返回 `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 的 Excel 二進制文件。

**注意：** 當前後端此端點僅確認權限，實際報告生成需額外處理。Android APP 使用 `DownloadManager` 系統服務下載。

---

## 5. 數據模型

### 5.1 通用響應包裝
```json
{
    "success": true|false,
    "message": "操作成功/失敗提示",
    "data": { ... },
    "error": "錯誤信息（失敗時返回）"
}
```

### 5.2 登入數據
| 字段 | 類型 | 說明 |
|------|------|------|
| `token` | string | JWT Token |
| `expires_in` | int | 過期時間（秒） |
| `user.id` | int | 用戶ID |
| `user.username` | string | 用戶名 |
| `user.role` | string | 角色：vendor/admin/user/readonly |
| `user.manufacturers` | List<int> | 關聯廠商ID列表（vendor角色） |
| `user.models` | List<int> | 關聯型號ID列表 |

### 5.3 初始化數據
| 字段 | 類型 | 說明 |
|------|------|------|
| `manufacturers` | List<{id, name}> | 廠商下拉選項 |
| `models` | List<{id, name}> | 型號下拉選項 |
| `test_stations` | List<{id, name}> | 測試站下拉選項 |
| `reviewers` | List<{id, username}> | 審核員下拉選項 |
| `current_user` | CurrentUserInfo | 當前用戶信息 |

### 5.4 提交巡檢
| 字段 | 類型 | 必填 | 說明 |
|------|------|------|------|
| `production_line_id` | int | 是 | 產線ID |
| `model_id` | int | 是 | 型號ID |
| `reviewer_id` | int | 是 | 審核員ID |
| `inspection_date` | string | 是 | 日期 (yyyy-MM-dd) |
| `test_station_ids` | List<int> | 是 | 選擇的測試站ID列表 |
| `skip_test_station_ids` | List<int> | 否 | 設為「可不測」的測試站ID |
| `is_draft` | bool | 是 | true=暫存, false=正式提交 |
| `manufacturer_id` | int | 否 | 廠商ID（vendor自動填入） |
| `draft_id` | int | 否 | 編輯草稿時傳入 |
| `items` | List<SubmitItem> | 是 | 巡檢項目列表（含細項） |

### 5.5 SubmitItem 結構
| 字段 | 類型 | 說明 |
|------|------|------|
| `item_id` | int | 主項ID |
| `value` | string | 檢查結果 |
| `image_path` | string | 多張圖片路徑，用逗號分隔 |
| `remarks` | string | 備註 |
| `test_station_id` | int | 所屬測試站ID |
| `details` | List<SubmitDetailItem> | 細項列表 |

### 5.6 SubmitDetailItem 結構
| 字段 | 類型 | 說明 |
|------|------|------|
| `item_id` | string | 格式：`"detail_{detailId}"` |
| `value` | string | 檢查結果 |
| `image_path` | string | 圖片路徑 |
| `remarks` | string | 備註 |

### 5.7 查詢記錄（InspectionRecord）
| 字段 | 類型 | 說明 |
|------|------|------|
| `id` | int | 記錄ID |
| `manufacturer_name` | string | 廠商名稱 |
| `production_line_name` | string | 產線名稱 |
| `model_name` | string | 型號名稱 |
| `inspector_name` | string | 檢驗員用戶名 |
| `reviewer_name` | string | 審核員用戶名 |
| `test_station_names` | string | 測試站名稱（逗號分隔） |
| `inspection_date` | string | 巡檢日期 (yyyy-MM-dd) |
| `review_status` | string | 審核狀態: pending/approved/rejected |
| `is_draft` | int | 0=正式, 1=草稿 |
| `created_at` | string | 創建時間 |

### 5.8 詳情三層結構
```
StationGroup
├── test_station_id: int
├── test_station_name: string
└── items: List<DetailItem>
    ├── item_id: int
    ├── item_name: string
    ├── input_type: string
    ├── options: string (逗號分隔)
    ├── value: string
    ├── image_path: string (多張圖逗號/分號分隔)
    ├── remarks: string
    └── details: List<DetailSubItem>
        ├── detail_id: int
        ├── detail_name: string
        ├── value: string
        ├── image_path: string
        └── remarks: string
```

---

## 6. 權限對照表

| 端點 | vendor | admin | user | readonly |
|------|--------|-------|------|----------|
| POST /api/auth/login | ✅ | ✅ | ✅ | ✅ |
| GET /api/vendor/init_data | ✅ | ✅ | - | - |
| GET /api/vendor/dashboard | ✅ | ✅ | - | - |
| GET /api/vendor/production_lines | ✅ | ✅ | - | - |
| POST /api/vendor/load_items | ✅ | ✅ | - | - |
| POST /api/vendor/upload_image | ✅ | ✅ | - | - |
| POST /api/vendor/submit | ✅ | ✅ | - | - |
| GET /api/vendor/query | ✅ | ✅ | ✅ | ? |
| GET /api/vendor/detail | ✅ | ✅ | ✅ | ? |
| POST /api/vendor/review | - | ✅ | ✅ | - |
| POST /api/vendor/delete_record | - | ✅ | ✅ | - |
| GET /api/vendor/drafts | ✅ | ✅ | - | - |
| GET /api/vendor/draft_data | ✅ | ✅ | - | - |
| POST /api/vendor/delete_draft | ✅ | ✅ | - | - |
| GET /api/vendor/download_report | ✅ | ✅ | ✅ | ? |

> ✅ = 有權限，- = 無權限，? = 未明確定義但可訪問

---

## 7. 常見錯誤處理

### HTTP 狀態碼
| 狀態碼 | 說明 | 處理方式 |
|--------|------|----------|
| 200 | 成功 | 檢查 `success` 字段 |
| 400 | 請求格式錯誤或缺少必填字段 | 檢查請求體 |
| 401 | Token 無效或過期 | 重新登入 |
| 403 | 無權限 | 檢查用戶角色 |
| 404 | 資源不存在 | 檢查 ID |
| 500 | 服務器內部錯誤 | 聯繫管理員 |

### 響應格式示例
```json
// 成功
{"success": true, "message": "提交成功", "record_id": 15, ...}

// 驗證錯誤
{"success": false, "error": "請選擇廠商"}
{"success": false, "error": "缺少必填字段: production_line_id"}

// 權限錯誤
{"success": false, "message": "无权访问"}
{"success": false, "error": "無權操作該廠商"}

// Token 驗證失敗
// (HTTP 401)
{"success": false, "message": "Token已过期或无效"}
```

### 廠商權限特殊處理
- Vendor 角色的 `data.manufacturers` 限制可操作的廠商範圍
- 多廠商場景下，API 使用 `IN (...)` 子句查詢
- 查詢時 vendor 用戶如果選擇具體廠商，使用該廠商過濾；不選擇時查詢所有關聯廠商
