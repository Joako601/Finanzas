package io.github.joako601.finanzas.core.domain.metas

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

/**
 * Tope mensual tranquilo para una categoría de gasto.
 *
 * No obliga a presupuestar cada peso del mes: el usuario solo define límites en las
 * categorías que quiere cuidar. El gasto real se calcula al vuelo desde el libro para el
 * mes calendario vigente (incluyendo compras con tarjeta y excluyendo pagos de tarjeta).
 */
public data class TopeCategoria(
    val categoriaId: CuentaId,
    val limiteMensual: Dinero,
) {
    init {
        require(limiteMensual.esPositivo) { "El tope mensual debe ser mayor a cero" }
    }
}

@JvmInline
public value class MetaAhorroId(public val valor: Long)

/**
 * Apartado o meta de ahorro virtual ("cajita").
 *
 * No crea cuentas bancarias ficticias ni movimientos en la partida doble: representa una
 * etiqueta de reserva sobre la liquidez actual del usuario. Cuando [reservarDeDisponible]
 * está activo, su monto apartado se muestra conectado con el disponible real.
 */
public data class MetaAhorro(
    val id: MetaAhorroId? = null,
    val nombre: String,
    val objetivo: Dinero,
    val apartado: Dinero = Dinero.CERO,
    val fechaLimite: LocalDate? = null,
    val reservarDeDisponible: Boolean = true,
    val archivada: Boolean = false,
) {
    init {
        require(nombre.isNotBlank()) { "La meta necesita nombre" }
        require(nombre.length <= LARGO_MAXIMO_NOMBRE) { "Nombre demasiado largo" }
        require(objetivo.esPositivo) { "El objetivo debe ser mayor a cero" }
        require(!apartado.esNegativo) { "El monto apartado no puede ser negativo" }
    }

    public companion object {
        public const val LARGO_MAXIMO_NOMBRE: Int = 60
    }
}

/** Puerto de persistencia para topes mensuales por categoría y metas de ahorro. */
public interface RepositorioMetas {
    public fun observarTopes(): Flow<List<TopeCategoria>>
    public suspend fun guardarTope(tope: TopeCategoria)
    public suspend fun eliminarTope(categoriaId: CuentaId)

    public fun observarMetas(): Flow<List<MetaAhorro>>
    /** Crea [meta]; si ya trae apartado, lo anota en su historial con [fecha], en una sola transacción. */
    public suspend fun crearMeta(meta: MetaAhorro, fecha: LocalDate): MetaAhorroId
    public suspend fun actualizarMeta(meta: MetaAhorro)
    public suspend fun eliminarMeta(id: MetaAhorroId)

    /** Historial de una meta, del más reciente al más antiguo. Solo existe desde la v7 (D-023). */
    public fun observarAportes(meta: MetaAhorroId): Flow<List<AporteMeta>>

    /** Guarda [meta] con su apartado nuevo y anota [monto] en su historial, en una sola transacción. */
    public suspend fun moverApartado(meta: MetaAhorro, fecha: LocalDate, monto: Dinero)
}

/** Un aporte (monto positivo) o retiro (negativo) a un apartado. */
public data class AporteMeta(val fecha: LocalDate, val monto: Dinero)

public enum class ErrorTope {
    SIN_CATEGORIA,
    CATEGORIA_NO_ES_GASTO,
    MONTO_INVALIDO,
}

public enum class ErrorMeta {
    NOMBRE_VACIO,
    OBJETIVO_INVALIDO,
    APARTADO_INVALIDO,
}

public enum class ErrorMovimientoMeta {
    MONTO_INVALIDO,
    SUPERA_APARTADO,
}

/** Casos de uso y validaciones de topes mensuales y metas de ahorro. */
public class ServicioMetas(private val repositorio: RepositorioMetas) {

    public fun validarTope(categoria: Cuenta?, montoTexto: String): Set<ErrorTope> = buildSet {
        when {
            categoria == null -> add(ErrorTope.SIN_CATEGORIA)
            categoria.tipo != TipoCuenta.GASTO -> add(ErrorTope.CATEGORIA_NO_ES_GASTO)
        }
        val monto = Dinero.parsear(montoTexto)
        if (monto == null || !monto.esPositivo) add(ErrorTope.MONTO_INVALIDO)
    }

