package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioLibro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate

/** Errores de la captura de un estado de cuenta. */
public enum class ErrorEstadoDeCuenta {
    PAGO_SIN_INTERESES_INVALIDO,
    PAGO_MINIMO_INVALIDO,
    MINIMO_MAYOR_A_SIN_INTERESES,
    LIMITE_NO_POSTERIOR_AL_CORTE,
}

/** Errores del alta de una tarjeta. */
public enum class ErrorTarjeta {
    NOMBRE_VACIO,
    LINEA_INVALIDA,
    DIA_DE_CORTE_INVALIDO,
    DIA_LIMITE_INVALIDO,
    DEUDA_INVALIDA,
}

/**
 * Casos de uso de tarjetas. Usa la transacción del libro: tarjetas y libro viven en la
 * misma base, así que una sola transacción cubre ambos repositorios.
 */
public class ServicioTarjetas(
    private val libro: RepositorioLibro,
    private val tarjetas: RepositorioTarjetas,
) {

    /**
     * Valida lo capturado en el alta. Devuelve los errores encontrados; vacío si todo
     * está bien. Los textos llegan tal cual los escribió el usuario.
     */
    public fun validarAlta(
        nombre: String,
        lineaTexto: String,
        diaDeCorteTexto: String,
        diaLimiteTexto: String,
        deudaTexto: String,
    ): Set<ErrorTarjeta> = buildSet {
        if (nombre.isBlank() || nombre.trim().length > Cuenta.LARGO_MAXIMO_NOMBRE) add(ErrorTarjeta.NOMBRE_VACIO)
        if (Dinero.parsear(lineaTexto)?.esPositivo != true) add(ErrorTarjeta.LINEA_INVALIDA)
        if (diaDeCorteTexto.trim().toIntOrNull() !in 1..31) add(ErrorTarjeta.DIA_DE_CORTE_INVALIDO)
        if (diaLimiteTexto.trim().toIntOrNull() !in 1..31) add(ErrorTarjeta.DIA_LIMITE_INVALIDO)
        if (deudaTexto.isNotBlank() && Dinero.parsear(deudaTexto) == null) add(ErrorTarjeta.DEUDA_INVALIDA)
    }

    /** Valida la edición de los parámetros de una tarjeta existente. */
    public fun validarEdicion(
        nombre: String,
        lineaTexto: String,
        diaDeCorteTexto: String,
        diaLimiteTexto: String,
    ): Set<ErrorTarjeta> = validarAlta(
        nombre = nombre,
        lineaTexto = lineaTexto,
        diaDeCorteTexto = diaDeCorteTexto,
        diaLimiteTexto = diaLimiteTexto,
        deudaTexto = "",
    )

    /**
     * Da de alta una tarjeta: crea su cuenta de pasivo, guarda sus datos y, si se indica,
     * registra la deuda actual como saldo inicial. Todo en una transacción.
     */
    public suspend fun crearTarjeta(
        nombre: String,
        lineaDeCredito: Dinero,
        diaDeCorte: Int,
        diaLimite: Int,
        deudaActual: Dinero,
        hoy: LocalDate,
    ): Tarjeta {
        val nombreLimpio = nombre.trim()
        require(nombreLimpio.isNotEmpty()) { "La tarjeta necesita nombre" }
        require(lineaDeCredito.esPositivo) { "La línea de crédito debe ser mayor a cero" }
        require(!deudaActual.esNegativo) { "La deuda no puede ser negativa" }
        return libro.enTransaccion {
            val cuenta = libro.crearCuenta(nombreLimpio, TipoCuenta.PASIVO)
            val tarjeta = tarjetas.guardarTarjeta(cuenta, lineaDeCredito, diaDeCorte, diaLimite)
            if (deudaActual.esPositivo) {
                // Deber dinero es un saldo negativo en una cuenta de pasivo.
                libro.registrar(Registro.saldoInicial(hoy, cuenta, -deudaActual, libro.cuentaDeSaldosIniciales()))
            }
            tarjeta
        }
    }

    /** Actualiza nombre, línea de crédito y días de corte/límite de una tarjeta existente. */
    public suspend fun actualizarTarjeta(
        actual: Tarjeta,
        nombre: String,
        lineaDeCredito: Dinero,
        diaDeCorte: Int,
        diaLimite: Int,
    ): Tarjeta {
        val nombreLimpio = nombre.trim()
        require(nombreLimpio.isNotEmpty()) { "La tarjeta necesita nombre" }
        require(lineaDeCredito.esPositivo) { "La línea de crédito debe ser mayor a cero" }
        return libro.enTransaccion {
            val cuentaActualizada = actual.cuenta.copy(nombre = nombreLimpio)
            tarjetas.actualizarTarjeta(cuentaActualizada, lineaDeCredito, diaDeCorte, diaLimite)
        }
    }

    /**
     * Valida y guarda el estado de cuenta capturado, junto con la predicción que la app
     * tenía para ese ciclo (para medir su error, D-011).
     * Si se está corrigiendo un estado previo y cambió [fechaCorte], pasa [fechaCorteAnterior]
     * para reemplazar el registro anterior dentro de la misma transacción sin dejar duplicados.
     */
    public suspend fun capturarEstadoDeCuenta(
        tarjeta: Tarjeta,
        fechaCorte: LocalDate,
        fechaLimite: LocalDate,
        pagoSinInteresesTexto: String,
        pagoMinimoTexto: String,
        fechaCorteAnterior: LocalDate? = null,
    ): Set<ErrorEstadoDeCuenta> {
        val pagoSinIntereses = Dinero.parsear(pagoSinInteresesTexto)
        val pagoMinimo = Dinero.parsear(pagoMinimoTexto)
        val errores = buildSet {
            if (pagoSinIntereses == null) add(ErrorEstadoDeCuenta.PAGO_SIN_INTERESES_INVALIDO)
            if (pagoMinimo == null) add(ErrorEstadoDeCuenta.PAGO_MINIMO_INVALIDO)
            if (pagoSinIntereses != null && pagoMinimo != null && pagoMinimo > pagoSinIntereses) {
                add(ErrorEstadoDeCuenta.MINIMO_MAYOR_A_SIN_INTERESES)
            }
            if (fechaLimite <= fechaCorte) add(ErrorEstadoDeCuenta.LIMITE_NO_POSTERIOR_AL_CORTE)
        }
        if (errores.isNotEmpty()) return errores

        val (cortePredicho, limitePredicho) = CalendarioTarjeta.prediccionPara(tarjeta, fechaCorte)
        libro.enTransaccion {
            if (fechaCorteAnterior != null && fechaCorteAnterior != fechaCorte) {
                tarjetas.eliminarEstadoDeCuenta(tarjeta.id, fechaCorteAnterior)
            }
            tarjetas.guardarEstadoDeCuenta(
                EstadoDeCuenta(
                    id = null,
                    tarjeta = tarjeta.id,
                    fechaCorte = fechaCorte,
                    fechaLimite = fechaLimite,
                    pagoSinIntereses = pagoSinIntereses!!,
                    pagoMinimo = pagoMinimo!!,
                    cortePredicho = cortePredicho,
                    limitePredicho = limitePredicho,
                ),
            )
        }
        return emptySet()
    }
}
