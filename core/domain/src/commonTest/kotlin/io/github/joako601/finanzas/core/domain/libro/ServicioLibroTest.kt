package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServicioLibroTest {

    private val fecha = LocalDate(2026, 11, 15)

    @Test
    fun crea_cuenta_con_saldo_inicial() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)

        val cuenta = servicio.crearCuentaDeDinero("  Nu débito ", Dinero(500_000), fecha)

        assertEquals("Nu débito", cuenta.nombre)
        assertEquals(TipoCuenta.ACTIVO, cuenta.tipo)
        assertEquals(Dinero(500_000), repositorio.observarSaldos().first()[cuenta.id])
        val movimientos = repositorio.observarMovimientos().first()
        assertEquals(1, movimientos.size)
        assertEquals(1, repositorio.transacciones)
    }

    @Test
    fun saldo_inicial_cero_no_registra_movimiento() = runTest {
        val repositorio = RepositorioEnMemoria()
        ServicioLibro(repositorio).crearCuentaDeDinero("Cartera", Dinero.CERO, fecha)
        assertTrue(repositorio.observarMovimientos().first().isEmpty())
    }
}

/** Implementación mínima en memoria para probar el dominio sin base de datos. */
private class RepositorioEnMemoria : RepositorioLibro {
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
