package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.datetime.LocalDate

/** Lo que el usuario elige en la pantalla de captura. */
public enum class ClaseRegistro { GASTO, INGRESO }

/** Por qué no se pudo armar un movimiento con lo que capturó el usuario. */
public enum class ErrorRegistro {
    MONTO_VACIO,
    MONTO_INVALIDO,
    MONTO_CERO,
    SIN_CUENTA,
    SIN_CATEGORIA,
    CATEGORIA_NO_CORRESPONDE,
    NOTA_DEMASIADO_LARGA,
}

public sealed interface ResultadoRegistro {
    public data class Valido(val movimiento: Movimiento) : ResultadoRegistro
    public data class Invalido(val errores: Set<ErrorRegistro>) : ResultadoRegistro
}

/**
 * Traduce los conceptos de la interfaz (gasto, ingreso, saldo inicial) a asientos de
 * partida doble. Es el único lugar que conoce los signos; el resto de la app pide
 * "un gasto de $100 en efectivo para comida" y nunca arma asientos a mano.
 */
public object Registro {

    /** Gasto pagado desde una cuenta de dinero: sube la categoría, baja la cuenta. */
    public fun gasto(
        fecha: LocalDate,
        monto: Dinero,
        desde: Cuenta,
        categoria: Cuenta,
        nota: String = "",
    ): Movimiento {
        require(monto.esPositivo) { "El monto de un gasto debe ser positivo" }
        require(desde.tipo.esDeDinero) { "Un gasto sale de una cuenta de dinero" }
        require(categoria.tipo == TipoCuenta.GASTO) { "La categoría debe ser de gasto" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = nota.trim(),
            asientos = listOf(
                Asiento(categoria.id, monto),
                Asiento(desde.id, -monto),
            ),
        )
    }

    /** Ingreso que llega a una cuenta de dinero: sube la cuenta, la categoría se abona. */
    public fun ingreso(
        fecha: LocalDate,
        monto: Dinero,
        hacia: Cuenta,
        categoria: Cuenta,
        nota: String = "",
    ): Movimiento {
        require(monto.esPositivo) { "El monto de un ingreso debe ser positivo" }
        require(hacia.tipo.esDeDinero) { "Un ingreso llega a una cuenta de dinero" }
        require(categoria.tipo == TipoCuenta.INGRESO) { "La categoría debe ser de ingreso" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = nota.trim(),
            asientos = listOf(
                Asiento(hacia.id, monto),
                Asiento(categoria.id, -monto),
            ),
        )
    }

    /**
     * Saldo con el que arranca una cuenta. La contrapartida es una cuenta de capital,
     * así el dinero que "ya existía" no se cuenta como ingreso del mes.
     */
    public fun saldoInicial(
        fecha: LocalDate,
        cuenta: Cuenta,
        saldo: Dinero,
        contrapartida: Cuenta,
    ): Movimiento {
        require(!saldo.esCero) { "Un saldo inicial en cero no se registra" }
        require(cuenta.tipo.esDeDinero) { "Solo las cuentas de dinero tienen saldo inicial" }
        require(contrapartida.tipo == TipoCuenta.CAPITAL) { "La contrapartida debe ser de capital" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = "Saldo inicial",
            asientos = listOf(
                Asiento(cuenta.id, saldo),
                Asiento(contrapartida.id, -saldo),
            ),
        )
    }

    /**
     * Valida lo capturado en la pantalla y, si todo está bien, arma el movimiento.
     * Junta todos los errores en lugar de detenerse en el primero, para que la
     * interfaz pueda marcar todos los campos de una vez.
     */
    public fun desdeCaptura(
        clase: ClaseRegistro,
        montoTexto: String,
        cuenta: Cuenta?,
        categoria: Cuenta?,
        fecha: LocalDate,
        nota: String,
    ): ResultadoRegistro {
        val errores = mutableSetOf<ErrorRegistro>()

        val monto = if (montoTexto.isBlank()) {
            errores += ErrorRegistro.MONTO_VACIO
            null
        } else {
            Dinero.parsear(montoTexto).also {
                when {
                    it == null -> errores += ErrorRegistro.MONTO_INVALIDO
                    it.esCero -> errores += ErrorRegistro.MONTO_CERO
                }
            }
        }

        if (cuenta == null || !cuenta.tipo.esDeDinero) errores += ErrorRegistro.SIN_CUENTA

        val tipoEsperado = when (clase) {
            ClaseRegistro.GASTO -> TipoCuenta.GASTO
            ClaseRegistro.INGRESO -> TipoCuenta.INGRESO
        }
        when {
            categoria == null -> errores += ErrorRegistro.SIN_CATEGORIA
            categoria.tipo != tipoEsperado -> errores += ErrorRegistro.CATEGORIA_NO_CORRESPONDE
        }

        if (nota.trim().length > Movimiento.LARGO_MAXIMO_DESCRIPCION) {
            errores += ErrorRegistro.NOTA_DEMASIADO_LARGA
        }

        if (errores.isNotEmpty()) return ResultadoRegistro.Invalido(errores)

        // Aquí ninguno es null: cualquier null habría agregado un error arriba.
        val movimiento = when (clase) {
            ClaseRegistro.GASTO -> gasto(fecha, monto!!, cuenta!!, categoria!!, nota)
            ClaseRegistro.INGRESO -> ingreso(fecha, monto!!, cuenta!!, categoria!!, nota)
        }
        return ResultadoRegistro.Valido(movimiento)
    }
}
