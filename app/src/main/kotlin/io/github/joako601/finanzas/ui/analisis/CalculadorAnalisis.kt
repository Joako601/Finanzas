package io.github.joako601.finanzas.ui.analisis

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.disponible.EventoFuturo
import io.github.joako601.finanzas.core.domain.disponible.LineaDeTiempo
import io.github.joako601.finanzas.core.domain.disponible.TipoEvento
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import io.github.joako601.finanzas.core.domain.libro.resumir
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Horizontes de análisis disponibles en el selector global superior.
 * Sincroniza las 3 tarjetas de la pantalla con un solo toque.
 */
enum class PeriodoAnalisis(val diasHistoricos: Int) {
    QUINCE_DIAS(15),
    TREINTA_DIAS(30),
}

/** Fase temporal de un punto en la curva unificada de liquidez. */
enum class FasePuntoLiquidez {
    HISTORICO,
    HOY,
    PROYECCION,
}

/**
 * Un día dentro de la curva unificada de liquidez (pasado real + hoy + próximos 30 días).
 */
data class PuntoLiquidez(
    val fecha: LocalDate,
    val saldo: Dinero,
    val fase: FasePuntoLiquidez,
    val deltaDia: Dinero,
    val tieneActividad: Boolean,
    val esPuntoMasBajo: Boolean,
    val esPrimerFaltante: Boolean,
    val cantidadMovimientosActivos: Int = 0,
    val eventosFuturos: List<EventoFuturo> = emptyList(),
)

/**
 * Comparativa de entradas (ingresos) vs. salidas (gastos) del periodo seleccionado.
 */
data class FlujoPeriodo(
    val entradas: Dinero = Dinero.CERO,
    val salidas: Dinero = Dinero.CERO,
    val ritmoDiarioSalidas: Dinero = Dinero.CERO,
    val proporcionSalidas: Float = 0f,
    val proporcionEntradas: Float = 0f,
) {
    val tieneDatos: Boolean get() = entradas.esPositivo || salidas.esPositivo
}

/**
 * Una fila del ranking de gastos por categoría en barras horizontales 1D.
 */
data class GastoCategoria(
    val categoriaId: CuentaId?,
    val nombre: String,
    val monto: Dinero,
    val proporcionDelTotal: Float,
    val porcentajeRedondeado: Int,
    val esAgrupacionOtras: Boolean = false,
    val cantidadAgrupadas: Int = 0,
)

/**
 * Datos calculados para un [PeriodoAnalisis] específico (`15 días` o `30 días`).
 */
data class CortePeriodoAnalisis(
    val periodo: PeriodoAnalisis,
    val puntosCurva: List<PuntoLiquidez>,
    val indiceHoy: Int,
    val indicePuntoMasBajo: Int?,
    val indicePrimerFaltante: Int?,
    val saldoMinimoCurva: Dinero,
    val saldoMaximoCurva: Dinero,
    val mostrarLineaCero: Boolean,
    val flujo: FlujoPeriodo,
    val totalGastosCategorias: Dinero,
    val categorias: List<GastoCategoria>,
)

/**
 * Cálculo puro y determinista de las series históricas y proyecciones para `Análisis`.
 * No depende de Android ni de Compose para poder probarse de forma aislada.
 */
object CalculadorAnalisis {

    private const val MAX_CATEGORIAS_INDIVIDUALES = 5