    public suspend fun guardarTope(categoria: Cuenta, limiteMensual: Dinero) {
        require(categoria.tipo == TipoCuenta.GASTO) { "Solo se pueden poner topes a categorías de gasto" }
        require(limiteMensual.esPositivo) { "El tope debe ser mayor a cero" }
        repositorio.guardarTope(TopeCategoria(categoria.id, limiteMensual))
    }

    public suspend fun eliminarTope(categoriaId: CuentaId) {
        repositorio.eliminarTope(categoriaId)
    }

    public fun validarMeta(nombre: String, objetivoTexto: String, apartadoTexto: String = ""): Set<ErrorMeta> = buildSet {
        if (nombre.isBlank()) add(ErrorMeta.NOMBRE_VACIO)
        val objetivo = Dinero.parsear(objetivoTexto)
        if (objetivo == null || !objetivo.esPositivo) add(ErrorMeta.OBJETIVO_INVALIDO)
        if (apartadoTexto.isNotBlank()) {
            val apartado = Dinero.parsear(apartadoTexto)
            if (apartado == null || apartado.esNegativo) add(ErrorMeta.APARTADO_INVALIDO)
        }
    }

    public suspend fun crearMeta(
        nombre: String,
        objetivo: Dinero,
        apartadoInicial: Dinero = Dinero.CERO,
        fechaLimite: LocalDate? = null,
        reservarDeDisponible: Boolean = true,
        /** Día del aporte inicial en el historial, para que cuadre con el apartado. */
        hoy: LocalDate,
    ): MetaAhorroId {
        val limpia = MetaAhorro(
            nombre = nombre.trim().take(MetaAhorro.LARGO_MAXIMO_NOMBRE),
            objetivo = objetivo,
            apartado = apartadoInicial,
            fechaLimite = fechaLimite,
            reservarDeDisponible = reservarDeDisponible,
        )
        return repositorio.crearMeta(limpia, hoy)
    }

    public suspend fun actualizarMeta(meta: MetaAhorro) {
        requireNotNull(meta.id) { "La meta debe tener id para actualizarse" }
        repositorio.actualizarMeta(meta.copy(nombre = meta.nombre.trim().take(MetaAhorro.LARGO_MAXIMO_NOMBRE)))
    }

    public suspend fun eliminarMeta(id: MetaAhorroId) {
        repositorio.eliminarMeta(id)
    }

    public fun validarMovimientoMeta(
        montoTexto: String,
        maximoPermitido: Dinero? = null,
        apartadoActualParaAporte: Dinero? = null,
    ): ErrorMovimientoMeta? {
        val monto = Dinero.parsear(montoTexto)
        if (monto == null || !monto.esPositivo) return ErrorMovimientoMeta.MONTO_INVALIDO
        if (maximoPermitido != null && monto > maximoPermitido) return ErrorMovimientoMeta.SUPERA_APARTADO
        if (apartadoActualParaAporte != null) {
            try {
                apartadoActualParaAporte + monto
            } catch (_: ArithmeticException) {
                return ErrorMovimientoMeta.MONTO_INVALIDO
            }
        }
        return null
    }

    public suspend fun aportarAMeta(meta: MetaAhorro, monto: Dinero, fecha: LocalDate): MetaAhorro {
        requireNotNull(meta.id) { "La meta debe tener id para aportar" }
        require(monto.esPositivo) { "El aporte debe ser mayor a cero" }
        val actualizada = meta.copy(apartado = meta.apartado + monto, archivada = false)
        repositorio.moverApartado(actualizada, fecha, monto)
        return actualizada
    }

    public suspend fun retirarDeMeta(
        meta: MetaAhorro,
        monto: Dinero,
        fecha: LocalDate,
        archivarSiQuedaEnCero: Boolean = false,
    ): MetaAhorro {
        requireNotNull(meta.id) { "La meta debe tener id para retirar" }
        require(monto.esPositivo) { "El retiro debe ser mayor a cero" }
        require(monto <= meta.apartado) { "No puedes retirar más de lo apartado" }
        val nuevoApartado = meta.apartado - monto
        val actualizada = meta.copy(
            apartado = nuevoApartado,
            archivada = if (archivarSiQuedaEnCero && nuevoApartado.esCero) true else meta.archivada,
        )
        repositorio.moverApartado(actualizada, fecha, -monto)
        return actualizada
    }
}
