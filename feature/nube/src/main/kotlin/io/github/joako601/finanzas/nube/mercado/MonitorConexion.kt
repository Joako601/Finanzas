package io.github.joako601.finanzas.nube.mercado

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Si el teléfono tiene internet validado, como [StateFlow]. Vive lo mismo que el proceso
 * (se crea una vez en `ContenedorNube`), así que el callback no se desregistra.
 * Usa `ACCESS_NETWORK_STATE`, declarado solo en la distribución nube.
 */
internal class MonitorConexion(context: Context) {

    private val conectividad = context.getSystemService(ConnectivityManager::class.java)
    private val _conectado = MutableStateFlow(hayInternet())
    val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

    init {
        conectividad?.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(red: Network, capacidades: NetworkCapabilities) {
                    _conectado.value = capacidades.tieneInternet()
                }

                override fun onLost(red: Network) {
                    _conectado.value = false
                }
            },
        )
    }

    private fun hayInternet(): Boolean =
        conectividad?.activeNetwork?.let(conectividad::getNetworkCapabilities)?.tieneInternet() == true

    private fun NetworkCapabilities.tieneInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