    fun calcularCorte(
        periodo: PeriodoAnalisis,
        cuentasPorId: Map<CuentaId, Cuenta>,
        movimientos: List<Movimiento>,
        linea: LineaDeTiempo,
    ): CortePeriodoAnalisis {
        val hoy = linea.hoy
        val nDias = periodo.diasHistoricos

        // 1. Curva de liquidez unificada: [-nDias .. hoy .. +30 días]
        val cuentasActivo = cuentasPorId.values
            .filter { it.tipo == TipoCuenta.ACTIVO }
            .map { it.id }
            .toSet()

        val deltaPorFecha = mutableMapOf<LocalDate, Dinero>()
        val conteoPorFecha = mutableMapOf<LocalDate, Int>()
        var deltaPosteriorAHoy = Dinero.CERO

        for (movimiento in movimientos) {
            var deltaMovimiento = Dinero.CERO
            var tocaActivo = false
            for (asiento in movimiento.asientos) {
                if (asiento.cuenta in cuentasActivo) {
                    deltaMovimiento += asiento.monto
                    tocaActivo = true
                }
            }
            if (tocaActivo) {
                if (movimiento.fecha > hoy) {
                    deltaPosteriorAHoy += deltaMovimiento
                } else {
                    deltaPorFecha[movimiento.fecha] = (deltaPorFecha[movimiento.fecha] ?: Dinero.CERO) + deltaMovimiento
                    conteoPorFecha[movimiento.fecha] = (conteoPorFecha[movimiento.fecha] ?: 0) + 1
                }
            }
        }

        // Reconstrucción hacia atrás desde el saldo en cuentas al cierre de hoy.
        val saldosHistoricos = Array(nDias + 1) { Dinero.CERO }
        var saldoCursor = linea.enCuentas - deltaPosteriorAHoy
        for (i in nDias downTo 0) {
            saldosHistoricos[i] = saldoCursor
            val fechaPunto = hoy.minus(nDias - i, DateTimeUnit.DAY)
            val deltaDia = deltaPorFecha[fechaPunto] ?: Dinero.CERO
            saldoCursor -= deltaDia
        }

        // Eventos futuros agrupados por su fecha efectiva (los vencidos se agrupan en hoy).
        val eventosPorFechaEfectiva = linea.eventos.groupBy { maxOf(it.fecha, hoy) }
        val eventosQueMuevenDinero = linea.eventos.filter { it.tipo != TipoEvento.CORTE }

        val diaMasBajo = linea.diaMasBajo
        val fechaPrimerFaltante = linea.primerFaltante?.fecha

        val puntos = ArrayList<PuntoLiquidez>(nDias + 1 + LineaDeTiempo.DIAS)

        // Tramo histórico [hoy - nDias .. hoy - 1] y ancla [hoy]
        for (i in 0..nDias) {
            val fecha = hoy.minus(nDias - i, DateTimeUnit.DAY)
            val esHoy = i == nDias
            val delta = deltaPorFecha[fecha] ?: Dinero.CERO
            val conteo = conteoPorFecha[fecha] ?: 0
            val eventosEnHoy = if (esHoy) eventosPorFechaEfectiva[hoy].orEmpty() else emptyList()
            puntos.add(
                PuntoLiquidez(
                    fecha = fecha,
                    saldo = if (esHoy) linea.enCuentas else saldosHistoricos[i],
                    fase = if (esHoy) FasePuntoLiquidez.HOY else FasePuntoLiquidez.HISTORICO,
                    deltaDia = delta,
                    tieneActividad = conteo > 0 || eventosEnHoy.any { it.tipo != TipoEvento.CORTE },
                    esPuntoMasBajo = esHoy && diaMasBajo == hoy,
                    esPrimerFaltante = esHoy && fechaPrimerFaltante == hoy,
                    cantidadMovimientosActivos = conteo,
                    eventosFuturos = eventosEnHoy,
                ),
            )
        }

        // Tramo proyectado [hoy + 1 .. hoy + 30]
        var saldoProyectadoActual = eventosQueMuevenDinero
            .lastOrNull { maxOf(it.fecha, hoy) <= hoy }
            ?.saldoDespues
            ?: linea.enCuentas

        for (d in 1..LineaDeTiempo.DIAS) {
            val fechaFutura = hoy.plus(d, DateTimeUnit.DAY)
            val eventosDelDia = eventosPorFechaEfectiva[fechaFutura].orEmpty()
            val eventosConMonto = eventosDelDia.filter { it.tipo != TipoEvento.CORTE }
            val saldoAnterior = saldoProyectadoActual
            if (eventosConMonto.isNotEmpty()) {
                saldoProyectadoActual = eventosConMonto.last().saldoDespues
            }
            val deltaProyectado = saldoProyectadoActual - saldoAnterior
            puntos.add(
                PuntoLiquidez(
                    fecha = fechaFutura,
                    saldo = saldoProyectadoActual,
                    fase = FasePuntoLiquidez.PROYECCION,
                    deltaDia = deltaProyectado,
                    tieneActividad = eventosConMonto.isNotEmpty(),
                    esPuntoMasBajo = diaMasBajo == fechaFutura,
                    esPrimerFaltante = fechaPrimerFaltante == fechaFutura,
                    cantidadMovimientosActivos = 0,
                    eventosFuturos = eventosDelDia,
                ),
            )
        }

        val indiceHoy = nDias
        val indicePuntoMasBajo = diaMasBajo?.let { fechaBaja ->
            val diasDesdeHoy = (fechaBaja.toEpochDays() - hoy.toEpochDays()).toInt().coerceIn(0, LineaDeTiempo.DIAS)
            indiceHoy + diasDesdeHoy
        }
        val indicePrimerFaltante = fechaPrimerFaltante?.let { fechaFalta ->
            val diasDesdeHoy = (fechaFalta.toEpochDays() - hoy.toEpochDays()).toInt().coerceIn(0, LineaDeTiempo.DIAS)
            indiceHoy + diasDesdeHoy
        }

        val todosLosSaldos = puntos.map { it.saldo } + linea.disponible
        val saldoMinimo = todosLosSaldos.min()
        val saldoMaximo = todosLosSaldos.max()

        val mostrarLineaCero = saldoMinimo.esNegativo ||
            linea.disponible.esNegativo ||
            (linea.enCuentas.esPositivo && linea.disponible.centavos <= linea.enCuentas.centavos / 4L)

        // 2. Flujo y Categorías en la ventana histórica [hoy - (nDias - 1) .. hoy]
        val fechaInicioVentana = hoy.minus(nDias - 1, DateTimeUnit.DAY)
        val resumidosEnVentana = movimientos
            .asSequence()
            .filter { it.fecha in fechaInicioVentana..hoy }
            .map { it.resumir(cuentasPorId) }
            .toList()

        val entradas = resumidosEnVentana
            .filter { it.clase == ClaseMovimiento.INGRESO }
            .map { it.monto }
            .sumar()

        val gastos = resumidosEnVentana
            .filter { it.clase == ClaseMovimiento.GASTO }

        val salidas = gastos.map { it.monto }.sumar()
        val ritmoDiario = if (salidas.esPositivo) {
            Dinero((salidas.centavos.toDouble() / nDias.toDouble()).roundToLong())
        } else {
            Dinero.CERO
        }

        val sumaFlujoCentavos = entradas.centavos + salidas.centavos
        val flujo = if (sumaFlujoCentavos > 0L) {
            FlujoPeriodo(
                entradas = entradas,
                salidas = salidas,
                ritmoDiarioSalidas = ritmoDiario,
                proporcionSalidas = (salidas.centavos.toDouble() / sumaFlujoCentavos.toDouble()).toFloat().coerceIn(0f, 1f),
                proporcionEntradas = (entradas.centavos.toDouble() / sumaFlujoCentavos.toDouble()).toFloat().coerceIn(0f, 1f),
            )
        } else {
            FlujoPeriodo()
        }

        // 3. Desglose de gastos por categoría (Top 5 + fila 6 "Otras categorías (N)")
        val categorias = construirRankingCategorias(gastos, salidas)

        return CortePeriodoAnalisis(
            periodo = periodo,
            puntosCurva = puntos,
            indiceHoy = indiceHoy,
            indicePuntoMasBajo = indicePuntoMasBajo,
            indicePrimerFaltante = indicePrimerFaltante,
            saldoMinimoCurva = saldoMinimo,
            saldoMaximoCurva = saldoMaximo,
            mostrarLineaCero = mostrarLineaCero,
            flujo = flujo,
            totalGastosCategorias = salidas,
            categorias = categorias,
        )
    }

