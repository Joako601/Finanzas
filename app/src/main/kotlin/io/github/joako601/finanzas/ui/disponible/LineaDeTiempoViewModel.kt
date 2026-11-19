package io.github.joako601.finanzas.ui.disponible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.ui.hoy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** La línea de tiempo y las categorías para poner el ícono de cada pago fijo. */
data class EstadoLineaDeTiempo(
    val linea: LineaDeTiempo? = null,
    val categorias: Map<CuentaId, Cuenta> = emptyMap(),
)

/**
 * Calcula la línea de tiempo con los datos que ya existen; no guarda nada. Se recalcula
 * sola cuando cambia cualquier dato que la afecta.
 */
class LineaDeTiempoViewModel(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    private val fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    val estado: StateFlow<EstadoLineaDeTiempo> = combine(
        combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
        combine(tarjetas.observarTarjetas(), tarjetas.observarEstadosDeCuenta(), recurrentes.observarRecurrentes(), ::Triple),
    ) { (cuentas, saldos, movimientos), (listaTarjetas, estados, listaRecurrentes) ->
        val porId = cuentas.associateBy { it.id }
        EstadoLineaDeTiempo(
            linea = LineaDeTiempo.calcular(
                cuentas = cuentas,
                saldos = saldos,
                tarjetas = listaTarjetas,
                estados = estados,
                pagos = pagosATarjetas(movimientos, porId),
                recurrentes = listaRecurrentes,
                hoy = fechaDeHoy(),
            ),
            categorias = porId,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoLineaDeTiempo())

    companion object {
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contenedor = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp).contenedor
                LineaDeTiempoViewModel(
                    libro = contenedor.repositorioLibro,
                    tarjetas = contenedor.repositorioTarjetas,
                    recurrentes = contenedor.repositorioRecurrentes,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
