package io.github.joako601.finanzas.ui.analisis

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import io.github.joako601.finanzas.core.domain.tarjetas.CalendarioBancario
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.joako601.finanzas.FinanzasApp
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.metas.RepositorioMetas
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.ui.diasDesde
import io.github.joako601.finanzas.ui.disponible.EstadoLineaDeTiempo
import io.github.joako601.finanzas.ui.disponible.calcularLinea
import io.github.joako601.finanzas.ui.hoy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

/**
 * Estado inmutable del apartado `Análisis`.
 * Precalcula en hilo de fondo los cortes de los tres periodos para que el cambio de
 * horizonte en la interfaz sea instantáneo (`0 ms`). [mesDesplazamiento]: 0 = este mes,
 * −1 = el anterior; solo mueve el periodo "Mes".
 */
data class EstadoAnalisis(
    val cargando: Boolean = true,
    val tieneCuentasDeDinero: Boolean = false,
    val estadoLinea: EstadoLineaDeTiempo = EstadoLineaDeTiempo(),
    val cortes: Map<PeriodoAnalisis, CortePeriodoAnalisis> = emptyMap(),
    val mesDesplazamiento: Int = 0,
)

class AnalisisViewModel(
    libro: RepositorioLibro,
    tarjetas: RepositorioTarjetas,
    recurrentes: RepositorioRecurrentes,
    metas: RepositorioMetas,
    fechaDeHoy: () -> LocalDate,
    private val calendario: Flow<CalendarioBancario> = flowOf(CalendarioBancario.NINGUNO),
) : ViewModel() {

    private val dias = diasDesde(fechaDeHoy)

    private val mesDesplazamiento = MutableStateFlow(0)

    /** −1 = mes anterior; nunca pasa del mes actual. */
    fun moverMes(delta: Int) = mesDesplazamiento.update { (it + delta).coerceAtMost(0) }

    val estado: StateFlow<EstadoAnalisis> = calcularLinea(libro, tarjetas, recurrentes, metas, dias, calendario)
        .combine(mesDesplazamiento) { calculo, desplazamiento ->
            EstadoAnalisis(
                cargando = false,
                tieneCuentasDeDinero = calculo.cuentas.any { !it.archivada && it.tipo == TipoCuenta.ACTIVO },
                estadoLinea = calculo.estado,
                cortes = PeriodoAnalisis.entries.associateWith { periodo ->
                    CalculadorAnalisis.calcularCorte(
                        periodo = periodo,
                        cuentasPorId = calculo.estado.categorias,
                        movimientos = calculo.movimientos,
                        linea = calculo.linea,
                        mesDesplazamiento = desplazamiento,
                    )
                },
                mesDesplazamiento = desplazamiento,
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
                    calendario = contenedor.calendarioBancario,
                )
            }
        }
    }
}
