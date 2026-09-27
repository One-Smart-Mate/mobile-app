package com.ih.osm.features.settings.data.local

import android.content.Context
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesMobileDataSyncPreferences(context: Context) : MobileDataSyncPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableAllowMobileData = MutableStateFlow(
        preferences.getBoolean(ALLOW_MOBILE_DATA_KEY, true),
    )

    override val allowMobileData: StateFlow<Boolean> = mutableAllowMobileData.asStateFlow()

    override fun setAllowMobileData(allow: Boolean) {
        if (mutableAllowMobileData.value == allow) return
        preferences.edit().putBoolean(ALLOW_MOBILE_DATA_KEY, allow).apply()
        mutableAllowMobileData.value = allow
    }

    override fun reset() {
        preferences.edit().remove(ALLOW_MOBILE_DATA_KEY).apply()
        mutableAllowMobileData.value = true
    }

    private companion object {
        const val PREFERENCES_NAME = "sync_preferences"
        const val ALLOW_MOBILE_DATA_KEY = "allow_mobile_data"
    }
}
