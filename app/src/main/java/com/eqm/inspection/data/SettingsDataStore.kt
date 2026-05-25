package com.eqm.inspection.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

class SettingsDataStore(private val context: Context) {

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        const val DEFAULT_SERVER_URL = "http://192.168.16.226:8000"
    }

    val serverUrlFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: DEFAULT_SERVER_URL
    }

    suspend fun getServerUrl(): String {
        return context.settingsDataStore.data.first()[SERVER_URL_KEY] ?: DEFAULT_SERVER_URL
    }

    suspend fun saveServerUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = url.trimEnd('/')
        }
    }
}
