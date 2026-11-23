package io.github.joako601.finanzas.core.domain.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.totalEnCuentas
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Estado de avance de una meta o apartado de ahorro respecto a su objetivo y fecha límite. */
public enum class EstadoMetaAhorro {
    FECHA_ALCANZADA,
    EN_CURSO,
    COMPLETADA,
}

/** Análisis calculado para una meta o apartado individual. */
public data class AnalisisMetaAhorro(
    val meta: MetaAhorro,
    /** Cuánto falta para llegar al objetivo (`$0.00` si ya se completó). */
    val faltante: Dinero,
    /** Cuánto excede del objetivo si el usuario aportó de más (`$0.00` en caso normal). */
    val excedente: Dinero,
    /** Proporción acotada `0f..1f` para barras de progreso 1D. */
    val fraccionAvance: Float,
    /** Porcentaje entero redondeado (`0..N`, puede superar `100` si hay sobre-aportación). */
    val porcentajeEntero: Int,
    val estado: EstadoMetaAhorro,
    /** Días hasta [MetaAhorro.fechaLimite] (`null` si no tiene fecha límite; `<= 0` si ya venció). */
    val diasRestantes: Int?,
    /** Cuota sugerida por quincena (solo si está en curso y faltan al menos 15 días). */
    val cuotaQuincenal: Dinero?,
    /** Cuota sugerida por mes (solo si está en curso y faltan al menos 30 días). */
    val cuotaMensual: Dinero?,
)

/** Resumen consolidado de todos los apartados y su relación con la liquidez real en cuentas. */
public data class ResumenMetasAhorro(
    val hoy: LocalDate,
    val totalApartado: Dinero,
    /** Suma de lo apartado únicamente en metas activas con [MetaAhorro.reservarDeDisponible] = true. */
    val totalReservadoDeDisponible: Dinero,
    val totalObjetivo: Dinero,
    val saldoEnCuentas: Dinero,
    /**
     * Exceso de apartados reservados por encima del saldo positivo actual en cuentas de efectivo/débito.
     * Nunca se mezcla con deuda bancaria: sirve para avisar con transparencia si se sobre-reservó.
     */
    val sobreReserva: Dinero,
    val cantidadActivas: Int,
    val cantidadEnCurso: Int,
    val cantidadCompletadas: Int,
    val metasActivas: List<AnalisisMetaAhorro>,
    val metasArchivadas: List<AnalisisMetaAhorro>,
)

/**
 * Relación transparente entre el disponible tras pagos obligatorios (`LineaDeTiempo.disponible`)
 * y las metas activas marcadas con [MetaAhorro.reservarDeDisponible] = true.
 *
 * Los apartados voluntarios nunca se mezclan ni se disfrazan como deuda bancaria: se muestran
 * como una capa informativa encima del disponible real.
 */
public data class DesgloseDisponibleMetas(
    /** Suma de lo apartado en metas activas con reserva habilitada. */
    val apartadoEnMetas: Dinero,
    /** Liquidez libre tras cubrir pagos próximos y apartados reservados (`>= $0.00`). */
    val disponibleLibre: Dinero,
    /** Cuánto de los apartados reservados excede el disponible positivo actual (`$0.00` si alcanza). */
    val excesoApartados: Dinero,
    /** Metas activas con reserva y saldo positivo que componen [apartadoEnMetas]. */
    val metasReservadas: List<MetaAhorro>,
) {
    public val hayApartados: Boolean get() = apartadoEnMetas.esPositivo
}

/**
 * Motor puro de análisis para metas / apartados de ahorro ("cajitas").
 *
 * No altera la partida doble ni los saldos reales de efectivo/débito: evalúa progreso,
 * cuotas sugeridas hacia arriba (para nunca quedar corto) y consistencia contra el saldo real.
 */
public object AnalizadorMetas {

    public fun analizar(
        metas: List<MetaAhorro>,
        cuentas: List<Cuenta>,
        saldos: Map<CuentaId, Dinero>,
        hoy: LocalDate,
    ): ResumenMetasAhorro {
        val (archivadas, activas) = metas.partition { it.archivada }
        val analisisActivas = activas
            .map { analizarUna(it, hoy) }
            .sortedWith(
                compareBy<AnalisisMetaAhorro> { it.estado.ordinal }
                    .thenBy { it.meta.fechaLimite ?: LocalDate(9999, 12, 31) }
                    .thenByDescending { it.fraccionAvance }
                    .thenBy { it.meta.nombre },
            )
        val analisisArchivadas = archivadas
            .map { analizarUna(it, hoy) }
            .sortedBy { it.meta.nombre }

        val totalApartado = activas.map { it.apartado }.sumarSaturado()
        val totalReservado = activas
            .filter { it.reservarDeDisponible }
            .map { it.apartado }
            .sumarSaturado()
        val totalObjetivo = activas.map { it.objetivo }.sumarSaturado()
        val saldoEnCuentas = totalEnCuentas(cuentas, saldos)
        val liquidezPositiva = if (saldoEnCuentas.esPositivo) saldoEnCuentas else Dinero.CERO
        val sobreReserva = if (totalReservado > liquidezPositiva) {
            totalReservado - liquidezPositiva
        } else {
            Dinero.CERO
        }

        val completadas = analisisActivas.count { it.estado == EstadoMetaAhorro.COMPLETADA }
        return ResumenMetasAhorro(
            hoy = hoy,
            totalApartado = totalApartado,
            totalReservadoDeDisponible = totalReservado,
            totalObjetivo = totalObjetivo,
            saldoEnCuentas = saldoEnCuentas,
            sobreReserva = sobreReserva,
            cantidadActivas = analisisActivas.size,
            cantidadEnCurso = analisisActivas.size - completadas,
            cantidadCompletadas = completadas,
            metasActivas = analisisActivas,
            metasArchivadas = analisisArchivadas,
        )
    }

