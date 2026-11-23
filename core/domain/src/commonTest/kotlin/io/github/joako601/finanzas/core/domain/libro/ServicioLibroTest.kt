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

    @Test
    fun editar_cuenta_renombra_y_ajusta_saldo_con_movimiento_contable() = runTest {
        val repositorio = RepositorioEnMemoria()
        val servicio = ServicioLibro(repositorio)
        val cuenta = servicio.crearCuentaDeDinero("Nu debito", Dinero.pesos(1_000), fecha)

        val actualizada = servicio.editarCuentaDeDinero(
            cuenta = cuenta,
            nuevoNombre = "  Nu débito ",
            saldoActual = Dinero.pesos(1_000),
            nuevoSaldo = Dinero.pesos(1_350),
            fecha = fecha,
        )

        assertEquals("Nu débito", actualizada.nombre)
        assertEquals(Dinero.pesos(1_350), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(2, repositorio.observarMovimientos().first().size)

        // Ajuste hacia abajo ($1,350 -> $900)
        servicio.editarCuentaDeDinero(
            cuenta = actualizada,
            nuevoNombre = "Nu débito",
            saldoActual = Dinero.pesos(1_350),
            nuevoSaldo = Dinero.pesos(900),
            fecha = fecha,
        )
        assertEquals(Dinero.pesos(900), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(3, repositorio.observarMovimientos().first().size)

        // Solo cambio de nombre ($900 -> $900): no agrega movimiento nuevo
        val renombrada = servicio.editarCuentaDeDinero(
            cuenta = actualizada,
            nuevoNombre = "Nu principal",
            saldoActual = Dinero.pesos(900),
            nuevoSaldo = Dinero.pesos(900),
            fecha = fecha,
        )
        assertEquals("Nu principal", renombrada.nombre)
        assertEquals(Dinero.pesos(900), repositorio.observarSaldos().first()[cuenta.id])
        assertEquals(3, repositorio.observarMovimientos().first().size)
    }
}