    private fun construirRankingCategorias(
        gastos: List<MovimientoResumido>,
        totalGastos: Dinero,
    ): List<GastoCategoria> {
        if (gastos.isEmpty() || !totalGastos.esPositivo) return emptyList()

        data class Acumulado(val id: CuentaId?, val nombre: String, val monto: Dinero)

        val ordenadas = gastos
            .groupBy { it.categoria?.id to (it.categoria?.nombre ?: "Otros gastos") }
            .map { (clave, lista) ->
                Acumulado(
                    id = clave.first,
                    nombre = clave.second,
                    monto = lista.map { it.monto }.sumar(),
                )
            }
            .filter { it.monto.esPositivo }
            .sortedWith(compareByDescending<Acumulado> { it.monto.centavos }.thenBy { it.nombre })

        if (ordenadas.isEmpty()) return emptyList()

        val totalCentavos = totalGastos.centavos.toDouble()

        fun aGastoCategoria(
            id: CuentaId?,
            nombre: String,
            monto: Dinero,
            esOtras: Boolean = false,
            cantidadAgrupadas: Int = 0,
        ): GastoCategoria {
            val proporcion = (monto.centavos.toDouble() / totalCentavos).toFloat().coerceIn(0f, 1f)
            val porcentaje = ((monto.centavos.toDouble() * 100.0) / totalCentavos)
                .roundToInt()
                .coerceIn(if (monto.esPositivo) 1 else 0, 100)
            return GastoCategoria(
                categoriaId = id,
                nombre = nombre,
                monto = monto,
                proporcionDelTotal = proporcion,
                porcentajeRedondeado = porcentaje,
                esAgrupacionOtras = esOtras,
                cantidadAgrupadas = cantidadAgrupadas,
            )
        }

        if (ordenadas.size <= MAX_CATEGORIAS_INDIVIDUALES) {
            return ordenadas.map { aGastoCategoria(it.id, it.nombre, it.monto) }
        }

        val principales = ordenadas.take(MAX_CATEGORIAS_INDIVIDUALES)
        val restantes = ordenadas.drop(MAX_CATEGORIAS_INDIVIDUALES)
        val montoRestantes = restantes.map { it.monto }.sumar()

        return buildList(MAX_CATEGORIAS_INDIVIDUALES + 1) {
            principales.forEach { add(aGastoCategoria(it.id, it.nombre, it.monto)) }
            add(
                aGastoCategoria(
                    id = null,
                    nombre = "",
                    monto = montoRestantes,
                    esOtras = true,
                    cantidadAgrupadas = restantes.size,
                ),
            )
        }
    }
}
