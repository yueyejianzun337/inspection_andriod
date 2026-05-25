package com.eqm.inspection.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.data.api.models.LoginRequest
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val token: String? = null,
    val username: String? = null,
    val role: String? = null
)

class LoginViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState(error = "請輸入用戶名和密碼")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            try {
                val response = ApiClient.apiService.login(
                    LoginRequest(username.trim(), password)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success && body.data != null) {
                        val data = body.data
                        ApiClient.currentToken = data.token
                        _uiState.value = LoginUiState(
                            isSuccess = true,
                            token = data.token,
                            username = data.user.username,
                            role = data.user.role
                        )
                    } else {
                        _uiState.value = LoginUiState(
                            error = body?.message ?: body?.error ?: "登入失敗"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = try {
                        val err = Gson().fromJson(errorBody, Map::class.java)
                        (err["message"] ?: err["error"]) as? String ?: "登入失敗"
                    } catch (e: Exception) {
                        "登入失敗 (${response.code()})"
                    }
                    _uiState.value = LoginUiState(error = msg)
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState(
                    error = "網絡錯誤: ${e.localizedMessage ?: "無法連接到服務器"}"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState()
    }
}
