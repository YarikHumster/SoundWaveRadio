package com.yaros.RadioUrl.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(context: Context) {
    interface Listener {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val listeners = mutableListOf<Listener>()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            listeners.forEach { it.onNetworkAvailable() }
        }

        override fun onLost(network: Network) {
            listeners.forEach { it.onNetworkLost() }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }
}