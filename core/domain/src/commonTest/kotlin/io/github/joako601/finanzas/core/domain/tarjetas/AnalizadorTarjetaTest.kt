package io.github.joako601.finanzas.core.domain.tarjetas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.RepositorioEnMemoria
import io.github.joako601.finanzas.core.domain.libro.RepositorioTarjetasEnMemoria
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalizadorTarjetaTest {

    private val cuentaNu = Cuenta(CuentaId(4), "Nu crédito", TipoCuenta.PASIVO)
    private val nu = Tarjeta(cuentaNu, Dinero.pesos(10_000), diaDeCorte = 15, diaLimite = 5)

    private fun fecha(texto: String) = LocalDate.parse(texto)

    @Test
    fun detecta_pago_vencido_al_dia_siguiente_de_fecha_limite_y_separa_compras_nuevo_ciclo() {
        val estadoOctubre = EstadoDeCuenta(
            id = 1,
            tarjeta = nu.id,
            fechaCorte = fecha("2026-10-15"),
            fechaLimite = fecha("2026-11-05"),
            pagoSinIntereses = Dinero.pesos(4_000),
            pagoMinimo = Dinero.pesos(600),
            cortePredicho = null,
            limitePredicho = null,
        )
        // Se abonó $1,000 el 20 de octubre (cubre mínimo de $600, pero faltan $3,000 del corte).
        // Además se compraron $1,200 adicionales después del corte (deuda total hoy = $4,200).
        val pagos = listOf(PagoATarjeta(nu.id, fecha("2026-10-20"), Dinero.pesos(1_000)))

        val analisis = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(4_200),
            estados = listOf(estadoOctubre),
            pagos = pagos,
            hoy = fecha("2026-11-08"),
        )

        val vencido = assertNotNull(analisis.vencidoPendiente)
        assertEquals(Dinero.pesos(3_000), vencido.restante)
        assertEquals(3, vencido.diasVencido)

        val progreso = assertNotNull(analisis.progresoCiclo)
        assertTrue(progreso.estaVencido)
        assertTrue(progreso.minimoCubierto)
        assertTrue(progreso.tieneAbonoParcial)
        assertEquals(Dinero.CERO, progreso.restanteMinimo)

        // Desglose de deuda: $3,000 exigibles del corte vencido + $1,200 de compras nuevas = $4,200
        assertEquals(Dinero.pesos(4_200), analisis.desglose.deudaTotal)
        assertEquals(Dinero.pesos(3_000), analisis.desglose.exigibleCorteActual)
        assertEquals(Dinero.pesos(1_200), analisis.desglose.comprasNuevoCiclo)
        assertEquals(42, analisis.desglose.porcentajeUtilizacion)
        assertEquals(NivelUtilizacionLinea.MODERADO, analisis.desglose.nivelUtilizacion)
    }

    @Test
    fun maneja_sobregiro_sin_disponible_negativo_y_saldo_a_favor() {
        // Caso 1: Sobregiro ($11,500 de deuda sobre línea de $10,000)
        val conSobregiro = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(11_500),
            estados = emptyList(),
            pagos = emptyList(),
            hoy = fecha("2026-11-10"),
        )
        assertEquals(Dinero.CERO, conSobregiro.desglose.creditoDisponible)
        assertEquals(Dinero.pesos(1_500), conSobregiro.desglose.sobregiro)
        assertEquals(115, conSobregiro.desglose.porcentajeUtilizacion)
        assertEquals(NivelUtilizacionLinea.SOBREGIRO, conSobregiro.desglose.nivelUtilizacion)

        // Caso 2: Saldo a favor (-$800 de deuda = $800 a favor)
        val aFavor = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = -Dinero.pesos(800),
            estados = emptyList(),
            pagos = emptyList(),
            hoy = fecha("2026-11-10"),
        )
        assertEquals(Dinero.CERO, aFavor.desglose.deudaTotal)
        assertEquals(Dinero.pesos(800), aFavor.desglose.saldoAFavor)
        assertEquals(Dinero.pesos(10_800), aFavor.desglose.creditoDisponible)
        assertEquals(0, aFavor.desglose.porcentajeUtilizacion)
        assertEquals(NivelUtilizacionLinea.SALUDABLE, aFavor.desglose.nivelUtilizacion)
    }

    @Test
    fun simula_las_5_categorias_de_pago_e_intereses_con_iva() {
        val estadoVigente = EstadoDeCuenta(
            id = 1,
            tarjeta = nu.id,
            fechaCorte = fecha("2026-11-15"),
            fechaLimite = fecha("2026-12-05"),
            pagoSinIntereses = Dinero.pesos(6_000),
            pagoMinimo = Dinero.pesos(600),
            cortePredicho = null,
            limitePredicho = null,
        )
        // Deuda total = $8,000 ($6,000 del corte + $2,000 del nuevo ciclo)
        val analisis = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(8_000),
            estados = listOf(estadoVigente),
            pagos = emptyList(),
            hoy = fecha("2026-11-22"),
        )

        // 1. Debajo del mínimo ($200 < $600)
        val bajoMinimo = AnalizadorTarjeta.simularPago(analisis, Dinero.pesos(200), tasaAnualPorcentaje = 60)
        assertEquals(CategoriaSimulacionPago.DEBAJO_DEL_MINIMO, bajoMinimo.categoria)
        assertEquals(Dinero.pesos(400), bajoMinimo.faltanteParaMinimo)
        assertTrue(bajoMinimo.interesSobreSaldoFinanciado.esPositivo)

        // 2. Entre el mínimo y el pago sin intereses ($5,000: deja $1,000 sin cubrir de un corte de $6,000)
        // Con tasa 60% anual -> 5% mensual * 1.16 IVA = 5.8% mensual:
        // - Sobre $1,000 restantes = $58.00
        // - Sobre $6,000 saldo promedio del corte = $348.00
        val conInteres = AnalizadorTarjeta.simularPago(analisis, Dinero.pesos(5_000), tasaAnualPorcentaje = 60)
        assertEquals(CategoriaSimulacionPago.GENERA_INTERESES_ORDINARIOS, conInteres.categoria)
        assertEquals(Dinero.pesos(1_000), conInteres.saldoFinanciado)
        assertEquals(Dinero.pesos(58), conInteres.interesSobreSaldoFinanciado)
        assertEquals(Dinero.pesos(348), conInteres.interesSobreSaldoPromedioCorte)

        // 3. Cubre el pago sin intereses ($6,000), quedan $2,000 para el próximo mes
        val sinInteres = AnalizadorTarjeta.simularPago(analisis, Dinero.pesos(6_000), tasaAnualPorcentaje = 60)
        assertEquals(CategoriaSimulacionPago.SIN_INTERESES, sinInteres.categoria)
        assertEquals(Dinero.CERO, sinInteres.interesSobreSaldoFinanciado)
        assertEquals(Dinero.pesos(2_000), sinInteres.deudaRestanteTrasPago)

        // 4. Liquidación total ($8,000)
        val liquidacion = AnalizadorTarjeta.simularPago(analisis, Dinero.pesos(8_000), tasaAnualPorcentaje = 60)
        assertEquals(CategoriaSimulacionPago.LIQUIDACION_TOTAL, liquidacion.categoria)
        assertEquals(Dinero.CERO, liquidacion.deudaRestanteTrasPago)

        // 5. Pago superior a la deuda total ($8,500 -> $500 a favor)
        val sobrePago = AnalizadorTarjeta.simularPago(analisis, Dinero.pesos(8_500), tasaAnualPorcentaje = 60)
        assertEquals(CategoriaSimulacionPago.SALDO_A_FAVOR, sobrePago.categoria)
        assertEquals(Dinero.pesos(500), sobrePago.excedenteSaldoAFavor)
    }

    @Test
    fun casos_borde_tope_deuda_abono_parcial_transicion_vencido_y_saldo_a_favor_previo() {
        // 1. Tope con deuda: estado pide $4,000 (mínimo $600), pero deuda hoy es solo $1,500
        val estadoOct = EstadoDeCuenta(
            id = 1,
            tarjeta = nu.id,
            fechaCorte = fecha("2026-10-15"),
            fechaLimite = fecha("2026-11-05"),
            pagoSinIntereses = Dinero.pesos(4_000),
            pagoMinimo = Dinero.pesos(600),
            cortePredicho = null,
            limitePredicho = null,
        )
        val conTope = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(1_500),
            estados = listOf(estadoOct),
            pagos = emptyList(),
            hoy = fecha("2026-10-25"),
        )
        val progTope = assertNotNull(conTope.progresoCiclo)
        assertEquals(Dinero.pesos(1_500), progTope.restanteSinIntereses)
        assertEquals(Dinero.pesos(600), progTope.restanteMinimo)
        assertFalse(progTope.tieneAbonoParcial)

        // 2. Segundo abono parcial tras un primer abono de $200 (queda mínimo restante de $400)
        val conPrimerAbono = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(3_800),
            estados = listOf(estadoOct),
            pagos = listOf(PagoATarjeta(nu.id, fecha("2026-10-18"), Dinero.pesos(200))),
            hoy = fecha("2026-10-25"),
        )
        val simSegundoAbono = AnalizadorTarjeta.simularPago(conPrimerAbono, Dinero.pesos(150))
        assertEquals(CategoriaSimulacionPago.DEBAJO_DEL_MINIMO, simSegundoAbono.categoria)
        assertEquals(
            assertNotNull(conPrimerAbono.progresoCiclo).restanteMinimo,
            simSegundoAbono.montoAPagar + simSegundoAbono.faltanteParaMinimo,
        )

        // 3. Transición: cuando se captura el nuevo estado de noviembre, vencidoPendiente desaparece (no duplica)
        val estadoNov = EstadoDeCuenta(
            id = 2,
            tarjeta = nu.id,
            fechaCorte = fecha("2026-11-15"),
            fechaLimite = fecha("2026-12-05"),
            pagoSinIntereses = Dinero.pesos(5_000),
            pagoMinimo = Dinero.pesos(500),
            cortePredicho = null,
            limitePredicho = null,
        )
        val conNuevoEstado = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = Dinero.pesos(5_000),
            estados = listOf(estadoOct, estadoNov),
            pagos = emptyList(),
            hoy = fecha("2026-11-18"),
        )
        assertNull(conNuevoEstado.vencidoPendiente)
        assertEquals(Dinero.pesos(5_000), conNuevoEstado.desglose.exigibleCorteActual)

        // 4. Simular pago sobre tarjeta que ya tenía -$800 (es decir $800 de saldo a favor)
        val yaAFavor = AnalizadorTarjeta.analizar(
            tarjeta = nu,
            deuda = -Dinero.pesos(800),
            estados = emptyList(),
            pagos = emptyList(),
            hoy = fecha("2026-11-18"),
        )
        val simSobreSaldoAFavor = AnalizadorTarjeta.simularPago(yaAFavor, Dinero.pesos(500))
        assertEquals(CategoriaSimulacionPago.SALDO_A_FAVOR, simSobreSaldoAFavor.categoria)
        assertEquals(Dinero.pesos(1_300), simSobreSaldoAFavor.excedenteSaldoAFavor)
    }

    @Test
    fun servicio_rechaza_minimo_mayor_a_sin_intereses_y_actualiza_tarjeta() = runTest {
        val libro = RepositorioEnMemoria()
        val repo = RepositorioTarjetasEnMemoria()
        val servicio = ServicioTarjetas(libro, repo)

        val creada = servicio.crearTarjeta("Nu", Dinero.pesos(10_000), 15, 5, Dinero.CERO, fecha("2026-11-18"))
        val errores = servicio.capturarEstadoDeCuenta(
            tarjeta = creada,
            fechaCorte = fecha("2026-11-15"),
            fechaLimite = fecha("2026-12-05"),
            pagoSinInteresesTexto = "1,000",
            pagoMinimoTexto = "1,200",
        )
        assertEquals(setOf(ErrorEstadoDeCuenta.MINIMO_MAYOR_A_SIN_INTERESES), errores)

        val actualizada = servicio.actualizarTarjeta(
            actual = creada,
            nombre = "Nu Platino",
            lineaDeCredito = Dinero.pesos(25_000),
            diaDeCorte = 20,
            diaLimite = 10,
        )
        assertEquals("Nu Platino", actualizada.cuenta.nombre)
        assertEquals(Dinero.pesos(25_000), actualizada.lineaDeCredito)
        assertEquals(20, actualizada.diaDeCorte)
        assertEquals(10, actualizada.diaLimite)
        assertNull(analisisVacio(actualizada).progresoCiclo)
        assertFalse(analisisVacio(actualizada).desglose.sobregiro.esPositivo)
    }

    private fun analisisVacio(tarjeta: Tarjeta) = AnalizadorTarjeta.analizar(
        tarjeta = tarjeta,
        deuda = Dinero.CERO,
        estados = emptyList(),
        pagos = emptyList(),
        hoy = fecha("2026-11-22"),
    )
}
