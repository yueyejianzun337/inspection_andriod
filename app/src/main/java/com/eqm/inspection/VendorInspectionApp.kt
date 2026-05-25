package com.eqm.inspection

import android.app.Application
import com.eqm.inspection.data.SettingsDataStore
import com.eqm.inspection.data.api.TokenManager

class VendorInspectionApp : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var settingsDataStore: SettingsDataStore
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        settingsDataStore = SettingsDataStore(this)
    }
}
