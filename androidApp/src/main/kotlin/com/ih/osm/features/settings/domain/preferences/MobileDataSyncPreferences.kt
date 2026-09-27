package com.ih.osm.features.settings.domain.preferences

import kotlinx.coroutines.flow.StateFlow

interface MobileDataSyncPreferences {
    val allowMobileData: StateFlow<Boolean>

    fun setAllowMobileData(allow: Boolean)
    fun reset()
}
