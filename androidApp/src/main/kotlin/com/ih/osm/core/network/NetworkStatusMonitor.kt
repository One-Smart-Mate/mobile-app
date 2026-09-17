package com.ih.osm.core.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkConnectionStatus {
    WIFI_CONNECTED,
    WIFI_NO_INTERNET,
    CELLULAR_CONNECTED,
    CELLULAR_NO_INTERNET,
    OFFLINE,
}

interface NetworkStatusMonitor {
    val status: StateFlow<NetworkConnectionStatus>
}

internal class AndroidNetworkStatusMonitor(
    context: Context,
) : NetworkStatusMonitor {
    private val applicationContext = context.applicationContext
    private val connectivityManager = applicationContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
    private val mutableStatus = MutableStateFlow(resolveStatus())

    override val status: StateFlow<NetworkConnectionStatus> = mutableStatus.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        applicationContext.registerReceiver(
            wifiStateReceiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
        )
    }

    private fun refresh() {
        mutableStatus.value = resolveStatus()
    }

    private fun resolveStatus(): NetworkConnectionStatus {
        val capabilities = connectivityManager.allNetworks.mapNotNull(
            connectivityManager::getNetworkCapabilities,
        )
        val wifiNetworks = capabilities.filter { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        val cellularNetworks = capabilities.filter {
            it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

        return when {
            wifiNetworks.any { it.hasValidatedInternet() } ->
                NetworkConnectionStatus.WIFI_CONNECTED

            cellularNetworks.any { it.hasValidatedInternet() } ->
                NetworkConnectionStatus.CELLULAR_CONNECTED

            wifiNetworks.isNotEmpty() || wifiManager.isWifiEnabled ->
                NetworkConnectionStatus.WIFI_NO_INTERNET

            cellularNetworks.isNotEmpty() ->
                NetworkConnectionStatus.CELLULAR_NO_INTERNET

            else -> NetworkConnectionStatus.OFFLINE
        }
    }

    private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
