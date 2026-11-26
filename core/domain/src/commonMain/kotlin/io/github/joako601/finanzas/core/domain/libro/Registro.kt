package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.datetime.LocalDate

/** Lo que el usuario elige en la pantalla de captura. */
public enum class ClaseRegistro {
    GASTO,
    INGRESO,
    ;

    /** El tipo de cuenta de sus categorías: un gasto va a una categoría GASTO. */
    public val tipoDeCategoria: TipoCuenta
        get() = when (this) {
            GASTO -> TipoCuenta.GASTO
            INGRESO -> TipoCuenta.INGRESO
        }
}

/** Por qué no se pudo armar un movimiento con lo que capturó el usuario. */
public enum class ErrorRegistro {
    MONTO_VACIO,
    MONTO_INVALIDO,
    MONTO_CERO,
    SIN_CUENTA,
    SIN_CATEGORIA,
    CATEGORIA_NO_CORRESPONDE,
    NOTA_DEMASIADO_LARGA,

    /** Transferencia sin cuenta de destino, o con un destino que no es efectivo ni débito. */
    SIN_DESTINO,

    /** Transferencia de una cuenta a ella misma. */
    MISMA_CUENTA,
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
    /** Descripción del saldo inicial que cuadra una cuenta al editar su saldo actual. */
    public const val DESCRIPCION_AJUSTE: String = "Ajuste de saldo"


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
     * Pago a una tarjeta de crédito desde una cuenta de dinero: baja la deuda (cargo al
     * pasivo) y baja el dinero (abono al activo). No es un gasto: el gasto se registró al
     * comprar con la tarjeta, y contarlo otra vez al pagar lo duplicaría.
     */
    public fun pagoDeTarjeta(
        fecha: LocalDate,
        monto: Dinero,
        desde: Cuenta,
        tarjeta: Cuenta,
        nota: String = "",
    ): Movimiento {
        require(monto.esPositivo) { "El monto de un pago debe ser positivo" }
        require(desde.tipo == TipoCuenta.ACTIVO) { "Una tarjeta se paga desde efectivo o débito" }
        require(tarjeta.tipo == TipoCuenta.PASIVO) { "Solo se paga una cuenta de pasivo" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = nota.trim(),
            asientos = listOf(
                Asiento(tarjeta.id, monto),
                Asiento(desde.id, -monto),
            ),
        )
    }

    /**
     * Dinero que pasa entre cuentas propias de efectivo o débito (retirar del cajero,
     * mover a otra cuenta): baja el origen y sube el destino. No es gasto ni ingreso y no
     * cambia el total en cuentas. Pasar dinero a una tarjeta es [pagoDeTarjeta].
     */
    public fun transferencia(
        fecha: LocalDate,
        monto: Dinero,
        desde: Cuenta,
        hacia: Cuenta,
        nota: String = "",
    ): Movimiento {
        require(monto.esPositivo) { "El monto de una transferencia debe ser positivo" }
        require(desde.tipo == TipoCuenta.ACTIVO && hacia.tipo == TipoCuenta.ACTIVO) { "Solo entre efectivo y débito" }
        require(desde.id != hacia.id) { "Origen y destino deben ser distintos" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = nota.trim(),
            asientos = listOf(
                Asiento(hacia.id, monto),
                Asiento(desde.id, -monto),
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
        descripcion: String = "Saldo inicial",
    ): Movimiento {
        require(!saldo.esCero) { "Un saldo inicial en cero no se registra" }
        require(cuenta.tipo.esDeDinero) { "Solo las cuentas de dinero tienen saldo inicial" }
        require(contrapartida.tipo == TipoCuenta.CAPITAL) { "La contrapartida debe ser de capital" }
        return Movimiento(
            id = null,
            fecha = fecha,
            descripcion = descripcion,
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
        meses: Int? = null,
    ): ResultadoRegistro {
        val errores = mutableSetOf<ErrorRegistro>()
        val monto = montoCapturado(montoTexto, errores)

        if (cuenta == null || !cuenta.tipo.esDeDinero) errores += ErrorRegistro.SIN_CUENTA

        when {
            categoria == null -> errores += ErrorRegistro.SIN_CATEGORIA
            categoria.tipo != clase.tipoDeCategoria -> errores += ErrorRegistro.CATEGORIA_NO_CORRESPONDE
        }

        if (nota.trim().length > Movimiento.LARGO_MAXIMO_DESCRIPCION) {
            errores += ErrorRegistro.NOTA_DEMASIADO_LARGA
        }

        if (errores.isNotEmpty()) return ResultadoRegistro.Invalido(errores)

        // Aquí ninguno es null: cualquier null habría agregado un error arriba.
        val movimiento = when (clase) {
            // Meses sin intereses solo existe en un gasto con tarjeta; en lo demás se ignora.
            ClaseRegistro.GASTO -> gasto(fecha, monto!!, cuenta!!, categoria!!, nota)
                .copy(meses = meses.takeIf { cuenta.tipo == TipoCuenta.PASIVO })
            ClaseRegistro.INGRESO -> ingreso(fecha, monto!!, cuenta!!, categoria!!, nota)
        }
        return ResultadoRegistro.Valido(movimiento)
    }

    /** Valida la captura de una transferencia; como [desdeCaptura], junta todos los errores. */
    public fun desdeTransferencia(
        montoTexto: String,
        desde: Cuenta?,
        hacia: Cuenta?,
        fecha: LocalDate,
        nota: String,
    ): ResultadoRegistro {
        val errores = mutableSetOf<ErrorRegistro>()
        val monto = montoCapturado(montoTexto, errores)
        if (desde == null || desde.tipo != TipoCuenta.ACTIVO) errores += ErrorRegistro.SIN_CUENTA
        if (hacia == null || hacia.tipo != TipoCuenta.ACTIVO) errores += ErrorRegistro.SIN_DESTINO
        if (desde != null && desde.id == hacia?.id) errores += ErrorRegistro.MISMA_CUENTA
        if (nota.trim().length > Movimiento.LARGO_MAXIMO_DESCRIPCION) errores += ErrorRegistro.NOTA_DEMASIADO_LARGA

        if (errores.isNotEmpty()) return ResultadoRegistro.Invalido(errores)
        return ResultadoRegistro.Valido(transferencia(fecha, monto!!, desde!!, hacia!!, nota))
    }

    /** El monto escrito, o null con su error agregado a [errores]. */
    private fun montoCapturado(texto: String, errores: MutableSet<ErrorRegistro>): Dinero? {
        if (texto.isBlank()) {
            errores += ErrorRegistro.MONTO_VACIO
            return null
        }
        return Dinero.parsear(texto).also {
            when {
                it == null -> errores += ErrorRegistro.MONTO_INVALIDO
                it.esCero -> errores += ErrorRegistro.MONTO_CERO
            }
        }
    }
}
