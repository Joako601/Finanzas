package io.github.joako601.finanzas.core.domain.recordatorios

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
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanRecordatoriosTest {

    // Corte el 15, límite el 5 del mes siguiente.
    private val nu = Tarjeta(Cuenta(CuentaId(4), "Nu", TipoCuenta.PASIVO), Dinero.pesos(30_000), 15, 5)

    private fun fecha(texto: String) = LocalDate.parse(texto)
    private fun momento(texto: String) = LocalDateTime.parse(texto)

    private fun estado(corte: String, limite: String, sinIntereses: Long) = EstadoDeCuenta(
        id = 1, tarjeta = nu.id, fechaCorte = fecha(corte), fechaLimite = fecha(limite),
        pagoSinIntereses = Dinero.pesos(sinIntereses), pagoMinimo = Dinero.pesos(1), cortePredicho = null,
        limitePredicho = null,
    )

    private fun plan(hoy: String, estados: List<EstadoDeCuenta> = emptyList(), pagos: List<PagoATarjeta> = emptyList()) =
        PlanRecordatorios.calcular(listOf(nu), estados, pagos, fecha(hoy))

    @Test
    fun con_saldo_a_favor_no_avisa_que_pague() {
        val avisos = PlanRecordatorios.calcular(
            listOf(nu), listOf(estado("2026-11-15", "2026-12-05", 1_000)), emptyList(), fecha("2026-11-20"),
            saldos = mapOf(nu.id to Dinero.pesos(950)),
        )
        assertTrue(avisos.none { it.tipo == TipoRecordatorio.PAGO_PROXIMO })
    }

    @Test
    fun avisa_un_dia_antes_del_proximo_corte() {
        val corte = plan("2026-11-19").single { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE }
        assertEquals(fecha("2026-12-15"), corte.fechaReferencia)
        assertEquals(momento("2026-12-14T09:00"), corte.momento)
    }

    @Test
    fun las_horas_elegidas_reemplazan_las_de_omision() {
        val horas = HorasAviso(dia = LocalTime(7, 30), noche = LocalTime(21, 0))
        val avisos = PlanRecordatorios.calcular(
            listOf(nu), emptyList(), emptyList(), fecha("2026-11-19"), horas = horas,
        )
        assertEquals(momento("2026-12-14T07:30"), avisos.single { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE }.momento)
        // Pedir la captura del estado (2 días después del corte) es el aviso de la noche.
        val captura = PlanRecordatorios.calcular(listOf(nu), emptyList(), emptyList(), fecha("2026-11-16"), horas = horas)
            .filter { it.tipo == TipoRecordatorio.CAPTURAR_ESTADO }
        assertTrue(captura.isNotEmpty())
        assertTrue(captura.all { it.momento.time == LocalTime(21, 0) })
    }

    @Test
    fun el_dia_del_corte_avisa_del_siguiente() {
        // El de hoy se conserva solo para recuperarlo si no se entregó ayer.
        val cortes = plan("2026-11-15").filter { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE }
        assertEquals(listOf(fecha("2026-11-15"), fecha("2026-12-15")), cortes.map { it.fechaReferencia })
    }

    @Test
    fun pide_capturar_dos_dias_despues_del_corte() {
        val capturas = plan("2026-11-16").filter { it.tipo == TipoRecordatorio.CAPTURAR_ESTADO }
        assertEquals(momento("2026-11-17T19:00"), capturas.first().momento)
        assertEquals(fecha("2026-11-15"), capturas.first().fechaReferencia)
    }

    @Test
    fun no_pide_capturar_lo_ya_capturado() {
        val capturas = plan("2026-11-19", listOf(estado("2026-11-16", "2026-12-06", 1_000)))
            .filter { it.tipo == TipoRecordatorio.CAPTURAR_ESTADO }
        // El del 15 nov ya está (con corte recorrido al 16); queda solo el del 15 dic.
        assertEquals(listOf(fecha("2026-12-15")), capturas.map { it.fechaReferencia })
    }

    @Test
    fun avisa_tres_y_un_dia_antes_del_limite_con_lo_que_falta() {
        val pagos = plan(
            "2026-11-19",
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(PagoATarjeta(nu.id, fecha("2026-11-20"), Dinero.pesos(1_000))),
        ).filter { it.tipo == TipoRecordatorio.PAGO_PROXIMO }
        assertEquals(listOf(momento("2026-12-02T09:00"), momento("2026-12-04T09:00")), pagos.map { it.momento })
        assertTrue(pagos.all { it.monto == Dinero.pesos(3_000) })
    }

    @Test
    fun ya_pagado_no_avisa_del_pago() {
        val pagos = plan(
            "2026-11-19",
            listOf(estado("2026-11-15", "2026-12-05", 4_000)),
            listOf(PagoATarjeta(nu.id, fecha("2026-11-20"), Dinero.pesos(4_000))),
        ).filter { it.tipo == TipoRecordatorio.PAGO_PROXIMO }
        assertTrue(pagos.isEmpty())
    }

    @Test
    fun captura_tardia_omite_avisos_de_dias_pasados() {
        // Capturado el 4 dic: el aviso de 3 días antes (2 dic) ya pasó hace más de un día; queda el de 1 día.
        val pagos = plan("2026-12-04", listOf(estado("2026-11-15", "2026-12-05", 4_000)))
            .filter { it.tipo == TipoRecordatorio.PAGO_PROXIMO }
        assertEquals(listOf(momento("2026-12-04T09:00")), pagos.map { it.momento })
    }

    @Test
    fun las_claves_son_estables_y_unicas() {
        val hoy = "2026-11-19"
        val estados = listOf(estado("2026-11-15", "2026-12-05", 4_000))
        val a = plan(hoy, estados)
        val b = plan(hoy, estados)
        assertEquals(a.map { it.clave }, b.map { it.clave })
        assertEquals(a.size, a.map { it.clave }.toSet().size)
    }

    @Test
    fun seleccionar_muestra_vencidos_no_entregados_y_programa_el_siguiente() {
        val recordatorios = plan("2026-11-16")
        val ahora = momento("2026-11-17T19:05")

        val seleccion = PlanRecordatorios.seleccionar(recordatorios, ahora, entregados = emptySet())
        assertEquals(listOf("capturar:4:2026-11-15"), seleccion.mostrarAhora.map { it.clave })
        assertTrue(seleccion.siguiente!!.momento > ahora)

        val yaEntregado = PlanRecordatorios.seleccionar(recordatorios, ahora, entregados = setOf("capturar:4:2026-11-15"))
        assertTrue(yaEntregado.mostrarAhora.isEmpty())
    }

    @Test
    fun seleccionar_descarta_avisos_viejos() {
        val recordatorios = plan("2026-11-16")
        // Una semana después sin haber corrido: el aviso del 17 ya no se muestra.
        val seleccion = PlanRecordatorios.seleccionar(recordatorios, momento("2026-11-24T10:00"), emptySet())
        assertTrue(seleccion.mostrarAhora.none { it.momento.date == fecha("2026-11-17") })
    }

    @Test
    fun sin_tarjetas_no_hay_nada_que_programar() {
        val seleccion = PlanRecordatorios.seleccionar(
            PlanRecordatorios.calcular(emptyList(), emptyList(), emptyList(), fecha("2026-11-19")),
            momento("2026-11-19T10:00"),
            emptySet(),
        )
        assertTrue(seleccion.mostrarAhora.isEmpty())
        assertNull(seleccion.siguiente)
    }

    /** Para cualquier día de corte, límite y fecha: siempre hay algo futuro que programar y nada antes de ayer. */
    @Test
    fun invariantes_del_plan() = runTest {
        val dias = Arb.int(1..31)
        checkAll(dias, dias, Arb.int(0..(6 * 366))) { corte, limite, desplazamiento ->
            val tarjeta = Tarjeta(Cuenta(CuentaId(4), "T", TipoCuenta.PASIVO), Dinero.CERO, corte, limite)
            val hoy = LocalDate.fromEpochDays(LocalDate(2026, 1, 1).toEpochDays() + desplazamiento)
            val plan = PlanRecordatorios.calcular(listOf(tarjeta), emptyList(), emptyList(), hoy)

            assertTrue(plan.any { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE && it.momento.date >= hoy })
            assertTrue(plan.zipWithNext().all { (a, b) -> a.momento <= b.momento }, "plan desordenado")
            assertEquals(plan.size, plan.map { it.clave }.toSet().size, "claves repetidas")
        }
    }

    // ---- Recuperación de avisos con el teléfono apagado ----

    /** Lo que muestra el programador al encender el teléfono en [ahora], sin nada entregado. */
    private fun alEncender(ahora: String, tarjetas: List<Tarjeta> = listOf(nu), estados: List<EstadoDeCuenta> = emptyList(), recurrentes: List<Recurrente> = emptyList()): List<Recordatorio> {
        val momentoAhora = momento(ahora)
        val plan = PlanRecordatorios.calcular(tarjetas, estados, emptyList(), momentoAhora.date, recurrentes)
        return PlanRecordatorios.seleccionar(plan, momentoAhora, entregados = emptySet()).mostrarAhora
    }

    @Test
    fun apagado_la_noche_anterior_recupera_el_aviso_de_corte_el_dia_del_corte() {
        // Aviso del 14 a las 9:00; el teléfono se prende el 15 (día del corte) a las 8:00.
        val corte = alEncender("2026-11-15T08:00").single { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE }
        assertEquals(fecha("2026-11-15"), corte.fechaReferencia)
        // Y el siguiente corte sigue programado.
        val siguiente = PlanRecordatorios.calcular(listOf(nu), emptyList(), emptyList(), fecha("2026-11-15"))
        assertTrue(siguiente.any { it.clave == "corte:4:2026-12-15" })
    }

    @Test
    fun apagado_recupera_el_aviso_de_pago_del_dia_anterior() {
        // Límite el 5 de dic.: el aviso de "1 día antes" (4 de dic.) se recupera el 5.
        val pago = alEncender("2026-12-05T08:00", estados = listOf(estado("2026-11-15", "2026-12-05", 1_000)))
            .single { it.tipo == TipoRecordatorio.PAGO_PROXIMO }
        assertEquals("pago:4:2026-12-05:1", pago.clave)
    }

    @Test
    fun apagado_recupera_el_aviso_de_pago_fijo_del_dia_anterior() {
        val internet = Recurrente(
            RecurrenteId(7), "Internet", ClaseRegistro.GASTO, Dinero.pesos(399), nu.id,
            Periodicidad.MENSUAL, fecha("2026-11-20"), fecha("2026-11-20"),
        )
        val aviso = alEncender("2026-11-20T08:00", tarjetas = emptyList(), recurrentes = listOf(internet)).single()
        assertEquals("recurrente:7:2026-11-20", aviso.clave)
    }

    @Test
    fun apagado_dos_dias_no_rescata_avisos_mas_viejos() {
        // Aviso del corte el 14; el teléfono se prende el 16: ya no aplica.
        assertTrue(alEncender("2026-11-16T08:00").none { it.tipo == TipoRecordatorio.ANTES_DEL_CORTE })
    }

    @Test
    fun una_tarjeta_archivada_no_avisa_ni_recupera_avisos() {
        val archivada = nu.copy(cuenta = nu.cuenta.copy(archivada = true))
        val estados = listOf(estado("2026-11-15", "2026-12-05", 1_000))
        assertTrue(PlanRecordatorios.calcular(listOf(archivada), estados, emptyList(), fecha("2026-12-05")).isEmpty())
        assertTrue(alEncender("2026-12-05T08:00", tarjetas = listOf(archivada), estados = estados).isEmpty())
    }
}
