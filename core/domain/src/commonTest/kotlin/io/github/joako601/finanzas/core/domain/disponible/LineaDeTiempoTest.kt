package io.github.joako601.finanzas.core.domain.disponible

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.PagoATarjeta
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineaDeTiempoTest {

    private fun fecha(texto: String) = LocalDate.parse(texto)

    private val debito = Cuenta(CuentaId(1), "Débito", TipoCuenta.ACTIVO)
    private val cuentaNu = Cuenta(CuentaId(2), "Nu", TipoCuenta.PASIVO)
    private val luz = Cuenta(CuentaId(3), "Luz, agua y gas", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(4), "Sueldo", TipoCuenta.INGRESO)

    // Corte el 15, límite el 5 del mes siguiente.
    private val nu = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 15, diaLimite = 5)

    private fun estado(corte: String, limite: String, sinIntereses: Long) = EstadoDeCuenta(
        id = 1, tarjeta = nu.id, fechaCorte = fecha(corte), fechaLimite = fecha(limite),
        pagoSinIntereses = Dinero.pesos(sinIntereses), pagoMinimo = Dinero.pesos(1),
        cortePredicho = null, limitePredicho = null,
    )

    private fun fijo(id: Long, nombre: String, clase: ClaseRegistro, monto: Long, periodicidad: Periodicidad, proxima: String) =
        Recurrente(
            RecurrenteId(id), nombre, clase, Dinero.pesos(monto), if (clase == ClaseRegistro.GASTO) luz.id else sueldo.id,
            periodicidad, fecha(proxima), fecha(proxima),
        )

    private fun pesosConSigno(pesos: Long) = if (pesos < 0) -Dinero.pesos(-pesos) else Dinero.pesos(pesos)

    private fun linea(
        hoy: String,
        enDebito: Long = 10_000,
        deudaNu: Long = 0,
        tarjetas: List<Tarjeta> = emptyList(),
        estados: List<EstadoDeCuenta> = emptyList(),
        pagos: List<PagoATarjeta> = emptyList(),
        recurrentes: List<Recurrente> = emptyList(),
    ) = LineaDeTiempo.calcular(
        cuentas = listOf(debito, cuentaNu, luz, sueldo),
        saldos = mapOf(debito.id to pesosConSigno(enDebito), cuentaNu.id to -pesosConSigno(deudaNu)),
        tarjetas = tarjetas,
        estados = estados,
        pagos = pagos,
        recurrentes = recurrentes,
        hoy = fecha(hoy),
    )

    // ---- Disponible real ----

    @Test
    fun sin_compromisos_el_disponible_es_lo_que_hay_en_cuentas() {
        val l = linea(hoy = "2026-11-19")
        assertEquals(Dinero.pesos(10_000), l.disponible)
        assertEquals(Dinero.pesos(10_000), l.enCuentas)
        assertEquals(fecha("2026-12-19"), l.horizonte)
        assertNull(l.ingresoQueCierra)
    }

    @Test
    fun el_horizonte_es_el_proximo_ingreso_esperado() {
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(quincena))
        assertEquals(fecha("2026-11-30"), l.horizonte)
        assertEquals("Quincena", l.ingresoQueCierra?.nombre)
    }

    @Test
    fun descuenta_los_pagos_antes_del_horizonte_y_no_los_de_despues() {
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val internet = fijo(2, "Internet", ClaseRegistro.GASTO, 400, Periodicidad.MENSUAL, "2026-11-25")
        val cfe = fijo(3, "CFE", ClaseRegistro.GASTO, 650, Periodicidad.BIMESTRAL, "2026-12-10")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(quincena, internet, cfe))

        assertEquals(Dinero.pesos(9_600), l.disponible)
        assertEquals(listOf("Internet"), l.comprometido.map { it.nombre })
    }

    @Test
    fun un_pago_fijo_vencido_sigue_comprometido() {
        // El del 10 nov no se registró; el del 10 dic también cae en los 30 días.
        val internet = fijo(2, "Internet", ClaseRegistro.GASTO, 400, Periodicidad.MENSUAL, "2026-11-10")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(internet))
        assertEquals(Dinero.pesos(9_200), l.disponible)
        assertEquals(listOf(true, false), l.comprometido.map { it.vencido })
    }

    @Test
    fun descuenta_lo_que_falta_del_estado_de_cuenta_vigente() {
        // Estado del 15 nov: $2,500 para no generar intereses, límite 5 dic. Ya pagó $1,000.
        val l = linea(
            hoy = "2026-11-19", deudaNu = 3_000, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-11-17"), Dinero.pesos(1_000))),
        )
        val pago = l.comprometido.single()
        assertEquals(TipoEvento.PAGO_TARJETA, pago.tipo)
        assertEquals(fecha("2026-12-05"), pago.fecha)
        assertEquals(Dinero.pesos(1_500), pago.monto)
        assertTrue(pago.exacto)
        assertEquals(Dinero.pesos(8_500), l.disponible)
    }

    @Test
    fun sin_estado_capturado_estima_con_la_deuda_actual() {
        // Corte del 15 nov sin capturar; límite predicho 5 dic.
        val l = linea(hoy = "2026-11-19", deudaNu = 3_000, tarjetas = listOf(nu))
        val pago = l.comprometido.single { it.tipo == TipoEvento.PAGO_TARJETA }
        assertEquals(fecha("2026-12-05"), pago.fecha)
        assertEquals(Dinero.pesos(3_000), pago.monto)
        assertEquals(false, pago.exacto)
    }

    @Test
    fun el_siguiente_ciclo_solo_estima_lo_que_no_cubre_el_vigente() {
        // Hoy 20 dic: el vigente (corte 15 dic) vence el 5 ene; el siguiente corte (15 ene)
        // vence el 5 feb, fuera de 30 días. Deuda 4,000; el vigente pide 2,500.
        val l = linea(
            hoy = "2026-12-20", deudaNu = 4_000, tarjetas = listOf(nu),
            estados = listOf(estado("2026-12-15", "2027-01-05", 2_500)),
        )
        assertEquals(listOf(Dinero.pesos(2_500)), l.eventos.filter { it.tipo == TipoEvento.PAGO_TARJETA }.map { it.monto })
    }

    @Test
    fun una_tarjeta_pagada_no_compromete_nada() {
        val l = linea(
            hoy = "2026-11-19", deudaNu = 0, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-11-17"), Dinero.pesos(2_500))),
        )
        assertTrue(l.eventos.none { it.tipo == TipoEvento.PAGO_TARJETA })
        assertEquals(Dinero.pesos(10_000), l.disponible)
    }

    // ---- Línea de tiempo ----

    @Test
    fun repite_los_fijos_dentro_de_los_30_dias_y_muestra_cortes() {
        val semanal = fijo(1, "Gimnasio", ClaseRegistro.GASTO, 100, Periodicidad.SEMANAL, "2026-11-20")
        val l = linea(hoy = "2026-11-19", tarjetas = listOf(nu), recurrentes = listOf(semanal))

        assertEquals(
            listOf("2026-11-20", "2026-11-27", "2026-12-04", "2026-12-11", "2026-12-18").map(::fecha),
            l.eventos.filter { it.nombre == "Gimnasio" }.map { it.fecha },
        )
        assertEquals(listOf(fecha("2026-12-15")), l.eventos.filter { it.tipo == TipoEvento.CORTE }.map { it.fecha })
        assertTrue(l.eventos.zipWithNext().all { (a, b) -> a.fecha <= b.fecha }, "ordenados por fecha")
    }

    @Test
    fun el_saldo_proyectado_avisa_el_primer_dia_en_negativo() {
        val renta = fijo(1, "Renta", ClaseRegistro.GASTO, 8_000, Periodicidad.MENSUAL, "2026-11-25")
        val luzFijo = fijo(2, "CFE", ClaseRegistro.GASTO, 3_000, Periodicidad.BIMESTRAL, "2026-11-28")
        val quincena = fijo(3, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(renta, luzFijo, quincena))

        assertEquals(listOf(Dinero.pesos(2_000), -Dinero.pesos(1_000), Dinero.pesos(11_000)), l.eventos.take(3).map { it.saldoDespues })
        assertEquals(fecha("2026-11-28"), l.primerFaltante?.fecha)
        assertEquals(Dinero.pesos(1_000), l.primerFaltante?.falta)
    }

    @Test
    fun el_mismo_dia_los_pagos_van_antes_que_los_ingresos() {
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val renta = fijo(2, "Renta", ClaseRegistro.GASTO, 15_000, Periodicidad.MENSUAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(quincena, renta))
        assertEquals(listOf("Renta", "Quincena"), l.eventos.take(2).map { it.nombre })
        assertEquals(fecha("2026-11-30"), l.primerFaltante?.fecha)
    }

    @Test
    fun los_ingresos_vencidos_no_se_cuentan() {
        // La quincena del 15 no se registró: no se sabe si llegó; no se suma.
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-15")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(quincena))
        assertTrue(l.eventos.none { it.vencido })
        assertEquals(fecha("2026-11-30"), l.horizonte)
    }

    /** Para cualquier saldo, deuda y pagos fijos: el disponible nunca supera lo que hay en cuentas. */
    @Test
    fun el_disponible_nunca_supera_lo_que_hay_en_cuentas() = runTest {
        checkAll(Arb.long(-5_000L..50_000L), Arb.long(-2_000L..20_000L), Arb.int(1..400), Arb.int(0..60)) { enDebito, deuda, monto, dias ->
            val proxima = fecha("2026-11-19").plus(dias, DateTimeUnit.DAY).toString()
            val l = linea(
                hoy = "2026-11-19", enDebito = enDebito, deudaNu = deuda, tarjetas = listOf(nu),
                recurrentes = listOf(fijo(1, "X", ClaseRegistro.GASTO, monto.toLong(), Periodicidad.SEMANAL, proxima)),
            )
            assertTrue(l.disponible <= l.enCuentas)
            assertTrue(l.comprometido.all { !it.monto.esNegativo && it.fecha <= l.horizonte })
        }
    }
}
