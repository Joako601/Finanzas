package io.github.joako601.finanzas.core.domain.recurrentes

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
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
            if (nombre.isBlank()) add(ErrorRecurrente.NOMBRE_VACIO)
            if (Dinero.parsear(montoTexto)?.esPositivo != true) add(ErrorRecurrente.MONTO_INVALIDO)
            if (categoria == null || categoria.tipo != tipoDeCategoria(clase)) add(ErrorRecurrente.SIN_CATEGORIA)
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
    ): Recurrente {
        require(categoria.tipo == tipoDeCategoria(clase)) { "La categoría no corresponde" }
        val primera = periodicidad.primeraDesde(inicio)
        val proxima = if (primera >= hoy) primera else periodicidad.siguienteDespues(inicio, hoy.minus(1, DateTimeUnit.DAY))
        val nuevo = Recurrente(null, nombre.trim(), clase, montoEstimado, categoria.id, periodicidad, inicio, proxima)
        return nuevo.copy(id = recurrentes.guardar(nuevo))
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

    private fun tipoDeCategoria(clase: ClaseRegistro): TipoCuenta = when (clase) {
        ClaseRegistro.GASTO -> TipoCuenta.GASTO
        ClaseRegistro.INGRESO -> TipoCuenta.INGRESO
    }
}
