package com.eqm.inspection.data.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val ROLE_KEY = stringPreferencesKey("role")
    }

    /** 保存 Token 和相关用户信息 */
    suspend fun saveToken(token: String, username: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USERNAME_KEY] = username
            prefs[ROLE_KEY] = role
        }
    }

    /** 获取 Token */
    val tokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[TOKEN_KEY]
    }

    /** 获取用户名 */
    val usernameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }

    /** 获取角色 */
    val roleFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ROLE_KEY]
    }

    /** 同步获取 Token */
    suspend fun getTokenSync(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    /** 同步获取用户名 */
    suspend fun getUsernameSync(): String? {
        return context.dataStore.data.first()[USERNAME_KEY]
    }

    /** 同步获取角色 */
    suspend fun getRoleSync(): String? {
        return context.dataStore.data.first()[ROLE_KEY]
    }

    /** 清除登录信息 */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
