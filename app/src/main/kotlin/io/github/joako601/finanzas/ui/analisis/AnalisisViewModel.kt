package io.github.joako601.finanzas.ui.analisis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.metas.AnalizadorMetas
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.pagosATarjetas
import io.github.joako601.finanzas.ui.disponible.EstadoLineaDeTiempo
import io.github.joako601.finanzas.ui.hoy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/**
 * Estado inmutable del apartado `Análisis`.
 * Precalcula en hilo de fondo los cortes de 15 y 30 días para que el cambio de
 * horizonte en la interfaz sea instantáneo (`0 ms`).
 */
data class EstadoAnalisis(
    val cargando: Boolean = true,
    val tieneCuentasDeDinero: Boolean = false,
    val estadoLinea: EstadoLineaDeTiempo = EstadoLineaDeTiempo(),
    val cortes: Map<PeriodoAnalisis, CortePeriodoAnalisis> = emptyMap(),
)

class AnalisisViewModel(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    metas: RepositorioMetas,
    fechaDeHoy: () -> LocalDate,
) : ViewModel() {

    private val dias = flow {
        while (true) {
            emit(fechaDeHoy())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val estado: StateFlow<EstadoAnalisis> = combine(
        combine(libro.observarCuentas(), libro.observarSaldos(), libro.observarMovimientos(), ::Triple),
        combine(tarjetas.observarTarjetas(), tarjetas.observarEstadosDeCuenta(), recurrentes.observarRecurrentes(), ::Triple),
        combine(metas.observarMetas(), dias, ::Pair),
    ) { (cuentas, saldos, movimientos), (listaTarjetas, estados, listaRecurrentes), (listaMetas, hoy) ->
        val porId = cuentas.associateBy { it.id }
        val tieneCuentas = cuentas.any { !it.archivada && it.tipo == TipoCuenta.ACTIVO }
        val linea = LineaDeTiempo.calcular(
            cuentas = cuentas,
            saldos = saldos,
            tarjetas = listaTarjetas,
            estados = estados,
            pagos = pagosATarjetas(movimientos, porId),
            recurrentes = listaRecurrentes,
            hoy = hoy,
        )
        val cortes = PeriodoAnalisis.entries.associateWith { periodo ->
            CalculadorAnalisis.calcularCorte(
                periodo = periodo,
                cuentasPorId = porId,
                movimientos = movimientos,
                linea = linea,
            )
        }
        EstadoAnalisis(
            cargando = false,
            tieneCuentasDeDinero = tieneCuentas,
            estadoLinea = EstadoLineaDeTiempo(
                linea = linea,
                categorias = porId,
                desgloseMetas = AnalizadorMetas.calcularDesgloseDisponible(linea.disponible, listaMetas),
            ),
            cortes = cortes,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoAnalisis())

    companion object {
        val Fabrica: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val contenedor = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as FinanzasApp).contenedor
                AnalisisViewModel(
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
