package io.github.joako601.finanzas.core.domain.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.dinero.sumar
import io.github.joako601.finanzas.core.domain.libro.ClaseMovimiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoResumido
import io.github.joako601.finanzas.core.domain.libro.resumir
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Estado de avance de un tope mensual respecto al ritmo esperado para el día del mes. */
public enum class EstadoRitmoTope {
    EXCEDIDO,
    RITMO_ALTO,
    EN_RITMO,
}

/** Análisis completo de un tope mensual para una categoría en el mes calendario actual. */
public data class AnalisisTope(
    val tope: TopeCategoria,
    val categoria: Cuenta,
    val gastadoMesActual: Dinero,
    val gastadoMesAnterior: Dinero,
    /** Lo que queda disponible dentro del tope (`>= $0.00`). */
    val restante: Dinero,
    /** Cuánto se pasó del tope (`$0.00` si no se ha excedido). */
    val excedente: Dinero,
    /** Disponible diario para los días que faltan del mes (incluyendo hoy). */
    val disponiblePorDiaRestante: Dinero,
    /** Proporción real consumida (`0f..Float.MAX_VALUE`); puede superar `1f` si está excedido. */
    val fraccionConsumida: Float,
    /** Porcentaje entero redondeado (`0..N`). */
    val porcentajeEntero: Int,
    val ritmo: EstadoRitmoTope,
    /** Movimientos de gasto del mes actual pertenecientes a esta categoría (más recientes primero). */
    val movimientosMesActual: List<MovimientoResumido>,
)

/** Resumen consolidado de todos los topes activos del mes calendario actual. */
public data class ResumenTopesMes(
    val hoy: LocalDate,
    val diaDelMes: Int,
    val diasDelMes: Int,
    val diasRestantes: Int,
    val fraccionMesTranscurrido: Float,
    val totalLimite: Dinero,
    val totalGastado: Dinero,
    val totalRestante: Dinero,
    val totalExcedente: Dinero,
    val ritmoGlobal: EstadoRitmoTope,
    val fraccionGlobal: Float,
    val cantidadExcedidos: Int,
    val cantidadRitmoAlto: Int,
    val cantidadEnRitmo: Int,
    val topes: List<AnalisisTope>,
)

/** Sugerencia transparente basada en el gasto histórico real de una categoría. */
public data class SugerenciaTope(
    val promedioMensual: Dinero,
    val montoSugeridoRedondeado: Dinero,
    val mesesConDatos: Int,
    /** `true` solo cuando no hay historial previo y la referencia viene del gasto del mes actual. */
    val basadaEnMesActual: Boolean = false,
    /** `true` solo cuando el único mes con gasto en el historial corresponde exactamente al mes anterior. */
    val esDelMesAnterior: Boolean = false,
)

/**
 * Motor puro de cálculo para topes mensuales por categoría.
 *
 * Reglas contables:
 * - Solo cuenta movimientos clasificados como [ClaseMovimiento.GASTO] (efectivo, débito y
 *   compras con tarjeta de crédito).
 * - Nunca cuenta pagos de tarjeta, transferencias, ingresos ni saldos iniciales: cero doble conteo.
 * - Compara el consumo contra el día actual del mes con un colchón de tolerancia de 8 puntos
 *   porcentuales para evitar falsas alarmas al inicio del mes.
 */
public object AnalizadorTopes {

    /** Colchón sobre la fracción del mes transcurrido antes de marcar "Ritmo alto". */
    private const val MARGEN_RITMO_TOLERANCIA: Double = 0.08

