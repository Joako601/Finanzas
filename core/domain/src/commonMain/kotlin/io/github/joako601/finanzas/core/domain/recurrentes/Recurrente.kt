package io.github.joako601.finanzas.core.domain.recurrentes

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

@JvmInline
public value class RecurrenteId(public val valor: Long)

/**
 * Un pago que se repite (CFE, internet, predial) o un ingreso que se espera (quincena,
 * aguinaldo, PTU). No es un movimiento: es la plantilla con la que el usuario registra
 * cada uno con un toque cuando ocurre.
 *
 * [montoEstimado] es lo que se espera; el real se captura al registrar (el recibo de luz
 * cambia cada bimestre). [inicio] fija la serie de fechas y [proxima] es la que sigue
 * pendiente: avanza al registrar u omitir.
 */
public data class Recurrente(
    val id: RecurrenteId?,
    val nombre: String,
    val clase: ClaseRegistro,
    val montoEstimado: Dinero,
    val categoria: CuentaId,
    val periodicidad: Periodicidad,
    val inicio: LocalDate,
    val proxima: LocalDate,
) {
    init {
        require(nombre.isNotBlank()) { "Necesita nombre" }
        require(nombre.length <= Cuenta.LARGO_MAXIMO_NOMBRE) { "Nombre demasiado largo" }
        require(montoEstimado.esPositivo) { "El monto estimado debe ser positivo" }
        require(proxima >= periodicidad.primeraDesde(inicio)) { "La próxima fecha no puede ser anterior a la serie" }
    }

    /** La fecha que queda pendiente después de registrar u omitir la de [proxima]. */
    public fun despuesDeProxima(): LocalDate = periodicidad.siguienteDespues(inicio, proxima)
}

/** Puerto de persistencia de pagos e ingresos fijos. */
public interface RepositorioRecurrentes {

    /** Todos, de la próxima fecha más cercana a la más lejana. */
    public fun observarRecurrentes(): Flow<List<Recurrente>>

    public suspend fun guardar(recurrente: Recurrente): RecurrenteId

    public suspend fun actualizar(recurrente: Recurrente)

    public suspend fun cambiarProxima(id: RecurrenteId, proxima: LocalDate)

    public suspend fun eliminar(id: RecurrenteId)
}
