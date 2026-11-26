package io.github.joako601.finanzas.core.data.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.libro.Asiento
import io.github.joako601.finanzas.core.domain.libro.Cuenta
import io.github.joako601.finanzas.core.domain.libro.CuentaId
import io.github.joako601.finanzas.core.domain.libro.Movimiento
import io.github.joako601.finanzas.core.domain.libro.MovimientoId
import io.github.joako601.finanzas.core.domain.libro.TipoCuenta
import kotlinx.datetime.LocalDate

/*
 * Conversión entre tablas y dominio. Al leer, el constructor de Movimiento vuelve a
 * validar que los asientos sumen cero: si la base tuviera datos corruptos, falla aquí
 * en lugar de mostrar saldos equivocados.
 */

internal fun CuentaEntity.aDominio(): Cuenta = Cuenta(
    id = CuentaId(id),
    nombre = nombre,
    tipo = TipoCuenta.valueOf(tipo),
    archivada = archivada,
    icono = icono,
)

internal fun MovimientoConAsientos.aDominio(): Movimiento = Movimiento(
    id = MovimientoId(movimiento.id),
    fecha = LocalDate.fromEpochDays(movimiento.fechaDiaEpoca),
    descripcion = movimiento.descripcion,
    asientos = asientos.sortedBy { it.id }.map { Asiento(CuentaId(it.cuentaId), Dinero(it.centavos)) },
    meses = movimiento.meses,
)

internal fun Movimiento.aEntidad(creadoEnMillis: Long): MovimientoEntity = MovimientoEntity(
    id = 0,
    fechaDiaEpoca = fecha.toEpochDays(),
    descripcion = descripcion,
    creadoEnMillis = creadoEnMillis,
    meses = meses,
)

/** El id del movimiento se completa dentro de la transacción de inserción. */
internal fun Movimiento.asientosAEntidades(): List<AsientoEntity> = asientos.map {
    AsientoEntity(movimientoId = 0, cuentaId = it.cuenta.valor, centavos = it.monto.centavos)
}
