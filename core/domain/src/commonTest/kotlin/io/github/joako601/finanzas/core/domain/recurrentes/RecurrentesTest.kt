package io.github.joako601.finanzas.core.domain.recurrentes

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.ClaseRegistro
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.RepositorioEnMemoria
import io.github.joako601.finanzas.core.domain.libro.RepositorioRecurrentesEnMemoria
import io.github.joako601.finanzas.core.domain.libro.ResultadoRegistro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import io.github.joako601.finanzas.core.domain.recordatorios.PlanRecordatorios
import io.github.joako601.finanzas.core.domain.recordatorios.TipoRecordatorio
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecurrentesTest {

    private fun fecha(texto: String) = LocalDate.parse(texto)

    /** Las fechas que siguen a [desde], una tras otra. */
    private fun Periodicidad.serie(inicio: String, desde: String, cuantas: Int): List<LocalDate> =
        generateSequence(siguienteDespues(fecha(inicio), fecha(desde))) { siguienteDespues(fecha(inicio), it) }
            .take(cuantas).toList()

    // ---- Periodicidad ----

    @Test
    fun mensual_el_31_vuelve_al_31_despues_de_un_mes_corto() {
        assertEquals(
            listOf(fecha("2027-02-28"), fecha("2027-03-31"), fecha("2027-04-30"), fecha("2027-05-31")),
            Periodicidad.MENSUAL.serie(inicio = "2027-01-31", desde = "2027-01-31", cuantas = 4),
        )
    }

    @Test
    fun bimestral_como_la_luz() {
        assertEquals(
            listOf(fecha("2027-02-10"), fecha("2027-04-10")),
            Periodicidad.BIMESTRAL.serie(inicio = "2026-12-10", desde = "2026-12-10", cuantas = 2),
        )
    }

    @Test
    fun anual_desde_un_29_de_febrero() {
        assertEquals(
            listOf(fecha("2029-02-28"), fecha("2030-02-28"), fecha("2031-02-28"), fecha("2032-02-29")),
            Periodicidad.ANUAL.serie(inicio = "2028-02-29", desde = "2028-02-29", cuantas = 4),
        )
    }

    @Test
    fun quincena_el_15_y_el_ultimo_dia() {
        assertEquals(fecha("2027-02-28"), Periodicidad.QUINCENAL.primeraDesde(fecha("2027-02-16")))
        assertEquals(
            listOf(fecha("2027-02-28"), fecha("2027-03-15"), fecha("2027-03-31"), fecha("2027-04-15")),
            Periodicidad.QUINCENAL.serie(inicio = "2027-02-01", desde = "2027-02-15", cuantas = 4),
        )
    }

    @Test
    fun semanal_y_catorcenal_cuentan_dias() {
        assertEquals(fecha("2026-11-27"), Periodicidad.SEMANAL.siguienteDespues(fecha("2026-11-20"), fecha("2026-11-26")))
        assertEquals(fecha("2026-12-18"), Periodicidad.CATORCENAL.siguienteDespues(fecha("2026-11-20"), fecha("2026-12-04")))
    }

    @Test
    fun antes_del_inicio_la_siguiente_es_la_primera() {
        assertEquals(fecha("2026-12-10"), Periodicidad.MENSUAL.siguienteDespues(fecha("2026-12-10"), fecha("2026-11-01")))
    }

    /**
     * Para cualquier periodicidad, inicio y fecha: la siguiente es posterior a la fecha y no
     * se salta ninguna (ningún día intermedio tiene otra siguiente distinta).
     */
    @Test
    fun la_siguiente_es_posterior_y_no_se_salta_ninguna() = runTest {
        val periodicidades = Arb.element(
            Periodicidad.SEMANAL, Periodicidad.CATORCENAL, Periodicidad.QUINCENAL, Periodicidad.MENSUAL,
            Periodicidad.BIMESTRAL, Periodicidad.ANUAL, Periodicidad.CadaMeses(5), Periodicidad.DosVecesAlMes(1, 30),
        )
        checkAll(periodicidades, Arb.int(0..3 * 366), Arb.int(0..4 * 366)) { periodicidad, inicioDias, desplazamiento ->
            val inicio = LocalDate.fromEpochDays(LocalDate(2026, 1, 1).toEpochDays() + inicioDias)
            val fecha = inicio.plus(desplazamiento - 30, DateTimeUnit.DAY)
            val siguiente = periodicidad.siguienteDespues(inicio, fecha)
            assertTrue(siguiente > fecha, "$periodicidad $inicio $fecha -> $siguiente")
            assertTrue(siguiente >= periodicidad.primeraDesde(inicio))
            var dia = fecha.plus(1, DateTimeUnit.DAY)
            while (dia < siguiente) {
                assertEquals(siguiente, periodicidad.siguienteDespues(inicio, dia), "se saltó una fecha: $periodicidad $inicio $dia")
                dia = dia.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    // ---- Servicio ----

    private val efectivo = Cuenta(CuentaId(10), "Efectivo", TipoCuenta.ACTIVO)
    private val luz = Cuenta(CuentaId(11), "Luz, agua y gas", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(12), "Sueldo", TipoCuenta.INGRESO)

    private val libro = RepositorioEnMemoria()
    private val repositorio = RepositorioRecurrentesEnMemoria()
    private val servicio = ServicioRecurrentes(libro, repositorio)

    private suspend fun cfe(inicio: String, hoy: String) = servicio.crear(
        "CFE", ClaseRegistro.GASTO, Dinero.pesos(650), luz, Periodicidad.BIMESTRAL, fecha(inicio), fecha(hoy),
    )

    @Test
    fun alta_con_fecha_pasada_deja_pendiente_la_siguiente_desde_hoy() = runTest {
        // El último recibo fue el 10 de septiembre; hoy es 19 de noviembre.
        assertEquals(fecha("2027-01-10"), cfe(inicio = "2026-09-10", hoy = "2026-11-19").proxima)
        // Si hoy toca, hoy queda pendiente.
        assertEquals(fecha("2026-11-10"), cfe(inicio = "2026-09-10", hoy = "2026-11-10").proxima)
    }

    @Test
    fun validar_alta_exige_categoria_de_la_misma_clase() {
        assertEquals(
            setOf(ErrorRecurrente.NOMBRE_VACIO, ErrorRecurrente.MONTO_INVALIDO, ErrorRecurrente.SIN_CATEGORIA),
            servicio.validarAlta(" ", "0", ClaseRegistro.INGRESO, luz),
        )
        assertEquals(emptySet(), servicio.validarAlta("Quincena", "12,500", ClaseRegistro.INGRESO, sueldo))
    }

    @Test
    fun registrar_anota_el_gasto_real_y_avanza_la_fecha_en_una_transaccion() = runTest {
        val recibo = cfe(inicio = "2026-11-10", hoy = "2026-11-01")
        val resultado = servicio.registrar(recibo, "712.40", efectivo, luz, fecha("2026-11-09"))

        assertIs<ResultadoRegistro.Valido>(resultado)
        val movimiento = libro.observarMovimientos().first().single()
        val resumen = movimiento.resumir(listOf(efectivo, luz).associateBy { it.id })
        assertEquals(Dinero.pesos(712, 40), resumen.monto)
        assertEquals("CFE", movimiento.descripcion)
        assertEquals(fecha("2027-01-10"), repositorio.observarRecurrentes().first().single().proxima)
        assertEquals(1, libro.transacciones)
    }

    @Test
    fun registrar_con_errores_no_guarda_ni_avanza() = runTest {
        val recibo = cfe(inicio = "2026-11-10", hoy = "2026-11-01")
        val resultado = servicio.registrar(recibo, "", null, luz, fecha("2026-11-09"))

        assertIs<ResultadoRegistro.Invalido>(resultado)
        assertTrue(libro.observarMovimientos().first().isEmpty())
        assertEquals(fecha("2026-11-10"), repositorio.observarRecurrentes().first().single().proxima)
    }

    @Test
    fun actualizar_modifica_nombre_monto_y_frecuencia() = runTest {
        val recibo = cfe(inicio = "2026-11-10", hoy = "2026-11-01")
        val actualizado = servicio.actualizar(
            id = recibo.id!!,
            nombre = "CFE Hogar",
            clase = ClaseRegistro.GASTO,
            montoEstimado = Dinero.pesos(820),
            categoria = luz,
            periodicidad = Periodicidad.MENSUAL,
            inicio = fecha("2026-11-15"),
            hoy = fecha("2026-11-01"),
        )
        assertEquals("CFE Hogar", actualizado.nombre)
        assertEquals(Dinero.pesos(820), actualizado.montoEstimado)
        assertEquals(Periodicidad.MENSUAL, actualizado.periodicidad)
        assertEquals(fecha("2026-11-15"), repositorio.observarRecurrentes().first().single().proxima)
    }

    @Test
    fun omitir_solo_avanza_la_fecha() = runTest {
        servicio.omitir(cfe(inicio = "2026-11-10", hoy = "2026-11-01"))
        assertTrue(libro.observarMovimientos().first().isEmpty())
        assertEquals(fecha("2027-01-10"), repositorio.observarRecurrentes().first().single().proxima)
    }

    // ---- Avisos ----

    private fun recurrente(clase: ClaseRegistro, proxima: String) = Recurrente(
        RecurrenteId(7), "Internet", clase, Dinero.pesos(399), luz.id, Periodicidad.MENSUAL, fecha(proxima), fecha(proxima),
    )

    @Test
    fun un_pago_fijo_avisa_un_dia_antes_con_su_monto() {
        val aviso = PlanRecordatorios.calcular(
            emptyList(), emptyList(), emptyList(), fecha("2026-11-19"),
            listOf(recurrente(ClaseRegistro.GASTO, "2026-11-25")),
        ).single()
        assertEquals(TipoRecordatorio.PAGO_RECURRENTE, aviso.tipo)
        assertEquals(LocalDateTime.parse("2026-11-24T09:00"), aviso.momento)
        assertEquals("recurrente:7:2026-11-25", aviso.clave)
        assertEquals("Internet", aviso.nombre)
        assertEquals(Dinero.pesos(399), aviso.monto)
    }

    @Test
    fun ingresos_y_pagos_cuyo_aviso_ya_paso_no_avisan() {
        val plan = PlanRecordatorios.calcular(
            emptyList(), emptyList(), emptyList(), fecha("2026-11-19"),
            listOf(recurrente(ClaseRegistro.INGRESO, "2026-11-25"), recurrente(ClaseRegistro.GASTO, "2026-11-19")),
        )
        assertTrue(plan.isEmpty())
    }
}