    public fun analizarUna(meta: MetaAhorro, hoy: LocalDate): AnalisisMetaAhorro {
        val faltante = if (meta.apartado < meta.objetivo) meta.objetivo - meta.apartado else Dinero.CERO
        val excedente = if (meta.apartado > meta.objetivo) meta.apartado - meta.objetivo else Dinero.CERO
        val fraccionDouble = (meta.apartado.centavos.toDouble() / meta.objetivo.centavos.toDouble())
            .coerceAtLeast(0.0)
        val porcentajeBruto = (fraccionDouble * 100.0 + 0.5)
            .coerceIn(0.0, Int.MAX_VALUE.toDouble())
            .toInt()
        // Si todavía falta dinero, nunca redondear a 100% para evitar contradecir "Te faltan $X".
        val porcentaje = if (faltante.esPositivo) porcentajeBruto.coerceAtMost(99) else porcentajeBruto
        val diasRestantes = meta.fechaLimite?.let { hoy.daysUntil(it) }

        val estado = when {
            !faltante.esPositivo -> EstadoMetaAhorro.COMPLETADA
            diasRestantes != null && diasRestantes <= 0 -> EstadoMetaAhorro.FECHA_ALCANZADA
            else -> EstadoMetaAhorro.EN_CURSO
        }

        val cuotaQuincenal = if (estado == EstadoMetaAhorro.EN_CURSO && diasRestantes != null && diasRestantes >= 15) {
            dividirHaciaArriba(faltante, diasRestantes / 15)
        } else {
            null
        }
        val cuotaMensual = if (estado == EstadoMetaAhorro.EN_CURSO && diasRestantes != null && diasRestantes >= 30) {
            dividirHaciaArriba(faltante, diasRestantes / 30)
        } else {
            null
        }

        return AnalisisMetaAhorro(
            meta = meta,
            faltante = faltante,
            excedente = excedente,
            fraccionAvance = fraccionDouble.toFloat().coerceIn(0f, 1f),
            porcentajeEntero = porcentaje,
            estado = estado,
            diasRestantes = diasRestantes,
            cuotaQuincenal = cuotaQuincenal,
            cuotaMensual = cuotaMensual,
        )
    }

    /**
     * Calcula el desglose entre [disponible] (resultado de `LineaDeTiempo`) y las metas activas
     * con `reservarDeDisponible = true`.
     */
    public fun calcularDesgloseDisponible(
        disponible: Dinero,
        metas: List<MetaAhorro>,
    ): DesgloseDisponibleMetas {
        val reservadas = metas
            .filter { !it.archivada && it.reservarDeDisponible && it.apartado.esPositivo }
            .sortedWith(compareByDescending<MetaAhorro> { it.apartado.centavos }.thenBy { it.nombre })
        val apartadoEnMetas = reservadas.map { it.apartado }.sumarSaturado()
        val disponiblePositivo = if (disponible.esPositivo) disponible else Dinero.CERO
        val disponibleLibre = if (disponiblePositivo > apartadoEnMetas) {
            disponiblePositivo - apartadoEnMetas
        } else {
            Dinero.CERO
        }
        val excesoApartados = if (apartadoEnMetas > disponiblePositivo) {
            apartadoEnMetas - disponiblePositivo
        } else {
            Dinero.CERO
        }
        return DesgloseDisponibleMetas(
            apartadoEnMetas = apartadoEnMetas,
            disponibleLibre = disponibleLibre,
            excesoApartados = excesoApartados,
            metasReservadas = reservadas,
        )
    }

    /** Suma cantidades no negativas saturando a `Long.MAX_VALUE` para agregaciones informativas sin crash. */
    private fun Iterable<Dinero>.sumarSaturado(): Dinero {
        var total = 0L
        for (monto in this) {
            val c = monto.centavos
            total = if (c > 0L && total > Long.MAX_VALUE - c) Long.MAX_VALUE else total + c
        }
        return Dinero(total)
    }

    /** Divide centavos redondeando hacia arriba sin desborde de `Long`, asegurando cubrir el objetivo. */
    private fun dividirHaciaArriba(monto: Dinero, partes: Int): Dinero {
        val divisor = partes.coerceAtLeast(1).toLong()
        val cociente = monto.centavos / divisor
        val residuo = monto.centavos % divisor
        return if (residuo == 0L) Dinero(cociente) else Dinero(cociente) + Dinero(1L)
    }
}
