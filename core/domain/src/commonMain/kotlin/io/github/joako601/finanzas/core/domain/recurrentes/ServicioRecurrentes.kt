package io.github.joako601.finanzas.core.domain.recurrentes

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Errores del alta de un pago o ingreso fijo. */
public enum class ErrorRecurrente { NOMBRE_VACIO, MONTO_INVALIDO, SIN_CATEGORIA }

/**
 * Casos de uso de pagos e ingresos fijos. Registrar uno escribe en el libro y avanza la
 * próxima fecha en la misma transacción: nunca queda el movimiento sin avanzar la fecha
 * ni la fecha avanzada sin el movimiento.
 */
public class ServicioRecurrentes(
    private val libro: RepositorioLibro,
    private val recurrentes: RepositorioRecurrentes,
) {

    /** Valida el alta. La categoría debe ser de gasto para un pago y de ingreso para un ingreso. */
    public fun validarAlta(nombre: String, montoTexto: String, clase: ClaseRegistro, categoria: Cuenta?): Set<ErrorRecurrente> =
        buildSet {
            if (nombre.isBlank() || nombre.trim().length > Cuenta.LARGO_MAXIMO_NOMBRE) add(ErrorRecurrente.NOMBRE_VACIO)
            if (Dinero.parsear(montoTexto)?.esPositivo != true) add(ErrorRecurrente.MONTO_INVALIDO)
            if (categoria == null || categoria.tipo != clase.tipoDeCategoria) add(ErrorRecurrente.SIN_CATEGORIA)
        }

    /** Un pago se cobra de efectivo, débito o tarjeta; un ingreso solo llega a efectivo o débito. */
    private fun validarCuenta(cuenta: Cuenta?, clase: ClaseRegistro) {
        require(
            cuenta == null || cuenta.tipo == TipoCuenta.ACTIVO || (cuenta.tipo == TipoCuenta.PASIVO && clase == ClaseRegistro.GASTO),
        ) { "La cuenta no corresponde" }
    }

    /**
     * Da de alta la serie. Si [inicio] ya pasó (por ejemplo, la fecha del último recibo),
     * la próxima pendiente es la primera de la serie desde [hoy].
     */
    public suspend fun crear(
        nombre: String,
        clase: ClaseRegistro,
        montoEstimado: Dinero,
        categoria: Cuenta,
        periodicidad: Periodicidad,
        inicio: LocalDate,
        hoy: LocalDate,
        cuenta: Cuenta? = null,
    ): Recurrente {
        require(categoria.tipo == clase.tipoDeCategoria) { "La categoría no corresponde" }
        validarCuenta(cuenta, clase)
        val primera = periodicidad.primeraDesde(inicio)
        val proxima = if (primera >= hoy) primera else periodicidad.siguienteDespues(inicio, hoy.minus(1, DateTimeUnit.DAY))
        val nuevo = Recurrente(null, nombre.trim(), clase, montoEstimado, categoria.id, periodicidad, inicio, proxima, cuenta?.id)
        return nuevo.copy(id = recurrentes.guardar(nuevo))
    }

    /**
     * Actualiza los datos de un pago o ingreso fijo existente y recalcula su próxima fecha
     * pendiente a partir de [inicio] y [hoy].
     *
     * Si [conservarProxima] es verdadero y se proporciona [proximaAnterior] (por ejemplo, al
     * editar solo nombre, monto o categoría sin tocar fecha ni frecuencia), se conservan tanto
     * el ancla [inicio] como la [proximaAnterior] pendiente (incluso si está vencida o adelantada).
     * De lo contrario, si [proximaAnterior] estaba vencida y [inicio] == [proximaAnterior],
     * conserva esa ocurrencia pendiente.
     */
    public suspend fun actualizar(
        id: RecurrenteId,
        nombre: String,
        clase: ClaseRegistro,
        montoEstimado: Dinero,
        categoria: Cuenta,
        periodicidad: Periodicidad,
        inicio: LocalDate,
        hoy: LocalDate,
        proximaAnterior: LocalDate? = null,
        conservarProxima: Boolean = false,
        cuenta: Cuenta? = null,
    ): Recurrente {
        require(categoria.tipo == clase.tipoDeCategoria) { "La categoría no corresponde" }
        validarCuenta(cuenta, clase)
        val primera = periodicidad.primeraDesde(inicio)
        val proxima = when {
            conservarProxima && proximaAnterior != null && proximaAnterior >= primera -> proximaAnterior
            proximaAnterior != null && inicio == proximaAnterior && primera == proximaAnterior -> primera
            primera >= hoy -> primera
            else -> periodicidad.siguienteDespues(inicio, hoy.minus(1, DateTimeUnit.DAY))
        }
        val actualizado = Recurrente(id, nombre.trim(), clase, montoEstimado, categoria.id, periodicidad, inicio, proxima, cuenta?.id)
        recurrentes.actualizar(actualizado)
        return actualizado
    }

    /**
     * Registra la ocurrencia pendiente con lo que pasó en realidad (monto, cuenta y fecha)
     * y avanza la próxima fecha. Devuelve los errores de captura sin guardar nada si los hay.
     */
    public suspend fun registrar(
        recurrente: Recurrente,
        montoTexto: String,
        cuenta: Cuenta?,
        categoria: Cuenta?,
        fecha: LocalDate,
    ): ResultadoRegistro {
        val id = requireNotNull(recurrente.id) { "Solo se registra una serie guardada" }
        val resultado = Registro.desdeCaptura(recurrente.clase, montoTexto, cuenta, categoria, fecha, recurrente.nombre)
        if (resultado is ResultadoRegistro.Valido) {
            libro.enTransaccion {
                libro.registrar(resultado.movimiento)
                recurrentes.cambiarProxima(id, recurrente.despuesDeProxima())
            }
        }
        return resultado
    }

    /** Esta vez no hubo pago o ingreso (o se registró a mano): solo avanza la fecha. */
    public suspend fun omitir(recurrente: Recurrente) {
        recurrentes.cambiarProxima(requireNotNull(recurrente.id), recurrente.despuesDeProxima())
    }

    public suspend fun eliminar(recurrente: Recurrente) {
        recurrentes.eliminar(requireNotNull(recurrente.id))
    }
}
