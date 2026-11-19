package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import io.github.joako601.finanzas.core.domain.recurrentes.Recurrente
import io.github.joako601.finanzas.core.domain.recurrentes.RecurrenteId
import io.github.joako601.finanzas.core.domain.recurrentes.RepositorioRecurrentes
import io.github.joako601.finanzas.core.domain.tarjetas.EstadoDeCuenta
import io.github.joako601.finanzas.core.domain.tarjetas.RepositorioTarjetas
import io.github.joako601.finanzas.core.domain.tarjetas.Tarjeta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Implementaciones mínimas en memoria para probar el dominio sin base de datos. */
internal class RepositorioEnMemoria : RepositorioLibro {
    private val cuentas = MutableStateFlow(
        listOf(Cuenta(CuentaId(1), "Saldos iniciales", TipoCuenta.CAPITAL)),
    )
    private val movimientos = MutableStateFlow(emptyList<Movimiento>())
    var transacciones = 0
        private set

    override fun observarCuentas(): Flow<List<Cuenta>> = cuentas

    override fun observarSaldos(): Flow<Map<CuentaId, Dinero>> = movimientos.map { saldosPorCuenta(it) }

    override fun observarMovimientos(): Flow<List<Movimiento>> = movimientos

    override suspend fun registrar(movimiento: Movimiento): MovimientoId {
        val id = MovimientoId(movimientos.value.size + 1L)
        movimientos.value = listOf(movimiento.copy(id = id)) + movimientos.value
        return id
    }

    override suspend fun eliminar(id: MovimientoId) {
        movimientos.value = movimientos.value.filterNot { it.id == id }
    }

    override suspend fun crearCuenta(nombre: String, tipo: TipoCuenta): Cuenta {
        val cuenta = Cuenta(CuentaId(cuentas.value.size + 1L), nombre, tipo)
        cuentas.value = cuentas.value + cuenta
        return cuenta
    }

    override suspend fun cuentaDeSaldosIniciales(): Cuenta = cuentas.value.first { it.tipo == TipoCuenta.CAPITAL }

    override suspend fun <T> enTransaccion(bloque: suspend () -> T): T {
        transacciones++
        return bloque()
    }
}

internal class RepositorioTarjetasEnMemoria : RepositorioTarjetas {
    val tarjetas = MutableStateFlow(emptyList<Tarjeta>())
    val estados = MutableStateFlow(emptyList<EstadoDeCuenta>())

    override fun observarTarjetas(): Flow<List<Tarjeta>> = tarjetas

    override fun observarEstadosDeCuenta(): Flow<List<EstadoDeCuenta>> = estados

    override suspend fun guardarTarjeta(cuenta: Cuenta, lineaDeCredito: Dinero, diaDeCorte: Int, diaLimite: Int): Tarjeta {
        val tarjeta = Tarjeta(cuenta, lineaDeCredito, diaDeCorte, diaLimite)
        tarjetas.value = tarjetas.value + tarjeta
        return tarjeta
    }

    override suspend fun guardarEstadoDeCuenta(estado: EstadoDeCuenta) {
        val sinDuplicado = estados.value.filterNot { it.tarjeta == estado.tarjeta && it.fechaCorte == estado.fechaCorte }
        estados.value = (sinDuplicado + estado.copy(id = estados.value.size + 1L)).sortedByDescending { it.fechaCorte }
    }
}

internal class RepositorioRecurrentesEnMemoria : RepositorioRecurrentes {
    val recurrentes = MutableStateFlow(emptyList<Recurrente>())

    override fun observarRecurrentes(): Flow<List<Recurrente>> = recurrentes.map { lista -> lista.sortedBy { it.proxima } }

    override suspend fun guardar(recurrente: Recurrente): RecurrenteId {
        val id = RecurrenteId(recurrentes.value.size + 1L)
        recurrentes.value = recurrentes.value + recurrente.copy(id = id)
        return id
    }

    override suspend fun cambiarProxima(id: RecurrenteId, proxima: LocalDate) {
        recurrentes.value = recurrentes.value.map { if (it.id == id) it.copy(proxima = proxima) else it }
    }

    override suspend fun eliminar(id: RecurrenteId) {
        recurrentes.value = recurrentes.value.filterNot { it.id == id }
    }
}
