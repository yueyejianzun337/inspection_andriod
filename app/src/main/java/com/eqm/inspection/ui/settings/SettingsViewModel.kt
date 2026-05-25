package com.eqm.inspection.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eqm.inspection.data.SettingsDataStore
import com.eqm.inspection.data.api.ApiClient
import com.eqm.inspection.data.api.models.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val saved: Boolean = false,
    val message: String? = null,
    // 修改密码相关
    val isChangingPassword: Boolean = false,
    val passwordSuccess: Boolean = false,
    val passwordError: String? = null
)

class SettingsViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            val url = settingsDataStore.serverUrlFlow.first()
            _uiState.value = _uiState.value.copy(serverUrl = url)
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, saved = false, message = null)
    }

    fun saveSettings() {
        viewModelScope.launch {
            val url = _uiState.value.serverUrl.trimEnd('/')
            settingsDataStore.saveServerUrl(url)
            ApiClient.updateBaseUrl(url)
            _uiState.value = _uiState.value.copy(
                saved = true,
                message = "設置已保存"
            )
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (oldPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = "請填寫所有欄位")
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(passwordError = "兩次輸入的新密碼不一致")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChangingPassword = true, passwordError = null)
            try {
                val response = ApiClient.apiService.changePassword(
                    ChangePasswordRequest(oldPassword, newPassword, confirmPassword)
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        _uiState.value = _uiState.value.copy(
                            isChangingPassword = false,
                            passwordSuccess = true,
                            passwordError = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isChangingPassword = false,
                            passwordError = body?.error ?: "修改失敗"
                        )
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = try {
                        val errorJson = com.google.gson.Gson().fromJson(errorBody, Map::class.java)
                        errorJson?.get("error")?.toString() ?: "修改失敗 (${response.code()})"
                    } catch (_: Exception) {
                        errorBody ?: "修改失敗 (${response.code()})"
                    }
                    _uiState.value = _uiState.value.copy(
                        isChangingPassword = false,
                        passwordError = errorMsg
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChangingPassword = false,
                    passwordError = "網絡錯誤: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, saved = false)
    }

    fun clearPasswordState() {
        _uiState.value = _uiState.value.copy(
            passwordSuccess = false,
            passwordError = null
        )
    }
}
