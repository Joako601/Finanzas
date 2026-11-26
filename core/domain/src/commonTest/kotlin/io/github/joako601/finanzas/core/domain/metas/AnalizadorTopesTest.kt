package io.github.joako601.finanzas.core.domain.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Registro
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalizadorTopesTest {

    private val efectivo = Cuenta(CuentaId(1), "Efectivo", TipoCuenta.ACTIVO)
    private val debito = Cuenta(CuentaId(2), "Débito", TipoCuenta.ACTIVO)
    private val nu = Cuenta(CuentaId(3), "Nu", TipoCuenta.PASIVO)
    private val comida = Cuenta(CuentaId(4), "Comida fuera", TipoCuenta.GASTO)
    private val super_ = Cuenta(CuentaId(5), "Súper y despensa", TipoCuenta.GASTO)
    private val sueldo = Cuenta(CuentaId(6), "Sueldo", TipoCuenta.INGRESO)
    private val cuentas = listOf(efectivo, debito, nu, comida, super_, sueldo)

    @Test
    fun incluye_compras_con_tarjeta_y_excluye_pagos_de_tarjeta_y_transferencias() {
        val hoy = LocalDate(2026, 11, 15)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(300), efectivo, comida),
            Registro.gasto(LocalDate(2026, 11, 10), Dinero.pesos(450), nu, comida),
            Registro.pagoDeTarjeta(LocalDate(2026, 11, 12), Dinero.pesos(450), debito, nu),
            Registro.transferencia(LocalDate(2026, 11, 12), Dinero.pesos(500), debito, efectivo),
            Registro.ingreso(LocalDate(2026, 11, 15), Dinero.pesos(10_000), debito, sueldo),
        )
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(2_000)))

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)
        val analisis = resumen.topes.single()

        assertEquals(Dinero.pesos(750), analisis.gastadoMesActual)
        assertEquals(Dinero.pesos(1_250), analisis.restante)
        assertEquals(Dinero.CERO, analisis.excedente)
        assertEquals(EstadoRitmoTope.EN_RITMO, analisis.ritmo)
        assertEquals(2, analisis.movimientosMesActual.size)
    }

    @Test
    fun detecta_ritmo_alto_cuando_gasto_supera_progreso_del_mes_mas_colchon() {
        // Día 6 de 30 = 20% del mes; gastar 50% ($1,500 de $3,000) es claramente ritmo alto.
        val hoy = LocalDate(2026, 11, 6)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 11, 4), Dinero.pesos(1_500), debito, comida),
        )
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(3_000)))

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)
        val analisis = resumen.topes.single()

        assertEquals(EstadoRitmoTope.RITMO_ALTO, analisis.ritmo)
        assertEquals(1, resumen.cantidadRitmoAlto)
        assertEquals(24, resumen.diasRestantes)
        // Quedan $1,500 para 25 días (incluyendo hoy) = $60/día.
        assertEquals(Dinero.pesos(60), analisis.disponiblePorDiaRestante)
    }

    @Test
    fun en_el_ultimo_dia_del_mes_llegar_exacto_al_tope_es_en_ritmo_y_pasarse_es_excedido() {
        // Febrero 2028 es bisiesto (29 días).
        val hoy = LocalDate(2028, 2, 29)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2028, 2, 20), Dinero.pesos(2_000), debito, comida),
            Registro.gasto(LocalDate(2028, 2, 25), Dinero.pesos(2_100), debito, super_),
        )
        val topes = listOf(
            TopeCategoria(comida.id, Dinero.pesos(2_000)),
            TopeCategoria(super_.id, Dinero.pesos(2_000)),
        )

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)

        assertEquals(29, resumen.diasDelMes)
        assertEquals(0, resumen.diasRestantes)
        // Ordenado primero el excedido.
        assertEquals(super_.id, resumen.topes[0].categoria.id)
        assertEquals(EstadoRitmoTope.EXCEDIDO, resumen.topes[0].ritmo)
        assertEquals(Dinero.pesos(100), resumen.topes[0].excedente)
        assertEquals(Dinero.CERO, resumen.topes[0].restante)

        assertEquals(comida.id, resumen.topes[1].categoria.id)
        assertEquals(EstadoRitmoTope.EN_RITMO, resumen.topes[1].ritmo)
    }

    @Test
    fun compara_contra_mes_anterior_y_calcula_sugerencia_redondeada() {
        val hoy = LocalDate(2026, 11, 20)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 9, 10), Dinero.pesos(1_200), efectivo, comida),
            Registro.gasto(LocalDate(2026, 10, 12), Dinero.pesos(1_600), nu, comida),
            Registro.gasto(LocalDate(2026, 11, 5), Dinero.pesos(500), efectivo, comida),
        )
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(2_000)))

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)
        assertEquals(Dinero.pesos(1_600), resumen.topes.single().gastadoMesAnterior)

        val sugerencias = AnalizadorTopes.sugerenciasPorCategoria(cuentas, movimientos, hoy)
        val sug = sugerencias[comida.id]
        assertNotNull(sug)
        // Promedio de sep ($1,200) y oct ($1,600) = $1,400 -> redondeado a múltiplos de $50 = $1,400.
        assertEquals(Dinero.pesos(1_400), sug.promedioMensual)
        assertEquals(Dinero.pesos(1_400), sug.montoSugeridoRedondeado)
        assertEquals(2, sug.mesesConDatos)
    }

    @Test
    fun sin_topes_devuelve_resumen_vacio_sin_division_por_cero() {
        val resumen = AnalizadorTopes.analizar(emptyList(), cuentas, emptyList(), LocalDate(2026, 11, 22))
        assertTrue(resumen.topes.isEmpty())
        assertEquals(Dinero.CERO, resumen.totalLimite)
        assertEquals(0f, resumen.fraccionGlobal)
        assertEquals(EstadoRitmoTope.EN_RITMO, resumen.ritmoGlobal)
    }

    @Test
    fun enero_compara_correctamente_contra_diciembre_del_anio_anterior_y_distingue_origen_de_sugerencia() {
        val hoy = LocalDate(2027, 1, 10)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 12, 18), Dinero.pesos(1_850), efectivo, comida),
            Registro.gasto(LocalDate(2027, 1, 4), Dinero.pesos(400), efectivo, super_),
        )
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(2_000)))

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)
        assertEquals(Dinero.pesos(1_850), resumen.topes.single().gastadoMesAnterior)
        assertEquals(Dinero.CERO, resumen.topes.single().gastadoMesActual)

        val sugerencias = AnalizadorTopes.sugerenciasPorCategoria(cuentas, movimientos, hoy)
        val sugComida = assertNotNull(sugerencias[comida.id])
        assertEquals(1, sugComida.mesesConDatos)
        assertEquals(false, sugComida.basadaEnMesActual)

        val sugSuper = assertNotNull(sugerencias[super_.id])
        assertEquals(1, sugSuper.mesesConDatos)
        assertEquals(true, sugSuper.basadaEnMesActual)
    }

    @Test
    fun frontera_exacta_del_margen_de_ritmo_no_da_falso_ritmo_alto() {
        // Día 3 de 30 = 10% exacto; gasto 18% exacto ($180 de $1,000) cae justo en 10% + 8% -> EN_RITMO.
        val hoy = LocalDate(2026, 11, 3)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 11, 2), Dinero.pesos(180), efectivo, comida),
        )
        val resumen = AnalizadorTopes.analizar(
            listOf(TopeCategoria(comida.id, Dinero.pesos(1_000))),
            cuentas,
            movimientos,
            hoy,
        )
        assertEquals(EstadoRitmoTope.EN_RITMO, resumen.topes.single().ritmo)
    }

    @Test
    fun sugerencia_distingue_mes_pasado_de_un_mes_mas_antiguo() {
        val hoy = LocalDate(2026, 11, 15)
        val transporte = Cuenta(CuentaId(9), "Transporte", TipoCuenta.GASTO)
        val cuentasExt = cuentas + transporte
        val movimientos = listOf(
            // Comida: gasto solo en octubre (el mes pasado) -> esDelMesAnterior = true
            Registro.gasto(LocalDate(2026, 10, 10), Dinero.pesos(1_200), efectivo, comida),
            // Transporte: gasto solo en septiembre (hace 2 meses) -> esDelMesAnterior = false
            Registro.gasto(LocalDate(2026, 9, 10), Dinero.pesos(800), efectivo, transporte),
        )
        val sugerencias = AnalizadorTopes.sugerenciasPorCategoria(cuentasExt, movimientos, hoy)
        assertEquals(true, sugerencias[comida.id]?.esDelMesAnterior)
        assertEquals(false, sugerencias[transporte.id]?.esDelMesAnterior)
        assertEquals(1, sugerencias[transporte.id]?.mesesConDatos)
    }

    @Test
    fun historial_de_los_3_meses_anteriores_y_gasto_fuera_de_topes() {
        val hoy = LocalDate(2026, 11, 15)
        val movimientos = listOf(
            Registro.gasto(LocalDate(2026, 10, 5), Dinero.pesos(2_500), efectivo, comida),
            Registro.gasto(LocalDate(2026, 9, 5), Dinero.pesos(1_200), efectivo, comida),
            // Agosto sin gastos: igual aparece, en cero y cumplido.
            Registro.gasto(LocalDate(2026, 7, 5), Dinero.pesos(9_999), efectivo, comida),
            // Súper no tiene tope: cuenta como "fuera de topes" este mes.
            Registro.gasto(LocalDate(2026, 11, 2), Dinero.pesos(800), efectivo, super_),
            Registro.gasto(LocalDate(2026, 11, 3), Dinero.pesos(100), efectivo, comida),
        )
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(2_000)))

        val resumen = AnalizadorTopes.analizar(topes, cuentas, movimientos, hoy)
        val historial = resumen.topes.single().historial

        // Del más reciente al más antiguo; se compara contra el tope de hoy (no se guarda el de antes).
        assertEquals(listOf(LocalDate(2026, 10, 1), LocalDate(2026, 9, 1), LocalDate(2026, 8, 1)), historial.map { it.mes })
        assertEquals(listOf(Dinero.pesos(2_500), Dinero.pesos(1_200), Dinero.CERO), historial.map { it.gastado })
        assertEquals(listOf(false, true, true), historial.map { it.cumplido })
        assertEquals(Dinero.pesos(2_500), resumen.topes.single().gastadoMesAnterior)
        assertEquals(Dinero.pesos(800), resumen.gastadoSinTope)
    }


    @Test
    fun con_saldo_restante_el_porcentaje_no_se_redondea_a_100() {
        // $2,997 de $3,000 = 99.9 %: quedan $3, así que no puede decir 100 %.
        val hoy = LocalDate(2026, 11, 28)
        val topes = listOf(TopeCategoria(comida.id, Dinero.pesos(3_000)))
        val casi = AnalizadorTopes.analizar(
            topes, cuentas, listOf(Registro.gasto(LocalDate(2026, 11, 4), Dinero.pesos(2_997), debito, comida)), hoy,
        ).topes.single()
        assertEquals(99, casi.porcentajeEntero)

        val justo = AnalizadorTopes.analizar(
            topes, cuentas, listOf(Registro.gasto(LocalDate(2026, 11, 4), Dinero.pesos(3_000), debito, comida)), hoy,
        ).topes.single()
        assertEquals(100, justo.porcentajeEntero)
    }
}
