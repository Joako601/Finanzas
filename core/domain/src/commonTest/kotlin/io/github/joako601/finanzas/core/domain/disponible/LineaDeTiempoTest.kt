package io.github.joako601.finanzas.core.domain.disponible

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.recurrentes.Periodicidad
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.tarjetas.CompraAMeses
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
import kotlin.test.assertFalse
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

    private fun pesosConSigno(pesos: Long) = if (pesos < 0) -Dinero.pesos(-pesos) else Dinero.pesos(pesos)

    private fun estado(corte: String, limite: String, sinIntereses: Long) = EstadoDeCuenta(
        id = 1, tarjeta = nu.id, fechaCorte = fecha(corte), fechaLimite = fecha(limite),
        pagoSinIntereses = Dinero.pesos(sinIntereses), pagoMinimo = Dinero.pesos(minOf(1, sinIntereses)),
        cortePredicho = null, limitePredicho = null,
    )

    private fun fijo(id: Long, nombre: String, clase: ClaseRegistro, monto: Long, periodicidad: Periodicidad, proxima: String) =
        Recurrente(
            RecurrenteId(id), nombre, clase, Dinero.pesos(monto), if (clase == ClaseRegistro.GASTO) luz.id else sueldo.id,
            periodicidad, fecha(proxima), fecha(proxima),
        )

    private fun linea(
        hoy: String,
        enDebito: Long = 10_000,
        deudaNu: Long = 0,
        tarjetas: List<Tarjeta> = emptyList(),
        estados: List<EstadoDeCuenta> = emptyList(),
        pagos: List<PagoATarjeta> = emptyList(),
        recurrentes: List<Recurrente> = emptyList(),
        compras: List<CompraAMeses> = emptyList(),
    ) = LineaDeTiempo.calcular(
        cuentas = listOf(debito, cuentaNu, luz, sueldo),
        saldos = mapOf(debito.id to pesosConSigno(enDebito), cuentaNu.id to -pesosConSigno(deudaNu)),
        tarjetas = tarjetas,
        estados = estados,
        pagos = pagos,
        recurrentes = recurrentes,
        hoy = fecha(hoy),
        compras = compras,
    )

    private fun LineaDeTiempo.pagosDeTarjeta() = eventos.filter { it.tipo == TipoEvento.PAGO_TARJETA }

    // ---- Disponible real: lo que se puede gastar hoy sin quedar en negativo ningún día ----

    @Test
    fun sin_compromisos_el_disponible_es_lo_que_hay_en_cuentas() {
        val l = linea(hoy = "2026-11-19")
        assertEquals(Dinero.pesos(10_000), l.disponible)
        assertEquals(Dinero.CERO, l.comprometido)
        assertNull(l.puntoMasBajo)
        assertEquals(fecha("2026-12-19"), l.fin)
    }

    @Test
    fun el_disponible_es_el_saldo_mas_bajo_de_los_30_dias() {
        val renta = fijo(1, "Renta", ClaseRegistro.GASTO, 8_000, Periodicidad.MENSUAL, "2026-11-25")
        val quincena = fijo(2, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(renta, quincena))

        assertEquals(Dinero.pesos(2_000), l.disponible)
        assertEquals(Dinero.pesos(8_000), l.comprometido)
        assertEquals("Renta", l.puntoMasBajo?.nombre)
    }

    @Test
    fun un_ingreso_chico_no_esconde_los_pagos_que_siguen() {
        // Caso del revisor: un reembolso de $100 mañana no puede hacer que la renta deje de contar.
        val reembolso = fijo(1, "Reembolso", ClaseRegistro.INGRESO, 100, Periodicidad.ANUAL, "2026-11-20")
        val renta = fijo(2, "Renta", ClaseRegistro.GASTO, 8_000, Periodicidad.MENSUAL, "2026-11-25")
        val quincena = fijo(3, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(reembolso, renta, quincena))

        assertEquals(Dinero.pesos(2_100), l.disponible)
    }

    @Test
    fun la_quincena_de_hoy_sin_registrar_si_cuenta() {
        // Caso del revisor: $1,000 hoy, quincena hoy, renta mañana. No debe salir en negativo.
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val renta = fijo(2, "Renta", ClaseRegistro.GASTO, 8_000, Periodicidad.MENSUAL, "2026-12-01")
        val l = linea(hoy = "2026-11-30", enDebito = 1_000, recurrentes = listOf(quincena, renta))

        assertEquals(Dinero.pesos(1_000), l.disponible)
        assertNull(l.primerFaltante)
    }

    @Test
    fun el_disponible_nunca_es_mayor_que_lo_que_hay_hoy() {
        // Un ingreso que llega antes de cualquier pago no se puede gastar hoy.
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", enDebito = 500, recurrentes = listOf(quincena))
        assertEquals(Dinero.pesos(500), l.disponible)
    }

    @Test
    fun los_ingresos_vencidos_no_se_cuentan() {
        // La quincena del 15 no se registró: no se sabe si llegó; no se suma (S11).
        val quincena = fijo(1, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-15")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(quincena))
        assertTrue(l.eventos.none { it.vencido })
        assertEquals(fecha("2026-11-30"), l.eventos.first().fecha)
    }

    @Test
    fun un_pago_fijo_vencido_sigue_comprometido() {
        // El del 10 nov no se registró; el del 10 dic también cae en los 30 días.
        val internet = fijo(2, "Internet", ClaseRegistro.GASTO, 400, Periodicidad.MENSUAL, "2026-11-10")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(internet))
        assertEquals(Dinero.pesos(9_200), l.disponible)
        assertEquals(listOf(true, false), l.eventos.map { it.vencido })
        assertEquals(RecurrenteId(2), l.eventos.first().recurrente)
    }

    // ---- Tarjetas ----

    @Test
    fun descuenta_lo_que_falta_del_estado_de_cuenta_vigente() {
        // Estado del 15 nov: $2,500 para no generar intereses, límite 5 dic. Ya pagó $1,000.
        val l = linea(
            hoy = "2026-11-19", deudaNu = 3_000, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-11-17"), Dinero.pesos(1_000))),
        )
        val pago = l.pagosDeTarjeta().single()
        assertEquals(fecha("2026-12-05"), pago.fecha)
        assertEquals(Dinero.pesos(1_500), pago.monto)
        assertTrue(pago.exacto)
        assertEquals(nu.id, pago.tarjeta)
        assertEquals(Dinero.pesos(8_500), l.disponible)
    }

    @Test
    fun un_estado_de_cuenta_vencido_y_sin_pagar_sigue_comprometido() {
        // Caso del revisor: hoy 6 dic; el estado del 15 nov (límite 5 dic) pedía $2,500 y no se pagó.
        val l = linea(
            hoy = "2026-12-06", deudaNu = 3_000, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
        )
        val pagos = l.pagosDeTarjeta()
        val vencido = pagos.first()
        assertTrue(vencido.vencido)
        assertEquals(fecha("2026-12-05"), vencido.fecha)
        assertEquals(Dinero.pesos(2_500), vencido.monto)
        // El ciclo del 15 dic (límite 5 ene) se estima solo con lo que el vencido no cubre.
        assertEquals(listOf(Dinero.pesos(2_500), Dinero.pesos(500)), pagos.map { it.monto })
        assertEquals(Dinero.pesos(7_000), l.disponible)
    }

    @Test
    fun sin_estado_capturado_estima_con_la_deuda_actual() {
        // Corte del 15 nov sin capturar; límite predicho 5 dic.
        val l = linea(hoy = "2026-11-19", deudaNu = 3_000, tarjetas = listOf(nu))
        val pago = l.pagosDeTarjeta().single()
        assertEquals(fecha("2026-12-05"), pago.fecha)
        assertEquals(Dinero.pesos(3_000), pago.monto)
        assertEquals(false, pago.exacto)
    }

    @Test
    fun sin_estado_capturado_las_mensualidades_por_facturar_no_se_piden() {
        // $12,000 a 12 MSI comprados el 1 nov: el corte del 15 nov factura solo $1,000.
        val compra = CompraAMeses(
            Registro.gasto(fecha("2026-11-01"), Dinero.pesos(12_000), cuentaNu, luz).copy(meses = 12),
            nu, Dinero.pesos(12_000), 12,
        )
        val l = linea(hoy = "2026-11-19", deudaNu = 12_500, tarjetas = listOf(nu), compras = listOf(compra))
        // Lo que se pide es la deuda menos lo que aún no se factura: 12,500 − 11,000.
        assertEquals(listOf(Dinero.pesos(1_500)), l.pagosDeTarjeta().map { it.monto })
    }

    @Test
    fun las_mensualidades_por_facturar_nunca_dejan_el_pago_en_negativo() {
        val compra = CompraAMeses(
            Registro.gasto(fecha("2026-11-01"), Dinero.pesos(12_000), cuentaNu, luz).copy(meses = 12),
            nu, Dinero.pesos(12_000), 12,
        )
        // Ya se abonó casi todo: la deuda es menor que lo que falta por facturar.
        val l = linea(hoy = "2026-11-19", deudaNu = 500, tarjetas = listOf(nu), compras = listOf(compra))
        assertEquals(emptyList(), l.pagosDeTarjeta())
        assertEquals(Dinero.pesos(10_000), l.disponible)
    }

    @Test
    fun el_siguiente_ciclo_solo_estima_lo_que_no_cubre_el_vigente() {
        // Hoy 20 dic: el vigente (corte 15 dic) vence el 5 ene; el siguiente (15 ene) vence el 5 feb, fuera de 30 días.
        val l = linea(
            hoy = "2026-12-20", deudaNu = 4_000, tarjetas = listOf(nu),
            estados = listOf(estado("2026-12-15", "2027-01-05", 2_500)),
        )
        assertEquals(listOf(Dinero.pesos(2_500)), l.pagosDeTarjeta().map { it.monto })
    }

    @Test
    fun lo_que_falta_del_estado_no_pasa_de_la_deuda() {
        // Pagó $2,700 el mismo día del corte (S4: no cuenta para ese estado). Ya no debe nada.
        val l = linea(
            hoy = "2026-11-19", deudaNu = -200, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-11-15"), Dinero.pesos(2_700))),
        )
        assertTrue(l.pagosDeTarjeta().isEmpty())
        assertEquals(Dinero.pesos(10_000), l.disponible)
    }

    @Test
    fun una_tarjeta_pagada_no_compromete_nada() {
        val l = linea(
            hoy = "2026-11-19", deudaNu = 0, tarjetas = listOf(nu),
            estados = listOf(estado("2026-11-15", "2026-12-05", 2_500)),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-11-17"), Dinero.pesos(2_500))),
        )
        assertTrue(l.pagosDeTarjeta().isEmpty())
        assertEquals(Dinero.pesos(10_000), l.disponible)
    }

    @Test
    fun una_tarjeta_archivada_no_aparece() {
        val archivada = nu.copy(cuenta = cuentaNu.copy(archivada = true))
        val l = linea(hoy = "2026-11-19", deudaNu = 3_000, tarjetas = listOf(archivada))
        assertTrue(l.eventos.isEmpty())
    }

    @Test
    fun dos_fechas_limite_en_30_dias_no_cuentan_dos_veces_la_deuda() {
        // Corte el 28 y límite el 18: en febrero caben dos límites en 30 días.
        val corta = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 28, diaLimite = 18)
        val l = linea(hoy = "2027-02-01", deudaNu = 5_000, tarjetas = listOf(corta))
        assertEquals(Dinero.pesos(5_000), l.pagosDeTarjeta().map { it.monto }.sumar())
    }

    // ---- Línea de tiempo y faltante ----

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
        val cfe = fijo(2, "CFE", ClaseRegistro.GASTO, 3_000, Periodicidad.BIMESTRAL, "2026-11-28")
        val quincena = fijo(3, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(renta, cfe, quincena))

        assertEquals(listOf(Dinero.pesos(2_000), -Dinero.pesos(1_000), Dinero.pesos(11_000)), l.eventos.take(3).map { it.saldoDespues })
        assertEquals(Faltante(fecha("2026-11-28"), Dinero.pesos(1_000)), l.primerFaltante)
        assertEquals(-Dinero.pesos(1_000), l.disponible)
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
    fun un_faltante_por_un_pago_vencido_es_de_hoy_no_del_pasado() {
        // Caso del revisor: $300 y el internet de $400 vencido el 10 nov.
        val internet = fijo(1, "Internet", ClaseRegistro.GASTO, 400, Periodicidad.MENSUAL, "2026-11-10")
        val l = linea(hoy = "2026-11-19", enDebito = 300, recurrentes = listOf(internet))
        assertEquals(Faltante(fecha("2026-11-19"), Dinero.pesos(100)), l.primerFaltante)
    }

    @Test
    fun el_dia_mas_bajo_nunca_es_una_fecha_pasada() {
        // Caso del revisor: el internet venció el 10 nov y no se ha pagado; hoy es 19 nov.
        val internet = fijo(1, "Internet", ClaseRegistro.GASTO, 400, Periodicidad.MENSUAL, "2026-11-10")
        val quincena = fijo(2, "Quincena", ClaseRegistro.INGRESO, 12_000, Periodicidad.QUINCENAL, "2026-11-30")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(internet, quincena))
        assertEquals(fecha("2026-11-19"), l.diaMasBajo)
    }

    @Test
    fun un_corte_el_31_cae_en_el_ultimo_dia_de_un_mes_corto() {
        val fin31 = Tarjeta(cuentaNu, Dinero.pesos(30_000), diaDeCorte = 31, diaLimite = 20)
        val l = linea(hoy = "2027-02-10", tarjetas = listOf(fin31))
        assertEquals(listOf(fecha("2027-02-28")), l.eventos.filter { it.tipo == TipoEvento.CORTE }.map { it.fecha })
    }

    @Test
    fun el_dia_30_entra_y_el_31_no() {
        val dia30 = fijo(1, "Dia 30", ClaseRegistro.GASTO, 100, Periodicidad.ANUAL, "2026-12-19")
        val dia31 = fijo(2, "Dia 31", ClaseRegistro.GASTO, 100, Periodicidad.ANUAL, "2026-12-20")
        val l = linea(hoy = "2026-11-19", recurrentes = listOf(dia30, dia31))
        assertEquals(listOf("Dia 30"), l.eventos.map { it.nombre })
    }

    @Test
    fun si_hoy_ya_esta_en_negativo_el_faltante_es_hoy() {
        val l = linea(hoy = "2026-11-19", enDebito = -500, tarjetas = listOf(nu))
        assertEquals(Faltante(fecha("2026-11-19"), Dinero.pesos(500)), l.primerFaltante)
    }

    @Test
    fun si_mas_adelante_falta_mas_que_hoy_se_dice_cuanto_y_cuando() {
        // Caso real del S24 FE: hoy −$1,128.50 en cuentas y un pago de $2,000 el 29 nov.
        val bbva = fijo(1, "BBVA", ClaseRegistro.GASTO, 2_000, Periodicidad.MENSUAL, "2026-11-29")
        val l = linea(hoy = "2026-11-19", enDebito = -1_128, recurrentes = listOf(bbva))

        assertEquals(Faltante(fecha("2026-11-19"), Dinero.pesos(1_128)), l.primerFaltante)
        assertEquals(Faltante(fecha("2026-11-29"), Dinero.pesos(3_128)), l.faltanteMayor)
        assertEquals(-l.faltanteMayor!!.falta, l.disponible)
    }

    @Test
    fun si_el_primer_faltante_es_el_peor_no_hay_faltante_mayor() {
        val l = linea(hoy = "2026-11-19", enDebito = -500, tarjetas = listOf(nu))
        assertNull(l.faltanteMayor)
    }

    @Test
    fun varios_pagos_el_mismo_dia_reportan_el_faltante_total_del_dia_sin_contradiccion() {
        val renta = fijo(1, "Renta", ClaseRegistro.GASTO, 1_200, Periodicidad.MENSUAL, "2026-11-25")
        val luzFija = fijo(2, "Luz", ClaseRegistro.GASTO, 500, Periodicidad.MENSUAL, "2026-11-25")
        val l = linea(hoy = "2026-11-19", enDebito = 1_000, recurrentes = listOf(renta, luzFija))

        assertEquals(Faltante(fecha("2026-11-25"), Dinero.pesos(700)), l.primerFaltante)
        assertNull(l.faltanteMayor)
        assertEquals(-Dinero.pesos(700), l.disponible)
    }

    @Test
    fun saldo_negativo_hoy_mas_pago_vencido_consolida_el_faltante_en_hoy() {
        val predial = fijo(1, "Predial", ClaseRegistro.GASTO, 400, Periodicidad.ANUAL, "2026-11-10")
        val l = linea(hoy = "2026-11-19", enDebito = -500, recurrentes = listOf(predial))

        assertEquals(Faltante(fecha("2026-11-19"), Dinero.pesos(900)), l.primerFaltante)
        assertNull(l.faltanteMayor)
        assertEquals(-Dinero.pesos(900), l.disponible)
    }

    @Test
    fun cuenta_de_activo_archivada_no_se_suma_en_enCuentas() {
        val archivada = Cuenta(CuentaId(9), "Cuenta vieja", TipoCuenta.ACTIVO, archivada = true)
        val l = LineaDeTiempo.calcular(
            cuentas = listOf(debito, archivada),
            saldos = mapOf(debito.id to Dinero.pesos(1_000), archivada.id to Dinero.pesos(5_000)),
            tarjetas = emptyList(),
            estados = emptyList(),
            pagos = emptyList(),
            recurrentes = emptyList(),
            hoy = fecha("2026-11-19"),
            compras = emptyList(),
        )
        assertEquals(Dinero.pesos(1_000), l.enCuentas)
        assertEquals(Dinero.pesos(1_000), l.disponible)
    }

    // ---- Propiedades ----

    /**
     * Para cualquier saldo, deuda, estado de cuenta, pagos y fecha:
     * - las salidas de una tarjeta nunca suman más que su deuda (no hay doble conteo);
     * - el disponible nunca supera lo que hay hoy ni ningún saldo proyectado;
     * - el disponible es negativo exactamente cuando hay un faltante.
     */
    @Test
    fun invariantes_del_disponible() = runTest {
        checkAll(
            Arb.long(-5_000L..50_000L), Arb.long(-2_000L..20_000L), Arb.long(0L..20_000L),
            Arb.long(0L..20_000L), Arb.int(0..90), Arb.int(1..400),
        ) { enDebito, deuda, sinIntereses, pagado, dias, monto ->
            val hoy = fecha("2026-10-01").plus(dias, DateTimeUnit.DAY)
            val l = LineaDeTiempo.calcular(
                cuentas = listOf(debito, cuentaNu, luz, sueldo),
                saldos = mapOf(debito.id to pesosConSigno(enDebito), cuentaNu.id to -pesosConSigno(deuda)),
                tarjetas = listOf(nu),
                estados = listOf(estado("2026-10-15", "2026-11-05", sinIntereses)),
                pagos = listOf(PagoATarjeta(nu.id, fecha("2026-10-20"), Dinero.pesos(pagado))).filter { pagado > 0 },
                recurrentes = listOf(
                    fijo(1, "X", ClaseRegistro.GASTO, monto.toLong(), Periodicidad.SEMANAL, "2026-10-03"),
                    // Un ingreso hace que el saldo baje y vuelva a subir.
                    fijo(2, "Quincena", ClaseRegistro.INGRESO, monto.toLong() * 3, Periodicidad.QUINCENAL, "2026-10-15"),
                ),
                hoy = hoy,
                compras = emptyList(),
            )
            val deTarjeta = l.pagosDeTarjeta().map { it.monto }.sumar()
            assertTrue(deTarjeta <= maxOf(pesosConSigno(deuda), Dinero.CERO), "doble conteo: $deTarjeta con deuda $deuda")
            assertTrue(l.disponible <= l.enCuentas)
            assertTrue(l.eventos.all { l.disponible <= it.saldoDespues })
            assertEquals(l.disponible.esNegativo, l.primerFaltante != null)
            // El faltante mayor, si existe, es la cifra del disponible y supera al primero.
            l.faltanteMayor?.let { mayor ->
                assertEquals(-mayor.falta, l.disponible)
                assertTrue(mayor.falta > l.primerFaltante!!.falta)
            }
            assertTrue(l.primerFaltante?.let { it.fecha >= hoy } ?: true)
            assertTrue(l.diaMasBajo?.let { it >= hoy } ?: true, "día más bajo en el pasado")
            assertEquals(l.puntoMasBajo == null, l.diaMasBajo == null)
        }
    }

    // ---- Pago fijo domiciliado a tarjeta (D-023) ----

    @Test
    fun un_pago_fijo_cargado_a_la_tarjeta_aparece_pero_no_resta_del_efectivo_en_su_fecha() {
        val netflix = fijo(9, "Netflix", ClaseRegistro.GASTO, 300, Periodicidad.MENSUAL, "2026-11-25").copy(cuenta = nu.id)
        val luzDebito = fijo(10, "CFE", ClaseRegistro.GASTO, 500, Periodicidad.MENSUAL, "2026-11-26").copy(cuenta = debito.id)

        val l = linea(hoy = "2026-11-20", enDebito = 1_000, tarjetas = listOf(nu), recurrentes = listOf(netflix, luzDebito))

        val cargo = l.eventos.single { it.recurrente == netflix.id }
        assertEquals(nu.id, cargo.tarjeta)
        assertFalse(cargo.esSalida)
        // Solo la luz de débito baja el disponible.
        assertEquals(Dinero.pesos(500), l.disponible)
    }

}