    public fun analizar(
        topes: List<TopeCategoria>,
        cuentas: List<Cuenta>,
        movimientos: List<Movimiento>,
        hoy: LocalDate,
    ): ResumenTopesMes {
        val inicioMes = LocalDate(hoy.year, hoy.month, 1)
        val inicioMesSiguiente = inicioMes.plus(1, DateTimeUnit.MONTH)
        val inicioMesAnterior = inicioMes.minus(1, DateTimeUnit.MONTH)
        val diasDelMes = inicioMesSiguiente.minus(1, DateTimeUnit.DAY).day
        val diaDelMes = hoy.day.coerceIn(1, diasDelMes)
        val diasRestantes = (diasDelMes - diaDelMes).coerceAtLeast(0)
        val diasIncluyendoHoy = (diasDelMes - diaDelMes + 1).coerceAtLeast(1)
        val fraccionMesDouble = (diaDelMes.toDouble() / diasDelMes.toDouble()).coerceIn(0.0, 1.0)
        val fraccionMes = fraccionMesDouble.toFloat()

        val porId = cuentas.associateBy { it.id }

        // Clasificar solo gastos del mes actual y del mes anterior (sin pagos de tarjeta ni transferencias).
        val gastosMesActual = mutableMapOf<CuentaId, MutableList<MovimientoResumido>>()
        val sumaMesAnterior = mutableMapOf<CuentaId, Dinero>()

        for (mov in movimientos) {
            if (mov.fecha < inicioMesAnterior || mov.fecha >= inicioMesSiguiente) continue
            val resumido = mov.resumir(porId)
            if (resumido.clase != ClaseMovimiento.GASTO) continue
            val catId = resumido.categoria?.id ?: continue

            if (mov.fecha >= inicioMes) {
                gastosMesActual.getOrPut(catId) { mutableListOf() }.add(resumido)
            } else {
                sumaMesAnterior[catId] = (sumaMesAnterior[catId] ?: Dinero.CERO) + resumido.monto
            }
        }

        val analisisLista = topes.mapNotNull { tope ->
            val categoria = porId[tope.categoriaId] ?: return@mapNotNull null
            val listaMovs = gastosMesActual[tope.categoriaId].orEmpty()
                .sortedByDescending { it.movimiento.fecha }
            val gastado = listaMovs.map { it.monto }.sumar()
            val gastadoAnterior = sumaMesAnterior[tope.categoriaId] ?: Dinero.CERO
            val restante = if (gastado < tope.limiteMensual) tope.limiteMensual - gastado else Dinero.CERO
            val excedente = if (gastado > tope.limiteMensual) gastado - tope.limiteMensual else Dinero.CERO
            val disponiblePorDia = if (restante.esPositivo) {
                Dinero(restante.centavos / diasIncluyendoHoy.toLong())
            } else {
                Dinero.CERO
            }

            val fraccionDouble = (gastado.centavos.toDouble() / tope.limiteMensual.centavos.toDouble())
                .coerceAtLeast(0.0)
            val fraccion = fraccionDouble.toFloat()
            val porcentaje = ((gastado.centavos.toDouble() * 100.0) / tope.limiteMensual.centavos.toDouble() + 0.5)
                .toInt()
                .coerceAtLeast(0)
            val ritmo = evaluarRitmo(
                gastado = gastado,
                limite = tope.limiteMensual,
                fraccionConsumida = fraccionDouble,
                fraccionMes = fraccionMesDouble,
                esUltimoDia = diaDelMes >= diasDelMes,
            )

            AnalisisTope(
                tope = tope,
                categoria = categoria,
                gastadoMesActual = gastado,
                gastadoMesAnterior = gastadoAnterior,
                restante = restante,
                excedente = excedente,
                disponiblePorDiaRestante = disponiblePorDia,
                fraccionConsumida = fraccion,
                porcentajeEntero = porcentaje,
                ritmo = ritmo,
                movimientosMesActual = listaMovs,
            )
        }.sortedWith(
            compareBy<AnalisisTope> { it.ritmo.ordinal }
                .thenByDescending { it.fraccionConsumida }
                .thenBy { it.categoria.nombre },
        )

        val totalLimite = analisisLista.map { it.tope.limiteMensual }.sumar()
        val totalGastado = analisisLista.map { it.gastadoMesActual }.sumar()
        val totalRestante = if (totalGastado < totalLimite) totalLimite - totalGastado else Dinero.CERO
        val totalExcedente = if (totalGastado > totalLimite) totalGastado - totalLimite else Dinero.CERO
        val fraccionGlobalDouble = if (totalLimite.esPositivo) {
            (totalGastado.centavos.toDouble() / totalLimite.centavos.toDouble()).coerceAtLeast(0.0)
        } else {
            0.0
        }
        val fraccionGlobal = fraccionGlobalDouble.toFloat()
        val ritmoGlobal = if (totalLimite.esPositivo) {
            evaluarRitmo(
                gastado = totalGastado,
                limite = totalLimite,
                fraccionConsumida = fraccionGlobalDouble,
                fraccionMes = fraccionMesDouble,
                esUltimoDia = diaDelMes >= diasDelMes,
            )
        } else {
            EstadoRitmoTope.EN_RITMO
        }

        return ResumenTopesMes(
            hoy = hoy,
            diaDelMes = diaDelMes,
            diasDelMes = diasDelMes,
            diasRestantes = diasRestantes,
            fraccionMesTranscurrido = fraccionMes,
            totalLimite = totalLimite,
            totalGastado = totalGastado,
            totalRestante = totalRestante,
            totalExcedente = totalExcedente,
            ritmoGlobal = ritmoGlobal,
            fraccionGlobal = fraccionGlobal,
            cantidadExcedidos = analisisLista.count { it.ritmo == EstadoRitmoTope.EXCEDIDO },
            cantidadRitmoAlto = analisisLista.count { it.ritmo == EstadoRitmoTope.RITMO_ALTO },
            cantidadEnRitmo = analisisLista.count { it.ritmo == EstadoRitmoTope.EN_RITMO },
            topes = analisisLista,
        )
    }

