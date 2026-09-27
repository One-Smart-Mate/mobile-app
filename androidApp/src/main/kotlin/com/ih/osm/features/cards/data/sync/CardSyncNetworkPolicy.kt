package com.ih.osm.features.cards.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ih.osm.features.settings.domain.preferences.MobileDataSyncPreferences

class CardSyncNetworkPolicy(
    context: Context,
    private val preferences: MobileDataSyncPreferences,
) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun canSynchronizeNow(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(
            connectivityManager.activeNetwork,
        ) ?: return false
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!validated) return false

        val usesCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val usesWifiOrEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return usesWifiOrEthernet || !usesCellular || preferences.allowMobileData.value
    }
}
