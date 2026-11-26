package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.RepositorioEnMemoria
import io.github.joako601.finanzas.core.domain.libro.RepositorioTarjetasEnMemoria
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.libro.saldosPorCuenta
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TarjetasTest {

    private val debito = Cuenta(CuentaId(1), "Débito", TipoCuenta.ACTIVO)
    private val capital = Cuenta(CuentaId(2), "Saldos iniciales", TipoCuenta.CAPITAL)
    private val super_ = Cuenta(CuentaId(3), "Súper", TipoCuenta.GASTO)
    private val cuentaNu = Cuenta(CuentaId(4), "Nu", TipoCuenta.PASIVO)
    private val cuentaOtra = Cuenta(CuentaId(5), "Otra", TipoCuenta.PASIVO)
    private val cuentas = listOf(debito, capital, super_, cuentaNu, cuentaOtra).associateBy { it.id }

    // Corte el 15, límite el 5 del mes siguiente.
    private val nu = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 15, diaLimite = 5)

    private fun fecha(texto: String) = LocalDate.parse(texto)

    private fun estado(corte: String, limite: String, sinIntereses: Long, tarjeta: CuentaId = nu.id) = EstadoDeCuenta(
        id = 1, tarjeta = tarjeta, fechaCorte = fecha(corte), fechaLimite = fecha(limite),
        pagoSinIntereses = Dinero.pesos(sinIntereses), pagoMinimo = Dinero.pesos(sinIntereses / 10),
        cortePredicho = null, limitePredicho = null,
    )

    private fun pago(fechaPago: String, pesos: Long, tarjeta: CuentaId = nu.id) =
        PagoATarjeta(tarjeta, fecha(fechaPago), Dinero.pesos(pesos))

    // ---- Situación ----

    @Test
    fun pago_pendiente_descuenta_pagos_posteriores_al_corte() {
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(pago("2026-11-20", 1_500)),
            hoy = fecha("2026-11-25"),
        )
        assertIs<SituacionTarjeta.PagoPendiente>(situacion)
        assertEquals(Dinero.pesos(2_500), situacion.restante)
        assertEquals(Dinero.pesos(1_500), situacion.pagado)
        assertEquals(10, situacion.diasParaLimite)
    }

    @Test
    fun pago_del_dia_del_corte_no_cuenta_para_ese_estado() {
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(pago("2026-11-15", 4_000)),
            hoy = fecha("2026-11-20"),
        )
        assertIs<SituacionTarjeta.PagoPendiente>(situacion)
        assertEquals(Dinero.pesos(4_000), situacion.restante)
    }

    @Test
    fun cubrir_el_pago_completo_marca_pagado() {
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(pago("2026-11-20", 3_000), pago("2026-12-01", 1_000)),
            hoy = fecha("2026-12-02"),
        )
        assertIs<SituacionTarjeta.Pagado>(situacion)
    }

    @Test
    fun con_saldo_a_favor_el_estado_de_cuenta_ya_esta_cubierto() {
        // Caso real del S24 FE: pago de $1,000 el mismo día del corte (S4 no lo cuenta para
        // ese estado), pero la tarjeta quedó con $950 a favor.
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-19", "2026-12-10", 1_000)),
            listOf(pago("2026-11-19", 1_000)),
            hoy = fecha("2026-11-20"),
            deuda = -Dinero.pesos(950),
        )
        assertIs<SituacionTarjeta.Pagado>(situacion)
    }

    @Test
    fun nunca_pide_pagar_mas_de_lo_que_se_debe_hoy() {
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            emptyList(),
            hoy = fecha("2026-11-20"),
            deuda = Dinero.pesos(1_500),
        )
        assertIs<SituacionTarjeta.PagoPendiente>(situacion)
        assertEquals(Dinero.pesos(1_500), situacion.restante)
    }

    @Test
    fun el_dia_limite_todavia_es_vigente() {
        val situacion = situacionTarjeta(
            nu, listOf(estado("2026-11-15", "2026-12-05", 4_000)), emptyList(), hoy = fecha("2026-12-05"),
        )
        assertIs<SituacionTarjeta.PagoPendiente>(situacion)
        assertEquals(0, situacion.diasParaLimite)
    }

    @Test
    fun pagos_de_otra_tarjeta_no_cuentan() {
        val situacion = situacionTarjeta(
            nu,
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(pago("2026-11-20", 4_000, tarjeta = cuentaOtra.id)),
            hoy = fecha("2026-11-25"),
        )
        assertIs<SituacionTarjeta.PagoPendiente>(situacion)
    }

    @Test
    fun corte_pasado_sin_captura_pide_capturar() {
        val situacion = situacionTarjeta(nu, emptyList(), emptyList(), hoy = fecha("2026-11-18"))
        assertEquals(SituacionTarjeta.CapturaPendiente(fecha("2026-11-15"), fecha("2026-12-05")), situacion)
    }

    @Test
    fun el_mismo_dia_del_corte_aun_no_pide_captura() {
        val situacion = situacionTarjeta(nu, emptyList(), emptyList(), hoy = fecha("2026-11-15"))
        // El corte anterior (15 oct) tenía límite el 5 nov, ya pasó: se muestra el corte de hoy.
        assertEquals(SituacionTarjeta.EnCiclo(fecha("2026-11-15")), situacion)
    }

    @Test
    fun captura_con_corte_recorrido_cuenta_como_capturado() {
        // El banco cortó el 16 en lugar del 15; ya pasó su límite: no debe pedir capturar otra vez.
        val situacion = situacionTarjeta(
            nu, listOf(estado("2026-10-16", "2026-11-05", 1_000)), emptyList(), hoy = fecha("2026-11-10"),
        )
        assertEquals(SituacionTarjeta.EnCiclo(fecha("2026-11-15")), situacion)
    }

    // ---- Pagos y resumen ----

    @Test
    fun solo_los_pagos_desde_activo_cuentan_como_pagos() {
        val f = fecha("2026-11-20")
        val movimientos = listOf(
            Registro.pagoDeTarjeta(f, Dinero.pesos(500), debito, cuentaNu),
            Registro.gasto(f, Dinero.pesos(200), cuentaNu, super_),
            Registro.saldoInicial(f, cuentaNu, -Dinero.pesos(1_000), capital),
        )
        assertEquals(listOf(PagoATarjeta(nu.id, f, Dinero.pesos(500))), pagosATarjetas(movimientos, cuentas))
    }

    @Test
    fun pasar_deuda_de_una_tarjeta_a_otra_no_es_un_pago() {
        // Baja la deuda de Nu y sube la de Otra: no salió dinero de efectivo ni débito.
        val traspaso = Movimiento(
            id = null,
            fecha = fecha("2026-11-20"),
            descripcion = "",
            asientos = listOf(Asiento(cuentaNu.id, Dinero.pesos(100)), Asiento(cuentaOtra.id, -Dinero.pesos(100))),
        )
        assertEquals(ClaseMovimiento.TRANSFERENCIA, traspaso.resumir(cuentas).clase)
        assertEquals(emptyList(), pagosATarjetas(listOf(traspaso), cuentas))
    }

    @Test
    fun deuda_sube_con_compras_y_baja_con_pagos() {
        val f = fecha("2026-11-20")
        val saldos = saldosPorCuenta(
            listOf(
                Registro.saldoInicial(f, cuentaNu, -Dinero.pesos(1_000), capital),
                Registro.gasto(f, Dinero.pesos(200), cuentaNu, super_),
                Registro.pagoDeTarjeta(f, Dinero.pesos(500), debito, cuentaNu),
            ),
        )
        assertEquals(Dinero.pesos(700), deudaDe(nu, saldos))
    }

    @Test
    fun resumen_reconoce_pago_compra_y_deuda_inicial() {
        val f = fecha("2026-11-20")

        val pago = Registro.pagoDeTarjeta(f, Dinero.pesos(500), debito, cuentaNu).resumir(cuentas)
        assertEquals(ClaseMovimiento.PAGO_TARJETA, pago.clase)
        assertEquals(Dinero.pesos(500), pago.monto)
        assertEquals(debito, pago.cuenta)
        assertEquals(cuentaNu, pago.contraparte)

        val compra = Registro.gasto(f, Dinero.pesos(200), cuentaNu, super_).resumir(cuentas)
        assertEquals(ClaseMovimiento.GASTO, compra.clase)
        assertEquals(cuentaNu, compra.cuenta)

        val deuda = Registro.saldoInicial(f, cuentaNu, -Dinero.pesos(1_000), capital).resumir(cuentas)
        assertEquals(ClaseMovimiento.SALDO_INICIAL, deuda.clase)
        assertEquals(Dinero.pesos(1_000), deuda.monto)
        assertEquals(cuentaNu, deuda.cuenta)
    }

    @Test
    fun pago_de_tarjeta_rechaza_cuentas_equivocadas() {
        val f = fecha("2026-11-20")
        assertFailsWith<IllegalArgumentException> { Registro.pagoDeTarjeta(f, Dinero.pesos(1), cuentaNu, cuentaNu) }
        assertFailsWith<IllegalArgumentException> { Registro.pagoDeTarjeta(f, Dinero.pesos(1), debito, debito) }
        assertFailsWith<IllegalArgumentException> { Registro.pagoDeTarjeta(f, Dinero.CERO, debito, cuentaNu) }
    }

    @Test
    fun estado_de_cuenta_rechaza_limite_antes_del_corte() {
        assertFailsWith<IllegalArgumentException> { estado("2026-11-15", "2026-11-15", 100) }
    }

    // ---- Servicio ----

    @Test
    fun crear_tarjeta_con_deuda_registra_saldo_inicial_negativo() = runTest {
        val libro = RepositorioEnMemoria()
        val repo = RepositorioTarjetasEnMemoria()
        val servicio = ServicioTarjetas(libro, repo)

        val tarjeta = servicio.crearTarjeta("  Nu ", Dinero.pesos(30_000), 15, 5, Dinero.pesos(2_000), fecha("2026-11-18"))

        assertEquals("Nu", tarjeta.cuenta.nombre)
        assertEquals(TipoCuenta.PASIVO, tarjeta.cuenta.tipo)
        assertEquals(Dinero.pesos(2_000), deudaDe(tarjeta, libro.observarSaldos().first()))
        assertEquals(1, libro.transacciones)
    }

    @Test
    fun validar_alta_junta_los_errores() {
        val servicio = ServicioTarjetas(RepositorioEnMemoria(), RepositorioTarjetasEnMemoria())
        assertEquals(
            setOf(
                ErrorTarjeta.NOMBRE_VACIO, ErrorTarjeta.LINEA_INVALIDA, ErrorTarjeta.DIA_DE_CORTE_INVALIDO,
                ErrorTarjeta.DIA_LIMITE_INVALIDO, ErrorTarjeta.DEUDA_INVALIDA,
            ),
            servicio.validarAlta(" ", "abc", "0", "32", "1.234"),
        )
        assertTrue(servicio.validarAlta("Nu", "30,000", "15", "5", "").isEmpty())
        assertEquals(setOf(ErrorTarjeta.NOMBRE_VACIO), servicio.validarAlta("x".repeat(61), "30,000", "15", "5", ""))
    }

    @Test
    fun capturar_guarda_la_prediccion_del_ciclo() = runTest {
        val repo = RepositorioTarjetasEnMemoria()
        val servicio = ServicioTarjetas(RepositorioEnMemoria(), repo)

        val errores = servicio.capturarEstadoDeCuenta(nu, fecha("2026-11-16"), fecha("2026-12-06"), "4,000.50", "400")

        assertTrue(errores.isEmpty())
        val guardado = repo.estados.value.single()
        assertEquals(Dinero(400_050), guardado.pagoSinIntereses)
        assertEquals(fecha("2026-11-15"), guardado.cortePredicho)
        assertEquals(fecha("2026-12-05"), guardado.limitePredicho)
    }

    @Test
    fun capturar_valida_montos_y_fechas() = runTest {
        val repo = RepositorioTarjetasEnMemoria()
        val servicio = ServicioTarjetas(RepositorioEnMemoria(), repo)
        val errores = servicio.capturarEstadoDeCuenta(nu, fecha("2026-11-15"), fecha("2026-11-10"), "", "x")
        assertEquals(
            setOf(
                ErrorEstadoDeCuenta.PAGO_SIN_INTERESES_INVALIDO,
                ErrorEstadoDeCuenta.PAGO_MINIMO_INVALIDO,
                ErrorEstadoDeCuenta.LIMITE_NO_POSTERIOR_AL_CORTE,
            ),
            errores,
        )
        assertTrue(repo.estados.value.isEmpty())
    }

    @Test
    fun capturar_el_mismo_corte_reemplaza() = runTest {
        val repo = RepositorioTarjetasEnMemoria()
        val servicio = ServicioTarjetas(RepositorioEnMemoria(), repo)
        servicio.capturarEstadoDeCuenta(nu, fecha("2026-11-15"), fecha("2026-12-05"), "100", "10")
        servicio.capturarEstadoDeCuenta(nu, fecha("2026-11-15"), fecha("2026-12-05"), "200", "20")
        assertEquals(Dinero.pesos(200), repo.estados.value.single().pagoSinIntereses)
    }

    @Test
    fun un_estado_de_cuenta_rechaza_minimo_mayor_al_pago_sin_intereses() {
        assertFailsWith<IllegalArgumentException> {
            estado("2026-11-15", "2026-12-05", 1_000).copy(pagoMinimo = Dinero.pesos(1_001))
        }
    }
}
