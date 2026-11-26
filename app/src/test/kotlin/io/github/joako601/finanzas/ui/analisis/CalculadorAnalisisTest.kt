package io.github.joako601.finanzas.ui.analisis

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculadorAnalisisTest {

    private val hoy = LocalDate(2026, 11, 22)
    private val cuentaDebito = Cuenta(id = CuentaId(1), nombre = "Nu débito", tipo = TipoCuenta.ACTIVO)
    private val tarjetaCredito = Cuenta(id = CuentaId(2), nombre = "Nu crédito", tipo = TipoCuenta.PASIVO)

    private val catSuper = Cuenta(id = CuentaId(10), nombre = "Súper y despensa", tipo = TipoCuenta.GASTO)
    private val catTransporte = Cuenta(id = CuentaId(11), nombre = "Transporte", tipo = TipoCuenta.GASTO)
    private val catRenta = Cuenta(id = CuentaId(12), nombre = "Renta o hipoteca", tipo = TipoCuenta.GASTO)
    private val catSalud = Cuenta(id = CuentaId(13), nombre = "Salud", tipo = TipoCuenta.GASTO)
    private val catComida = Cuenta(id = CuentaId(14), nombre = "Comida fuera", tipo = TipoCuenta.GASTO)
    private val catSuscripciones = Cuenta(id = CuentaId(15), nombre = "Suscripciones", tipo = TipoCuenta.GASTO)
    private val catMascotas = Cuenta(id = CuentaId(16), nombre = "Mascotas", tipo = TipoCuenta.GASTO)
    private val catSueldo = Cuenta(id = CuentaId(20), nombre = "Sueldo", tipo = TipoCuenta.INGRESO)

    private val cuentasPorId = listOf(
        cuentaDebito,
        tarjetaCredito,
        catSuper,
        catTransporte,
        catRenta,
        catSalud,
        catComida,
        catSuscripciones,
        catMascotas,
        catSueldo,
    ).associateBy { it.id }

    @Test
    fun `reconstruye saldo historico exacto hacia atras y une con proyeccion en hoy`() {
        val haceTresDias = hoy.minus(3, DateTimeUnit.DAY)
        val ayer = hoy.minus(1, DateTimeUnit.DAY)
        val enCincoDias = hoy.plus(5, DateTimeUnit.DAY)

        // Hace 3 días: ingreso de +$10,000 a débito
        // Ayer: gasto de -$2,000 con débito
        // Hoy: compra con tarjeta de crédito de -$1,500 (no baja débito hoy, pero sí es gasto en Tarjeta 3)
        // Saldo actual en débito (enCuentas) = $8,000
        val movimientos = listOf(
            Movimiento(
                id = MovimientoId(1),
                fecha = haceTresDias,
                descripcion = "Quincena",
                asientos = listOf(
                    Asiento(cuentaDebito.id, Dinero.pesos(10_000)),
                    Asiento(catSueldo.id, -Dinero.pesos(10_000)),
                ),
            ),
            Movimiento(
                id = MovimientoId(2),
                fecha = ayer,
                descripcion = "Súper",
                asientos = listOf(
                    Asiento(catSuper.id, Dinero.pesos(2_000)),
                    Asiento(cuentaDebito.id, -Dinero.pesos(2_000)),
                ),
            ),
            Movimiento(
                id = MovimientoId(3),
                fecha = hoy,
                descripcion = "Transporte con TDC",
                asientos = listOf(
                    Asiento(catTransporte.id, Dinero.pesos(1_500)),
                    Asiento(tarjetaCredito.id, -Dinero.pesos(1_500)),
                ),
            ),
        )

        val linea = LineaDeTiempo(
            hoy = hoy,
            enCuentas = Dinero.pesos(8_000),
            fin = hoy.plus(30, DateTimeUnit.DAY),
            eventos = listOf(
                EventoFuturo(
                    fecha = enCincoDias,
                    tipo = TipoEvento.PAGO_FIJO,
                    nombre = "Renta",
                    monto = Dinero.pesos(5_000),
                    exacto = false,
                    saldoDespues = Dinero.pesos(3_000),
                ),
            ),
        )

        val corte = CalculadorAnalisis.calcularCorte(
            periodo = PeriodoAnalisis.TREINTA_DIAS,
            cuentasPorId = cuentasPorId,
            movimientos = movimientos,
            linea = linea,
        )

        // 30 días históricos + hoy + 30 días proyectados = 61 puntos
        assertEquals(61, corte.puntosCurva.size)
        assertEquals(30, corte.indiceHoy)

        // En hoy y ayer (después del gasto de $2,000) el saldo en cuentas es $8,000
        assertEquals(Dinero.pesos(8_000), corte.puntosCurva[corte.indiceHoy].saldo)
        assertEquals(Dinero.pesos(8_000), corte.puntosCurva[corte.indiceHoy - 1].saldo)

        // Hace 2 días (antes del gasto de ayer de $2,000) el saldo en cuentas era $10,000
        assertEquals(Dinero.pesos(10_000), corte.puntosCurva[corte.indiceHoy - 2].saldo)
        assertEquals(Dinero.pesos(10_000), corte.puntosCurva[corte.indiceHoy - 3].saldo)

        // Hace 4 días (antes del ingreso de $10,000) el saldo en cuentas era $0
        assertEquals(Dinero.CERO, corte.puntosCurva[corte.indiceHoy - 4].saldo)

        // En +5 días baja a $3,000 por el pago fijo proyectado
        val idxCincoDias = corte.indiceHoy + 5
        assertEquals(Dinero.pesos(3_000), corte.puntosCurva[idxCincoDias].saldo)
        assertEquals(idxCincoDias, corte.indicePuntoMasBajo)
        assertFalse(corte.mostrarLineaCero)

        // Flujo del periodo: Entradas = $10,000; Salidas = $2,000 (débito) + $1,500 (TDC) = $3,500
        assertEquals(Dinero.pesos(10_000), corte.flujo.entradas)
        assertEquals(Dinero.pesos(3_500), corte.flujo.salidas)
        assertEquals(Dinero.pesos(3_500), corte.totalGastosCategorias)
        assertEquals(2, corte.categorias.size)
        assertEquals("Súper y despensa", corte.categorias[0].nombre)
        assertEquals(Dinero.pesos(2_000), corte.categorias[0].monto)
        assertEquals("Transporte", corte.categorias[1].nombre)
        assertEquals(Dinero.pesos(1_500), corte.categorias[1].monto)

        // Neto, tasa de ahorro, método de pago y patrimonio (8,000 en cuentas − 1,500 en la tarjeta).
        assertEquals(Dinero.pesos(6_500), corte.flujo.neto)
        assertEquals(65, corte.flujo.tasaAhorro)
        assertEquals(Dinero.pesos(1_500), corte.flujo.salidasConCredito)
        assertEquals(Dinero.pesos(2_000), corte.flujo.salidasConDinero)
        assertEquals(Dinero.pesos(1_500), corte.patrimonio.deudaTarjetas)
        assertEquals(Dinero.pesos(6_500), corte.patrimonio.neto)
        // Sin salidas en los 30 días anteriores no hay comparación.
        assertEquals(null, corte.flujo.variacionSalidas)
    }

    private fun gasto(id: Long, fecha: LocalDate, pesos: Long) = Movimiento(
        id = MovimientoId(id),
        fecha = fecha,
        descripcion = "",
        asientos = listOf(Asiento(catSuper.id, Dinero.pesos(pesos)), Asiento(cuentaDebito.id, -Dinero.pesos(pesos))),
    )

    @Test
    fun `el mes va del dia 1 a hoy y se compara con los mismos dias del mes anterior`() {
        val linea = LineaDeTiempo(hoy = hoy, enCuentas = Dinero.pesos(5_000), fin = hoy.plus(30, DateTimeUnit.DAY), eventos = emptyList())
        val movimientos = listOf(
            gasto(1, LocalDate(2026, 11, 5), 1_200),
            gasto(2, LocalDate(2026, 10, 10), 1_000),
            // 25 de octubre: fuera del 1–22 de octubre, no cuenta para comparar.
            gasto(3, LocalDate(2026, 10, 25), 9_000),
        )

        val corte = CalculadorAnalisis.calcularCorte(PeriodoAnalisis.MES, cuentasPorId, movimientos, linea)

        assertEquals(LocalDate(2026, 11, 1), corte.desde)
        assertEquals(hoy, corte.hasta)
        assertEquals(21, corte.diasHistoricos)
        assertEquals(Dinero.pesos(1_200), corte.flujo.salidas)
        assertEquals(20, corte.flujo.variacionSalidas)
        assertEquals(null, corte.flujo.tasaAhorro)

        // Un mes atrás: octubre completo, comparado con septiembre (sin gastos).
        val octubre = CalculadorAnalisis.calcularCorte(PeriodoAnalisis.MES, cuentasPorId, movimientos, linea, mesDesplazamiento = -1)
        assertEquals(LocalDate(2026, 10, 1), octubre.desde)
        assertEquals(LocalDate(2026, 10, 31), octubre.hasta)
        assertEquals(Dinero.pesos(10_000), octubre.flujo.salidas)
        assertEquals(null, octubre.flujo.variacionSalidas)
        // La curva sigue anclada a hoy.
        assertEquals(21, octubre.diasHistoricos)
    }

    @Test
    fun `ventana anterior de un mes de 31 dias no se pasa al mes siguiente`() {
        val (desde, hasta) = CalculadorAnalisis.ventanaAnterior(PeriodoAnalisis.MES, LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
        assertEquals(LocalDate(2026, 2, 1), desde)
        assertEquals(LocalDate(2026, 2, 28), hasta)
        val (desde30, hasta30) = CalculadorAnalisis.ventanaAnterior(PeriodoAnalisis.TREINTA_DIAS, hoy.minus(29, DateTimeUnit.DAY), hoy)
        assertEquals(hoy.minus(59, DateTimeUnit.DAY), desde30)
        assertEquals(hoy.minus(30, DateTimeUnit.DAY), hasta30)
    }

    @Test
    fun `un gasto con fecha futura no baja el saldo de hoy en la curva`() {
        // El saldo en cuentas ($8,000) ya incluye un gasto de $1,000 capturado para pasado mañana.
        val movimientos = listOf(
            Movimiento(
                id = MovimientoId(1),
                fecha = hoy.plus(2, DateTimeUnit.DAY),
                descripcion = "Colegiatura adelantada",
                asientos = listOf(
                    Asiento(catSuper.id, Dinero.pesos(1_000)),
                    Asiento(cuentaDebito.id, -Dinero.pesos(1_000)),
                ),
            ),
        )
        val linea = LineaDeTiempo(hoy = hoy, enCuentas = Dinero.pesos(8_000), fin = hoy.plus(30, DateTimeUnit.DAY), eventos = emptyList())

        val corte = CalculadorAnalisis.calcularCorte(PeriodoAnalisis.TREINTA_DIAS, cuentasPorId, movimientos, linea)

        assertEquals(Dinero.pesos(9_000), corte.puntosCurva[corte.indiceHoy].saldo)
        assertEquals(Dinero.pesos(9_000), corte.puntosCurva[corte.indiceHoy - 15].saldo)
    }

    @Test
    fun `agrupa mas de 5 categorias en fila sexta Otras categorias`() {
        val categoriasGasto = listOf(
            catRenta to 5_000L,
            catSuper to 3_000L,
            catTransporte to 1_500L,
            catSalud to 1_000L,
            catComida to 800L,
            catSuscripciones to 400L,
            catMascotas to 300L,
        )

        val movimientos = categoriasGasto.mapIndexed { idx, (cat, pesos) ->
            Movimiento(
                id = MovimientoId((idx + 1).toLong()),
                fecha = hoy.minus(1, DateTimeUnit.DAY),
                descripcion = cat.nombre,
                asientos = listOf(
                    Asiento(cat.id, Dinero.pesos(pesos)),
                    Asiento(cuentaDebito.id, -Dinero.pesos(pesos)),
                ),
            )
        }

        val linea = LineaDeTiempo(
            hoy = hoy,
            enCuentas = Dinero.pesos(20_000),
            fin = hoy.plus(30, DateTimeUnit.DAY),
            eventos = emptyList(),
        )

        val corte = CalculadorAnalisis.calcularCorte(
            periodo = PeriodoAnalisis.TREINTA_DIAS,
            cuentasPorId = cuentasPorId,
            movimientos = movimientos,
            linea = linea,
        )

        // Top 5 individuales + 1 fila de "Otras categorías (2)" = 6 filas; expandida, las 7.
        assertEquals(6, corte.categorias.size)
        assertEquals(7, corte.todasLasCategorias.size)
        assertFalse(corte.categorias[0].esAgrupacionOtras)
        assertEquals("Renta o hipoteca", corte.categorias[0].nombre)

        val filaOtras = corte.categorias[5]
        assertTrue(filaOtras.esAgrupacionOtras)
        assertEquals(2, filaOtras.cantidadAgrupadas)
        assertEquals(Dinero.pesos(700), filaOtras.monto)
    }

    @Test
    fun una_categoria_propia_lleva_su_icono_elegido_a_la_fila() {
        val gimnasio = Cuenta(id = CuentaId(30), nombre = "Gimnasio", tipo = TipoCuenta.GASTO, icono = "salud")
        val cuentas = cuentasPorId + (gimnasio.id to gimnasio)
        val linea = LineaDeTiempo(hoy = hoy, enCuentas = Dinero.pesos(5_000), fin = hoy.plus(30, DateTimeUnit.DAY), eventos = emptyList())
        val movimientos = listOf(
            Movimiento(
                id = MovimientoId(1),
                fecha = hoy,
                descripcion = "",
                asientos = listOf(Asiento(gimnasio.id, Dinero.pesos(600)), Asiento(cuentaDebito.id, -Dinero.pesos(600))),
            ),
            gasto(2, hoy, 300),
        )

        val corte = CalculadorAnalisis.calcularCorte(PeriodoAnalisis.TREINTA_DIAS, cuentas, movimientos, linea)

        assertEquals("salud", corte.categorias.single { it.nombre == "Gimnasio" }.icono)
        // Las del catálogo sin ícono elegido siguen resolviéndose por nombre.
        assertEquals(null, corte.categorias.single { it.nombre == "Súper y despensa" }.icono)
    }

}
