package io.github.joako601.finanzas.core.domain.libro

import io.github.joako601.finanzas.core.domain.dinero.Dinero
import kotlinx.coroutines.flow.first
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
