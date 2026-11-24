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
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.metas.AnalizadorMetas
import io.github.joako601.finanzas.core.domain.metas.DesgloseDisponibleMetas
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.ui.diasDesde
import io.github.joako601.finanzas.ui.hoy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/** La línea de tiempo, las categorías de pagos fijos y el desglose con apartados de metas. */
data class EstadoLineaDeTiempo(
    val linea: LineaDeTiempo? = null,
    val categorias: Map<CuentaId, Cuenta> = emptyMap(),
    val desgloseMetas: DesgloseDisponibleMetas? = null,
    val proximasRecurrentes: Map<RecurrenteId, LocalDate> = emptyMap(),
)

/** La línea de tiempo y el historial con el que se calculó; lo comparten Inicio y Análisis. */
internal class CalculoLinea(
    val estado: EstadoLineaDeTiempo,
    val linea: LineaDeTiempo,
    val cuentas: List<Cuenta>,
    val movimientos: List<Movimiento>,
)

/** Se recalcula sola cuando cambia cualquier dato que afecta la línea de tiempo. */
internal fun calcularLinea(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    metas: RepositorioMetas,
    dias: Flow<LocalDate>,
): Flow<CalculoLinea> = combine(
    combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
    combine(tarjetas.observarTarjetas(), tarjetas.observarEstadosDeCuenta(), recurrentes.observarRecurrentes(), ::Triple),
    combine(metas.observarMetas(), dias, ::Pair),
) { (cuentas, saldos, movimientos), (listaTarjetas, estados, listaRecurrentes), (listaMetas, hoy) ->
    val porId = cuentas.associateBy { it.id }
    val linea = LineaDeTiempo.calcular(
        cuentas = cuentas,
        saldos = saldos,
        tarjetas = listaTarjetas,
        estados = estados,
        pagos = pagosATarjetas(movimientos, porId),
        recurrentes = listaRecurrentes,
        hoy = hoy,
    )
    val estado = EstadoLineaDeTiempo(
        linea = linea,
        categorias = porId,
        desgloseMetas = AnalizadorMetas.calcularDesgloseDisponible(linea.disponible, listaMetas),
        proximasRecurrentes = listaRecurrentes.mapNotNull { r -> r.id?.let { it to r.proxima } }.toMap(),
    )
    CalculoLinea(estado, linea, cuentas, movimientos)
}

/**
 * Calcula la línea de tiempo con los datos que ya existen; no guarda nada. Se recalcula
 * sola cuando cambia cualquier dato que la afecta.
 */
class LineaDeTiempoViewModel(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    metas: RepositorioMetas,
    fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val dias = diasDesde(fechaDeHoy)

    val estado: StateFlow<EstadoLineaDeTiempo> = calcularLinea(libro, tarjetas, recurrentes, metas, dias)
        .map { it.estado }
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
                    metas = contenedor.repositorioMetas,
                    fechaDeHoy = ::hoy,
                )
            }
        }
    }
}