    /**
     * Calcula sugerencias de tope por categoría basadas en el gasto real de hasta los últimos
     * 3 meses calendario anteriores (o el mes actual si aún no hay historial previo).
     */
    public fun sugerenciasPorCategoria(
        cuentas: List<Cuenta>,
        movimientos: List<Movimiento>,
        hoy: LocalDate,
    ): Map<CuentaId, SugerenciaTope> {
        val inicioMes = LocalDate(hoy.year, hoy.month, 1)
        val inicioMesSiguiente = inicioMes.plus(1, DateTimeUnit.MONTH)
        val inicioMesAnterior = inicioMes.minus(1, DateTimeUnit.MONTH)
        val claveMesAnterior = inicioMesAnterior.year * 12 + inicioMesAnterior.month.ordinal
        val inicioVentana = inicioMes.minus(3, DateTimeUnit.MONTH)
        val porId = cuentas.associateBy { it.id }

        // Mapa: categoriaId -> (claveMes "YYYY-MM" -> gasto total)
        val porCategoriaMesPrevio = mutableMapOf<CuentaId, MutableMap<Int, Dinero>>()
        val mesActualPorCategoria = mutableMapOf<CuentaId, Dinero>()

        for (mov in movimientos) {
            if (mov.fecha < inicioVentana || mov.fecha >= inicioMesSiguiente) continue
            val resumido = mov.resumir(porId)
            if (resumido.clase != ClaseMovimiento.GASTO) continue
            val catId = resumido.categoria?.id ?: continue

            if (mov.fecha >= inicioMes) {
                mesActualPorCategoria[catId] = (mesActualPorCategoria[catId] ?: Dinero.CERO) + resumido.monto
            } else {
                val claveMes = mov.fecha.year * 12 + mov.fecha.month.ordinal
                val mapa = porCategoriaMesPrevio.getOrPut(catId) { mutableMapOf() }
                mapa[claveMes] = (mapa[claveMes] ?: Dinero.CERO) + resumido.monto
            }
        }

        val categoriasCandidatas = porCategoriaMesPrevio.keys + mesActualPorCategoria.keys
        return buildMap {
            for (catId in categoriasCandidatas) {
                val mapaPrevios = porCategoriaMesPrevio[catId]?.filterValues { it.esPositivo }.orEmpty()
                val sugerencia = if (mapaPrevios.isNotEmpty()) {
                    val suma = mapaPrevios.values.sumar()
                    val promedio = Dinero(suma.centavos / mapaPrevios.size.toLong())
                    SugerenciaTope(
                        promedioMensual = promedio,
                        montoSugeridoRedondeado = redondearSugerencia(promedio),
                        mesesConDatos = mapaPrevios.size,
                        basadaEnMesActual = false,
                        esDelMesAnterior = mapaPrevios.size == 1 && claveMesAnterior in mapaPrevios,
                    )
                } else {
                    val actual = mesActualPorCategoria[catId]
                    if (actual != null && actual.esPositivo) {
                        SugerenciaTope(
                            promedioMensual = actual,
                            montoSugeridoRedondeado = redondearSugerencia(actual),
                            mesesConDatos = 1,
                            basadaEnMesActual = true,
                            esDelMesAnterior = false,
                        )
                    } else {
                        continue
                    }
                }
                put(catId, sugerencia)
            }
        }
    }

    private fun evaluarRitmo(
        gastado: Dinero,
        limite: Dinero,
        fraccionConsumida: Double,
        fraccionMes: Double,
        esUltimoDia: Boolean,
    ): EstadoRitmoTope = when {
        gastado > limite -> EstadoRitmoTope.EXCEDIDO
        !gastado.esPositivo -> EstadoRitmoTope.EN_RITMO
        gastado == limite && !esUltimoDia -> EstadoRitmoTope.RITMO_ALTO
        fraccionConsumida > fraccionMes + MARGEN_RITMO_TOLERANCIA -> EstadoRitmoTope.RITMO_ALTO
        else -> EstadoRitmoTope.EN_RITMO
    }

    /** Redondea hacia arriba a múltiplos limpios de $50 (o $10 si es menor a $100) usando suma exacta. */
    private fun redondearSugerencia(promedio: Dinero): Dinero {
        val paso = if (promedio.centavos >= 10_000L) 5_000L else 1_000L
        val resto = promedio.centavos % paso
        return if (resto == 0L) promedio else promedio + Dinero(paso - resto)
    }
}
